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
 * <h3>⛔⛔ Khối cũ ở đây ĐÃ HẾT ĐÚNG — sửa 20/09/2026 (T73.7)</h3>
 *
 * Nó khai: *"`script-src` phải có `'unsafe-inline'` … nonce phải khác nhau mỗi request — tức mọi
 * trang thành động và **ISR tắt hẳn**, trong khi NFR-02 (trang chủ < 3s) dựa vào ISR"*.
 *
 * **Đo lại bằng `next build` thì lập luận ấy đã đổ**: 18/19 route vốn ĐÃ là `ƒ (Dynamic)` và bảng
 * route ⛔ có một dòng `●` (ISR) nào — `apiGetWithMeta` gọi `await connection()` trước mọi lượt
 * fetch. Cái giá mà nó e ngại **đã trả từ lâu**, nên nonce cộng thêm **0**; dựng lại sau khi thêm
 * middleware cho **đúng bảng route cũ**, `/robots.txt` vẫn `○`.
 *
 * ⚠ Chỗ lập luận cũ sai là **trộn hai cơ chế cùng tên "ISR"**: thứ NFR-02 dựa vào là bộ đệm
 * **DỮ LIỆU FETCH** (`next: { revalidate: 300 }` — backend chỉ bị hỏi 1 lần / 5 phút), ⛔ phải bộ
 * đệm TRANG. Nonce ⛔ đụng tới cái thứ nhất.
 *
 * ⇒ CSP nay dựng ở `src/lib/csp.ts` và gắn ở `src/middleware.ts`. `admin-app` là bundle Vite tĩnh,
 * ⛔ có script nội tuyến nào, nên nó vẫn giữ `script-src 'self'` trần — hai tầng, hai cách, và
 * `NginxSecurityHeadersTest` canh riêng tầng kia.
 *
 * <h3>`frame-src` — đúng hai host, mỗi host một lý do</h3>
 *
 * - `www.google.com` — khung bản đồ trụ sở ở trang Liên hệ và chân trang (CR-22);
 * - `www.youtube-nocookie.com` — video phóng sự ở khối Truyền thông (CN-01.3).
 *
 * ⚠ Thêm host thứ ba ở đây mà quên `noFabricatedContent.test.ts` (danh sách tên miền được
 * phép trong mã component) thì hai danh sách lệch nhau — luật 14.
 */
// ⭐⭐ Hằng `CSP` ĐÃ CHUYỂN sang `src/lib/csp.ts` ngày 20/09/2026 (T73.7).
//
// Lý do: nonce chỉ sinh được LÚC CHẠY (mỗi request một giá trị), mà `headers()` ở tệp này giải
// **một lần lúc build** — một nonce nướng vào image còn tệ hơn `'unsafe-inline'`, vì nó TRÔNG như
// đã siết. Nay `src/middleware.ts` là nơi DUY NHẤT đặt header ấy.
//
// ⛔⛔ Và ⛔ để lại một bản CSP "dự phòng" ở đây: hai nơi cùng đặt một header chính là cái bẫy đã
// làm cổng công khai chạy ⛔ CSP nào suốt từ WS-16 (§10.61) — tệp này bảo *"nginx đặt"*, nginx bảo
// *"image FE đặt"*, đọc tệp nào cũng thấy yên tâm. `csp.test.ts` canh đúng chuyện đó.

const nextConfig: NextConfig = {
  // Bắt buộc cho `deploy/docker/public-web.Dockerfile`: tầng runtime chép
  // `.next/standalone`, không có cờ này thì thư mục đó không tồn tại và image chép hụt.
  output: 'standalone',

  // `design-tokens` xuất thẳng mã TypeScript (không có bước biên dịch riêng), nên Next
  // phải được bảo là hãy transpile nó như mã nguồn của mình.
  transpilePackages: ['@songnhue/design-tokens'],

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
          // ⛔ `Content-Security-Policy` ⛔ nằm ở đây nữa — xem khối ⭐⭐ ở trên. Ba header còn lại
          //   ⛔ phụ thuộc request nên chúng ở lại: giải một lần lúc build là đủ và rẻ hơn.
        ],
      },
    ];
  },
};

export default nextConfig;
