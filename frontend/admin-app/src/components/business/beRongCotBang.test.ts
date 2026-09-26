import { Editor } from '@tiptap/core';
import { EDITOR_SAMPLE_HTML, LOP_KHUNG_BANG } from '@songnhue/design-tokens/editor-schema';
import { afterEach, describe, expect, it } from 'vitest';

import { EXTENSIONS_SOAN_THAO } from './editorExtensions';

/**
 * **Bề rộng cột bảng sống sót qua một vòng lưu–mở — T41.14.**
 *
 * ## ⛔⛔ Vì sao dòng nợ này đòi ĐỦ HAI VẾ
 *
 * Trước lượt này TipTap ghi bề rộng qua **hai** đường, và `HtmlSanitizer` gỡ **cả hai**:
 * `style` trên `<table>`/`<col>` (chỉ `class` được qua trên mọi thẻ), và thuộc tính `colwidth`
 * trên `td`/`th` (jsoup `Safelist.relaxed()` cho `td`/`th` đúng `abbr, axis, colspan, rowspan,
 * width`). ⇒ Mọi cú kéo giãn cột bị vứt lúc lưu — **im lặng**, ⛔ lỗi, ⛔ cảnh báo. Đó là lý do
 * `resizable` bị TẮT ở T41.3: nó gỡ một tay nắm nói dối.
 *
 * ⇒ Đường sống duy nhất là `<col width="N">`: jsoup cho `col[span,width]` qua **⛔ phải sửa
 * Safelist**, và `colgroup`/`col` đã nằm trong `EDITOR_TAGS` từ WS-41.
 *
 * ## ⛔⛔ Ô GỘP là ca hỏng thật, ⛔ phải một chi tiết
 *
 * `parseColwidth` của TipTap rơi về `parseColgroupWidth`, và hàm ấy tra
 * `Array.from(row.children).indexOf(element)` — **chỉ số Ô**, ⛔ phải **chỉ số CỘT**. Một bảng có
 * `colspan` làm hai thứ ấy lệch nhau từ ô gộp trở đi ⇒ các cột sau nhận bề rộng của cột khác, và
 * bảng **mất bề rộng sau đúng một vòng lưu–mở**. Bài `oGopKhongLamLechBeRong` đo đúng ca ấy.
 *
 * ## ⚠ Bài này mô phỏng bộ lọc, ⛔ gọi nó
 *
 * `HtmlSanitizer` là jsoup, chạy ở Java. Thứ mô phỏng được và đáng mô phỏng là **hành vi đã đo**
 * của nó: gỡ `style` ở mọi thẻ, gỡ `colwidth` ở `td`/`th`, giữ `col[width]`. Vế Java do
 * `EditorVocabularyTest` + `HtmlSanitizerTest` canh; ⛔ đọc cái xanh của bài này thành *"bộ lọc
 * thật cũng cho qua"* (luật 28).
 */

const editors: Editor[] = [];

afterEach(() => {
  editors.splice(0).forEach((editor) => editor.destroy());
});

function dung(html: string): Editor {
  const editor = new Editor({ extensions: EXTENSIONS_SOAN_THAO, content: html });
  editors.push(editor);
  return editor;
}

/**
 * Đúng ba phép gỡ mà `HtmlSanitizer` thực hiện trên bảng — đo trên jsoup 1.23.2.
 *
 * ⚠ ⛔ gỡ bừa: `col[width]` và `td[width]` **được** giữ, nên một bản vá dựa vào chúng phải sống
 * sót qua hàm này. Gỡ luôn cả hai là làm bài kiểm nghiêm hơn bộ lọc thật, và khi ấy nó bác một
 * bản vá đúng.
 */
function quaBoLoc(html: string): string {
  return html.replace(/\sstyle="[^"]*"/g, '').replace(/\scolwidth="[^"]*"/g, '');
}

/** Bề rộng từng `<col>` theo thứ tự, đọc từ thuộc tính `width`. */
function beRongCot(html: string): (string | null)[] {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  return Array.from(doc.querySelectorAll('colgroup > col')).map((c) => c.getAttribute('width'));
}

/** Bảng 3 cột, cột 1 rộng 120px và cột 3 rộng 200px — `colwidth` là cách TipTap lưu trong cây. */
const BANG_BA_COT = [
  '<table><tbody>',
  '<tr>',
  '<th colwidth="120">A</th><th>B</th><th colwidth="200">C</th>',
  '</tr>',
  '<tr><td>1</td><td>2</td><td>3</td></tr>',
  '</tbody></table>',
].join('');

/** Hàng đầu có một ô gộp HAI cột — chỗ *chỉ số ô* và *chỉ số cột* bắt đầu lệch nhau. */
const BANG_CO_O_GOP = [
  '<table><tbody>',
  '<tr>',
  '<th colspan="2" colwidth="120,90">Gộp</th><th colwidth="200">C</th>',
  '</tr>',
  '<tr><td>1</td><td>2</td><td>3</td></tr>',
  '</tbody></table>',
].join('');

describe('bề rộng cột bảng — T41.14', () => {
  it('⛔⛔ `<col>` phải mang THUỘC TÍNH `width`, ⛔ phải `style` — `style` bị bộ lọc gỡ', () => {
    const html = dung(BANG_BA_COT).getHTML();

    expect(
      beRongCot(html),
      'Đây là toàn bộ dòng nợ T41.14 gói trong một khẳng định: `style` ⛔ đi qua `HtmlSanitizer`, ' +
        'nên một bề rộng ghi bằng `style` là một bề rộng ⛔ bao giờ quay lại.',
    ).toEqual(['120', null, '200']);
  });

  it('⭐⭐ Một vòng lưu–mở QUA BỘ LỌC giữ nguyên bề rộng', () => {
    const luu = quaBoLoc(dung(BANG_BA_COT).getHTML());
    // Tiền đề, ⛔ phải kết luận: bản đã lọc phải CÒN `col width` thì mới có gì để đọc lại.
    expect(beRongCot(luu)).toEqual(['120', null, '200']);

    expect(
      beRongCot(dung(luu).getHTML()),
      'Mở lại bài rồi lưu lần hai mà bề rộng bay mất nghĩa là người dùng kéo cột xong, đóng ' +
        'bài, mở ra thấy như chưa kéo — ⛔ một dòng lỗi nào.',
    ).toEqual(['120', null, '200']);
  });

  it('⛔⛔ Ô GỘP ⛔ làm lệch bề rộng — `parseColgroupWidth` tra chỉ số Ô, ⛔ chỉ số CỘT', () => {
    const luu = quaBoLoc(dung(BANG_CO_O_GOP).getHTML());
    expect(beRongCot(luu), 'tiền đề: ba cột, hai cột đầu thuộc ô gộp').toEqual([
      '120',
      '90',
      '200',
    ]);

    expect(
      beRongCot(dung(luu).getHTML()),
      'Với bản cũ, ô `C` là con thứ HAI của hàng nên nó đọc `<col>` thứ hai (90px) thay vì ' +
        'thứ ba (200px) — 200px biến mất sau đúng một vòng, và bảng trông *gần đúng*.',
    ).toEqual(['120', '90', '200']);
  });

  it('⛔ Tên class trong mẫu phải khớp `LOP_KHUNG_BANG` — hai nơi phải nhớ nhau (luật 14)', () => {
    // `EDITOR_SAMPLE_HTML` buộc phải viết THẲNG tên class: `EditorVocabularyTest` phía Java đọc
    // hằng ấy bằng regex `'([^']*)'`, chỉ nhận nháy ĐƠN, nên một `${LOP_KHUNG_BANG}` bị bỏ qua
    // nguyên dòng ⇒ mẫu mất thẻ mở và bài kiểm đỏ ở một chỗ ⛔ liên quan gì tới nguyên nhân.
    // ⇒ Chỗ trùng lặp ấy ⛔ gỡ được bằng mã, nên nó cần một phép kiểm nhớ hộ.
    expect(EDITOR_SAMPLE_HTML).toContain(`<div class="${LOP_KHUNG_BANG}">`);
    // Vế phân biệt: khẳng định trên phải đỏ được nếu tên class đổi một bên.
    expect(EDITOR_SAMPLE_HTML).not.toContain('class="sn-bang-cuon-khong-ton-tai"');
  });

  it('⚠ Vế chống tập rỗng: bảng ⛔ ai đặt bề rộng thì ⛔ bịa ra con số nào', () => {
    // Thiếu vế này thì mọi khẳng định trên vẫn xanh khi bản vá gán một bề rộng mặc định cho
    // MỌI cột — lúc ấy `col[width]` ⛔ còn phân biệt được *đã kéo* với *chưa kéo* (luật 9).
    const html = dung('<table><tbody><tr><th>A</th><th>B</th></tr></tbody></table>').getHTML();
    expect(beRongCot(html)).toEqual([null, null]);
  });
});
