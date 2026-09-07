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
  // ⚠⚠ `colgroup`/`col` KHÔNG phải thẻ mới — WS-41 phát hiện chúng **đã** đi vào CSDL từ trước mà
  // không ai khai. `Table.renderHTML` của TipTap gọi `createColGroup` **vô điều kiện**, kể cả khi
  // `resizable: false` (đã đo trên 3.31.0). jsoup `relaxed()` cho chúng qua, nên chúng nằm **ngoài
  // tầm** cả ba bộ canh suốt thời gian đó — đúng hình dạng "một thẻ không ai khai, không ai canh".
  'colgroup',
  'col',
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
 * **Sàn bề rộng một ô bảng, tính bằng px** — WS-41 (T41.1, T41.4).
 *
 * <h3>Vì sao cần một con số, và vì sao nó phải nằm ở đây</h3>
 *
 * Bề rộng cột **không lưu được**: TipTap ghi nó bằng `style` (bị `HtmlSanitizer` gỡ) và bằng thuộc
 * tính `colwidth` trên `td`/`th` (jsoup `Safelist.relaxed()` không có nó ⇒ cũng gỡ). Nên trình soạn
 * thảo tắt hẳn `resizable` — xem `admin-app/.../editorExtensions.ts`.
 *
 * Thứ **sống sót** là CSS, vì CSS không đi qua bộ lọc HTML. Sàn `min-width` làm bảng nhiều cột
 * **cuộn ngang** thay vì bóp mỗi cột còn một ký tự — mà bảng số liệu thuỷ lợi thường 6–7 cột và
 * bảng tiến độ theo tháng tới 13 cột.
 *
 * ⚠ Con số này phải giống nhau ở **hai** tệp CSS (`richTextEditor.css` của trình soạn thảo và
 * `article-content.css` của cổng). Chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm
 * nhớ hộ (quy tắc 14) — cả hai bài kiểm CSS đọc hằng này và đòi tệp khai **đúng** con số.
 *
 * <h3>⭐ Đo được, không ước lượng — khuôn T25.30</h3>
 *
 * Đo bằng `fontTools` trên **chính** font đang chạy (Noto Sans, `sizing.fontSize = 14`), trên nội
 * dung THẬT: bảy nhãn cột của một bảng mực nước/lượng mưa và bảy ô dữ liệu tương ứng.
 *
 * <pre>
 *   từ rộng nhất  'thượng' (600)   51,7px   ← "Mực nước thượng lưu (m)"
 *   + đệm ngang   0,75rem × 2      24px     ← article-content.css, bản lớn hơn của hai tệp
 *   + viền        1px × 2           2px
 *   ⇒ cần                          77,7px  ⇒ chốt 80px (bội của 4)
 * </pre>
 *
 * Hệ quả đo được: bảng **7 cột = 560px** (vừa khung soạn thảo ~1000px ở 1920), bảng **13 cột =
 * 1040px** (vượt khung ⇒ cuộn ngang trong `.tableWrapper` / `.sn-article table`).
 *
 * ⛔ Đổi con số này thì phải **đo lại**, không ước lượng: nó gắn với font và cỡ chữ đang dùng.
 * Câu hỏi nó trả lời là *"một ô số liệu có xuống dòng giữa từ không"*, không phải *"trông có đẹp
 * không"*.
 */
export const TABLE_CELL_MIN_WIDTH_PX = 80;

/**
 * **Thuộc tính của bảng phải sống sót qua bộ khử trùng** — WS-41 (T41.7).
 *
 * <h3>Vì sao cần một danh sách riêng cho THUỘC TÍNH</h3>
 *
 * Cả ba bộ canh của tam giác chỉ kiểm **thẻ** (và đúng hai class căn lề). Không cái nào kiểm một
 * thuộc tính nào khác — nên `colwidth` (thứ TipTap phát ra để mang bề rộng cột) bị jsoup gỡ suốt
 * thời gian dài mà **không cơ chế nào có thể thấy**.
 *
 * Ô gộp đi bằng `colspan`/`rowspan`, và nếu chúng bị gỡ thì một bảng có ô gộp **vỡ cấu trúc** khi
 * mở lại: số ô mỗi hàng không còn khớp số cột, và ProseMirror sẽ tự vá bằng cách thêm ô — người
 * soạn thấy bảng của mình mọc thêm ô trống, không lỗi nào.
 *
 * ⚠ **Nháy đơn bắt buộc.** Bộ đọc regex của `EditorVocabularyTest.java` chỉ nhận mảng chuỗi nháy
 * đơn dạng `export const TÊN = [...] as const`; phần tử viết nháy kép bị **bỏ qua im lặng** — và
 * một danh sách đọc hụt cho ra một bài kiểm xanh mà không kiểm gì.
 *
 * ⛔ **Không** thêm `colwidth` vào đây: nó *cố ý* bị gỡ. Nó là thuộc tính TipTap tự bịa, **không
 * trình duyệt nào đọc nó**, và cổng công khai không có dòng mã nào dịch nó thành bề rộng. Đường
 * mang bề rộng cột đang đóng — điều kiện mở lại ở T41.14.
 */
export const EDITOR_TABLE_ATTRS = ['colspan', 'rowspan'] as const;

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
  // ⚠⚠ Bảng THẬT mà trình soạn thảo sinh ra, không phải một bảng tối giản viết tay.
  // Bản trước là `<table><thead><tr><th>Cột</th></tr></thead>…` — một chuỗi TipTap **không bao giờ
  // sinh ra**: nó không có `colgroup`, không `colspan`/`rowspan`, và mô hình bảng của ProseMirror
  // còn không có khái niệm `thead`. Ba bộ canh chạy trên chuỗi ấy vì thế mù trước mọi thứ liên
  // quan tới bảng thật — đó là cách `resizable: true` sống sót trong khi mỗi cú kéo cột bị vứt.
  //
  // ⚠ Ô gộp cố ý đặt ở **hàng thân**, không ở hàng đầu: `parseColgroupWidth` của TipTap ánh xạ
  //   `<col>` về ô bằng **chỉ số Ô trong hàng**, nên `colspan` ở hàng đầu làm lệch chỉ số cột.
  //   Hôm nay ta không mang bề rộng cột nên chuyện đó vô hại, nhưng mẫu không nên chứa sẵn một
  //   hình dạng đã biết là bẫy — xem T41.14.
  '<table><colgroup><col><col><col></colgroup><tbody>',
  '<tr><th>Điểm đo</th><th>Mực nước (m)</th><th>Ghi chú</th></tr>',
  '<tr><td colspan="2">Cụm cống Liên Mạc</td><td rowspan="2">Đang vận hành</td></tr>',
  '<tr><td>Cống Hà Đông</td><td>+2,45</td></tr>',
  '</tbody></table>',
  // ⚠ `thead` KHÔNG do trình soạn thảo sinh ra — mô hình bảng của ProseMirror không có khái niệm
  //   nhóm đầu bảng, ô tiêu đề là `th` nằm thẳng trong `tbody`. Nhưng nội dung **dán từ Word/Excel**
  //   mang nó, nên bộ lọc phải giữ. Dòng này là chỗ vế ấy được kiểm; `editorRoundTrip.test.ts` xếp
  //   `thead` vào `CHI_CO_O_BO_LOC` vì trình soạn thảo chuẩn hoá nó đi (và `th` thì giữ nguyên —
  //   phần ngữ nghĩa quan trọng không mất).
  '<table><thead><tr><th>Dán từ Word</th></tr></thead><tbody><tr><td>Ô</td></tr></tbody></table>',
  '<hr>',
  '<p><iframe src="https://www.youtube-nocookie.com/embed/abc123" allowfullscreen></iframe></p>',
].join('');
