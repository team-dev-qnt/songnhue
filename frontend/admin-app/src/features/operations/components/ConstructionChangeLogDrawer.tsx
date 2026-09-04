import { useQuery } from '@tanstack/react-query';
import { Drawer, Table, Tag, Typography, Empty, Skeleton } from 'antd';
import { type ColumnsType } from 'antd/es/table';

import { type AuditLogView, type AuditAction } from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { formatDateTime } from '@/shared/format';

interface Props {
  publicId: string | null;
  open: boolean;
  onClose: () => void;
}

const ACTION_COLORS: Record<AuditAction, string> = {
  CREATE: 'green',
  UPDATE: 'blue',
  DELETE: 'red',
  RESTORE: 'cyan',
  LOGIN: 'default',
  LOGOUT: 'default',
  LOGIN_FAILED: 'error',
  PERMISSION_CHANGE: 'warning',
  EXPORT: 'default',
  IMPORT: 'processing',
  APPROVE: 'success',
  REJECT: 'error',
  PUBLISH: 'processing',
  BACKUP: 'default',
  DB_RESTORE: 'error',
};

const ACTION_LABELS: Record<AuditAction, string> = {
  CREATE: 'Thêm mới',
  UPDATE: 'Cập nhật',
  DELETE: 'Xoá',
  RESTORE: 'Khôi phục',
  LOGIN: 'Đăng nhập',
  LOGOUT: 'Đăng xuất',
  LOGIN_FAILED: 'Đăng nhập sai',
  PERMISSION_CHANGE: 'Đổi quyền',
  EXPORT: 'Xuất dữ liệu',
  IMPORT: 'Nhập dữ liệu',
  APPROVE: 'Duyệt',
  REJECT: 'Từ chối',
  PUBLISH: 'Xuất bản',
  BACKUP: 'Sao lưu',
  DB_RESTORE: 'Khôi phục DB',
};

export function ConstructionChangeLogDrawer({ publicId, open, onClose }: Props) {
  const query = useQuery({
    queryKey: ['ops', 'constructions', publicId, 'change-log'],
    queryFn: () => api.get<AuditLogView[]>(`/ops/constructions/${publicId}/change-log`),
    enabled: !!publicId && open,
  });

  const columns: ColumnsType<AuditLogView> = [
    {
      title: 'Thời gian',
      dataIndex: 'occurredAt',
      width: 150,
      render: (val: string) => formatDateTime(val),
    },
    {
      title: 'Người thực hiện',
      dataIndex: 'actorUsername',
      width: 150,
      render: (val: string | null) =>
        val ? <Typography.Text strong>{val}</Typography.Text> : 'Hệ thống',
    },
    {
      title: 'Thao tác',
      dataIndex: 'action',
      width: 120,
      render: (val: AuditAction) => (
        <Tag color={ACTION_COLORS[val] || 'default'}>{ACTION_LABELS[val] || val}</Tag>
      ),
    },
    {
      title: 'Nội dung cũ',
      dataIndex: 'oldValue',
      render: (val: string | null) => (
        <div
          style={{
            maxWidth: 200,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
          title={val || ''}
        >
          <Typography.Text type="secondary">{val || '-'}</Typography.Text>
        </div>
      ),
    },
    {
      title: 'Nội dung mới',
      dataIndex: 'newValue',
      render: (val: string | null) => (
        <div
          style={{
            maxWidth: 200,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
          title={val || ''}
        >
          {val || '-'}
        </div>
      ),
    },
  ];

  return (
    <Drawer
      title="Nhật ký thay đổi hồ sơ"
      placement="right"
      width={800}
      onClose={onClose}
      open={open}
    >
      {query.isLoading ? (
        <Skeleton active />
      ) : query.data?.length ? (
        <Table<AuditLogView>
          columns={columns}
          dataSource={query.data}
          rowKey="seq"
          // Bề ngang tối thiểu: hẹp hơn thì CUỘN NGANG, không bóp chữ.
          // Vì sao cần — xem chú thích cột "Địa chỉ" ở `features/hydro/ApiSourcesPage.tsx`.
          scroll={{ x: 900 }}
          pagination={false}
          size="small"
        />
      ) : (
        <Empty description="Không có lịch sử thay đổi nào" />
      )}
    </Drawer>
  );
}
