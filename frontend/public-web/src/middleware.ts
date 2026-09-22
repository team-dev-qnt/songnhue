import { NextResponse, type NextRequest } from 'next/server';

import { dungCsp, sinhNonce } from '@/lib/csp';

/**
 * ⭐⭐ **Gắn nonce cho CSP của cổng công khai** — T73.7 (ASVS 14.4.3).
 *
 * <h3>Vì sao phải là middleware chứ ⛔ `next.config.headers()`</h3>
 *
 * `headers()` giải **một lần lúc build**, mà nonce theo định nghĩa phải **khác nhau mỗi request**:
 * một nonce cố định nướng vào image còn tệ hơn `'unsafe-inline'`, vì nó trông như đã siết.
 *
 * <h3>BA lượt đặt header, và phép đo nói rõ cái nào làm gì</h3>
 *
 * <ul>
 *   <li><b>Header của RESPONSE</b> — thứ trình duyệt thật sự thi hành. Thiếu ⇒ ⛔ có CSP nào, đúng
 *       trạng thái cổng đã nằm từ WS-16 tới 27/08 (§10.61). <b>Bắt buộc.</b>
 *   <li><b>{@code x-nonce} và {@code Content-Security-Policy} của REQUEST</b> — Next đọc nonce từ
 *       đây để gắn vào chính những thẻ {@code <script>} nó sinh ra cho hydration.
 * </ul>
 *
 * <h3>⚠⚠ Một khẳng định của chính tôi bị lượt đo BÁC</h3>
 *
 * <p>Bản đầu của khối này viết: <i>"thiếu header CSP của request ⇒ script của Next ⛔ có nonce ⇒
 * cổng trắng trang"</i>. <b>Sai.</b> Đo trên máy chủ standalone đã dựng, Next <b>16.3.5</b>:
 *
 * <ul>
 *   <li>gỡ {@code Content-Security-Policy} của request, giữ {@code x-nonce} ⇒ <b>11/11</b> thẻ
 *       {@code <script>} vẫn mang ĐÚNG nonce của header;
 *   <li>gỡ {@code x-nonce}, giữ {@code Content-Security-Policy} của request ⇒ cũng <b>11/11</b>.
 * </ul>
 *
 * <p>⇒ Ở phiên bản này <b>mỗi header một mình đã đủ</b>. Giữ cả hai ⛔ phải vì cần cả hai, mà vì:
 * tài liệu Next chỉ cam kết đường {@code Content-Security-Policy}, còn {@code x-nonce} là đường mã
 * ứng dụng đọc qua {@code headers()}; một lượt nâng Next bỏ đi <b>một trong hai</b> sẽ làm cổng
 * trắng trang, và đó là lớp lỗi ⛔ cổng kiểm nào ở kho nhìn thấy. Một lời khẳng định chưa đo là một
 * lời khẳng định sai đang chờ tới lượt (luật 37).
 *
 * <p>⇒ `csp.test.ts` giữ cả ba lượt đặt như một <b>bánh cóc</b> — ⛔ phải vì *"thiếu là trắng
 * trang"*. Và phép đo thật (11/11 thẻ mang đúng nonce, nonce khác nhau mỗi request) ⛔ chạy được
 * trong `vitest`: nó cần một máy chủ ĐÃ DỰNG, nên số đo ghi ở đây và trong sổ (`T73.7`).
 */
export function middleware(request: NextRequest) {
  const nonce = sinhNonce();
  // ⛔ `||` chứ ⛔ `??` — luật 3: Docker gán CHUỖI RỖNG cho một biến ⛔ truyền, và `??` giữ nguyên
  //    chuỗi rỗng ấy trong khi `||` mới rơi về mặc định. Đây đúng chỗ §10.38 đã trả giá.
  const csp = dungCsp(nonce, process.env.MEDIA_ORIGIN || '');

  const headerYeuCau = new Headers(request.headers);
  headerYeuCau.set('x-nonce', nonce);
  headerYeuCau.set('Content-Security-Policy', csp);

  const phanHoi = NextResponse.next({ request: { headers: headerYeuCau } });
  phanHoi.headers.set('Content-Security-Policy', csp);
  return phanHoi;
}

export const config = {
  /**
   * ⚠⚠ Danh sách LOẠI TRỪ, ⛔ phải danh sách cho phép — và đó là một quyết định.
   *
   * <p>Một matcher kiểu *"chỉ những đường này"* sẽ để **trang mới ra đời ⛔ có CSP** và ⛔ gì báo:
   * đúng hình dạng luật 28 (<i>bộ canh hẹp hơn nơi nó phải chặn, và cái xanh của nó đọc như một lời
   * bảo đảm</i>). Loại trừ thì một trang quên khai vẫn <b>được</b> bảo vệ.
   *
   * <p>Ba thứ bỏ ra ngoài đều ⛔ phải tài liệu HTML nên ⛔ thi hành CSP, mà cho đi qua middleware
   * thì mỗi tệp tĩnh tốn một lượt chạy hàm:
   *
   * <ul>
   *   <li>{@code _next/static} — bundle JS/CSS đã băm nội dung;
   *   <li>{@code _next/image} — bộ tối ưu ảnh;
   *   <li>{@code favicon.ico}.
   * </ul>
   */
  matcher: ['/((?!_next/static|_next/image|favicon\\.ico).*)'],
};
