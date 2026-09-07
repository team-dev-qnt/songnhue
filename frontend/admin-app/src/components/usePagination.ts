import { useState } from 'react';

/**
 * Trạng thái phân trang dùng chung cho mọi màn hình danh sách.
 *
 * Backend đếm trang **từ 1** (conventions.md §1.3) — khác `Page.getNumber()` của Spring
 * đếm từ 0. Giữ đúng quy ước đó ở một chỗ để không màn hình nào tự trừ 1 rồi lệch mất
 * một trang.
 */
export function usePagination(initialSize = 20) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(initialSize);

  return {
    page,
    size,
    params: { page, size },
    onPageChange: (nextPage: number, nextSize: number) => {
      setPage(nextPage);
      setSize(nextSize);
    },
    /** Gọi khi đổi bộ lọc: giữ nguyên trang cũ thì dễ rơi vào trang trống. */
    reset: () => setPage(1),
  };
}
