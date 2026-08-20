/**
 * **Bộ từ vựng HTML mà trình soạn thảo được phép sinh ra** — và nó phải là **tập con** của
 * danh sách cho phép ở `HtmlSanitizer` (backend).
 *
 * <h3>Vì sao file này tồn tại</h3>
 *
 * `HtmlSanitizer` chạy lúc **ghi**: thẻ nào ngoài danh sách thì bị gỡ, im lặng, và bài vẫn
 * lưu thành công. Nếu trình soạn thảo có nút tạo ra một thẻ ngoài danh sách thì hậu quả
 * đúng như sau: biên tập viên chèn bảng, bấm Lưu, hệ thống báo "Đã lưu", mở lại thì bảng
 * biến mất. Không lỗi, không cảnh báo, và người dùng sẽ nghĩ mình thao tác sai.
 *
 * Đây là biến thể của lỗi đã trả giá nhiều lần trong dự án — **một cơ chế chạy đúng ở một
 * nửa đường**. Ở đây nó nguy hiểm hơn vì nạn nhân là người dùng cuối chứ không phải lập
 * trình viên.
 *
 * ⚠ Danh sách này **không tự đồng bộ**. Nó là bản khai của FE, và có một bài kiểm ở backend
 * (`HtmlSanitizerTest`) chạy đúng bộ mẫu dưới đây qua `HtmlSanitizer.clean()` rồi đòi hỏi
 * mọi thẻ phải sống sót. Sửa một bên mà quên bên kia là **CI đỏ**, không phải một lỗi chờ
 * tới lúc có người soạn bài mới lộ.
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
 * Căn lề đi bằng **class**, không bằng thuộc tính `style`.
 *
 * `HtmlSanitizer` cho phép `class` trên mọi thẻ nhưng **không** cho `style` — và đó là lựa
 * chọn đúng: `style` mở đường cho `position: fixed` phủ kín trang, hoặc chữ trắng trên nền
 * trắng để giấu nội dung. Nên cấu hình TipTap phát ra class, và cổng công khai định nghĩa
 * ba class này trong CSS của nó.
 */
export const ALIGN_CLASSES = ['sn-align-left', 'sn-align-center', 'sn-align-right'] as const;

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
  '<figure><img src="/api/v1/public/files/8a7b6c5d-0000-0000-0000-000000000000" alt="Ảnh" loading="lazy">',
  '<figcaption>Chú thích ảnh</figcaption></figure>',
  '<table><thead><tr><th>Cột</th></tr></thead><tbody><tr><td>Ô</td></tr></tbody></table>',
  '<hr>',
  '<p><iframe src="https://www.youtube-nocookie.com/embed/abc123" allowfullscreen></iframe></p>',
].join('');
