import { EditOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Form, Input, Modal, Select, Space, Tag, Tooltip } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { DataTable } from '@/components/DataTable';
import { usePagination } from '@/components/usePagination';
import { DateRangeFilter, type DateRange } from '@/components/business/DateRangeFilter';
import {
  type AllowedActionView,
  type QualityRuleStatus,
  type ReadingQuality,
  type Station,
  type ReviewRequest,
  type SuspectReadingRow,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { EMPTY_MARK, formatDateTimeWithSeconds } from '@/shared/format';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';

import { CHAT_LUONG_SO_DO, NGUON_SO_DO } from './hydroVocabulary';
import { TinhTrangQuyTacAlert } from './TinhTrangQuyTacAlert';
import { NhapTaySoDoModal } from './NhapTaySoDoModal';

/**
 * Dữ liệu nghi ngờ — CN-03.2 (WS-32 / T32.7).
 *
 * <h3>⭐⭐ Đây là màn hình DUY NHẤT được nhìn thấy dòng mà quy tắc 14 loại ra</h3>
 *
 * Bản ghi `NGHI_NGO` **nằm chung bảng chính** với dữ liệu tốt, và mọi truy vấn báo cáo / biểu đồ
 * / cảnh báo đều lọc `HOP_LE` — nghĩa là chúng **vô hình** ở mọi nơi khác trong hệ thống. Không có
 * màn hình này thì số liệu bị treo sẽ nằm im vĩnh viễn và không ai biết.
 *
 * <h3>⚠⚠ Bảng rỗng có BA nghĩa khác nhau — và giao diện phải nói ra cái nào</h3>
 *
 * Bộ phân loại đang chạy mà không có gì đáng ngờ · chưa ai cấu hình quy tắc · cấu hình có mà
 * **hỏng**. Cả ba đều cho ra một bảng trống y hệt nhau. Hiện nó như *"không có gì đáng ngờ"* trong
 * khi bộ phân loại đang tắt là đúng thứ quy tắc 16 cấm: **số 0 là một câu khẳng định.**
 *
 * <h3>⛔ Nút Duyệt / Loại bỏ đến từ BACKEND, ⛔ giao diện không tự suy</h3>
 *
 * Luật nằm ở bảng `workflow_transitions`, mà giao diện thì không đọc DB — tự suy là chắc chắn có
 * ngày lệch, và lệch theo hướng nguy hiểm nhất là hiện một nút mà máy chủ sẽ từ chối. Cờ
 * `requiresReason` cũng đi cùng đường ấy nên hộp thoại nhập lý do ⛔ không thể lệch với chốt chặn.
 * Đã trả giá đúng chỗ này: `AllowedAction` phía giao diện từng mang ba trường mà không nơi nào
 * điền, nên hộp thoại nhập lý do **không bao giờ mở**.
 */
/**
 * ⭐ Địa chỉ của một dòng — bộ ba khoá tự nhiên, ⛔ không phải khoá tự tăng.
 *
 * `hydro_readings.id` cố ý ⛔ không ra tới dây (`ApiSurfaceRuleTest` cấm mọi đường dẫn nhận khoá
 * số), nên `rowKey` cũng dựng từ đúng bộ khoá mà API nhận. Một địa chỉ dùng chung cho hiển thị và
 * cho lượt ghi là một chỗ để nhớ thay vì hai.
 */
function khoaCua(r: SuspectReadingRow) {
  return `${r.diemDoId}|${r.loaiChiSoCode}|${r.mocDo}`;
}

export function SuspectReadingsPage() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const [form] = Form.useForm<{ reason?: string }>();

  const coDuyet = hasPermission('hyd:measurement:review');
  const coNhapTay = hasPermission('hyd:measurement:create');

  const pagination = usePagination(20);
  const [trangThai, setTrangThai] = useState<ReadingQuality>('NGHI_NGO');
  const [diemDoId, setDiemDoId] = useState<string | undefined>();
  const [range, setRange] = useState<DateRange>({});
  const [dangXuLy, setDangXuLy] = useState<SuspectReadingRow | null>(null);
  const [moNhapTay, setMoNhapTay] = useState(false);

  const doiLoc = (apDung: () => void) => {
    apDung();
    pagination.reset();
  };

  const tinhTrang = useQuery({
    queryKey: ['hyd', 'so-do', 'tinh-trang'],
    queryFn: () => api.get<QualityRuleStatus>('/hyd/so-do/nghi-ngo/tinh-trang'),
    staleTime: 60_000,
  });

  const diemDoQuery = useQuery({
    queryKey: ['hyd', 'stations', 'chon'],
    queryFn: () => api.getPage<Station>('/hyd/stations', { page: 1, size: 100 }),
  });

  const hangCho = useQuery({
    queryKey: ['hyd', 'so-do', trangThai, diemDoId, range, pagination.page, pagination.size],
    queryFn: () =>
      api.getPage<SuspectReadingRow>('/hyd/so-do/nghi-ngo', {
        ...pagination.params,
        trangThai,
        diemDoId,
        tu: range.from,
        den: range.to,
      }),
  });

  // ⛔ Nút đến từ backend — xem javadoc. Chỉ tải khi thật sự mở hộp thoại của một dòng.
  const nutQuery = useQuery({
    queryKey: ['hyd', 'so-do', 'thao-tac', dangXuLy && khoaCua(dangXuLy)],
    queryFn: () =>
      api.get<AllowedActionView[]>('/hyd/so-do/thao-tac', {
        diemDoId: dangXuLy?.diemDoId,
        maLoaiChiSo: dangXuLy?.loaiChiSoCode,
        mocDo: dangXuLy?.mocDo,
      }),
    enabled: dangXuLy != null && coDuyet,
  });

  const xuLy = useMutation({
    mutationFn: (bien: ReviewRequest) => api.post('/hyd/so-do/thao-tac', bien),
    onSuccess: () => {
      message.success('Đã xử lý số đo');
      setDangXuLy(null);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['hyd', 'so-do'] });
    },
    // ⭐ T28.31: đường này có BA cách hỏng và cả ba đều vô hình nếu thiếu nhánh dưới —
    //   `SYS-0003` (bấm Loại bỏ mà bỏ trống lý do, gắn đúng ô `reason`), `HYD-2001` (giá trị vẫn
    //   ngoài khoảng vật lý nên ⛔ không duyệt lên hợp lệ được), và `AUTH-3001`.
    onError: (caught: unknown) => {
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xử lý được số đo');
    },
  });

  const rows = hangCho.data?.items ?? [];
  const nut = nutQuery.data ?? [];

  const columns: ColumnsType<SuspectReadingRow> = [
    {
      title: 'Mốc đo',
      dataIndex: 'mocDo',
      width: 180,
      render: (v: string) => formatDateTimeWithSeconds(v),
    },
    {
      title: 'Điểm đo',
      dataIndex: 'diemDoName',
      width: 240,
      ellipsis: true,
      render: (v: string, r) => (
        <Tooltip title={`${r.diemDoCode} — ${v}`}>
          <span>{v}</span>
        </Tooltip>
      ),
    },
    { title: 'Loại chỉ số', dataIndex: 'loaiChiSoName', width: 130 },
    {
      // ⚠⚠ Đơn vị LUÔN đi cạnh con số. Một ô số không nhãn sẽ được đọc bằng đơn vị người xem
      //    đang nghĩ tới, mà nguồn trả cm còn hệ thống lưu m — sai đúng 100 lần.
      title: 'Giá trị',
      dataIndex: 'giaTri',
      width: 140,
      align: 'right',
      render: (v: string, r) => (
        <span>
          <b>{v}</b> <Tag>{r.donVi}</Tag>
        </span>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'trangThai',
      width: 130,
      render: (v: ReadingQuality) => (
        <Tooltip title={CHAT_LUONG_SO_DO[v].giaiThich}>
          <Tag color={CHAT_LUONG_SO_DO[v].color}>{CHAT_LUONG_SO_DO[v].label}</Tag>
        </Tooltip>
      ),
    },
    {
      title: 'Nguồn',
      dataIndex: 'nguon',
      width: 110,
      render: (v: keyof typeof NGUON_SO_DO) => (
        <Tag color={NGUON_SO_DO[v].color}>{NGUON_SO_DO[v].label}</Tag>
      ),
    },
    {
      // ⭐ Cột chịu lực của cả màn hình: thiếu nó thì cờ đỏ không hành động được — người duyệt
      //   không phân biệt được "cảm biến hỏng" (⇒ loại bỏ) với "vừa mở cống" (⇒ duyệt), mà hai
      //   việc ấy ngược nhau.
      title: 'Máy nói',
      dataIndex: 'lyDoMay',
      width: 340,
      render: (v: string | null) => v ?? EMPTY_MARK,
    },
    {
      title: 'Người nói',
      dataIndex: 'lyDoNguoi',
      width: 260,
      render: (v: string | null) => v ?? EMPTY_MARK,
    },
    {
      title: '',
      width: 130,
      align: 'right',
      render: (_, r) =>
        coDuyet && r.trangThai === 'NGHI_NGO' ? (
          <Button type="link" onClick={() => setDangXuLy(r)}>
            Xử lý
          </Button>
        ) : null,
    },
  ];

  return (
    <Card
      title="Dữ liệu nghi ngờ"
      extra={
        coNhapTay ? (
          <Button icon={<EditOutlined />} onClick={() => setMoNhapTay(true)}>
            Nhập tay số đo
          </Button>
        ) : null
      }
    >
      <TinhTrangQuyTacAlert tinhTrang={tinhTrang.data} soDong={rows.length} />

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          value={trangThai}
          style={{ width: 200 }}
          onChange={(v) => doiLoc(() => setTrangThai(v))}
          options={(['NGHI_NGO', 'XOA'] as const).map((v) => ({
            value: v,
            label: CHAT_LUONG_SO_DO[v].label,
          }))}
        />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="Mọi điểm đo"
          style={{ width: 280 }}
          value={diemDoId}
          onChange={(v) => doiLoc(() => setDiemDoId(v))}
          options={(diemDoQuery.data?.items ?? []).map((s) => ({
            value: s.id,
            label: `${s.code} — ${s.name}`,
          }))}
        />
        <DateRangeFilter value={range} onChange={(v) => doiLoc(() => setRange(v))} />
      </Space>

      <DataTable<SuspectReadingRow>
        columns={columns}
        rows={hangCho.data?.items}
        meta={hangCho.data?.meta}
        loading={hangCho.isLoading}
        error={hangCho.error}
        rowKey={khoaCua}
        onPageChange={pagination.onPageChange}
        emptyText={
          trangThai === 'NGHI_NGO'
            ? 'Không có số đo nào đang chờ duyệt'
            : 'Chưa có số đo nào bị loại bỏ'
        }
        size="small"
        // 180+240+130+140+130+110+340+260+130 = 1660. ⚠ Thiếu con số này thì `tableLayout` rơi về
        // 'auto' và trình duyệt bóp cột không khai bề ngang xuống min-content — cột "Máy nói"
        // (câu dài nhất bảng) sẽ xuống dòng từng ký tự, đúng lỗi đã báo ở trang Nguồn dữ liệu.
        scrollX={1660}
      />

      <Modal
        open={dangXuLy != null}
        title={`Xử lý số đo lúc ${dangXuLy ? formatDateTimeWithSeconds(dangXuLy.mocDo) : ''}`}
        onCancel={() => {
          setDangXuLy(null);
          form.resetFields();
        }}
        footer={null}
        destroyOnClose
      >
        {dangXuLy && (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={`${dangXuLy.diemDoName} · ${dangXuLy.giaTri} ${dangXuLy.donVi}`}
              description={dangXuLy.lyDoMay}
            />
            <Form form={form} layout="vertical">
              <Form.Item
                name="reason"
                label="Lý do"
                extra="Bắt buộc khi loại bỏ — nguồn không có API lịch sử, nên không ai dựng lại được bối cảnh của quyết định này về sau"
              >
                <Input.TextArea rows={3} maxLength={500} showCount />
              </Form.Item>
            </Form>
            <Space>
              {nut.map((a) => (
                <Button
                  key={a.action}
                  type={a.toState === 'HOP_LE' ? 'primary' : 'default'}
                  danger={a.toState === 'XOA'}
                  loading={xuLy.isPending}
                  onClick={() =>
                    xuLy.mutate({
                      diemDoId: dangXuLy.diemDoId,
                      maLoaiChiSo: dangXuLy.loaiChiSoCode,
                      mocDo: dangXuLy.mocDo,
                      action: a.action,
                      reason: form.getFieldValue('reason'),
                    })
                  }
                >
                  {a.label}
                </Button>
              ))}
            </Space>
          </>
        )}
      </Modal>

      <NhapTaySoDoModal
        open={moNhapTay}
        diemDo={diemDoQuery.data?.items ?? []}
        onClose={() => setMoNhapTay(false)}
        onDone={() => {
          setMoNhapTay(false);
          void queryClient.invalidateQueries({ queryKey: ['hyd', 'so-do'] });
        }}
      />
    </Card>
  );
}

export default SuspectReadingsPage;
