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
