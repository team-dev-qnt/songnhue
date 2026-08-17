import { Tag, Tooltip } from 'antd';

import { statusColors } from '@/shared/tokens';

import { type StatusVocabulary } from './statusVocabulary';

/**
 * Hiển thị **mọi** trạng thái enum của hệ thống (conventions.md §3).
 *
 * Bộ từ vựng nằm ở `statusVocabulary.ts`; component này chỉ vẽ.
 */
export function StatusBadge({
  value,
  vocabulary,
}: {
  value: string | null | undefined;
  vocabulary: StatusVocabulary;
}) {
  if (!value) {
    return <Tag color={statusColors.unknown}>Không rõ</Tag>;
  }

  // Trạng thái backend mới thêm mà FE chưa biết: hiện nguyên mã, màu xám. Trả về `null`
  // thì ô trong bảng trống trơn và không ai biết là thiếu bản dịch hay thiếu dữ liệu.
  const meaning = vocabulary[value];
  if (!meaning) {
    return <Tag color={statusColors.unknown}>{value}</Tag>;
  }

  const tag = <Tag color={statusColors[meaning.color]}>{meaning.label}</Tag>;
  return meaning.hint ? <Tooltip title={meaning.hint}>{tag}</Tooltip> : tag;
}
