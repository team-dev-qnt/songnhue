import { Editor } from '@tiptap/core';
import { TableKit } from '@tiptap/extension-table';
import { StarterKit } from '@tiptap/starter-kit';
import { EDITOR_SAMPLE_HTML, EDITOR_TAGS } from 'design-tokens/editor-schema';
import { afterEach, describe, expect, it } from 'vitest';

import { AlignClass } from './AlignClass';
import { FigureImage } from './FigureImage';
import { VideoEmbed } from './VideoEmbed';

/**
 * **Nội dung đã qua bộ lọc phải đọc ngược lại được, không mất gì.**
 *
 * <h3>Câu hỏi bài kiểm này trả lời</h3>
 *
 * Nội dung bài đi một vòng: trình soạn thảo dựng HTML → `HtmlSanitizer` (jsoup) lọc và **ghi
 * lại** chuỗi → CSDL → mở bài ra thì TipTap phân tích chuỗi đó về lại cây nút. Câu hỏi tự
 * nhiên là: *vòng đó có làm hỏng nội dung không?*
 *
 * Có một lo ngại cụ thể và chính đáng: jsoup **không trả lại đúng chuỗi ta đưa vào** — nó
 * dựng lại tài liệu và in ra theo cách của nó, có thụt lề. Đo thật trên jsoup 1.23.1:
 *
 * <pre>
 *   vào : &lt;table&gt;&lt;tbody&gt;&lt;tr&gt;&lt;td&gt;A&lt;/td&gt;…
 *   ra  : &lt;table&gt;\n &lt;tbody&gt;\n  &lt;tr&gt;\n   &lt;td&gt;A&lt;/td&gt;…
 * </pre>
 *
 * Khoảng trắng thêm vào giữa các thẻ khối là **vô hại** — nhưng đó là một khẳng định, và
 * khẳng định thì phải kiểm. Nếu sai, hậu quả là chữ dính vào nhau hoặc thừa dấu cách ở giữa
 * câu, mỗi lần lưu lại tệ thêm một chút, và không ai truy ra nguyên nhân.
 *
 * <h3>Vì sao không lưu thẳng cây JSON xuống CSDL</h3>
 *
 * Đó là cách né vòng phân tích này hoàn toàn, và đã được cân nhắc. Cái giá quá đắt so với
 * vấn đề nó giải (`architecture-review.md` §10.25): mất `HtmlSanitizer` — lớp khử trùng đã
 * chạy ở backend bằng thư viện có tuổi đời — để đổi lấy một bộ duyệt cây JSON tự viết bằng
 * Java mà chưa ai thử tấn công; cổng công khai hết dựng được HTML nếu không đưa cả schema
 * TipTap sang phía máy chủ; và vỡ ba thứ đang chạy: so sánh phiên bản, tìm kiếm toàn văn,
 * chế độ soạn HTML. Bài kiểm này rẻ hơn nhiều, và nó đo đúng nỗi lo đó.
 */

const EXTENSIONS = [
  StarterKit.configure({ heading: { levels: [2, 3, 4] } }),
  TableKit.configure({ table: { resizable: true } }),
  AlignClass,
  FigureImage,
  VideoEmbed,
];

/**
 * Thẻ mà `HtmlSanitizer` cho qua nhưng trình soạn thảo **không tự sinh ra**.
 *
 * Hai thẻ, hai lý do khác nhau, và cả hai đều tìm ra khi bài kiểm này chạy lần đầu:
 *
 * <ul>
 *   <li>{@code span} — nằm trong danh sách cho phép để nội dung dán từ nơi khác không vỡ cấu
 *       trúc, chứ thanh công cụ không có nút nào tạo ra nó.
 *   <li>{@code thead} — mô hình bảng của ProseMirror **không có** khái niệm nhóm đầu bảng:
 *       ô tiêu đề là {@code th} nằm thẳng trong {@code tbody}. Nên dán một bảng có
 *       {@code thead} vào thì thẻ đó bị chuẩn hoá đi, còn {@code th} thì giữ nguyên — phần
 *       ngữ nghĩa quan trọng (ô này là tiêu đề) không mất, và CSS của cổng cũng bám theo
 *       {@code th} chứ không bám {@code thead}.
 * </ul>
 *
 * Cả hai vẫn phải nằm trong `EDITOR_TAGS` để `EditorVocabularyTest` phía Java canh rằng bộ
 * lọc không gỡ chúng — nội dung cũ và nội dung dán vào vẫn mang chúng.
 */
const CHI_CO_O_BO_LOC = ['span', 'thead'];

const editors: Editor[] = [];

afterEach(() => {
  editors.splice(0).forEach((editor) => editor.destroy());
});

function docThanhCay(html: string) {
  const editor = new Editor({ extensions: EXTENSIONS, content: html });
  editors.push(editor);
  return { json: editor.getJSON(), html: editor.getHTML(), text: editor.getText() };
}

/**
 * Chèn khoảng trắng giữa các thẻ, mô phỏng cách jsoup in lại tài liệu.
 *
 * ⚠ Chừa `<pre>` ra: khoảng trắng bên trong khối mã **là nội dung**, thêm vào đó là đổi bài
 * chứ không phải đổi cách trình bày. jsoup cũng chừa đúng chỗ này (đã đo).
 */
function themThutLe(html: string): string {
  return html
    .split(/(<pre>[\s\S]*?<\/pre>)/)
    .map((phan, i) => (i % 2 === 1 ? phan : phan.replace(/></g, '>\n  <')))
    .join('');
}

describe('vòng khứ hồi HTML ↔ cây nút', () => {
  it('⭐⭐ thụt lề do bộ lọc thêm vào KHÔNG làm đổi tài liệu', () => {
    const gon = docThanhCay(EDITOR_SAMPLE_HTML);
    const coThutLe = docThanhCay(themThutLe(EDITOR_SAMPLE_HTML));

    expect(
      coThutLe.json,
      'jsoup in lại tài liệu có thụt lề. Nếu phép so này đỏ thì mỗi lượt lưu đang làm nội ' +
        'dung xê dịch một chút — thừa dấu cách giữa câu hoặc chữ dính vào nhau — và không ' +
        'ai truy ra được nguyên nhân.',
    ).toEqual(gon.json);
  });

  it('⭐ mọi thẻ trình soạn thảo sinh ra đều đọc lại được rồi dựng lại được', () => {
    const { html } = docThanhCay(EDITOR_SAMPLE_HTML);
    const canCo = EDITOR_TAGS.filter((the) => !CHI_CO_O_BO_LOC.includes(the));

    const biMat = canCo.filter((the) => !new RegExp(`<${the}[\\s/>]`).test(html));

    expect(
      biMat,
      'Thẻ có trong mẫu mà không dựng lại được nghĩa là mở một bài cũ ra sẽ mất đúng phần ' +
        'định dạng đó — và lưu đè lên thì mất vĩnh viễn.',
    ).toEqual([]);
  });

  it('không mất một chữ nào qua vòng khứ hồi', () => {
    const mot = docThanhCay(EDITOR_SAMPLE_HTML);
    const hai = docThanhCay(mot.html);

    expect(hai.text.replace(/\s+/g, ' ').trim()).toBe(mot.text.replace(/\s+/g, ' ').trim());
  });

  it('⭐ vòng thứ hai không đổi gì nữa — nội dung đứng yên qua nhiều lần lưu', () => {
    const mot = docThanhCay(EDITOR_SAMPLE_HTML);
    const hai = docThanhCay(mot.html);

    // Không đòi vòng 1 phải giống hệt chuỗi gốc (trình soạn thảo chuẩn hoá lại là đúng), mà
    // đòi nó **hội tụ**: lưu bài mười lần liên tiếp không được sinh ra mười phiên bản khác
    // nhau. Đây cũng là điều kiện để màn hình so sánh phiên bản không hiện thay đổi ma.
    expect(hai.html).toBe(mot.html);
  });

  it('class căn lề và bề ngang ảnh sống sót qua vòng khứ hồi', () => {
    const { html } = docThanhCay(EDITOR_SAMPLE_HTML);

    expect(html).toContain('sn-align-center');
    expect(html, 'bề ngang ảnh là class riêng, không đi cùng căn lề').toContain('sn-w-1-2');
  });
});
