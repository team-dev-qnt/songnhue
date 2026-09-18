'use client';

import dynamic from 'next/dynamic';

import type { BieuDoCongTrinh } from '@/lib/api';

/**
 * Lớp bọc mỏng để nạp biểu đồ **chỉ ở trình duyệt**.
 *
 * <h2>⛔ Vì sao phải có tệp này thay vì `dynamic()` thẳng trong trang</h2>
 *
 * Next App Router **cấm** `ssr: false` bên trong một Server Component — nó hỏng lúc **build** với
 * đúng câu *"`ssr: false` is not allowed with `next/dynamic` in Server Components"*. Nên chỗ khai
 * phải là một Client Component, và đây là nó.
 *
 * <h2>⛔ Vì sao vẫn cần `ssr: false` chứ ⛔ không import thẳng</h2>
 *
 * ECharts vẽ vào `<canvas>`. Bản thân `BieuDoDienBien` chỉ chạm `document` trong `useEffect` nên
 * về lý thuyết dựng phía máy chủ được — nhưng `echarts/core` được nạp ở **tầng module**, và một
 * thư viện đồ hoạ chạm `window` lúc nạp là chuyện thường. Đổi lấy sự chắc chắn ấy là một lượt nạp
 * chậm hơn cho một khối ⛔ không có nội dung nào để lập chỉ mục — biểu đồ ⛔ không phải thứ máy tìm
 * kiếm đọc.
 *
 * <p>⚠ Khối chờ giữ **đúng chiều cao** biểu đồ (360px). Thiếu nó thì trang nhảy một đoạn khi biểu
 * đồ nạp xong — đúng thứ Cumulative Layout Shift đo, và nó nằm trong NFR-02.
 */
const BieuDoDienBien = dynamic(() => import('./BieuDoDienBien').then((m) => m.BieuDoDienBien), {
  ssr: false,
  loading: () => (
    <div className="flex h-[360px] w-full items-center justify-center text-[13px] text-surface-textSecondary">
      Đang nạp biểu đồ…
    </div>
  ),
});

export function BieuDoDienBienClient({ bieuDo }: { bieuDo: BieuDoCongTrinh }) {
  return <BieuDoDienBien bieuDo={bieuDo} />;
}
