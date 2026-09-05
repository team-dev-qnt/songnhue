import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd';
import { type ColumnsType } from 'antd/es/table';
import { useState } from 'react';

import { useAuth } from '@/app/auth/useAuth';
import { OrgUnitTreeSelect } from '@/components/business/OrgUnitTreeSelect';
import { StatusBadge } from '@/components/business/StatusBadge';
import { USER_STATUS } from '@/components/business/statusVocabulary';
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
  const { hasPermission } = useAuth();
  const [editing, setEditing] = useState<UserView | null>(null);
  const [creating, setCreating] = useState(false);
  const [assigning, setAssigning] = useState<UserView | null>(null);

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
      title: '',
      key: 'thao-tac',
      width: 260,
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
        scroll={{ x: 1100 }}
        pagination={{ pageSize: 20, showSizeChanger: true }}
      />

      <CreateUserModal open={creating} onClose={() => setCreating(false)} onDone={invalidate} />
      <EditUserModal user={editing} onClose={() => setEditing(null)} onDone={invalidate} />
      <AssignRolesModal user={assigning} onClose={() => setAssigning(null)} />
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
      destroyOnClose
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
      destroyOnClose
    >
      <Form<UpdateUserRequest>
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={{
          fullName: user?.fullName ?? '',
          email: user?.email ?? undefined,
          phone: user?.phone ?? undefined,
        }}
        onFinish={(values) => update.mutate(values)}
      >
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
    </Modal>
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
