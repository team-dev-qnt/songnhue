import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd';
import type { FormInstance } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useLayoutEffect, useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { HopThoaiMaXacThuc } from '@/components/business/HopThoaiMaXacThuc';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { StatusBadge } from '@/components/business/StatusBadge';
import { USER_STATUS } from '@/components/business/statusVocabulary';
import { type EmployeeRow } from '@/features/hr/hrVocabulary';
import {
  type CreateUserRequest,
  type RoleSummary,
  type UpdateUserRequest,
  type UserStatus,
  type UserView,
} from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { HuongDanMatKhau } from '@/shared/HuongDanMatKhau';
import { datLoiTheoTruong } from '@/shared/loiTheoTruong';
import { formatDateTime } from '@/shared/format';

/**
 * Quản lý tài khoản — lát cắt dọc chứng minh nền tảng (T6.15) nhìn từ phía giao diện.
 *
 * Mọi thao tác ở đây đi qua đủ ba tầng quyền của backend và để lại vết trong nhật ký
 * kiểm toán; màn hình không tự quyết gì cả.
 */
export function UsersPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { hasPermission, user: toi } = useAuth();
  const [editing, setEditing] = useState<UserView | null>(null);
  const [creating, setCreating] = useState(false);
  const [assigning, setAssigning] = useState<UserView | null>(null);
  const [linking, setLinking] = useState<UserView | null>(null);
  // T61.31 — đặt lại mật khẩu đi hai bước: nhập mật khẩu tạm, rồi nhập lại mã 2FA (T61.42).
  const [datLaiMk, setDatLaiMk] = useState<UserView | null>(null);
  // ⛔⛔ Hai bước phải là một TRẠNG THÁI tường minh. Bản đầu suy bước từ `matKhauTam !== ''` — gõ ký tự
  //   ĐẦU TIÊN là hộp mật khẩu tự đóng và hộp mã 2FA nhảy ra. Bài kiểm giao diện bắt ngay lượt chạy đầu.
  const [buocDatLai, setBuocDatLai] = useState<'mat-khau' | 'ma-xac-thuc'>('mat-khau');
  const [matKhauTam, setMatKhauTam] = useState('');
  const [loiXacThuc, setLoiXacThuc] = useState<string | null>(null);

  const users = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => api.get<UserView[]>('/admin/users'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });

  const setStatus = useMutation({
    mutationFn: ({ publicId, status }: { publicId: string; status: UserStatus }) =>
      api.post<UserView>(`/admin/users/${publicId}/status`, { status }),
    onSuccess: async () => {
      message.success('Đã cập nhật trạng thái');
      await invalidate();
    },
    onError: (caught: unknown) => {
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không đổi được trạng thái',
      );
    },
  });

  // T61.21 — `DELETE /admin/users/{id}` có 0 nơi gọi trước đây. Backend chặn TỰ xoá (ADM-2020);
  //   giao diện ẩn nút ở dòng của chính mình để người dùng ⛔ phải bấm mới biết.
  const xoa = useMutation({
    mutationFn: (publicId: string) => api.delete<void>(`/admin/users/${publicId}`),
    onSuccess: async () => {
      message.success('Đã xoá tài khoản');
      await invalidate();
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không xoá được tài khoản');
    },
  });

  // T61.30 — đường THẬT cho người mất cả ứng dụng xác thực lẫn mã khôi phục, sau khi backend chặn
  //   "đăng ký lại qua vé challenge" (AUTH-0009 — đường vượt 2FA của kẻ có mật khẩu). Chặn TỰ đặt lại (ADM-2021).
  const datLai2fa = useMutation({
    mutationFn: (publicId: string) => api.post<void>(`/admin/users/${publicId}/dat-lai-2fa`),
    onSuccess: () => {
      message.success('Đã đặt lại xác thực hai bước — mọi phiên của tài khoản đã bị thu hồi');
    },
    onError: (caught: unknown) => {
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không đặt lại được xác thực hai bước',
      );
    },
  });

  // T61.31 · CN-05.2 — quyền `adm:user:reset-password` có trong danh mục từ 13/08/2026 với 0 đầu
  //   nhận; đây là màn hình đầu tiên gọi nó. Người dùng quên mật khẩu trước đây ⛔ có đường nào.
  const datLaiMatKhau = useMutation({
    mutationFn: ({ publicId, maXacThuc }: { publicId: string; maXacThuc: string }) =>
      api.post<void>(`/admin/users/${publicId}/dat-lai-mat-khau`, {
        matKhauTam,
        maXacThuc,
      }),
    onSuccess: () => {
      message.success(
        'Đã đặt lại mật khẩu — mọi phiên bị thu hồi và người dùng phải đổi mật khẩu ở lần đăng nhập tới',
      );
      setDatLaiMk(null);
      setBuocDatLai('mat-khau');
      setMatKhauTam('');
      setLoiXacThuc(null);
    },
    onError: (caught: unknown) => {
      // Mã 2FA sai (ADM-2024) / thiếu (ADM-2023) hiện NGAY trong hộp thoại; lỗi khác ra toast.
      const maLoi = caught instanceof ApiClientError ? caught.code : undefined;
      if (maLoi === 'ADM-2023' || maLoi === 'ADM-2024') {
        setLoiXacThuc(caught instanceof ApiClientError ? caught.message : 'Mã xác thực không đúng');
        return;
      }
      setDatLaiMk(null);
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không đặt lại được mật khẩu',
      );
    },
  });

  const columns: ColumnsType<UserView> = [
    { title: 'Tên đăng nhập', dataIndex: 'username', width: 160 },
    { title: 'Họ tên', dataIndex: 'fullName' },
    { title: 'Email', dataIndex: 'email', width: 220 },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 150,
      render: (value: string) => <StatusBadge value={value} vocabulary={USER_STATUS} />,
    },
    {
      title: '2FA',
      dataIndex: 'twoFactorRequired',
      width: 110,
      render: (required: boolean) =>
        required ? <Tag color="blue">Bắt buộc</Tag> : <Tag>Không bắt buộc</Tag>,
    },
    {
      title: 'Đăng nhập gần nhất',
      dataIndex: 'lastLoginAt',
      width: 180,
      render: (value: string | null) => formatDateTime(value),
    },
    {
      // ⭐ Nửa ĐỌC của cặp đọc–ghi mà T51.8 mở ra. Cột `users.employee_id` có 0 đường ghi suốt 28
      //   ngày; dựng đường ghi mà ⛔ không hiện kết quả ra đây là để lại đúng một nửa vòng — người
      //   quản trị nhìn ô trống rồi liên kết hồ sơ ấy sang một tài khoản khác (luật 27).
      title: 'Hồ sơ CBNV',
      key: 'ho-so-nhan-su',
      width: 220,
      render: (_value, row) =>
        row.hoSoNhanSu ? (
          <Space size={4} wrap>
            <Tag color="blue">{row.hoSoNhanSu.code}</Tag>
            <span>{row.hoSoNhanSu.fullName}</span>
          </Space>
        ) : (
          <Tag>Chưa liên kết</Tag>
        ),
    },
    {
      title: '',
      key: 'thao-tac',
      width: 520,
      render: (_value, row) => (
        <Space size={0} wrap>
          {hasPermission('adm:user:update') && (
            <Button type="link" onClick={() => setEditing(row)}>
              Sửa
            </Button>
          )}
          {hasPermission('adm:user:assign-role') && (
            <Button type="link" onClick={() => setAssigning(row)}>
              Phân vai trò
            </Button>
          )}
          {hasPermission('adm:user:update') && (
            <Button type="link" onClick={() => setLinking(row)}>
              Hồ sơ CBNV
            </Button>
          )}
          {hasPermission('adm:user:lock') && (
            <Popconfirm
              title={row.status === 'LOCKED' ? 'Mở khóa tài khoản?' : 'Khóa tài khoản?'}
              okText="Đồng ý"
              cancelText="Hủy"
              onConfirm={() =>
                setStatus.mutate({
                  publicId: row.publicId,
                  status: row.status === 'LOCKED' ? 'ACTIVE' : 'LOCKED',
                })
              }
            >
              <Button type="link" danger={row.status !== 'LOCKED'}>
                {row.status === 'LOCKED' ? 'Mở khóa' : 'Khóa'}
              </Button>
            </Popconfirm>
          )}
          {hasPermission('adm:user:update') && row.publicId !== toi?.id && (
            <Popconfirm
              title={`Đặt lại xác thực hai bước của ${row.username}?`}
              description="Chỉ làm khi người dùng mất cả ứng dụng xác thực lẫn mã khôi phục, và đã xác minh đúng người. Mọi phiên của tài khoản bị thu hồi; lần đăng nhập sau họ đăng ký lại từ đầu."
              okText="Đặt lại"
              okButtonProps={{ danger: true }}
              cancelText="Hủy"
              onConfirm={() => datLai2fa.mutate(row.publicId)}
            >
              <Button type="link" aria-label={`Đặt lại 2FA của ${row.username}`}>
                Đặt lại 2FA
              </Button>
            </Popconfirm>
          )}
          {hasPermission('adm:user:reset-password') && row.publicId !== toi?.id && (
            <Button
              type="link"
              aria-label={`Đặt lại mật khẩu của ${row.username}`}
              onClick={() => {
                setDatLaiMk(row);
                setBuocDatLai('mat-khau');
                setMatKhauTam('');
                setLoiXacThuc(null);
              }}
            >
              Đặt lại mật khẩu
            </Button>
          )}
          {hasPermission('adm:user:update') && row.publicId !== toi?.id && (
            <Popconfirm
              title={`Xoá tài khoản ${row.username}?`}
              description="Tài khoản mất quyền đăng nhập ngay và giao diện không có đường khôi phục. Chỉ khoá nếu có thể cần lại."
              okText="Xoá"
              okButtonProps={{ danger: true }}
              cancelText="Hủy"
              onConfirm={() => xoa.mutate(row.publicId)}
            >
              <Button
                type="link"
                danger
                icon={<DeleteOutlined />}
                aria-label={`Xoá tài khoản ${row.username}`}
              >
                Xoá
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Tài khoản"
      extra={
        hasPermission('adm:user:create') && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
            Thêm tài khoản
          </Button>
        )
      }
    >
      <Table<UserView>
        columns={columns}
        dataSource={users.data ?? []}
        rowKey="publicId"
        loading={users.isLoading}
        scroll={{ x: 1420 }}
        pagination={{ pageSize: 20, showSizeChanger: true }}
      />

      <CreateUserModal open={creating} onClose={() => setCreating(false)} onDone={invalidate} />
      <EditUserModal user={editing} onClose={() => setEditing(null)} onDone={invalidate} />
      <AssignRolesModal user={assigning} onClose={() => setAssigning(null)} />
      <LienKetHoSoModal user={linking} onClose={() => setLinking(null)} onDone={invalidate} />
      <Modal
        title={`Đặt lại mật khẩu cho ${datLaiMk?.username ?? ''}`}
        open={datLaiMk !== null && buocDatLai === 'mat-khau'}
        okText="Tiếp tục"
        cancelText="Hủy"
        okButtonProps={{ disabled: matKhauTam === '' }}
        onOk={() => setBuocDatLai('ma-xac-thuc')}
        onCancel={() => setDatLaiMk(null)}
      >
        <p>
          Đặt một mật khẩu tạm và đưa cho người dùng ngoài hệ thống. Họ buộc phải đổi ở lần đăng
          nhập tới, và mọi phiên hiện có bị thu hồi.
        </p>
        <Input.Password
          autoFocus
          placeholder="Mật khẩu tạm"
          aria-label="Mật khẩu tạm"
          value={matKhauTam}
          onChange={(e) => setMatKhauTam(e.target.value)}
        />
        <HuongDanMatKhau />
      </Modal>

      <HopThoaiMaXacThuc
        open={datLaiMk !== null && buocDatLai === 'ma-xac-thuc'}
        title="Xác nhận đặt lại mật khẩu"
        moTa={`Thao tác này chiếm quyền đăng nhập vào tài khoản ${datLaiMk?.username ?? ''}.`}
        loi={loiXacThuc}
        dangGui={datLaiMatKhau.isPending}
        onHuy={() => {
          setDatLaiMk(null);
          setBuocDatLai('mat-khau');
          setMatKhauTam('');
          setLoiXacThuc(null);
        }}
        onXacNhan={(maXacThuc) => {
          if (datLaiMk) {
            datLaiMatKhau.mutate({ publicId: datLaiMk.publicId, maXacThuc });
          }
        }}
      />
    </Card>
  );
}

// =============================================================================

function CreateUserModal({
  open,
  onClose,
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  onDone: () => Promise<void>;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<CreateUserRequest>();

  const create = useMutation({
    mutationFn: (values: CreateUserRequest) => api.post<UserView>('/admin/users', values),
    onSuccess: async () => {
      message.success('Đã tạo tài khoản — người dùng phải đổi mật khẩu ở lần đăng nhập đầu');
      form.resetFields();
      onClose();
      await onDone();
    },
    onError: (caught: unknown) => {
      // ⭐ 01/09: `datLoiTheoTruong` TRẢ VỀ `false` khi không trường nào trên biểu mẫu nhận
      //    được lỗi — và lúc ấy phải rơi xuống toast. Bản trước gọi `form.setFields` rồi
      //    `return` vô điều kiện: backend gửi `field: "newPassword"` (trường của màn hình ĐỔI
      //    mật khẩu) trong khi biểu mẫu này khai `temporaryPassword`, AntD bỏ qua tên lạ trong
      //    im lặng, và 422 hiện ra thành MỘT MÀN HÌNH KHÔNG ĐỔI GÌ.
      if (caught instanceof ApiClientError && datLoiTheoTruong(form, caught)) return;
      message.error(caught instanceof ApiClientError ? caught.message : 'Không tạo được tài khoản');
    },
  });

  return (
    <Modal
      open={open}
      title="Thêm tài khoản"
      okText="Tạo"
      cancelText="Hủy"
      confirmLoading={create.isPending}
      onCancel={onClose}
      onOk={() => void form.submit()}
      destroyOnHidden
    >
      <Form<CreateUserRequest>
        form={form}
        layout="vertical"
        onFinish={(values) => create.mutate(values)}
        preserve={false}
      >
        <Form.Item
          name="username"
          label="Tên đăng nhập"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item
          name="fullName"
          label="Họ và tên"
          rules={[{ required: true, message: 'Bắt buộc' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="email"
          label="Email"
          rules={[{ type: 'email', message: 'Email không hợp lệ' }]}
        >
          <Input autoComplete="off" />
        </Form.Item>
        <Form.Item
          name="orgUnitPublicId"
          label="Đơn vị"
          rules={[{ required: true, message: 'Chọn đơn vị' }]}
          // Đơn vị quyết định phạm vi dữ liệu người này đọc được (phân quyền tầng 3),
          // nên đây không phải trường hành chính — chọn sai là mở nhầm phạm vi.
          extra="Quyết định phạm vi dữ liệu tài khoản được xem"
        >
          <OrgUnitTreeSelect />
        </Form.Item>
        {/* ⭐ 01/09: `extra` cũ chỉ nói *"Người dùng bắt buộc đổi ở lần đăng nhập đầu tiên"* —
            đúng, và không một chữ nào về yêu cầu độ mạnh, dù đó chính là thứ làm lượt bấm "Tạo"
            thất bại. `HuongDanMatKhau` đọc chính sách THẬT từ `settings`; ghi cứng "≥10 ký tự"
            vào đây là dựng một con số nói dối ngay lần đầu Admin sửa tham số (§10.69). */}
        <Form.Item
          name="temporaryPassword"
          label="Mật khẩu tạm"
          rules={[{ required: true, message: 'Bắt buộc' }]}
          extra={
            <>
              <HuongDanMatKhau />
              <div>Người dùng bắt buộc đổi ở lần đăng nhập đầu tiên.</div>
            </>
          }
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function EditUserModal({
  user,
  onClose,
  onDone,
}: {
  user: UserView | null;
  onClose: () => void;
  onDone: () => Promise<void>;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<UpdateUserRequest>();

  const update = useMutation({
    mutationFn: (values: UpdateUserRequest) =>
      api.put<UserView>(`/admin/users/${user?.publicId}`, values),
    onSuccess: async () => {
      message.success('Đã cập nhật');
      onClose();
      await onDone();
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không cập nhật được');
    },
  });

  return (
    <Modal
      open={user !== null}
      title={`Sửa tài khoản ${user?.username ?? ''}`}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={update.isPending}
      onCancel={onClose}
      onOk={() => void form.submit()}
      destroyOnHidden
    >
      <BieuMauSuaTaiKhoan
        // ⛔ `key` ép dựng lại thân biểu mẫu ngay khi đổi tài khoản — xem javadoc của nó.
        key={user?.publicId ?? 'chua-chon'}
        khoa={user?.publicId ?? 'chua-chon'}
        form={form}
        user={user}
        onFinish={(values) => update.mutate(values)}
      />
    </Modal>
  );
}

/**
 * Thân biểu mẫu sửa tài khoản — tách ra để {@code key} ép dựng lại khi đổi tài khoản.
 *
 * <h2>⛔⛔⛔ Vì sao {@code initialValues} + {@code preserve={false}} ⛔ ĐỦ — T63.17</h2>
 *
 * <p>{@code UsersPage} render {@code <EditUserModal user={editing} …/>} <b>vô điều kiện</b>, và
 * {@code Form.useForm()} nằm ở component NGOÀI {@code Modal}. {@code rc-field-form@2.7.1} áp
 * {@code setInitialValues(iv, init)} bằng {@code merge(initialValues, this.store)} ⇒ <b>kho giá
 * trị THẮNG {@code initialValues}</b>.
 *
 * <p>{@code preserve={false}} <i>về nguyên tắc</i> cứu được: khi Form unmount, rc-field-form ghi
 * các trường vào {@code prevWithoutPreserves} và lượt {@code init} sau lấy chúng từ
 * {@code initialValues} thay vì từ kho. Nhưng nó đứng trên tiền đề <b>Form ĐÃ unmount</b>, mà
 * T53.7 đo được: {@code destroyOnHidden} chỉ tháo cây con <b>sau khi hoạt ảnh đóng chạy xong</b>
 * (chờ 3 giây trong bộ kiểm, {@code <input>} <b>chưa bao giờ</b> unmount). ⇒ một <b>cuộc đua</b>,
 * ⛔ một bảo đảm — và đo được là nó THUA: {@code suaTaiKhoanVongKhuHoi.test.tsx} trước lượt vá này
 * gửi {@code fullName: "Nguyễn Văn Thắng"} lên {@code /admin/users/u-b}.
 *
 * <p>⛔⛔ {@code PUT /admin/users/{id}} là <b>thay toàn phần</b> ba trường danh tính ⇒ email
 * nhận cảnh báo và thư đặt lại mật khẩu của người B bị thay bằng của người A, kèm thông báo
 * <i>"Đã cập nhật"</i>.
 *
 * <p>⇒ {@code useLayoutEffect} đặt giá trị <b>tường minh</b>, chạy TRƯỚC lượt vẽ nên người dùng
 * ⛔ bao giờ thấy một khung hình mang danh tính của người khác. ⛔⛔ Và ⛔ thêm
 * {@code clearOnDestroy} (T53.7: lượt dọn của cây CŨ chạy SAU {@code useLayoutEffect} của cây MỚI
 * ⇒ ô ra RỖNG). MỘT cơ chế, tường minh, có bài kiểm.
 */
function BieuMauSuaTaiKhoan({
  khoa,
  form,
  user,
  onFinish,
}: {
  khoa: string;
  form: FormInstance<UpdateUserRequest>;
  user: UserView | null;
  onFinish: (values: UpdateUserRequest) => void;
}) {
  useLayoutEffect(() => {
    form.resetFields();
    // ⚠ Đặt ĐỦ ba trường của `UpdateUserRequest`. Thiếu một trường là để nó mang giá trị của tài
    //   khoản mở TRƯỚC — đúng khuyết tật này, chỉ hẹp lại còn một ô.
    form.setFieldsValue({
      fullName: user?.fullName ?? '',
      email: user?.email ?? undefined,
      phone: user?.phone ?? undefined,
    });
    // `khoa` là danh tính tài khoản; `user` là object dựng lại mỗi lượt render nên ⛔ đưa vào deps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [khoa, form]);

  return (
    <Form<UpdateUserRequest> form={form} layout="vertical" preserve={false} onFinish={onFinish}>
      <Form.Item
        name="fullName"
        label="Họ và tên"
        rules={[{ required: true, message: 'Bắt buộc' }]}
      >
        <Input />
      </Form.Item>
      <Form.Item
        name="email"
        label="Email"
        rules={[{ type: 'email', message: 'Email không hợp lệ' }]}
      >
        <Input />
      </Form.Item>
      <Form.Item name="phone" label="Điện thoại">
        <Input />
      </Form.Item>
    </Form>
  );
}

function AssignRolesModal({ user, onClose }: { user: UserView | null; onClose: () => void }) {
  const { message } = App.useApp();
  const [selected, setSelected] = useState<string[] | null>(null);

  const catalog = useQuery({
    queryKey: ['admin', 'roles', 'catalog'],
    queryFn: () => api.get<RoleSummary[]>('/admin/users/roles/catalog'),
    staleTime: 5 * 60 * 1000,
  });

  const current = useQuery({
    queryKey: ['admin', 'users', user?.publicId, 'roles'],
    queryFn: () => api.get<string[]>(`/admin/users/${user?.publicId}/roles`),
    enabled: user !== null,
  });

  const save = useMutation({
    mutationFn: (roleCodes: string[]) =>
      api.put<void>(`/admin/users/${user?.publicId}/roles`, { roleCodes }),
    onSuccess: () => {
      message.success('Đã cập nhật vai trò — có hiệu lực ở lần đăng nhập tiếp theo của người dùng');
      setSelected(null);
      onClose();
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không gán được vai trò');
    },
  });

  return (
    <Modal
      open={user !== null}
      title={`Phân vai trò — ${user?.fullName ?? ''}`}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={save.isPending}
      onCancel={() => {
        setSelected(null);
        onClose();
      }}
      onOk={() => save.mutate(selected ?? current.data ?? [])}
    >
      <Select
        mode="multiple"
        style={{ width: '100%' }}
        loading={catalog.isLoading || current.isLoading}
        value={selected ?? current.data ?? []}
        onChange={setSelected}
        optionFilterProp="label"
        options={(catalog.data ?? []).map((role) => ({
          value: role.code,
          label: `${role.name} (${role.permissionCount} quyền)`,
        }))}
      />
    </Modal>
  );
}

// =============================================================================
// Liên kết tài khoản ↔ hồ sơ CBNV — T51.8, CN-05.1
// =============================================================================

/**
 * ⛔⛔ Ô này quyết định **ai đọc được CCCD/lương/số tài khoản của ai** — ⛔ không phải một trường
 * hồ sơ bình thường.
 *
 * Vế thứ hai của CN-04.7 (*"chính nhân viên đó xem được trường 🔒 của mình"*) suy quyền đọc thẳng
 * từ `users.employee_id`. Backend vì thế: cấm tự liên kết chính mình (`ADM-2018`), ép mỗi hồ sơ
 * chỉ thuộc một tài khoản (`ADM-2017` + chỉ mục `uq_users_employee_id`), ghi một dòng
 * `security_events` mức DANGER cho mỗi lượt đổi, và xoá đệm phân quyền để lượt gỡ có hiệu lực ngay.
 *
 * ⚠ Hộp thoại này **⛔ không** dựng lại bất kỳ luật nào trong số đó — nó chỉ hiển thị lỗi backend
 * trả về. Chép luật xuống giao diện là hai nơi phải nhớ cùng một điều (luật 14), và cái ở giao
 * diện sẽ **nói dối** vào ngày backend đổi.
 */
function LienKetHoSoModal({
  user,
  onClose,
  onDone,
}: {
  user: UserView | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const { message } = App.useApp();
  const [chon, setChon] = useState<string | null>(null);
  const [tuKhoa, setTuKhoa] = useState('');

  // ⚠ `key={user?.publicId}` ở nơi gọi ⛔ không đủ — T51.12/T53.7 đã trả giá ba lần cho đúng chỗ
  //   này. Ở đây trạng thái là `useState` của CHÍNH component, và `Modal destroyOnHidden` tháo cả
  //   cây con nên nó ra đời lại rỗng mỗi lượt mở. ⛔ Không có `Form.useForm()` nào ở ngoài để rò rỉ.
  const danhSach = useQuery({
    queryKey: ['hr', 'employees', 'chon-lien-ket', tuKhoa],
    // ⛔⛔ `getPage`, ⛔ KHÔNG `api.get<PageResult<…>>`. Bản đầu của tôi dùng `api.get` và
    //   `apiPaging.test.ts` đỏ ngay lượt chạy đầu: envelope phân trang trả `data` là một MẢNG kèm
    //   `meta` ở NGOÀI, nên `.items` là `undefined` ⇒ ô chọn RỖNG vĩnh viễn, ⛔ không một dòng lỗi.
    //   Đây là bộ canh thứ ba của dự án bắt chính người vừa viết mã.
    queryFn: () =>
      api.getPage<EmployeeRow>('/hr/employees', {
        q: tuKhoa || undefined,
        size: 20,
        sort: 'fullName,asc',
      }),
    enabled: user !== null,
  });

  const luu = useMutation({
    mutationFn: (employeePublicId: string | null) =>
      api.put<UserView>(`/admin/users/${user?.publicId}/ho-so-nhan-su`, { employeePublicId }),
    onSuccess: (_data, employeePublicId) => {
      message.success(employeePublicId ? 'Đã liên kết hồ sơ' : 'Đã gỡ liên kết');
      onDone();
      onClose();
    },
    // ⛔ Bắt buộc — `moiLuotGhiPhaiBaoLoi.test.ts` canh đúng chuyện này: một `useMutation` thiếu
    //   `onError` là một nút bấm xong ⛔ không có gì xảy ra và ⛔ không có gì báo.
    onError: (e: unknown) => {
      message.error(e instanceof ApiClientError ? e.message : 'Không lưu được liên kết');
    },
  });

  return (
    <Modal
      open={user !== null}
      title={`Hồ sơ CBNV của tài khoản ${user?.username ?? ''}`}
      onCancel={onClose}
      destroyOnHidden
      footer={null}
    >
      <Space direction="vertical" size={12} style={{ display: 'flex' }}>
        <div>
          Đang liên kết:{' '}
          {user?.hoSoNhanSu ? (
            <Tag color="blue">
              {user.hoSoNhanSu.code} · {user.hoSoNhanSu.fullName}
            </Tag>
          ) : (
            <Tag>Chưa liên kết</Tag>
          )}
        </div>

        <Select
          showSearch
          allowClear
          style={{ width: '100%' }}
          placeholder="Gõ tên hoặc mã cán bộ để tìm"
          value={chon}
          onChange={setChon}
          onSearch={setTuKhoa}
          filterOption={false}
          loading={danhSach.isFetching}
          notFoundContent={danhSach.isFetching ? 'Đang tìm…' : 'Không có hồ sơ nào khớp'}
          options={(danhSach.data?.items ?? []).map((e) => ({
            value: e.publicId,
            label: `${e.code} · ${e.fullName}`,
          }))}
        />

        <Space>
          <Button
            type="primary"
            disabled={!chon}
            loading={luu.isPending}
            onClick={() => luu.mutate(chon)}
          >
            Liên kết
          </Button>
          {user?.hoSoNhanSu ? (
            <Popconfirm
              title="Gỡ liên kết hồ sơ?"
              description="Người dùng sẽ mất quyền xem thông tin bảo mật của chính mình ngay lập tức."
              okText="Gỡ"
              cancelText="Hủy"
              onConfirm={() => luu.mutate(null)}
            >
              <Button danger loading={luu.isPending}>
                Gỡ liên kết
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      </Space>
    </Modal>
  );
}
