/**
 * Content-Security-Policy của cổng công khai — **một nguồn sự thật duy nhất**.
 *
 * <h3>⚠⚠ Vì sao tệp này tồn tại thay vì một hằng trong `next.config.ts`</h3>
 *
 * Trước 20/09/2026 chuỗi CSP nằm trong `next.config.ts`. Nó phải chuyển ra đây vì nonce chỉ sinh
 * được **lúc chạy** (mỗi request một giá trị), mà `headers()` của `next.config` giải **một lần lúc
 * build**. Hai nơi cùng đặt một header là đúng cái bẫy đã làm cổng công khai chạy **⛔ CSP nào suốt
 * từ WS-16**: `next.config` bảo *"nginx đặt"*, `edge-headers.conf` bảo *"image FE đặt"*, và đọc tệp
 * nào cũng thấy yên tâm (§10.61).
 *
 * ⇒ Từ nay **chỉ `middleware.ts` gọi hàm này**, và `next.config.ts` ⛔ còn đặt CSP nữa.
 *
 * <h3>⛔⛔ Vì sao `'unsafe-inline'` của `script-src` BỎ ĐƯỢC — lý do cũ đã ĐỔ</h3>
 *
 * Chú thích cũ khai: *"nonce phải khác nhau mỗi request — tức mọi trang thành động và ISR tắt hẳn,
 * trong khi NFR-02 (trang chủ < 3s) dựa vào ISR"*. Đo lại bằng `next build` ngày 20/09/2026:
 *
 * ```
 * ƒ /            ƒ /bai-viet/[slug]     ƒ /danh-muc/[slug]     ƒ /tim-kiem   … (18 route)
 * ○ /robots.txt
 * ƒ  (Dynamic)  server-rendered on demand
 * ```
 *
 * **18/19 route ĐÃ là `ƒ` và bảng ⛔ có một dòng `●` (ISR) nào** — vì `apiGetWithMeta` gọi
 * `await connection()` trước mọi lượt fetch (`src/lib/api.ts`, chốt chặn của T35.x). Tức cái giá mà
 * chú thích cũ e ngại **đã trả từ lâu rồi**, và nonce cộng thêm **0**.
 *
 * ⚠ Thứ NFR-02 thật sự dựa vào ⛔ phải bộ đệm TRANG mà là bộ đệm **DỮ LIỆU FETCH**
 * (`next: { revalidate: 300 }`) — backend vẫn chỉ bị hỏi 1 lần / 5 phút, và nonce ⛔ đụng tới nó.
 * Hai thứ cùng tên "ISR" nhưng là hai cơ chế khác nhau; trộn chúng chính là chỗ lập luận cũ sai.
 */

/**
 * ⛔ `'strict-dynamic'` — cố ý ⛔ dùng, và biết vì sao.
 *
 * Nó làm trình duyệt **bỏ qua** `'self'` trong `script-src`, chỉ còn script mang nonce (và script do
 * chúng nạp) chạy được. Mạnh hơn thật, nhưng một thẻ `<script src>` nào đó ⛔ được gắn nonce sẽ bị
 * chặn **im lặng ở trình duyệt người dùng** — nơi ⛔ cổng kiểm nào của kho nhìn thấy (T46.7 · §10.61).
 * ⇒ Đổi một lỗ hổng đã đo được lấy một rủi ro ⛔ đo được là một cuộc đổi tồi. Mở nó là một lượt riêng,
 * sau khi có phép đo trên trình duyệt thật (thuộc `T61.29`).
 */
const KHONG_DUNG_STRICT_DYNAMIC = true;

/**
 * Dựng chuỗi CSP cho **một** request.
 *
 * @param nonce giá trị base64 ngẫu nhiên của chính request ấy; dùng lại giữa hai request là bỏ đi
 *   toàn bộ tác dụng của nonce
 */
export function dungCsp(nonce: string): string {
  if (!nonce) {
    // ⛔ Rơi về `'unsafe-inline'` cho "an toàn": đó là biến một lỗi ồn ào thành đúng lỗ hổng vừa vá,
    //   và nó sẽ sống mãi vì trang vẫn chạy bình thường (luật 3 · luật 9).
    throw new Error('CSP cần một nonce ⛔ rỗng — xem middleware.ts');
  }
  void KHONG_DUNG_STRICT_DYNAMIC;
  return [
    "default-src 'self'",
    // ⭐ ⛔ còn `'unsafe-inline'`: khi có `nonce-…`, trình duyệt hiện đại **bỏ qua** `'unsafe-inline'`
    //    trong cùng chỉ thị, nên để lại nó chỉ là một dòng trang trí gây hiểu nhầm cho lượt rà sau.
    `script-src 'self' 'nonce-${nonce}'`,
    // ⚠ `style-src` GIỮ `'unsafe-inline'`, và đây là một đánh đổi khác hẳn: Next chèn style nội tuyến
    //   cho từng đoạn CSS của route, còn Ant Design/emotion ở cổng thì ⛔ có. Rủi ro của inline STYLE
    //   thấp hơn hẳn inline SCRIPT (⛔ chạy được mã), nên siết vế này là một lượt riêng có phép đo.
    "style-src 'self' 'unsafe-inline'",
    // `https://tile.openstreetmap.org` — nguồn ô bản đồ của khối Bản đồ công trình (CN-02.4).
    // ⚠ Địa chỉ này phải khớp `TILE_HOST` ở `src/lib/mapTiles.ts`; `mapTiles.test.ts` đối chiếu hai
    //   bên. Lệch nhau thì bản đồ vẫn dựng và vẫn kéo thả được, chỉ toàn màu xám vì mọi ô ảnh bị
    //   chặn — lỗi chỉ hiện trong console trình duyệt, nơi ⛔ cổng kiểm nào nhìn.
    "img-src 'self' data: blob: https://tile.openstreetmap.org",
    "font-src 'self'",
    "connect-src 'self'",
    // - `www.google.com` — khung bản đồ trụ sở ở trang Liên hệ và chân trang (CR-22);
    // - `www.youtube-nocookie.com` — video phóng sự ở khối Truyền thông (CN-01.3).
    // ⚠ Thêm host thứ ba ở đây mà quên `noFabricatedContent.test.ts` (danh sách tên miền được phép
    //   trong mã component) thì hai danh sách lệch nhau — luật 14.
    "frame-src 'self' https://www.google.com https://www.youtube-nocookie.com",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ].join('; ');
}

/**
 * Sinh nonce cho một request.
 *
 * ⚠ Dùng `crypto.getRandomValues` + `btoa` chứ ⛔ `Buffer`: middleware chạy trên **edge runtime**,
 * nơi `Buffer` của Node ⛔ có mặt. Một lời gọi `Buffer` ở đây hỏng **lúc chạy trên máy chủ thật**
 * chứ ⛔ lúc build — tức đúng lớp lỗi mà `make ci-local` về nguyên tắc ⛔ thấy.
 */
export function sinhNonce(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  let s = '';
  for (const b of bytes) {
    s += String.fromCharCode(b);
  }
  return btoa(s);
}
