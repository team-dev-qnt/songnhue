import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Alert, Button, Card, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import { type ColumnsType } from 'antd/es/table';

import { type SessionView } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

/**
 * Phiên đăng nhập & đăng xuất từ xa — M5.14.
 *
 * Đây là công cụ tự cứu khi người dùng nghi mình bị chiếm phiên: nhìn thấy một thiết bị
 * lạ và tự cắt nó ngay, không phải chờ quản trị viên. Backend đối chiếu chủ sở hữu ở
 * service nên không ai thu hồi được phiên của người khác qua endpoint này.
 */
export function SessionsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['auth', 'sessions'],
    queryFn: () => api.get<SessionView[]>('/auth/sessions'),
  });

  const revoke = useMutation({
    mutationFn: (sessionId: string) => api.delete<void>(`/auth/sessions/${sessionId}`),
    onSuccess: async () => {
      message.success('Đã đăng xuất thiết bị');
      await queryClient.invalidateQueries({ queryKey: ['auth', 'sessions'] });
    },
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không thu hồi được phiên');
    },
  });

  const columns: ColumnsType<SessionView> = [
    {
      title: 'Thiết bị',
      dataIndex: 'deviceLabel',
      render: (label: string | null, row) => (
        <Space>
          <span>{label ?? 'Không rõ thiết bị'}</span>
          {row.current && <Tag color="blue">Thiết bị này</Tag>}
        </Space>
      ),
    },
    { title: 'Địa chỉ IP', dataIndex: 'ipAddress', width: 160 },
    {
      title: 'Đăng nhập lúc',
      dataIndex: 'issuedAt',
      width: 170,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: 'Hoạt động gần nhất',
      dataIndex: 'lastUsedAt',
      width: 180,
      render: (value: string | null) => formatDateTime(value),
    },
    {
      title: '',
      key: 'thao-tac',
      width: 140,
      render: (_value, row) =>
        row.current ? (
          // Cho phép tự cắt phiên đang dùng thì người dùng bấm nhầm là văng ra ngoài mà
          // không hiểu vì sao. Muốn thoát thì đã có nút Đăng xuất ở thanh trên.
          <Typography.Text type="secondary">—</Typography.Text>
        ) : (
          <Popconfirm
            title="Đăng xuất thiết bị này?"
            okText="Đăng xuất"
            cancelText="Hủy"
            onConfirm={() => revoke.mutate(row.id)}
          >
            <Button type="link" danger>
              Đăng xuất từ xa
            </Button>
          </Popconfirm>
        ),
    },
  ];

  return (
    <Card title="Phiên đăng nhập">
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Thấy thiết bị lạ trong danh sách? Đăng xuất nó ngay rồi đổi mật khẩu."
      />
      {error ? (
        <Alert
          type="error"
          showIcon
          message={
            error instanceof ApiClientError ? error.message : 'Không tải được danh sách phiên'
          }
        />
      ) : (
        <Table<SessionView>
          columns={columns}
          dataSource={data ?? []}
          rowKey="id"
          loading={isLoading}
          // Bề ngang tối thiểu: hẹp hơn thì CUỘN NGANG, không bóp chữ.
          // Vì sao cần — xem chú thích cột "Địa chỉ" ở `features/hydro/ApiSourcesPage.tsx`.
          scroll={{ x: 900 }}
          pagination={false}
        />
      )}
    </Card>
  );
}
