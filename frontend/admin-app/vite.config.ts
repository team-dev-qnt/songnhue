import { fileURLToPath, URL } from 'node:url';

import react from '@vitejs/plugin-react';
// `vitest/config` chứ không phải `vite`: cùng một `defineConfig` nhưng có thêm
// kiểu cho khối `test`, nếu không TypeScript báo thừa thuộc tính.
import { defineConfig } from 'vitest/config';

/**
 * ⚠ Vite nhúng biến `VITE_*` vào bundle LÚC BUILD — không đọc lúc chạy.
 * Đổi `VITE_API_BASE_URL` bắt buộc phải build lại (xem `deploy/docker/admin-app.Dockerfile`).
 *
 * Dev server KHÔNG proxy sang backend: FE gọi thẳng `VITE_API_BASE_URL`, đúng như
 * lúc chạy thật sau nginx. Proxy trong dev sẽ giấu mất lỗi CORS/cookie cho tới khi
 * lên staging mới lộ — mà cookie refresh là `SameSite=Strict`, đúng thứ dễ vỡ nhất
 * khi đổi origin.
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
