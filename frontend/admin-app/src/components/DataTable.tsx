import { Alert, Empty, Skeleton, Table } from 'antd';
import { type ColumnsType } from 'antd/es/table';

import { ApiClientError } from '@/shared/apiClient';
import { type PageMeta } from '@/shared/api-types';
import { formatInteger } from '@/shared/format';

/**
 * Bảng dữ liệu chuẩn (conventions.md §3) — phân trang **phía máy chủ**, trạng thái rỗng,
 * khung xương lúc tải.
 *
 * <h3>Vì sao ép phân trang server-side</h3>
 *
 * AntD phân trang sẵn ở phía client, và đó chính là cái bẫy: màn hình chạy mượt với 50
 * dòng lúc phát triển, rồi tải về 200.000 dòng nhật ký kiểm toán ở môi trường thật. Bảng
 * này **không nhận** mảng dữ liệu đầy đủ — nó nhận đúng một trang cùng `meta` của backend.
 */
export interface DataTableProps<T> {
  columns: ColumnsType<T>;
  rows: T[] | undefined;
  meta: PageMeta | undefined;
  loading: boolean;
  error?: unknown;
  rowKey: keyof T | ((row: T) => string);
  onPageChange: (page: number, size: number) => void;
  /** Câu mô tả riêng khi rỗng — mặc định là câu chung. */
  emptyText?: string;
  size?: 'small' | 'middle' | 'large';
  /** Bảng nhiều cột thì đặt để cuộn ngang thay vì bóp chữ. */
  scrollX?: number;
}

export function DataTable<T extends object>({
  columns,
  rows,
  meta,
  loading,
  error,
  rowKey,
  onPageChange,
  emptyText,
  size = 'middle',
  scrollX,
}: DataTableProps<T>) {
  // Lượt tải ĐẦU TIÊN hiện khung xương; những lượt sau chỉ mờ bảng đi. Thay cả bảng bằng
  // khung xương mỗi lần đổi trang làm bố cục nhảy và mất luôn vị trí cuộn.
  if (loading && rows === undefined) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }

  if (error) {
    const apiError = error instanceof ApiClientError ? error : null;
    return (
      <Alert
        type="error"
        showIcon
        message="Không tải được dữ liệu"
        description={
          <>
            {apiError?.message ?? 'Lỗi không xác định'}
            {apiError?.traceId && <div>Mã tra cứu: {apiError.traceId}</div>}
          </>
        }
      />
    );
  }

  return (
    <Table<T>
      columns={columns}
      dataSource={rows ?? []}
      rowKey={rowKey as string | ((row: T) => string)}
      loading={loading}
      size={size}
      scroll={scrollX ? { x: scrollX } : undefined}
      locale={{
        emptyText: <Empty description={emptyText ?? 'Chưa có dữ liệu'} />,
      }}
      pagination={{
        current: meta?.page ?? 1,
        pageSize: meta?.size ?? 20,
        total: meta?.totalElements ?? 0,
        showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100],
        showTotal: (total) => `Tổng ${formatInteger(total)} bản ghi`,
        onChange: onPageChange,
      }}
    />
  );
}
