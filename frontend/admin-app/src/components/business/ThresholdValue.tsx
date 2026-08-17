import { Tooltip } from 'antd';

import { formatNumber } from '@/shared/format';
import { statusColors, type StatusColorKey } from '@/shared/tokens';

/**
 * Số đo tự đổi màu theo ngưỡng (conventions.md §3).
 *
 * <h3>Ngưỡng đến từ API, FE không giữ bản sao quy tắc</h3>
 *
 * Mức ngưỡng là **danh mục do khách vận hành** (CLAUDE.md quy tắc 16) — Admin sửa được
 * trên màn hình cấu hình, và màn hình đó nằm trong hạng mục nghiệm thu (chốt G9). Nhúng
 * con số vào mã FE là mỗi lần Công ty đổi ngưỡng lại phải phát hành bản mới, mà tệ hơn:
 * cảnh báo do backend gửi và màu trên giao diện sẽ nói hai điều khác nhau.
 *
 * Component này chỉ so sánh, không quyết định ngưỡng là bao nhiêu.
 */
export interface ThresholdLevel {
  /** Giá trị từ mức này trở lên thì áp màu dưới đây. */
  from: number;
  color: StatusColorKey;
  label: string;
}

export interface ThresholdValueProps {
  value: number | null | undefined;
  unit?: string;
  fractionDigits?: number;
  /** Sắp xếp thế nào cũng được — component tự xét từ mức cao xuống. */
  levels?: readonly ThresholdLevel[];
  /**
   * Dữ liệu đã cũ / trạm mất tín hiệu → tô **xám**, không tô theo ngưỡng.
   *
   * Đây là quy tắc chốt ở G3: giá trị cũ của một trạm đang trục trặc **không được** dùng
   * để đánh giá ngưỡng (mã lỗi HYD-2004). Hiện màu xanh vì "số cuối cùng đọc được nằm
   * dưới ngưỡng" là nói dối người trực đúng lúc nguy hiểm nhất.
   */
  stale?: boolean;
}

export function ThresholdValue({
  value,
  unit,
  fractionDigits = 2,
  levels,
  stale = false,
}: ThresholdValueProps) {
  if (value === null || value === undefined) {
    return <span style={{ color: statusColors.unknown }}>—</span>;
  }

  const text = `${formatNumber(value, fractionDigits)}${unit ? ` ${unit}` : ''}`;

  if (stale) {
    return (
      <Tooltip title="Số liệu không còn tươi — không dùng để đánh giá ngưỡng">
        <span style={{ color: statusColors.unknown, fontWeight: 500 }}>{text}</span>
      </Tooltip>
    );
  }

  const matched = [...(levels ?? [])].sort((a, b) => b.from - a.from).find((l) => value >= l.from);

  const content = (
    <span style={{ color: matched ? statusColors[matched.color] : undefined, fontWeight: 500 }}>
      {text}
    </span>
  );

  return matched ? <Tooltip title={matched.label}>{content}</Tooltip> : content;
}
