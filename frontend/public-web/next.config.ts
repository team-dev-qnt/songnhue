import type { NextConfig } from 'next';

/**
 * Cấu hình Next.js cho cổng thông tin điện tử (MOD-01).
 *
 * ⚠ Biến `NEXT_PUBLIC_*` nhúng vào bundle **lúc build**, giống Vite — đổi là phải build
 *   lại image. Biến KHÔNG có tiền tố đó (VD `REVALIDATE_SECRET`) chỉ tồn tại phía máy
 *   chủ và đọc lúc chạy; đặt nhầm tiền tố `NEXT_PUBLIC_` cho một bí mật là đưa nó thẳng
 *   vào mã nguồn ai cũng tải được.
 */
/**
 * Content-Security-Policy của cổng công khai.
 *
 * <h3>⚠⚠ Header này TRƯỚC ĐÂY KHÔNG TỒN TẠI, và cả hai bên đều tưởng bên kia đặt</h3>
 *
 * Chú thích cũ ở đây ghi *"CSP đầy đủ và HSTS đặt ở nginx"*, trong khi
 * `deploy/nginx/snippets/edge-headers.conf` ghi ngược lại: *"Cố ý KHÔNG đặt lại CSP … Hai
 * image FE đã đặt đủ chúng (admin-app.Dockerfile · public-web `next.config`)"*. Kết quả là
 * cổng công khai chạy **không có CSP nào** suốt từ WS-16, và `NginxSecurityHeadersTest` không
 * bắt được vì nó chỉ soi `admin-app.Dockerfile`.
 *
 * Đây đúng hình dạng lỗi đặc trưng của dự án: *một cơ chế canh gác tồn tại trong tài liệu
 * nhưng chưa có hiệu lực ở nơi nó phải chặn*. Hai tệp trỏ vào nhau nên đọc tệp nào cũng thấy
 * yên tâm. `csp.test.ts` nay khẳng định từng chỉ thị bằng cách đọc chính giá trị đã giải.
 *
 * <h3>Vì sao `script-src` phải có `'unsafe-inline'`, khác admin-app</h3>
 *
 * Next App Router chèn `<script>` nội tuyến để truyền dữ liệu flight và khởi động hydration.
 * Cách chặt hơn là gắn `nonce`, nhưng nonce phải khác nhau mỗi request — tức mọi trang thành
 * động và **ISR tắt hẳn**, trong khi NFR-02 (trang chủ < 3s) dựa vào ISR. admin-app là bundle
 * Vite tĩnh, không có script nội tuyến nào, nên nó giữ được `script-src 'self'`.
 *
 * <h3>`frame-src` — đúng hai host, mỗi host một lý do</h3>
 *
 * - `www.google.com` — khung bản đồ trụ sở ở trang Liên hệ và chân trang (CR-22);
 * - `www.youtube-nocookie.com` — video phóng sự ở khối Truyền thông (CN-01.3).
 *
 * ⚠ Thêm host thứ ba ở đây mà quên `noFabricatedContent.test.ts` (danh sách tên miền được
 * phép trong mã component) thì hai danh sách lệch nhau — luật 14.
 */
const CSP = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline'",
  "style-src 'self' 'unsafe-inline'",
  // `https://tile.openstreetmap.org` — nguồn ô bản đồ của khối Bản đồ công trình (CN-02.4).
  // ⚠ Địa chỉ này phải khớp `TILE_HOST` ở `src/lib/mapTiles.ts`; `mapTiles.test.ts` đối chiếu
  //   hai bên. Lệch nhau thì bản đồ vẫn dựng và vẫn kéo thả được, chỉ toàn màu xám vì mọi ô
  //   ảnh bị chặn — lỗi chỉ hiện trong console trình duyệt, nơi không cổng kiểm nào nhìn.
  "img-src 'self' data: blob: https://tile.openstreetmap.org",
  "font-src 'self'",
  "connect-src 'self'",
  "frame-src 'self' https://www.google.com https://www.youtube-nocookie.com",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
].join('; ');

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

  /*
    ⚠⚠ KHÔNG dùng `rewrites()` để chuyển tiếp API — đã thử và hỏng.

    Với `output: 'standalone'`, Next **gọi `rewrites()` lúc BUILD** rồi ghi kết quả đã giải
    sẵn vào `.next/required-server-files.json`. Nên `process.env.API_INTERNAL_BASE_URL` đọc
    được ở đó là giá trị lúc build — mà lúc build trong Docker biến đó chưa tồn tại, nên nó
    rơi về `http://localhost:8080` và **bị nướng cứng vào image**.

    Triệu chứng đo được: container có đúng `API_INTERNAL_BASE_URL=http://app:8080/api/v1`
    (kiểm bằng `printenv`), mà log vẫn `Error: connect ECONNREFUSED 127.0.0.1:8080`.

    Việc chuyển tiếp nay nằm ở `src/app/api/v1/[...path]/route.ts` — một Route Handler chạy
    ở mỗi request, nên đọc env **lúc chạy** và một image dùng được cho mọi môi trường (đúng
    nguyên tắc "đóng gói một lần, đề bạt cùng image" của `docs/cicd.md`).
  */

  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Content-Security-Policy', value: CSP },
        ],
      },
    ];
  },
};

export default nextConfig;
