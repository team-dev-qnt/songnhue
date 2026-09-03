import { useQuery } from '@tanstack/react-query';
import { Alert, Card, DatePicker, Select, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';

import {
  type Station,
  type SyncDailyRow,
  type SyncQualityReport,
  type SyncQualityRow,
} from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

/**
 * **BC-13 — Nhật ký đồng bộ & chất lượng dữ liệu** (T34.3).
 *
 * ⭐⭐ Cột **"Khung bỏ sót"** là **phép đo duy nhất của NFR-03** (T37.1: 7 ngày liên tục / 1008
 * khung, hỏng giữa chừng là đếm lại từ đầu). Mọi lựa chọn hiển thị ở trang này phục vụ đúng một
 * câu hỏi: *hôm ấy có khung nào không về không, và vì sao*.
 *
 * ⛔⛔ **Ô rỗng ⛔ KHÔNG được vẽ thành `0`.** `soKhungBoSot = null` nghĩa là *chưa đo được* và
 * `lyDoTrong` nói lý do; `0` nghĩa là *poller chạy hoàn hảo*. Hai câu trái ngược nhau, và nếu
 * trang này gộp chúng thì con số nghiệm thu NFR-03 được tính trên một tập có số bịa. Quy tắc 16
 * ép ở hàm dựng phía backend (`DoDayDuKhung`), và ở đây là vế hiển thị của cùng ràng buộc ấy.
 *
 * ⚠ **Hai bảng, ⛔ không một.** Chúng có *hạt* khác nhau: bảng trên là (điểm đo × chỉ số × ngày) —
 * *số liệu nào không về*; bảng dưới là (nguồn × ngày) — *vì sao*. Trộn lại là mất nghĩa cả hai.
 */
export function SyncQualityReportPage() {
  const [khoang, setKhoang] = useState<[Dayjs, Dayjs]>(() => [dayjs().subtract(6, 'day'), dayjs()]);
  const [diemDo, setDiemDo] = useState<string | undefined>();

  const tuNgay = khoang[0].format('YYYY-MM-DD');
  const denNgay = khoang[1].format('YYYY-MM-DD');

  const dsDiemDo = useQuery({
    queryKey: ['hyd', 'stations', 'chon-bao-cao'],
    queryFn: () => api.get<{ content: Station[] }>('/hyd/stations?page=0&size=100'),
  });

  const baoCao = useQuery({
    queryKey: ['hyd', 'bao-cao', 'dong-bo', tuNgay, denNgay, diemDo ?? ''],
    queryFn: () =>
      api.get<SyncQualityReport>(
        `/hyd/bao-cao/dong-bo?tuNgay=${tuNgay}&denNgay=${denNgay}` +
          (diemDo ? `&stationPublicId=${diemDo}` : ''),
      ),
  });

  const khungPhut = baoCao.data?.khungPhut ?? 10;

  const cotChatLuong: ColumnsType<SyncQualityRow> = [
    { title: 'Ngày', dataIndex: 'ngay', width: 120, fixed: 'left' },
    {
      title: 'Điểm đo',
      dataIndex: 'stationName',
      width: 260,
      ellipsis: true,
      render: (_, row) => (
        <Space size={4}>
          <span>{row.stationName}</span>
          <Typography.Text type="secondary">({row.stationCode})</Typography.Text>
          {row.stationActive ? null : <Tag>Đã tắt</Tag>}
        </Space>
      ),
    },
    { title: 'Chỉ số', dataIndex: 'measurementTypeName', width: 170, ellipsis: true },
    {
      title: 'Hợp lệ',
      dataIndex: 'soHopLe',
      width: 100,
      align: 'right',
    },
    {
      title: 'Nghi ngờ',
      dataIndex: 'soNghiNgo',
      width: 110,
      align: 'right',
      // ⚠ Số nghi ngờ > 0 ⛔ KHÔNG phải một sự cố — đó là hàng chờ duyệt. Tô nhạt, ⛔ không tô đỏ.
      render: (v: number) => (v > 0 ? <Tag color="orange">{v}</Tag> : v),
    },
    {
      title: 'Đã xoá',
      dataIndex: 'soDaXoa',
      width: 100,
      align: 'right',
    },
    {
      title: `Khung mong đợi (${khungPhut}′)`,
      dataIndex: 'soKhungMongDoi',
      width: 160,
      align: 'right',
      render: (v: number | null) => oRong(v),
    },
    {
      // ⭐⭐ Cột chịu lực của cả trang.
      title: 'Khung bỏ sót',
      dataIndex: 'soKhungBoSot',
      width: 150,
      align: 'right',
      render: (v: number | null, row) => {
        if (v === null) {
          return oRong(v, row.lyDoTrong);
        }
        return v === 0 ? <Tag color="green">0</Tag> : <Tag color="red">{v}</Tag>;
      },
    },
    {
      title: 'Đầy đủ',
      dataIndex: 'tyLeDayDu',
      width: 110,
      align: 'right',
      // ⚠ Chuỗi từ backend — ⛔ không Number() rồi toFixed() lại: thang đo đã được BE chốt.
      render: (v: string | null, row) => (v === null ? oRong(v, row.lyDoTrong) : `${v}%`),
    },
    {
      title: 'Tính lúc',
      dataIndex: 'tinhLuc',
      width: 180,
      render: (v: string | null) =>
        v ? formatDateTime(v) : oRong(null, 'Kỳ này chưa được tổng hợp'),
    },
  ];

  const cotDongBo: ColumnsType<SyncDailyRow> = [
    { title: 'Ngày', dataIndex: 'ngay', width: 120, fixed: 'left' },
    {
      title: 'Nguồn',
      dataIndex: 'sourceName',
      width: 240,
      ellipsis: true,
      render: (_, row) => `${row.sourceName} (${row.sourceCode})`,
    },
    { title: 'Lượt gọi', dataIndex: 'soLuot', width: 110, align: 'right' },
    { title: 'Thành công', dataIndex: 'soThanhCong', width: 120, align: 'right' },
    { title: 'Một phần', dataIndex: 'soMotPhan', width: 110, align: 'right' },
    {
      title: 'Hỏng',
      dataIndex: 'soHong',
      width: 100,
      align: 'right',
      render: (v: number) => (v > 0 ? <Tag color="red">{v}</Tag> : v),
    },
    {
      title: (
        <Tooltip title="Lượt đã bỏ vì mọi điểm đo đã có bản ghi của khung hiện tại. Số này cao là TỐT — rate-limit đang làm việc.">
          Bỏ qua vì đã đủ
        </Tooltip>
      ),
      dataIndex: 'soBoQua',
      width: 150,
      align: 'right',
    },
    { title: 'Bản ghi nhận', dataIndex: 'soNhan', width: 130, align: 'right' },
    { title: 'Ghi mới', dataIndex: 'soGhiMoi', width: 110, align: 'right' },
    { title: 'Trùng, bỏ qua', dataIndex: 'soTrung', width: 140, align: 'right' },
    {
      title: (
        <Tooltip title="Bản ghi mang mã api chưa ai khai thành điểm đo. > 0 là một việc phải làm (mục G8), không phải một sự cố.">
          Mã lạ
        </Tooltip>
      ),
      dataIndex: 'soMaLa',
      width: 110,
      align: 'right',
      render: (v: number) => (v > 0 ? <Tag color="orange">{v}</Tag> : v),
    },
    {
      title: 'Hỏng gần nhất',
      dataIndex: 'hongGanNhat',
      width: 180,
      render: (v: string | null) => (v ? formatDateTime(v) : oRong(null, 'Không có lượt nào hỏng')),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="BC-13 — Nhật ký đồng bộ & chất lượng dữ liệu"
        extra={
          <Space wrap>
            <DatePicker.RangePicker
              allowClear={false}
              value={khoang}
              onChange={(v) => v?.[0] && v[1] && setKhoang([v[0], v[1]])}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Mọi điểm đo"
              style={{ minWidth: 260 }}
              value={diemDo}
              onChange={setDiemDo}
              options={(dsDiemDo.data?.content ?? []).map((s) => ({
                value: s.id,
                label: `${s.name} (${s.code})`,
              }))}
            />
          </Space>
        }
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={`Một khung là ${khungPhut} phút — mỗi ngày trọn vẹn có ${Math.round(1440 / khungPhut)} khung`}
          description={
            <>
              Cột <b>Khung bỏ sót</b> là thước đo của cam kết NFR-03. Ô để <b>trống</b> nghĩa là
              chưa đo được — lý do hiện ngay tại ô — và ⛔ <b>không</b> đồng nghĩa với 0.
              <br />
              <Typography.Text type="secondary">
                Khung đang diễn ra ⛔ không được tính là bỏ sót: nguồn trả rải rác trong cửa sổ của
                mỗi khung, nên khung chưa kết thúc mà chưa có dữ liệu là bình thường.
              </Typography.Text>
            </>
          }
        />

        <Table
          rowKey={(r) => `${r.ngay}|${r.stationCode}|${r.measurementTypeCode}`}
          size="small"
          loading={baoCao.isLoading}
          dataSource={baoCao.data?.chatLuong ?? []}
          columns={cotChatLuong}
          pagination={false}
          // ⚠ `scroll.x` = tổng bề ngang cột thật; thiếu nó thì `tableLayout` rơi về `auto` và cột
          //   không khai `width` bị bóp xuống `min-content` (lỗi đã đo được ở ApiSourcesPage).
          scroll={{ x: 1520, y: 460 }}
          locale={{
            emptyText: 'Chưa có điểm đo nào khai loại chỉ số — báo cáo không có hàng để dựng',
          }}
        />
      </Card>

      <Card title="Lượt lấy dữ liệu theo nguồn">
        <Table
          rowKey={(r) => `${r.ngay}|${r.sourceCode}`}
          size="small"
          loading={baoCao.isLoading}
          dataSource={baoCao.data?.dongBo ?? []}
          columns={cotDongBo}
          pagination={false}
          scroll={{ x: 1600 }}
          locale={{ emptyText: 'Chưa có lượt đồng bộ nào trong khoảng ngày đã chọn' }}
        />
      </Card>
    </Space>
  );
}

/**
 * ⛔⛔ Ô rỗng **kèm lý do** — quy tắc 16 ở tầng hiển thị.
 *
 * ⛔ Đây là hàm duy nhất được phép vẽ một ô số liệu trống trong trang này, và nó **bắt buộc** kèm
 * lời giải thích. Vẽ `—` trần thì người đọc ⛔ không phân biệt được *"chưa theo dõi"* với *"đang
 * tải"* với *"poller chết"* — ba tình huống cần ba hành động khác nhau.
 */
function oRong(giaTri: number | string | null, lyDo?: string | null) {
  if (giaTri !== null) {
    return giaTri;
  }
  return (
    <Tooltip title={lyDo ?? 'Chưa đo được'}>
      <Typography.Text type="secondary" style={{ cursor: 'help' }}>
        —
      </Typography.Text>
    </Tooltip>
  );
}

export default SyncQualityReportPage;
