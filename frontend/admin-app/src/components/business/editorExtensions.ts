import { TableKit } from '@tiptap/extension-table';
import { Placeholder } from '@tiptap/extensions';
import { StarterKit } from '@tiptap/starter-kit';

import { AlignClass } from './AlignClass';
import { FigureImage } from './FigureImage';
import { VideoEmbed } from './VideoEmbed';

/**
 * **Bộ extension của trình soạn thảo — một nguồn duy nhất** (T41.2).
 *
 * <h3>Vì sao phải gom lại</h3>
 *
 * Danh sách này từng được khai ở **ba** nơi và ba nơi ấy **đã lệch nhau**: `RichTextEditor.tsx`
 * (bản chạy thật), `editorRoundTrip.test.ts` và `alignClass.test.ts` (hai bản dựng lại bằng tay).
 * Hai bản kiểm không có `StarterKit.configure({ link })`, nên mọi khẳng định của chúng về liên kết
 * nói về một trình soạn thảo **không tồn tại**.
 *
 * Đó đúng là quy tắc 14: chỗ nào con người phải nhớ ba nơi thì chỗ đó cần một phép kiểm nhớ hộ —
 * và cách rẻ hơn một phép kiểm là **không có ba nơi**. `editorExtensions.test.ts` canh rằng chuỗi
 * `TableKit.configure(` chỉ xuất hiện ở đúng tệp này.
 *
 * ⚠ Tệp `.ts` chứ không `.tsx`, và **không** xuất component nào: ESLint
 * `react-refresh/only-export-components` ở mức **lỗi** với `--max-warnings=0`, và
 * `allowConstantExport` không cho `ArrayExpression` đi qua. Cùng lý do đã tách `AlignClass.ts`,
 * `statusVocabulary.ts`, `gridLayout.ts`.
 */

/**
 * ⛔⛔ **`resizable: false` — và đây là một bản vá, không phải một lựa chọn phong cách.**
 *
 * Bản trước bật `resizable: true`. Nó cho người dùng một tay nắm kéo giãn cột, và **mọi cú kéo đều
 * bị vứt lúc lưu** — im lặng, không lỗi, không cảnh báo. Đo được (04/09/2026):
 *
 * <ul>
 *   <li>TipTap ghi bề rộng qua <b>hai</b> đường: {@code style} trên {@code <table>}/{@code <col>}
 *       (`Table.renderHTML` + `createColGroup`), và thuộc tính {@code colwidth="120,80"} trên
 *       {@code td}/{@code th}.
 *   <li>`HtmlSanitizer` chỉ cho {@code class} đi qua trên mọi thẻ ⇒ {@code style} bị gỡ; và jsoup
 *       {@code Safelist.relaxed()} cho {@code td}/{@code th} đúng
 *       {@code abbr, axis, colspan, rowspan, width} ⇒ {@code colwidth} cũng bị gỡ.
 * </ul>
 *
 * ⇒ **Cả hai đường đều đứt.** Tắt nó không mất gì đang chạy — nó gỡ một tay nắm nói dối.
 *
 * <h3>Thay bằng gì</h3>
 *
 * Sàn {@code min-width} trên {@code th}/{@code td}, khai bằng **CSS** từ hằng dùng chung
 * `TABLE_CELL_MIN_WIDTH_PX` (`design-tokens/editor-schema`). CSS là thứ duy nhất sống qua bộ lọc,
 * vì nó không nằm trong HTML. Bảng 7–13 cột vì thế **cuộn ngang** thay vì bóp mỗi cột còn một ký tự.
 *
 * <h3>⚠ Tắt `resizable` KHÔNG làm `<colgroup>` biến mất</h3>
 *
 * `Table.renderHTML` gọi `createColGroup` **vô điều kiện**. Nên {@code colgroup}/{@code col} vẫn đi
 * vào CSDL và vẫn phải nằm trong `EDITOR_TAGS` — chúng đã đi vào đó từ trước, chỉ là chưa ai khai.
 *
 * <h3>Điều kiện bật lại — phải ĐỦ HAI VẾ, thiếu một vế là dựng lại đúng lỗi cũ ở chỗ mới</h3>
 *
 * <ol>
 *   <li><b>Đường ghi</b>: {@code TableKit.configure({ table: false })} + một node `Table` riêng
 *       (⛔ không {@code Table.extend()} chồng lên bản của kit — hai extension cùng tên {@code table}
 *       ném {@code RangeError: Adding different instances of a keyed plugin}, editor **không dựng
 *       được**). Node ấy phải phát {@code <col width="N">} (jsoup đã cho {@code col[span,width]} qua,
 *       không phải sửa Safelist) và dựng colgroup bằng {@code TableMap} chứ không bằng
 *       {@code node.firstChild} — {@code parseColgroupWidth} tra <b>chỉ số Ô</b> chứ không phải chỉ
 *       số CỘT, nên bảng có ô gộp mất bề rộng sau đúng một vòng lưu–mở (đã đo: 120px biến mất).
 *   <li><b>Đường hiển thị</b>: cổng công khai đang đặt {@code display:block} trên
 *       {@code .sn-article table} — quy tắc ấy **phá vỡ ngữ cảnh định dạng bảng**, nên
 *       {@code <colgroup>} vô tác dụng kể cả khi sống sót qua bộ lọc.
 * </ol>
 *
 * Xem `master-tracking.md` T41.14.
 */
export const EXTENSIONS_SOAN_THAO = [
  StarterKit.configure({
    // Chỉ h2–h4: h1 dành cho tiêu đề bài, do trang hiển thị đặt. Cho người soạn tạo h1
    // giữa bài là tạo ra hai tiêu đề cấp một trên cùng một trang — công cụ tìm kiếm
    // hiểu sai cấu trúc, và trình đọc màn hình cũng vậy.
    heading: { levels: [2, 3, 4] },
    link: {
      openOnClick: false,
      // `HtmlSanitizer` chỉ nhận http/https/mailto/tel. Khai lại ở đây để người dùng
      // biết ngay lúc dán, thay vì mất liên kết lúc lưu.
      protocols: ['http', 'https', 'mailto', 'tel'],
    },
  }),
  TableKit.configure({ table: { resizable: false } }),
  /**
   * ⚠⚠ `Placeholder` **không** nằm trong StarterKit v3 — đo được: 42 extension nạp thật, không có
   * `placeholder`, và DOM khi rỗng **không có** class `is-editor-empty`.
   *
   * Nghĩa là quy tắc CSS `.ProseMirror p.is-editor-empty::before` trong `richTextEditor.css`
   * **chưa từng chạy một lần nào** — một ô trắng trơn khi mở bài mới, trông như đang hỏng. Đúng
   * hình dạng "một nửa cặp đọc–ghi": có đầu đọc (CSS), không có đầu ghi (extension).
   *
   * ⚠ Gói `@tiptap/extensions` **đã có trên đĩa** từ trước nhưng lồng dưới
   * `starter-kit/node_modules` ⇒ không resolve được từ `admin-app`. Phải khai tường minh trong
   * `package.json` (⚠ `.npmrc` có `save-exact=true` ⇒ ⛔ không dấu `^`). Tác dụng phụ đo được và
   * đáng mừng: số bản sao `@tiptap/core` trên đĩa **2 → 1**.
   *
   * ⛔ Chuỗi gợi ý khai ở ĐÂY, không ở CSS: `content: 'Nhập nội dung…'` viết cứng trong CSS là hai
   * nguồn sự thật cho cùng một câu chữ. CSS đọc nó qua `attr(data-placeholder)`.
   */
  Placeholder.configure({ placeholder: 'Nhập nội dung bài viết…' }),
  AlignClass,
  FigureImage,
  VideoEmbed,
];
