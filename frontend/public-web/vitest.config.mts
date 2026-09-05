import { fileURLToPath } from 'node:url';

import { defineConfig } from 'vitest/config';

/**
 * Vitest cho public-web.
 *
 * Chỉ chạy trên phần **logic thuần** (`src/lib`): dựng cây menu, ghép đường dẫn, định dạng
 * ngày. Đó là chỗ lỗi âm thầm nhất của cổng — một mục menu ghép sai đường dẫn không làm
 * trang nổ, nó chỉ dẫn người dùng tới 404.
 *
 * ⚠ Phần mở rộng `.mts` là bắt buộc: `public-web/package.json` không có `"type": "module"`
 * (Next không cần), nên tệp `.ts` bị nạp như CommonJS và Vite cảnh báo ở mọi lượt chạy.
 *
 * ⛔ Cố ý **không** dựng môi trường DOM để render component. Trang của cổng là Server
 * Component gọi API; kiểm chúng cho tử tế cần cả backend, mà việc đó đã có bài kiểm tích hợp
 * ở `app` làm. Dựng một tầng mock nửa vời ở đây chỉ tạo ra thứ xanh mà không chứng minh gì.
 */
export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    include: ['src/**/*.test.ts'],
    environment: 'node',
  },
});
