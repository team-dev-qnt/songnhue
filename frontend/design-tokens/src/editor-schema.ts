/**
 * **Bộ từ vựng HTML của nội dung bài viết** — hợp đồng giữa **ba** bên, không phải hai.
 *
 * <h3>Ba bên phải đồng ý với nhau</h3>
 *
 * 1. **admin-app** — trình soạn thảo *sinh ra* các thẻ này.
 * 2. **backend** — `HtmlSanitizer` (jsoup, danh sách CHO PHÉP) *giữ lại* chúng lúc ghi.
 * 3. **public-web** — cổng công khai *hiển thị* chúng.
 *
 * Lệch ở bất kỳ cặp nào cũng cho ra cùng một kiểu hỏng: **im lặng**.
 *
 * - Soạn thảo ⊄ khử trùng → biên tập viên chèn bảng, bấm Lưu, hệ thống báo "Đã lưu", mở lại
 *   thì bảng biến mất. Không lỗi, không cảnh báo, và người dùng nghĩ mình thao tác sai.
 * - Khử trùng ⊄ hiển thị → nội dung qua được tới cổng nhưng **cổng không có CSS cho nó**.
 *   Bài lên đúng chữ, mất hết định dạng: danh sách không dấu đầu dòng, bảng không viền, chú
 *   thích ảnh trông y hệt một đoạn văn, căn lề bị bỏ qua hoàn toàn.
 *
 * ⚠ Vế thứ ba là vế **đã xảy ra thật**: `sn-align-*` từng chỉ được định nghĩa trong CSS của
 * trình soạn thảo, nên căn giữa một tấm ảnh hiển thị đúng ở màn hình soạn và **căn trái khi
 * lên cổng**. Tài liệu của `AlignClass` thậm chí đã viết sẵn điều kiện *"với điều kiện cổng
 * công khai có định nghĩa ba class đó"* — điều kiện được ghi ra và không ai thực hiện.
 *
 * <h3>Vì sao ở `design-tokens` chứ không ở admin-app</h3>
 *
 * Cùng lý do đã tách chính gói này: hai ứng dụng FE **ngang hàng**. Để bản khai ở admin-app
 * thì cổng công khai phải phụ thuộc vào ứng dụng quản trị nội bộ mới biết mình cần dựng CSS
 * cho những thẻ nào — quan hệ ngược chiều. Backend đọc tệp này bằng đường dẫn
 * (`EditorVocabularyTest`), nên nó cũng cần một chỗ đứng không thuộc về riêng ai.
 *
 * ⚠ Danh sách này **không tự đồng bộ**. Có ba phép kiểm canh ba cạnh của tam giác:
 * `EditorVocabularyTest` (Java — cạnh soạn thảo↔khử trùng), `editorRoundTrip.test.ts`
 * (admin-app — nội dung đã lọc còn đọc ngược lại được), `articleContentCss.test.ts`
 * (public-web — cạnh khử trùng↔hiển thị). Sửa một bên mà quên bên kia là **CI đỏ**, không
 * phải một lỗi chờ tới lúc có người soạn bài mới lộ.
 */

/** Thẻ khối và thẻ định dạng mà thanh công cụ có nút tương ứng. */
export const EDITOR_TAGS = [
  'p',
  'h2',
  'h3',
  'h4',
  'strong',
  'em',
  'u',
  's',
  'code',
  'pre',
  'blockquote',
  'ul',
  'ol',
  'li',
  'a',
  'img',
  'figure',
  'figcaption',
  'table',
  'thead',
  'tbody',
  'tr',
  'th',
  'td',
  'hr',
  'br',
  'span',
  'iframe',
] as const;

/**
 * Thẻ mà **cổng công khai bắt buộc phải có quy tắc CSS**.
 *
 * <h3>Vì sao không phải là toàn bộ `EDITOR_TAGS`</h3>
 *
 * `strong`, `em`, `br`, `span` giữ nguyên hình dạng mặc định của trình duyệt sau khi qua bộ
 * chuẩn hoá (preflight) của Tailwind — không cần khai gì thêm. Danh sách dưới đây là những
 * thẻ mà **preflight xoá sạch hình dạng mặc định**: nó đặt `list-style: none` cho `ul`/`ol`,
 * đưa mọi tiêu đề về đúng cỡ chữ của đoạn văn, bỏ viền `table`, bỏ khoảng cách `figure`.
 *
 * Nghĩa là: không khai lại thì `<h3>` trên cổng trông **y hệt** một đoạn văn thường, và bài
 * viết mất toàn bộ cấu trúc mà người soạn đã dựng.
 *
 * ⚠ Phép kiểm dựa trên danh sách này chỉ chứng minh *có quy tắc CSS mang tên đó*, không
 * chứng minh quy tắc đó đẹp hay đúng. Nó chặn đúng một thứ: **quên hẳn**. Đó cũng là thứ đã
 * xảy ra.
 */
export const PORTAL_STYLED_TAGS = [
  'h2',
  'h3',
  'h4',
  'p',
  'a',
  'ul',
  'ol',
  'li',
  'blockquote',
  'pre',
  'code',
  'table',
  'th',
  'td',
  'figure',
  'figcaption',
  'img',
  'iframe',
  'hr',
] as const;

/**
 * Căn lề đi bằng **class**, không bằng thuộc tính `style`.
 *
 * `HtmlSanitizer` cho phép `class` trên mọi thẻ nhưng **không** cho `style` — và đó là lựa
 * chọn đúng: `style` mở đường cho `position: fixed` phủ kín trang, hoặc chữ trắng trên nền
 * trắng để giấu nội dung trong một bài đã được duyệt.
 */
export const ALIGN_CLASSES = ['sn-align-left', 'sn-align-center', 'sn-align-right'] as const;

/**
 * Bề ngang ảnh — cũng bằng class, cùng lý do với căn lề.
 *
 * <h3>Vì sao căn lề một mình là chưa đủ</h3>
 *
 * Ảnh chiếm trọn bề ngang khung bài thì **căn phải và căn trái cho ra kết quả giống hệt
 * nhau** — không còn chỗ trống nào để đẩy sang bên. Người soạn bấm "căn phải", nhìn không
 * thấy gì đổi, và kết luận là chức năng hỏng. Nên hai thứ này phải đi cùng nhau: chọn bề
 * ngang trước, rồi căn lề mới có nghĩa.
 */
export const IMAGE_WIDTH_CLASSES = ['sn-w-full', 'sn-w-1-2', 'sn-w-1-3'] as const;

/**
 * Mẫu HTML dùng cho bài kiểm hai đầu.
 *
 * Cố ý là **một chuỗi duy nhất chứa đủ mọi thứ trình soạn thảo tạo ra**, không phải một
 * danh sách mảnh rời: bộ lọc xử lý cả tài liệu, và một thẻ có thể bị gỡ vì thẻ cha của nó
 * bị gỡ — điều mà kiểm từng mảnh riêng lẻ không phát hiện được.
 */
export const EDITOR_SAMPLE_HTML = [
  '<h2 class="sn-align-center">Tiêu đề mục</h2>',
  '<h3>Tiêu đề cấp 3</h3>',
  '<h4>Tiêu đề cấp 4</h4>',
  '<p><strong>Đậm</strong> <em>nghiêng</em> <u>gạch chân</u> <s>gạch ngang</s> <code>mã</code></p>',
  '<p>Dòng trên<br>dòng dưới <span class="sn-nho">chú thích nhỏ</span></p>',
  '<ul><li>Gạch đầu dòng</li></ul>',
  '<ol><li>Đánh số</li></ol>',
  '<blockquote><p>Trích dẫn</p></blockquote>',
  '<pre><code>khối mã</code></pre>',
  '<p><a href="https://songnhue.example.vn" target="_blank" rel="noopener">Liên kết ngoài</a></p>',
  '<p><a href="/bai-viet/tin-noi-bo">Liên kết nội bộ</a></p>',
  '<figure class="sn-align-center sn-w-1-2">',
  '<img src="/api/v1/public/files/8a7b6c5d-0000-0000-0000-000000000000" alt="Ảnh" loading="lazy">',
  '<figcaption>Chú thích ảnh</figcaption></figure>',
  '<table><thead><tr><th>Cột</th></tr></thead><tbody><tr><td>Ô</td></tr></tbody></table>',
  '<hr>',
  '<p><iframe src="https://www.youtube-nocookie.com/embed/abc123" allowfullscreen></iframe></p>',
].join('');
