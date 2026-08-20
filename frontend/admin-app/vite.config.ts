import { fileURLToPath, URL } from 'node:url';

import react from '@vitejs/plugin-react';
// `vitest/config` chứ không phải `vite`: cùng một `defineConfig` nhưng có thêm
// kiểu cho khối `test`, nếu không TypeScript báo thừa thuộc tính.
import { defineConfig } from 'vitest/config';

/**
 * ⚠ Vite nhúng biến `VITE_*` vào bundle LÚC BUILD — không đọc lúc chạy.
 * Đổi `VITE_API_BASE_URL` bắt buộc phải build lại (xem `deploy/docker/admin-app.Dockerfile`).
 * Và giá trị đúng của nó là **để trống**: xem `deploy/env/local.env.example`.
 *
 * ⚠⚠ Bản trước của chính chú thích này ghi *"dev server KHÔNG proxy sang backend, FE gọi
 * thẳng `VITE_API_BASE_URL` đúng như lúc chạy thật sau nginx"*. Vế sau tự bác bỏ vế trước:
 * **sau nginx thì FE và API CÙNG origin**, nên gọi thẳng sang một cổng khác mới là cái đi
 * lệch production, không phải proxy. Lý lẽ "proxy giấu mất lỗi CORS" cũng ngược — nó giấu
 * một lỗi chỉ tồn tại vì ta tự tạo ra sự khác origin. Kết quả thật: preflight bị backend
 * trả `403 Invalid CORS request` và cả giao diện quản trị không dùng được.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    /**
     * ⚠⚠ Chuyển tiếp `/api` sang backend chạy native — **cùng origin** với trang.
     *
     * Không có khối này thì `apiClient` gọi `/api/v1/...` vào chính máy chủ dev của Vite,
     * Vite không biết đường đó nên trả `index.html`, và axios báo lỗi phân tích JSON ở một
     * chỗ chẳng liên quan gì tới nguyên nhân.
     *
     * Đường vòng "trỏ thẳng sang `http://localhost:8080`" thì gặp tường CORS: backend
     * **không cấu hình CORS**, preflight trả `403 Invalid CORS request`. Và kể cả có mở CORS
     * thì local vẫn đi một đường khác production (nơi nginx gộp chung origin — T11.5), tức là
     * tự nhận lại đúng loại chênh lệch native-vs-Docker mà dự án đã trả giá.
     */
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET || 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/testsupport/setup.ts'],
    css: false,
  },
});
