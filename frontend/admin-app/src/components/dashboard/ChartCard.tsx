import { Card, Typography } from 'antd';
import { type ReactNode } from 'react';

/**
 * Khung thẻ chứa một biểu đồ — T23.4.
 *
 * Chỉ lo phần vỏ: tiêu đề, ghi chú, chiều cao. Nội dung biểu đồ do {@code BaseChart} vẽ,
 * và trạng thái rỗng/đang tải cũng nằm ở đó — gộp cả hai vào đây thì mỗi thẻ lại tự quyết
 * định "thế nào là rỗng" theo một kiểu.
 *
 * <p>`note` dùng để nói những điều mà một biểu đồ không tự nói được — ví dụ con số này đã
 * bị lọc theo phạm vi đơn vị của người đang xem, nên hai người xem cùng màn hình có thể
 * thấy hai tổng khác nhau và **cả hai đều đúng**.
 */
export function ChartCard({
  title,
  note,
  extra,
  wall = false,
  children,
}: {
  title: string;
  note?: string;
  extra?: ReactNode;
  wall?: boolean;
  children: ReactNode;
}) {
  return (
    <Card
      size={wall ? 'default' : 'small'}
      style={{ height: '100%' }}
      title={<span style={{ fontSize: wall ? 'clamp(15px, 0.75vw, 26px)' : 14 }}>{title}</span>}
      extra={extra}
      styles={{ body: { padding: wall ? 16 : 12 } }}
    >
      {children}
      {note && (
        <Typography.Text
          type="secondary"
          style={{
            display: 'block',
            marginTop: 8,
            fontSize: wall ? 'clamp(11px, 0.5vw, 16px)' : 12,
          }}
        >
          {note}
        </Typography.Text>
      )}
    </Card>
  );
}
