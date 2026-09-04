import { Editor } from '@tiptap/core';
import { EDITOR_SAMPLE_HTML, EDITOR_TAGS } from 'design-tokens/editor-schema';
import { afterEach, describe, expect, it } from 'vitest';

import { EXTENSIONS_SOAN_THAO } from './editorExtensions';

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

/**
 * ⚠ Dùng CHÍNH danh sách của bản chạy thật — T41.2.
 *
 * Bản trước dựng lại bằng tay ở đây và ở `alignClass.test.ts`, và cả hai **đã lệch** với
 * `RichTextEditor.tsx`: thiếu `StarterKit.configure({ link })`. Một bài kiểm dựng lại cấu hình
 * bằng tay là một bài kiểm về một trình soạn thảo **không tồn tại** — nó xanh trọn vẹn trong khi
 * bản thật hỏng.
 */
const EXTENSIONS = EXTENSIONS_SOAN_THAO;

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

/**
 * Mô phỏng ĐÚNG những gì `HtmlSanitizer` gỡ khỏi một bảng — không hơn, không kém.
 *
 * ⚠⚠ Hai thuộc tính, và cả hai đều **đo được**, không phải suy đoán:
 *
 * <ul>
 *   <li>{@code style} — `HtmlSanitizer` chỉ `addAttributes(":all", "class")`, không có `style`.
 *       TipTap phát nó trên `<table>` và mọi `<col>` (`min-width`).
 *   <li>{@code colwidth} — jsoup `Safelist.relaxed()` cho `td`/`th` đúng
 *       `abbr, axis, colspan, rowspan, width` (+`scope` cho `th`). Không có `colwidth`.
 * </ul>
 *
 * ⛔ Bản trước của tệp này chỉ mô phỏng jsoup **thêm khoảng trắng** (`themThutLe`) — nó chưa bao giờ
 * mô phỏng jsoup **GỠ THUỘC TÍNH**, và đó chính là lý do bề rộng cột mất im lặng mà cả ba bộ canh
 * đều xanh.
 *
 * ⚠ Giới hạn ghi ra (quy tắc 28): đây là một **bản mô phỏng**, không phải `HtmlSanitizer` thật —
 * logic ấy là Java, không chạy được ở đây. Vế "thẻ nào sống sót" do `EditorVocabularyTest` (Java)
 * canh; vế này chỉ hỏi *đọc lại có vỡ không*. Hai bài kiểm chứng ngược ở dưới giữ cho bản mô phỏng
 * không lặng lẽ trở thành vô hại.
 */
function boLoc(html: string): string {
  return html.replace(/ style="[^"]*"/g, '').replace(/ colwidth="[^"]*"/g, '');
}

function docLai(html: string): string {
  const editor = new Editor({ extensions: EXTENSIONS, content: html });
  editors.push(editor);
  return editor.getHTML();
}

/** Vị trí **trước** mỗi `tableCell` — `CellSelection` đòi `$pos.node(-1)` là nút `table`. */
function viTriOThuong(editor: Editor): number[] {
  const viTri: number[] = [];
  editor.state.doc.descendants((nut, pos) => {
    if (nut.type.name === 'tableCell') {
      viTri.push(pos);
    }
    return true;
  });
  return viTri;
}

/**
 * Vân tay **cấu trúc** của bảng: số hàng, số ô mỗi hàng, và colspan/rowspan từng ô.
 *
 * ⛔ Cố ý KHÔNG so chuỗi HTML: TipTap chuẩn hoá lại (thứ tự thuộc tính, khoảng trắng) nên so chuỗi
 * sẽ đỏ vì những khác biệt không ai nhìn thấy. Thứ người dùng mất khi bảng hỏng là **cấu trúc** —
 * số ô không khớp số cột — nên đó là thứ phải khẳng định.
 */
function vanTayBang(html: string): { hang: number; oMoiHang: string[]; oGop: number } {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const hangs = Array.from(doc.querySelectorAll('tr'));
  return {
    hang: hangs.length,
    oMoiHang: hangs.map((tr) =>
      Array.from(tr.children)
        .map(
          (o) =>
            `${o.tagName.toLowerCase()}:${o.getAttribute('colspan') ?? 1}x${o.getAttribute('rowspan') ?? 1}`,
        )
        .join(','),
    ),
    oGop: Array.from(doc.querySelectorAll('td,th')).filter(
      (o) =>
        Number(o.getAttribute('colspan') ?? 1) > 1 || Number(o.getAttribute('rowspan') ?? 1) > 1,
    ).length,
  };
}

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

  it('⭐⭐ BẢNG dựng bằng CHÍNH LỆNH, qua bộ lọc, đọc lại KHÔNG vỡ cấu trúc', () => {
    // ⚠⚠ Đây là bài vá lỗ lớn nhất của tam giác ba bên: cả ba bộ canh đều chạy trên
    // `EDITOR_SAMPLE_HTML` **viết tay**, nên chúng chỉ biết về thứ người viết mẫu nghĩ ra. Bài này
    // dựng bảng bằng đúng những lệnh mà thanh công cụ gọi, rồi hỏi: sau một vòng lưu–mở, bảng có
    // còn là bảng ấy không.
    const editor = new Editor({ extensions: EXTENSIONS, content: '<p></p>' });
    editors.push(editor);
    editor.commands.insertTable({ rows: 3, cols: 4, withHeaderRow: true });
    editor.commands.setCellSelection({
      anchorCell: viTriOThuong(editor)[0],
      headCell: viTriOThuong(editor)[1],
    });
    editor.commands.mergeCells();

    const truoc = vanTayBang(editor.getHTML());
    expect(truoc.hang, 'dựng được bảng 3 hàng').toBe(3);
    expect(truoc.oGop, 'và có đúng một ô gộp').toBe(1);

    const sau = vanTayBang(docLai(boLoc(editor.getHTML())));

    expect(
      sau,
      'Bảng vỡ cấu trúc sau một vòng lưu–mở. Triệu chứng người dùng thấy: số ô mỗi hàng không ' +
        'khớp số cột, ProseMirror tự vá bằng cách thêm ô, và bảng **mọc thêm ô trống** — không lỗi.',
    ).toEqual(truoc);
  });

  it('⛔ kiểm chứng ngược THEO SỐ LƯỢNG: gỡ thêm `colspan` thì vân tay lệch đúng số ô gộp', () => {
    // Luật 29 — một khẳng định về SỐ LƯỢNG không chia sẻ giả định nào với bộ lọc mô phỏng ở trên.
    // Không có bài này thì `boLoc` gõ sai (ví dụ regex không khớp gì) vẫn cho bài trên xanh trọn vẹn.
    const editor = new Editor({ extensions: EXTENSIONS, content: '<p></p>' });
    editors.push(editor);
    editor.commands.insertTable({ rows: 3, cols: 4, withHeaderRow: true });
    editor.commands.setCellSelection({
      anchorCell: viTriOThuong(editor)[0],
      headCell: viTriOThuong(editor)[1],
    });
    editor.commands.mergeCells();

    const truoc = vanTayBang(editor.getHTML());
    const hong = vanTayBang(docLai(boLoc(editor.getHTML()).replace(/ colspan="\d+"/g, '')));

    expect(truoc.oGop, 'mẫu phải CÓ ô gộp, nếu không bài này chứng minh số không').toBe(1);
    expect(hong.oGop, 'gỡ colspan ⇒ không còn ô gộp nào').toBe(0);
    expect(hong).not.toEqual(truoc);
  });

  it('class căn lề và bề ngang ảnh sống sót qua vòng khứ hồi', () => {
    const { html } = docThanhCay(EDITOR_SAMPLE_HTML);

    expect(html).toContain('sn-align-center');
    expect(html, 'bề ngang ảnh là class riêng, không đi cùng căn lề').toContain('sn-w-1-2');
  });
});
