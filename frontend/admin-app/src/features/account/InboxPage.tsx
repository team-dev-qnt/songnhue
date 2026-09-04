import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, List, Space, Tag, Typography } from 'antd';

import { DataTable } from '@/components/DataTable';
import { usePagination } from '@/components/usePagination';
import { type InboxEntry } from '@/shared/api-types';
import { ApiClientError, api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';
import { severityColorKey, statusColors } from 'design-tokens';

/** Hộp thư trong ứng dụng — kênh thông báo v1 (SMS đã bỏ khỏi phạm vi, chốt BOQ đợt 1). */
export function InboxPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const pagination = usePagination(20);

  const { data, isLoading, error } = useQuery({
    queryKey: ['notifications', 'inbox', pagination.page, pagination.size],
    queryFn: () => api.getPage<InboxEntry>('/notifications', pagination.params),
  });

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };

  const markRead = useMutation({
    mutationFn: (recipientId: number) => api.post<void>(`/notifications/${recipientId}/read`),
    onSuccess: invalidate,
    onError: (caught: unknown) => {
      message.error(caught instanceof ApiClientError ? caught.message : 'Không đánh dấu được');
    },
  });

  const markAll = useMutation({
    mutationFn: () => api.post<{ marked: number }>('/notifications/read-all'),
    onSuccess: async (result) => {
      message.success(`Đã đánh dấu ${result.marked} thông báo`);
      await invalidate();
    },
    onError: (caught: unknown) =>
      message.error(
        caught instanceof ApiClientError ? caught.message : 'Không đánh dấu được tất cả',
      ),
  });

  return (
    <Card
      title="Hộp thư"
      extra={
        <Button onClick={() => markAll.mutate()} loading={markAll.isPending}>
          Đánh dấu tất cả đã đọc
        </Button>
      }
    >
      <DataTable<InboxEntry>
        rows={data?.items}
        meta={data?.meta}
        loading={isLoading}
        error={error}
        rowKey="recipientId"
        onPageChange={pagination.onPageChange}
        emptyText="Chưa có thông báo nào"
        scrollX={900}
        columns={[
          {
            title: 'Nội dung',
            key: 'noi-dung',
            render: (_value, row) => (
              <List.Item.Meta
                title={
                  <Space>
                    {/* Chưa đọc thì đậm — dấu hiệu quen thuộc nhất của một hộp thư. */}
                    <Typography.Text strong={!row.readAt}>{row.title}</Typography.Text>
                    <Tag color={statusColors[severityColorKey[row.severity]]}>{row.severity}</Tag>
                    {row.broadcast && <Tag>Thông báo chung</Tag>}
                  </Space>
                }
                description={row.body}
              />
            ),
          },
          {
            title: 'Thời điểm',
            dataIndex: 'createdAt',
            width: 170,
            render: (value: string) => formatDateTime(value),
          },
          {
            title: '',
            key: 'thao-tac',
            width: 130,
            render: (_value, row) =>
              row.readAt ? (
                <Typography.Text type="secondary">Đã đọc</Typography.Text>
              ) : (
                <Button type="link" onClick={() => markRead.mutate(row.recipientId)}>
                  Đánh dấu đã đọc
                </Button>
              ),
          },
        ]}
      />
    </Card>
  );
}
