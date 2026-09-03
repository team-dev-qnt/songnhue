import { useQuery } from '@tanstack/react-query';
import { DownloadOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Drawer,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';

import {
  type PeriodSummaryReport,
  type PeriodSummaryRow,
  type ReadingDetailRow,
  type Station,
} from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { useAuth } from '@/app/auth/useAuth';
import { formatDateTime } from '@/shared/format';

import { CHAT_LUONG_SO_DO, NGUON_SO_DO, VAI_TRO_VI_TRI } from './hydroVocabulary';
import { useXuatBaoCao } from './useXuatBaoCao';

/** ⛔ Trần khoảng ngày của BC-12 — phải khớp `HydroReportService.TRAN_NGAY_CHI_TIET`. */
const TRAN_NGAY_CHI_TIET = 31;

/**
 * **BC-05 — Tổng hợp kỳ** (T34.5), với **BC-12 — Chi tiết theo yêu cầu** (T34.6) làm ngăn kéo
 * drill-down.
 *
 * ⭐ Hai báo cáo, **một** mục menu. BC-12 ⛔ không phải một báo cáo độc lập mà người ta mở ra rồi
 * mới nghĩ xem tra trạm nào — nó luôn là câu hỏi *tiếp theo* của một hàng trong BC-05: *"con số
 * trung bình này dựa trên những bản ghi nào?"*. Tách thành mục menu riêng là bắt người dùng gõ lại
 * điểm đo và khoảng ngày họ vừa chọn.
 *
 * ⛔⛔ **Ô rỗng ⛔ KHÔNG được vẽ thành `0`** (quy tắc 16). Mực nước trung bình `0.000` là một câu
 * *sai và đáng tin*: đúng định dạng, vẽ được biểu đồ, nằm gọn giữa các con số thật. Backend ép
 * ràng buộc ấy ở hàm dựng `TongHopKyView`; trang này là vế hiển thị của cùng một luật.
 *
 * ⚠ **Hai trần khoảng ngày khác nhau và cùng đúng**: BC-05 đọc bảng tổng hợp nên chịu được 366
 * ngày; BC-12 quét bảng gốc (144 bản ghi/ngày cho mỗi cặp) nên trần là 31. Ngăn kéo tự kẹp khoảng
 * ngày lại thay vì để người dùng nhận một lỗi họ ⛔ không gây ra.
 */
export function PeriodReportPage() {
  const { hasPermission } = useAuth();
  const { xuat, dangCho } = useXuatBaoCao();
  const [khoang, setKhoang] = useState<[Dayjs, Dayjs]>(() => [
    dayjs().startOf('month'),
    dayjs().endOf('month').isAfter(dayjs()) ? dayjs() : dayjs().endOf('month'),
  ]);
  const [chiTiet, setChiTiet] = useState<PeriodSummaryRow | null>(null);

  const tuNgay = khoang[0].format('YYYY-MM-DD');
  const denNgay = khoang[1].format('YYYY-MM-DD');

  const dsDiemDo = useQuery({
    queryKey: ['hyd', 'stations', 'chon-bao-cao'],
    queryFn: () => api.get<{ content: Station[] }>('/hyd/stations?page=0&size=100'),
  });

  const baoCao = useQuery({
    queryKey: ['hyd', 'bao-cao', 'tong-hop', tuNgay, denNgay],
    queryFn: () =>
      api.get<PeriodSummaryReport>(`/hyd/bao-cao/tong-hop?tuNgay=${tuNgay}&denNgay=${denNgay}`),
  });

  const cot: ColumnsType<PeriodSummaryRow> = [
    {
      title: 'Điểm đo',
      dataIndex: 'stationName',
      width: 280,
      fixed: 'left',
      ellipsis: true,
      render: (_, row) => (
        <Space size={4}>
          <span>{row.stationName}</span>
          <Typography.Text type="secondary">({row.stationCode})</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Tuyến sông',
      dataIndex: 'riverName',
      width: 170,
      ellipsis: true,
      // ⛔ `riverName` NULL là trạng thái ĐÚNG hôm nay — G8 chưa chốt tuyến sông/lý trình.
      //    ⛔ Không bịa một tuyến, ⛔ không để ô trắng không lời.
      render: (v: string | null) =>
        v ?? <Typography.Text type="secondary">Chưa phân tuyến</Typography.Text>,
    },
    {
      title: 'Vị trí',
      dataIndex: 'positionRole',
      width: 140,
      render: (v: string) => VAI_TRO_VI_TRI[v as keyof typeof VAI_TRO_VI_TRI] ?? v,
    },
    { title: 'Chỉ số', dataIndex: 'measurementTypeName', width: 170, ellipsis: true },
    {
      title: 'Nhỏ nhất',
      dataIndex: 'giaTriMin',
      width: 130,
      align: 'right',
      render: (v: string | null, row) => giaTri(v, row),
    },
    {
      title: 'Lúc',
      dataIndex: 'mocMin',
      width: 175,
      render: (v: string | null, row) => (v ? formatDateTime(v) : oRong(row.lyDoTrong)),
    },
    {
      title: 'Lớn nhất',
      dataIndex: 'giaTriMax',
      width: 130,
      align: 'right',
      render: (v: string | null, row) => giaTri(v, row),
    },
    {
      title: 'Lúc',
      dataIndex: 'mocMax',
      width: 175,
      render: (v: string | null, row) => (v ? formatDateTime(v) : oRong(row.lyDoTrong)),
    },
    {
      title: (
        <Tooltip title="Trung bình THEO TRỌNG SỐ: tổng giá trị chia tổng số bản ghi. ⛔ Không phải trung bình của các trung bình ngày — cách ấy tính ngày có 12 bản ghi ngang với ngày có 144.">
          Trung bình
        </Tooltip>
      ),
      dataIndex: 'giaTriTb',
      width: 140,
      align: 'right',
      render: (v: string | null, row) => giaTri(v, row),
    },
    {
      title: (
        <Tooltip title="Trung bình dựa trên bao nhiêu quan sát. Một trung bình của 12 bản ghi và một trung bình của 4320 bản ghi trông y hệt nhau nếu không nói ra.">
          Bản ghi / Ngày có dữ liệu
        </Tooltip>
      ),
      dataIndex: 'soBanGhi',
      width: 200,
      align: 'right',
      render: (_, row) => (
        <span>
          {row.soBanGhi.toLocaleString('vi-VN')}
          <Typography.Text type="secondary">
            {' / '}
            {row.soNgayCoDuLieu}/{baoCao.data?.soNgayTrongKy ?? '?'} ngày
          </Typography.Text>
        </span>
      ),
    },
  ];

  return (
    <Card
      title="BC-05 — Tổng hợp thuỷ văn theo kỳ"
      extra={
        <Space wrap>
          {hasPermission('hyd:report:export') ? (
            <Button
              icon={<DownloadOutlined />}
              loading={dangCho}
              onClick={() => {
                void xuat({ loai: 'BC05', tuNgay, denNgay })
                  .then((ten) => message.success(`Đã tải ${ten}`))
                  .catch((e: unknown) =>
                    message.error(e instanceof Error ? e.message : 'Không kết xuất được'),
                  );
              }}
            >
              {dangCho ? 'Đang dựng tệp…' : 'Xuất CSV'}
            </Button>
          ) : null}
          <DatePicker.RangePicker
            allowClear={false}
            value={khoang}
            onChange={(v) => v?.[0] && v[1] && setKhoang([v[0], v[1]])}
          />
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Mọi con số ở đây chỉ tính trên bản ghi HỢP LỆ"
        description={
          <>
            Bản ghi <b>nghi ngờ</b> và <b>đã xoá</b> ⛔ không tham gia max / min / trung bình. Muốn
            nhìn thấy chúng thì bấm vào một hàng để mở <b>chi tiết từng bản ghi</b> — đó là màn hình
            duy nhất hiện cả ba mức chất lượng, kèm cột Chất lượng và cột Nguồn.
            <br />
            <Typography.Text type="secondary">
              Điểm đo ⛔ không có bản ghi hợp lệ nào <b>vẫn có hàng</b>, với ô số liệu để trống kèm
              lý do — nó là thứ cần thấy nhất, ⛔ không phải thứ nên ẩn đi.
            </Typography.Text>
          </>
        }
      />

      <Table
        rowKey={(r) => `${r.stationCode}|${r.measurementTypeCode}`}
        size="small"
        loading={baoCao.isLoading}
        dataSource={baoCao.data?.hang ?? []}
        columns={cot}
        pagination={false}
        // ⚠ `scroll.x` = tổng bề ngang cột thật — thiếu nó thì cột không khai `width` bị bóp xuống
        //   `min-content` và URL/tên dài xuống dòng từng ký tự (lỗi đã đo ở ApiSourcesPage).
        scroll={{ x: 1810, y: 520 }}
        onRow={(row) => ({
          style: { cursor: 'pointer' },
          onClick: () => setChiTiet(row),
        })}
        locale={{
          emptyText: 'Chưa có điểm đo nào khai loại chỉ số — báo cáo không có hàng để dựng',
        }}
      />

      <Drawer
        width={880}
        open={chiTiet !== null}
        onClose={() => setChiTiet(null)}
        title={
          chiTiet
            ? `BC-12 — ${chiTiet.stationName} · ${chiTiet.measurementTypeName}`
            : 'Chi tiết bản ghi'
        }
        destroyOnClose
      >
        {chiTiet ? (
          <ChiTietSoDo
            stationCode={chiTiet.stationCode}
            maLoaiChiSo={chiTiet.measurementTypeCode}
            unit={chiTiet.unit}
            tuNgay={khoang[0]}
            denNgay={khoang[1]}
            dsDiemDo={dsDiemDo.data?.content ?? []}
          />
        ) : null}
      </Drawer>
    </Card>
  );
}

/**
 * BC-12 trong ngăn kéo.
 *
 * ⚠ Khoảng ngày bị **kẹp lại** còn {@link TRAN_NGAY_CHI_TIET} ngày cuối kỳ trước khi gọi. Người
 * dùng chọn một kỳ 3 tháng cho BC-05 là hợp lệ; ném cho họ một lỗi HYD-2012 khi bấm vào một hàng
 * là bắt họ trả giá cho một ràng buộc kỹ thuật họ ⛔ không gây ra và ⛔ không nhìn thấy.
 */
function ChiTietSoDo({
  stationCode,
  maLoaiChiSo,
  unit,
  tuNgay,
  denNgay,
  dsDiemDo,
}: {
  stationCode: string;
  maLoaiChiSo: string;
  unit: string;
  tuNgay: Dayjs;
  denNgay: Dayjs;
  dsDiemDo: Station[];
}) {
  const [trang, setTrang] = useState(1);
  const { hasPermission } = useAuth();
  const { xuat, dangCho } = useXuatBaoCao();

  const soNgay = denNgay.diff(tuNgay, 'day') + 1;
  const batDau =
    soNgay > TRAN_NGAY_CHI_TIET ? denNgay.subtract(TRAN_NGAY_CHI_TIET - 1, 'day') : tuNgay;
  const daKep = soNgay > TRAN_NGAY_CHI_TIET;

  // ⚠ BC-05 trả `stationCode` (mã người đọc), còn API chi tiết cần `publicId` — tra ngược ở đây
  //   thay vì bắt backend rò khoá nội bộ ra dây.
  const publicId = dsDiemDo.find((s) => s.code === stationCode)?.id;

  const dl = useQuery({
    enabled: publicId !== undefined,
    queryKey: [
      'hyd',
      'bao-cao',
      'chi-tiet',
      publicId ?? '',
      maLoaiChiSo,
      batDau.format('YYYY-MM-DD'),
      denNgay.format('YYYY-MM-DD'),
      trang,
    ],
    queryFn: () =>
      api.get<{ content: ReadingDetailRow[]; totalElements: number }>(
        `/hyd/bao-cao/chi-tiet?stationPublicId=${publicId}&maLoaiChiSo=${maLoaiChiSo}` +
          `&tuNgay=${batDau.format('YYYY-MM-DD')}&denNgay=${denNgay.format('YYYY-MM-DD')}` +
          `&page=${trang - 1}&size=100`,
      ),
  });

  if (publicId === undefined) {
    return (
      <Alert
        type="warning"
        showIcon
        message="Không tra được điểm đo"
        description={`Mã ${stationCode} không nằm trong 100 điểm đo đầu của danh mục — chi tiết chưa mở được từ đây.`}
      />
    );
  }

  const cot: ColumnsType<ReadingDetailRow> = [
    { title: 'Mốc đo', dataIndex: 'mocDo', width: 180, render: (v: string) => formatDateTime(v) },
    {
      title: `Giá trị (${unit})`,
      dataIndex: 'giaTri',
      width: 130,
      align: 'right',
    },
    {
      // ⭐⭐ Cột chịu lực: nó là thứ được đánh đổi lấy quyền ⛔ không lọc chất lượng.
      title: 'Chất lượng',
      dataIndex: 'quality',
      width: 130,
      render: (v: ReadingDetailRow['quality']) => {
        const vt = CHAT_LUONG_SO_DO[v];
        return (
          <Tooltip title={vt?.giaiThich}>
            <Tag color={vt?.color}>{vt?.label ?? v}</Tag>
          </Tooltip>
        );
      },
    },
    {
      // ⭐⭐ Cột chịu lực thứ hai.
      title: 'Nguồn',
      dataIndex: 'source',
      width: 110,
      render: (v: string) => {
        const n = NGUON_SO_DO[v as keyof typeof NGUON_SO_DO];
        return <Tag color={n?.color}>{n?.label ?? v}</Tag>;
      },
    },
    {
      title: 'Lý do / Ghi chú',
      dataIndex: 'qualityReason',
      ellipsis: true,
      // ⚠ Ba trường khác nguồn gốc: MÁY chẩn đoán (`qualityReason`), NGƯỜI quyết định
      //   (`reviewNote`), NGƯỜI nhập ghi chú (`note`). Gộp để hiển thị thì phải nói rõ cái nào là
      //   cái nào — ⛔ đừng để người đọc tưởng máy viết ra câu của người.
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          {row.qualityReason ? (
            <Typography.Text type="secondary">Máy: {row.qualityReason}</Typography.Text>
          ) : null}
          {row.reviewNote ? <Typography.Text>Người duyệt: {row.reviewNote}</Typography.Text> : null}
          {row.note ? <Typography.Text>Người nhập: {row.note}</Typography.Text> : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      {daKep ? (
        <Alert
          type="info"
          showIcon
          message={`Chi tiết chỉ hiện ${TRAN_NGAY_CHI_TIET} ngày cuối của kỳ (${batDau.format('DD/MM/YYYY')} – ${denNgay.format('DD/MM/YYYY')})`}
          description="Báo cáo chi tiết là báo cáo duy nhất đọc thẳng bảng số đo — mỗi điểm đo sinh 144 bản ghi một ngày, nên khoảng ngày phải có cận."
        />
      ) : null}

      {hasPermission('hyd:report:export') ? (
        <Button
          icon={<DownloadOutlined />}
          loading={dangCho}
          disabled={(dl.data?.totalElements ?? 0) === 0}
          onClick={() => {
            // ⚠ Xuất ĐÚNG khoảng đã kẹp mà bảng đang hiện — ⛔ không xuất khoảng gốc của BC-05.
            //   Một tệp có phạm vi khác thứ người dùng vừa nhìn là một tệp họ ⛔ không kiểm được.
            void xuat({
              loai: 'BC12',
              tuNgay: batDau.format('YYYY-MM-DD'),
              denNgay: denNgay.format('YYYY-MM-DD'),
              stationPublicId: publicId,
              maLoaiChiSo,
            })
              .then((ten) => message.success(`Đã tải ${ten}`))
              .catch((e: unknown) =>
                message.error(e instanceof Error ? e.message : 'Không kết xuất được'),
              );
          }}
        >
          {dangCho ? 'Đang dựng tệp…' : 'Xuất chi tiết ra CSV'}
        </Button>
      ) : null}

      <Table
        rowKey="mocDo"
        size="small"
        loading={dl.isLoading}
        dataSource={dl.data?.content ?? []}
        columns={cot}
        scroll={{ x: 900, y: 460 }}
        pagination={{
          current: trang,
          pageSize: 100,
          total: dl.data?.totalElements ?? 0,
          showSizeChanger: false,
          onChange: setTrang,
          showTotal: (t) => `${t.toLocaleString('vi-VN')} bản ghi`,
        }}
        locale={{ emptyText: 'Không có bản ghi nào trong khoảng ngày này' }}
      />
    </Space>
  );
}

/** Ô giá trị — rỗng thì kèm lý do, ⛔ không bao giờ là `0`. */
function giaTri(v: string | null, row: PeriodSummaryRow) {
  return v === null ? oRong(row.lyDoTrong) : `${v} ${row.unit}`;
}

/**
 * ⛔⛔ Ô rỗng **kèm lý do** — quy tắc 16 ở tầng hiển thị.
 *
 * ⛔ Vẽ `—` trần thì người đọc ⛔ không phân biệt được *"kỳ này không có số liệu hợp lệ"* với
 * *"đang tải"* với *"backend cũ chưa có trường này"*.
 */
function oRong(lyDo: string | null) {
  return (
    <Tooltip title={lyDo ?? 'Chưa đo được'}>
      <Typography.Text type="secondary" style={{ cursor: 'help' }}>
        —
      </Typography.Text>
    </Tooltip>
  );
}

export default PeriodReportPage;
