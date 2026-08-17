import type { NextConfig } from 'next';

/**
 * Cấu hình Next.js cho cổng thông tin điện tử (MOD-01).
 *
 * ⚠ Biến `NEXT_PUBLIC_*` nhúng vào bundle **lúc build**, giống Vite — đổi là phải build
 *   lại image. Biến KHÔNG có tiền tố đó (VD `REVALIDATE_SECRET`) chỉ tồn tại phía máy
 *   chủ và đọc lúc chạy; đặt nhầm tiền tố `NEXT_PUBLIC_` cho một bí mật là đưa nó thẳng
 *   vào mã nguồn ai cũng tải được.
 */
const nextConfig: NextConfig = {
  // Bắt buộc cho `deploy/docker/public-web.Dockerfile`: tầng runtime chép
  // `.next/standalone`, không có cờ này thì thư mục đó không tồn tại và image chép hụt.
  output: 'standalone',

  // `design-tokens` xuất thẳng mã TypeScript (không có bước biên dịch riêng), nên Next
  // phải được bảo là hãy transpile nó như mã nguồn của mình.
  transpilePackages: ['design-tokens'],

  // Ẩn `X-Powered-By: Next.js` — bớt một manh mối miễn phí cho người dò phiên bản
  // (conventions.md §4.5).
  poweredByHeader: false,

  // Ảnh bài viết (Phase 1) đến từ MinIO qua đường dẫn nội bộ; chưa mở host ngoài nào.
  images: {
    remotePatterns: [],
  },

  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          // CSP đầy đủ và HSTS đặt ở nginx (WS-11/T11.5) — nơi duy nhất biết đủ mọi
          // origin của cả hệ thống. Ở đây chỉ đặt phần không phụ thuộc hạ tầng.
        ],
      },
    ];
  },
};

export default nextConfig;
