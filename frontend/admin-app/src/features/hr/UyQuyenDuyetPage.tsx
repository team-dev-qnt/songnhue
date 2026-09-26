import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { type Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';

import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { type UserView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';

import { type UyQuyenDuyetView } from './hrVocabulary';
import { nghiPhepApi } from './nghiPhepApi';
import { ngayVn } from './nghiPhepFormat';

/**
 * Uỷ quyền duyệt nghỉ phép — **chốt B3** (WS-80).
 *
 * <h2>⛔⛔ Trang này KHÔNG cấp quyền cho ai</h2>
 *
 * Nó **chuyển vai**: người được chọn phải **đang có** `hr:leave:approve` từ trước (máy chủ từ chối
 * bằng `HR-2014`), và một hàng ở đây chỉ nói rằng trong khoảng ngày ấy họ đứng thay trưởng đơn vị.
 * Đó là lý do ô chọn người dưới đây **⛔ liệt kê mọi tài khoản** — nó bày ra một lựa chọn chắc chắn
 * hỏng nếu làm thế, đúng bài học `OTruongPho` của H24.
 *
 * Nếu người anh cần chưa có quyền duyệt, việc phải làm là **gán vai trò** ở *Quản trị › Vai trò* —
 * một hành động riêng, nhìn thấy được, có nhật ký. Để biểu mẫu này tự cấp quyền là dựng một đường
 * cấp quyền **ẩn** nằm ngoài màn hình phân quyền, và màn hình ấy thôi là bức tranh đầy đủ.
 *
 * <h2>Vì sao bảng giữ cả bản đã thu hồi / đã hết hạn</h2>
 *
 * `leave_requests.uy_quyen_id` trỏ vào chúng. Tranh chấp phép năm nổ ra hàng tháng sau và câu hỏi
 * đầu tiên là *"ai đã duyệt, với tư cách gì"* — lọc chúng khỏi màn hình quản lý chính chúng là xoá
 * đúng phần mà lượt rà soát đi tìm.
 */
export function UyQuyenDuyetPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [donVi, setDonVi] = useState<string | undefined>(undefined);
  const [moGiao, setMoGiao] = useState(false);

  const danhSach = useQuery({
    queryKey: ['hr', 'nghi-phep', 'uy-quyen', donVi],
    queryFn: () => nghiPhepApi.uyQuyen(donVi as string),
    enabled: donVi !== undefined,
  });

  const { data: taiKhoan } = useQuery({
    queryKey: ['admin-users', 'chon-nguoi-duyet-thay'],
    queryFn: () => api.get<UserView[]>('/admin/users'),
  });

  // ⛔ Chỉ tài khoản ĐANG HOẠT ĐỘNG: máy chủ đòi người nhận phải đang hoạt động VÀ đang giữ
  //   `hr:leave:approve`. Giao diện ⛔ đọc được vế thứ hai (danh sách quyền ⛔ nằm trong
  //   `/admin/users`), nên nó lọc vế đọc được và để máy chủ nói nốt vế kia bằng `HR-2014` —
  //   ⛔ đoán, và ⛔ bày ra một lựa chọn mình ⛔ kiểm được.
  const nguoiChon = useMemo(
    () =>
      (taiKhoan ?? [])
        .filter((u) => u.status === 'ACTIVE')
        .map((u) => ({ value: u.publicId, label: `${u.fullName} (${u.username})` })),
    [taiKhoan],
  );
  const tenTheoId = useMemo(
    () => new Map((taiKhoan ?? []).map((u) => [u.publicId, u.fullName])),
    [taiKhoan],
  );

  const thuHoi = useMutation({
    mutationFn: (publicId: string) => nghiPhepApi.thuHoiUyQuyen(publicId),
    onSuccess: () => {
      message.success('Đã thu hồi uỷ quyền');
      void queryClient.invalidateQueries({ queryKey: ['hr', 'nghi-phep', 'uy-quyen'] });
    },
    onError: (caught: unknown) =>
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không thu hồi được uỷ quyền',
      ),
  });

  const columns: ColumnsType<UyQuyenDuyetView> = [
    {
      title: 'Người được uỷ quyền',
      dataIndex: 'nguoiDuocUyQuyenPublicId',
      render: (id: string | null) => (id ? (tenTheoId.get(id) ?? id) : '—'),
    },
    {
      title: 'Người giao',
      dataIndex: 'nguoiUyQuyenPublicId',
      width: 200,
      render: (id: string | null) => (id ? (tenTheoId.get(id) ?? id) : '—'),
    },
    {
      title: 'Từ ngày',
      dataIndex: 'tuNgay',
      width: 120,
      render: (v: string) => ngayVn(v),
    },
    {
      title: 'Đến ngày',
      dataIndex: 'denNgay',
      width: 120,
      render: (v: string) => ngayVn(v),
    },
    { title: 'Lý do', dataIndex: 'lyDo', ellipsis: true, render: (v: string | null) => v ?? '—' },
    {
      title: 'Trạng thái',
      key: 'trangThai',
      width: 140,
      // ⛔⛔ BA trạng thái, ⛔ hai. *Đã thu hồi* và *hết hạn* đều ⛔ còn hiệu lực, nhưng một cái bị
      //    rút giữa chừng còn một cái chạy hết thời hạn của nó — hai câu chuyện khác nhau trên
      //    lịch sử duyệt.
      render: (_: unknown, ban: UyQuyenDuyetView) =>
        ban.daThuHoi ? (
          <Tag color="red">Đã thu hồi</Tag>
        ) : ban.dangHieuLuc ? (
          <Tag color="green">Đang hiệu lực</Tag>
        ) : (
          <Tag>Hết hạn</Tag>
        ),
    },
    {
      title: 'Thao tác',
      key: 'thaoTac',
      width: 130,
      render: (_: unknown, ban: UyQuyenDuyetView) =>
        ban.daThuHoi ? null : (
          <Popconfirm
            title="Thu hồi uỷ quyền này?"
            description="Có hiệu lực ngay. Bản ghi vẫn được giữ lại vì lịch sử duyệt trỏ vào nó."
            okText="Thu hồi"
            cancelText="Huỷ"
            onConfirm={() => thuHoi.mutate(ban.publicId)}
          >
            <Button danger size="small">
              Thu hồi
            </Button>
          </Popconfirm>
        ),
    },
  ];

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        Uỷ quyền duyệt nghỉ phép
      </Typography.Title>

      <Alert
        type="info"
        showIcon
        title="Uỷ quyền chuyển VAI người duyệt, không cấp thêm quyền"
        description="Người được chọn phải đang có quyền duyệt nghỉ phép. Uỷ quyền có hiệu lực theo ngày và thu hồi được bất cứ lúc nào — hiệu lực ngay, không phải đăng nhập lại."
      />

      <Card>
        <Space wrap>
          <span>Đơn vị:</span>
          <div style={{ minWidth: 320 }}>
            <OrgUnitTreeSelect
              value={donVi}
              onChange={setDonVi}
              placeholder="Chọn đơn vị"
              chiTrongPhamVi
            />
          </div>
          <Button type="primary" disabled={donVi === undefined} onClick={() => setMoGiao(true)}>
            Giao uỷ quyền
          </Button>
        </Space>
      </Card>

      {donVi === undefined ? (
        <Alert type="warning" showIcon title="Chọn một đơn vị để xem các uỷ quyền của đơn vị đó" />
      ) : (
        <Table
          rowKey="publicId"
          columns={columns}
          dataSource={danhSach.data ?? []}
          loading={danhSach.isLoading}
          pagination={false}
          scroll={{ x: 1000 }}
          locale={{ emptyText: 'Đơn vị này chưa có uỷ quyền nào' }}
        />
      )}

      <GiaoUyQuyenModal
        key={donVi ?? 'chua-chon'}
        open={moGiao}
        donVi={donVi}
        nguoiChon={nguoiChon}
        onClose={() => setMoGiao(false)}
      />
    </Space>
  );
}

/**
 * ⚠ `key` ở nơi gọi + `clearOnDestroy` ở đây — **cần cả hai** (T53.7 · T63.13). `clearOnDestroy`
 * chỉ dọn kho của `Form` khi phần tử unmount, mà hộp thoại đóng bằng hoạt ảnh nên lượt unmount ⛔
 * xảy ra trước lượt mở kế tiếp; `key` ép React dựng lại ngay khi đổi đơn vị.
 */
function GiaoUyQuyenModal({
  open,
  donVi,
  nguoiChon,
  onClose,
}: {
  open: boolean;
  donVi: string | undefined;
  nguoiChon: { value: string; label: string }[];
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<{ nguoi: string; khoang: [Dayjs, Dayjs]; lyDo?: string }>();

  const giao = useMutation({
    mutationFn: (v: { nguoi: string; khoang: [Dayjs, Dayjs]; lyDo?: string }) =>
      nghiPhepApi.giaoUyQuyen({
        orgUnitPublicId: donVi as string,
        nguoiDuocUyQuyenPublicId: v.nguoi,
        // ⛔ `dayjs()` trần ⛔ xuất hiện ở đây: hai giá trị này do NGƯỜI DÙNG chọn trên lịch, nên
        //   `.format` chạy trên chính mốc họ bấm — T63.18 nói về giá trị hệ tự sinh từ giờ MÁY.
        tuNgay: v.khoang[0].format('YYYY-MM-DD'),
        denNgay: v.khoang[1].format('YYYY-MM-DD'),
        lyDo: v.lyDo,
      }),
    onSuccess: () => {
      message.success('Đã giao uỷ quyền duyệt');
      void queryClient.invalidateQueries({ queryKey: ['hr', 'nghi-phep', 'uy-quyen'] });
      onClose();
    },
    onError: (caught: unknown) =>
      message.error(caught instanceof ApiClientError ? caught.message : 'Không giao được uỷ quyền'),
  });

  return (
    <Modal
      title="Giao uỷ quyền duyệt"
      open={open}
      onCancel={onClose}
      onOk={() => void form.submit()}
      okText="Giao"
      cancelText="Huỷ"
      confirmLoading={giao.isPending}
      destroyOnHidden
    >
      {/* ⚠ `clearOnDestroy` là prop của **Form**, ⛔ của Modal — `tsc` bắt được ở lượt chạy đầu
          (TS2322), trong khi ESLint và vitest đều XANH: React bỏ qua prop thừa lúc chạy, nên hộp
          thoại vẫn mở bình thường và bộ kiểm ⛔ thấy gì. Đúng hình dạng T51.14, và là lý do kho
          giữ ba cổng riêng thay vì một. */}
      <Form form={form} layout="vertical" clearOnDestroy onFinish={(v) => giao.mutate(v)}>
        <Form.Item
          name="nguoi"
          label="Người được uỷ quyền"
          rules={[{ required: true, message: 'Chọn người được uỷ quyền' }]}
          extra="Phải là người ĐANG có quyền duyệt nghỉ phép, thuộc cùng đơn vị hoặc đơn vị cấp trên."
        >
          <Select
            showSearch={{ optionFilterProp: 'label' }}
            options={nguoiChon}
            placeholder="Chọn tài khoản"
          />
        </Form.Item>
        <Form.Item
          name="khoang"
          label="Thời hạn"
          rules={[{ required: true, message: 'Chọn khoảng ngày có hiệu lực' }]}
          extra="Ngày bắt đầu được phép ở quá khứ — trưởng đơn vị đi công tác đột xuất là chuyện thật."
        >
          <DatePicker.RangePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="lyDo" label="Lý do">
          <Input.TextArea rows={2} maxLength={500} showCount placeholder="VD: đi công tác" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
