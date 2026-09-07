import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Col,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { DataTable } from '@/components/DataTable';
import { usePagination } from '@/components/usePagination';
import { DateRangeFilter, type DateRange } from '@/components/business/DateRangeFilter';
import {
  type ApiSource,
  type SyncFailureKind,
  type SyncLogRow,
  type SyncStatus,
  type SyncSummary,
  type SyncVocabulary,
} from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import {
  EMPTY_MARK,
  formatDateTimeWithSeconds,
  formatDuration,
  formatInteger,
} from '@/shared/format';

import { KET_CUC_DONG_BO, LY_DO_HONG } from './hydroVocabulary';

/**
 * Nhật ký đồng bộ — M3.16 (T31.13).
 *
 * <h3>Màn hình này trả lời đúng MỘT câu hỏi</h3>
 *
 * *"Lượt vừa rồi không ghi được gì — vì nguồn hỏng, vì đã đủ dữ liệu, hay vì ta chưa kịp gọi?"*
 * Ba câu trả lời ấy đòi ba việc phải làm ngược nhau (§10.68-B), nên chúng phải là ba **màu** và
 * ba **câu chữ** khác nhau, ⛔ không phải ba dòng log giống nhau.
 *
 * <h3>⛔⛔ `Bỏ qua — đã đủ` KHÔNG được vẽ màu đỏ</h3>
 *
 * Poller gọi 2 phút một lần trên một nguồn cập nhật 10 phút một lần ⇒ **4/5 lượt là bỏ qua**, và
 * đó là điều đúng. Trộn nó vào nhóm đỏ là dạy người vận hành bỏ qua màu đỏ, rồi ngày có sự cố
 * thật thì màu đỏ không còn nghĩa gì (§10.42).
 *
 * <h3>⚠ Bốn bộ đếm hiện RIÊNG</h3>
 *
 * `Ghi mới = 0` là kết cục bình thường của phần lớn lượt chạy. Gộp bốn con số thành một cột
 * "kết quả" là biến trạng thái bình thường nhất của hệ thống thành một dòng trông như lỗi.
 */
export function SyncLogsPage() {
  const pagination = usePagination(20);
  const [nguonId, setNguonId] = useState<string | undefined>();
  const [trangThai, setTrangThai] = useState<SyncStatus | undefined>();
  const [loi, setLoi] = useState<SyncFailureKind | undefined>();
  const [range, setRange] = useState<DateRange>({});
  const [chiHong, setChiHong] = useState(false);

  const doiLoc = (apDung: () => void) => {
    apDung();
    pagination.reset();
  };

  const nguonQuery = useQuery({
    queryKey: ['hyd', 'api-sources'],
    queryFn: () => api.get<ApiSource[]>('/hyd/api-sources'),
  });

  // ⛔ Hai bộ từ vựng đến từ BACKEND, không chép cứng ở đây: bốn kết cục và năm lý do hỏng đã
  //    sống ở enum Java + hai ràng buộc CHECK + api-types.ts. Một danh sách thứ tư trong tệp
  //    .tsx là nơi duy nhất không bài kiểm nào canh (luật 14).
  const tuVungQuery = useQuery({
    queryKey: ['hyd', 'sync-logs', 'tu-vung'],
    queryFn: () => api.get<SyncVocabulary>('/hyd/sync-logs/tu-vung'),
    staleTime: Infinity,
  });

  const tongHopQuery = useQuery({
    queryKey: ['hyd', 'sync-logs', 'tong-hop'],
    queryFn: () => api.get<SyncSummary>('/hyd/sync-logs/tong-hop'),
    refetchInterval: 60_000,
  });

  const logs = useQuery({
    queryKey: [
      'hyd',
      'sync-logs',
      nguonId,
      trangThai,
      loi,
      range,
      chiHong,
      pagination.page,
      pagination.size,
    ],
    queryFn: () =>
      api.getPage<SyncLogRow>('/hyd/sync-logs', {
        ...pagination.params,
        nguonId,
        trangThai,
        loi,
        tu: range.from,
        den: range.to,
        chiHong: chiHong || undefined,
      }),
  });

  const tongHop = tongHopQuery.data;
  const loiChuaGoi = new Set(tuVungQuery.data?.loiChuaGoi ?? []);

  const columns: ColumnsType<SyncLogRow> = [
    {
      title: 'Bắt đầu',
      dataIndex: 'batDau',
      width: 190,
      render: (v: string) => formatDateTimeWithSeconds(v),
    },
    {
      // ⚠ Cột này KHÁC cột "Bắt đầu" và đó là điểm dễ hiểu nhầm nhất của bảng: nguồn đóng dấu số
      //    đo theo khung 10 phút, còn ta gọi lúc nào là chuyện của ta. Một lượt gọi 10:24 mang về
      //    số đo của khung 10:20 — hai mốc ấy lệch nhau là bình thường.
      title: 'Khung nhắm tới',
      dataIndex: 'khungNhamToi',
      width: 190,
      render: (v: string | null) => (v ? formatDateTimeWithSeconds(v) : EMPTY_MARK),
    },
    { title: 'Nguồn', dataIndex: 'nguonCode', width: 110 },
    {
      title: 'Kết cục',
      dataIndex: 'trangThai',
      width: 150,
      render: (v: SyncStatus) => (
        <Tooltip title={KET_CUC_DONG_BO[v].giaiThich}>
          <Tag color={KET_CUC_DONG_BO[v].color}>{KET_CUC_DONG_BO[v].label}</Tag>
        </Tooltip>
      ),
    },
    {
      title: 'Lý do',
      dataIndex: 'loi',
      width: 200,
      render: (v: SyncFailureKind | null) =>
        v ? (
          <Tooltip title={LY_DO_HONG[v].viecPhaiLam}>
            <Tag color={loiChuaGoi.has(v) ? 'default' : 'volcano'}>{LY_DO_HONG[v].label}</Tag>
          </Tooltip>
        ) : (
          EMPTY_MARK
        ),
    },
    { title: 'Nhận', dataIndex: 'soNhan', width: 80, align: 'right', render: formatInteger },
    { title: 'Ghi mới', dataIndex: 'soGhiMoi', width: 90, align: 'right', render: formatInteger },
    {
      title: 'Trùng, bỏ qua',
      dataIndex: 'soTrungBoQua',
      width: 120,
      align: 'right',
      render: formatInteger,
    },
    {
      title: 'Mã lạ',
      dataIndex: 'soMaLa',
      width: 90,
      align: 'right',
      render: (v: number) =>
        v > 0 ? <Tag color="orange">{formatInteger(v)}</Tag> : formatInteger(v),
    },
    {
      title: 'Thời gian',
      dataIndex: 'durationMs',
      width: 110,
      align: 'right',
      render: (v: number | null) => (v == null ? EMPTY_MARK : formatDuration(v)),
    },
    { title: 'Chi tiết lỗi', dataIndex: 'lyDo', width: 320, ellipsis: true },
  ];

  return (
    <Card
      title="Nhật ký đồng bộ"
      extra={
        <Button
          icon={<ReloadOutlined />}
          onClick={() => {
            void logs.refetch();
            void tongHopQuery.refetch();
          }}
        >
          Tải lại
        </Button>
      }
    >
      {tongHop && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Row gutter={[16, 8]}>
            <Col xs={12} md={6}>
              <Statistic title={`Lượt chạy · ${tongHop.soGio} giờ qua`} value={tongHop.soLuot} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title="Thành công"
                value={tongHop.theoTrangThai.SUCCESS + tongHop.theoTrangThai.PARTIAL}
              />
            </Col>
            <Col xs={12} md={6}>
              {/* ⭐ Con số này do BACKEND tính (`soLuotGoiHong`): luật "lượt gọi đã thật sự xảy ra
                  chưa" nằm ở SyncFailureKind.duocGhiVaoRawLog() và đã có ba nơi dùng. Cộng lại ở
                  đây là mở nơi thứ tư — nơi duy nhất không bài kiểm nào canh. */}
              <Statistic title="Lượt gọi hỏng" value={tongHop.soLuotGoiHong} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic
                title="Lượt gần nhất"
                value={
                  tongHop.mocGanNhat
                    ? formatDateTimeWithSeconds(tongHop.mocGanNhat)
                    : 'Chưa có lượt nào'
                }
                valueStyle={{ fontSize: 16 }}
              />
            </Col>
          </Row>
        </Card>
      )}

      {tongHop && tongHop.soLuot === 0 && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message={`Không có lượt polling nào trong ${tongHop.soGio} giờ qua`}
          description="Poller đang không chạy, hoặc không nguồn nào ở trạng thái Đang hoạt động. Đây là triệu chứng nặng hơn mọi con số lỗi — nguồn KHÔNG có API lịch sử, mất khung nào là mất vĩnh viễn."
        />
      )}

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="“Bỏ qua — đã đủ” là kết cục BÌNH THƯỜNG của phần lớn lượt chạy"
        description="Poller gọi 2 phút một lần trên nguồn cập nhật 10 phút một lần, nên khi toàn bộ điểm đo đã có bản ghi của khung hiện tại thì lượt gọi được bỏ qua có chủ đích. Tương tự, “Ghi mới = 0” không phải lỗi — dữ liệu trùng là chuyện thường."
      />

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Mọi nguồn"
          style={{ width: 200 }}
          loading={nguonQuery.isLoading}
          value={nguonId}
          onChange={(v) => doiLoc(() => setNguonId(v))}
          options={(nguonQuery.data ?? []).map((n) => ({
            value: n.id,
            label: `${n.code} — ${n.name}`,
          }))}
        />
        <Select
          allowClear
          placeholder="Mọi kết cục"
          style={{ width: 190 }}
          value={trangThai}
          onChange={(v) => doiLoc(() => setTrangThai(v))}
          options={(tuVungQuery.data?.trangThai ?? []).map((v) => ({
            value: v,
            label: KET_CUC_DONG_BO[v].label,
          }))}
        />
        <Select
          allowClear
          placeholder="Mọi lý do hỏng"
          style={{ width: 220 }}
          value={loi}
          onChange={(v) => doiLoc(() => setLoi(v))}
          options={(tuVungQuery.data?.lyDoHong ?? []).map((v) => ({
            value: v,
            label: LY_DO_HONG[v].label,
          }))}
        />
        <DateRangeFilter value={range} onChange={(r) => doiLoc(() => setRange(r))} />
        <Space size={4}>
          <Switch checked={chiHong} onChange={(v) => doiLoc(() => setChiHong(v))} />
          <Typography.Text>Chỉ lượt có vấn đề</Typography.Text>
        </Space>
      </Space>

      <DataTable<SyncLogRow>
        columns={columns}
        rows={logs.data?.items}
        meta={logs.data?.meta}
        loading={logs.isLoading}
        error={logs.error}
        rowKey="id"
        onPageChange={pagination.onPageChange}
        emptyText="Chưa có lượt đồng bộ nào khớp bộ lọc"
        size="small"
        // 190+190+110+150+200+80+90+120+90+110+320 = 1650. ⚠ Thiếu con số này thì `tableLayout`
        // rơi về 'auto' và trình duyệt bóp cột không khai bề ngang xuống min-content — đúng lỗi
        // đã đo được ở trang Nguồn dữ liệu (cột "Địa chỉ" còn ~29px ở 1440).
        scrollX={1650}
      />

      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        Cột <b>Chi tiết lỗi</b> đã đi qua bộ che mã số. Muốn đối chiếu nguyên văn phản hồi của nguồn
        thì tra <code>hydro_raw_logs</code> theo <code>raw_log_id</code> — bảng ấy chỉ ghi thêm, có
        hạn lưu riêng và không phục vụ qua API.
      </Typography.Paragraph>
    </Card>
  );
}

export default SyncLogsPage;
