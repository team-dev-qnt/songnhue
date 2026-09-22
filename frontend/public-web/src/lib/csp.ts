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
 * @param mediaOrigin gốc của kho đối tượng (MinIO) cho `media-src` — T84.13. Rỗng ⇒ chỉ `'self'`.
 *
 *   ⚠ **Mặc định là bắt buộc**, ⛔ phải tiện tay: `mapTiles.test.ts:33,68` gọi `dungCsp(nonce)`
 *   với MỘT đối số. Đổi chữ ký thành hai tham số bắt buộc là làm đỏ một bộ canh ⛔ liên quan gì —
 *   và lượt rà sau sẽ đi tìm lỗi ở bản đồ.
 *
 *   ⚠⚠ Giá trị này đọc ở `middleware.ts` bằng `process.env.MEDIA_ORIGIN` **lúc chạy**, được vì
 *   Next 16 đổi middleware sang **Node.js runtime** (`proxy.md:255`). ⛔ Nướng lúc build: hai môi
 *   trường dùng chung một ảnh Docker thì chúng buộc phải mang chung giá trị — đúng bẫy `SITE_URL`
 *   của T68.12 đang mở.
 */
export function dungCsp(nonce: string, mediaOrigin = ''): string {
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
    // ⭐⭐ Video nhúng trong bài — T84.13. `/api/v1/public/videos/{id}` trả **302** sang kho đối
    //    tượng, nên gốc ĐÍCH phải có mặt ở đây.
    //
    // ⚠⚠ ĐIỀU PHẢI ĐO TRÊN TRÌNH DUYỆT THẬT, ⛔ khẳng định: CSP có kiểm host đích **sau chuyển
    //    hướng** ⛔. Đặc tả nới phần ĐƯỜNG DẪN sau một lượt 302; phần HOST theo hiểu biết chung
    //    vẫn bị kiểm — nhưng *"nghe có vẻ đúng"* chính là luật 9, và luật 37 nói thêm: một lời
    //    khẳng định chưa đo là một lời khẳng định sai đang chờ tới lượt. Nghiệm thu B2 vì thế có
    //    một PHÉP PHÂN BIỆT: gỡ `MEDIA_ORIGIN` khỏi container ⇒ console PHẢI báo chặn. Nếu ⛔ báo
    //    thì chỉ thị này là một dòng trang trí và nó đang xanh vì lý do sai (luật 1).
    //
    // ⛔ Rỗng ⇒ chỉ `'self'`, ⛔ để lại một khoảng trắng thừa: `"media-src 'self' "` trông giống
    //    hệt nhưng làm mọi phép so chuỗi ở bộ canh lệch đi.
    mediaOrigin ? `media-src 'self' ${mediaOrigin}` : "media-src 'self'",
    // - `www.google.com` — khung bản đồ trụ sở ở trang Liên hệ và chân trang (CR-22);
    // - `www.youtube-nocookie.com` — video phóng sự ở khối Truyền thông (CN-01.3);
    // - `player.vimeo.com` — video nhúng trong thân bài (T84.10).
    //
    // ⚠⚠ Vimeo là chỗ hai đầu của một cặp đã LỆCH NHAU suốt: `HtmlSanitizer.MIEN_NHUNG_VIDEO` cho
    //   `player.vimeo.com` đi qua bộ lọc, `VideoEmbed.toEmbedUrl` dựng đúng URL nhúng, và CSP của
    //   ADMIN đã có nó — nhưng CSP của CỔNG thì ⛔. Hệ quả: biên tập viên dán URL Vimeo, xem trước
    //   ở màn soạn bài thấy video chạy, duyệt xong thì độc giả nhận một KHUNG TRẮNG. Lỗi chỉ hiện
    //   trong console trình duyệt — nơi ⛔ cổng kiểm nào của kho nhìn (T46.7 · §10.61).
    //
    // ⛔ ⛔ KHÔNG đụng `noFabricatedContent.test.ts`: bộ canh ấy quét bằng `timTsx()` — CHỈ `.tsx` —
    //   còn tệp này là `.ts`, nên nó ⛔ liên quan. Thêm một mục vào danh sách ⛔ ai đọc là luật 15.
    "frame-src 'self' https://www.google.com https://www.youtube-nocookie.com https://player.vimeo.com",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ].join('; ');
}

/**
 * Sinh nonce cho một request.
 *
 * ⚠⚠ **Câu cũ ở đây đã HẾT ĐÚNG — sửa 22/09/2026 (T84.20).** Nó khai *"middleware chạy trên **edge
 * runtime**, nơi `Buffer` của Node ⛔ có mặt"*. Đo trên `node_modules/next` **16.3.5**:
 * `proxy.md:255` — *"Proxy defaults to using the **Node.js runtime**"*; `:806` — *"v16.0.0:
 * Middleware is deprecated and renamed to Proxy. Proxy defaults to the Node.js runtime"*. Tức
 * `Buffer` **có mặt**, và `process.env` **đọc được lúc chạy** — chính điều kiện để `MEDIA_ORIGIN`
 * ⛔ phải nướng vào ảnh (T84.13).
 *
 * Vẫn **giữ** `crypto.getRandomValues` + `btoa`: chúng là API chuẩn của cả hai runtime, nên mã này
 * chạy đúng dù một lượt nâng Next sau có đổi mặc định lần nữa. ⛔ Đổi sang `Buffer` chỉ vì *"giờ
 * dùng được"* — đó là đổi một thứ đang chạy lấy một ràng buộc mới ⛔ để làm gì.
 *
 * ⚠ Một khẳng định về RUNTIME hết hạn theo phiên bản framework, y như một số đo hết hạn theo ngày.
 */
export function sinhNonce(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  let s = '';
  for (const b of bytes) {
    s += String.fromCharCode(b);
  }
  return btoa(s);
}
