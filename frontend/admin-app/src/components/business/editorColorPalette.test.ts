import { Editor, getSchema } from '@tiptap/core';
import { CELL_BG_CLASSES, TEXT_BG_CLASSES, TEXT_COLOR_CLASSES } from 'design-tokens/editor-schema';
import { afterEach, describe, expect, it } from 'vitest';

import { CELL_BG_TYPES } from './CellBgClass';
import { maMauCuaClass, nhanSacCuaClass, NHOM_MAU } from './editorColorPalette';
import { EXTENSIONS_SOAN_THAO } from './editorExtensions';

/**
 * **Màu chữ · màu nền chữ · màu nền ô thật sự đi ra HTML** — T41.15, yêu cầu ĐÃ KÝ.
 *
 * ## Vì sao phải dựng trình soạn thảo THẬT
 *
 * Ba bộ canh khác chỉ chứng minh từng cạnh của tam giác: `EditorVocabularyTest` (class sống sót qua
 * jsoup), `articleContentCss.test.ts` và `richTextEditorCss.test.ts` (CSS hai phía có quy tắc). Cả
 * ba đều xanh trọn vẹn kể cả khi **lệnh không chạy** — một nút bấm không ra class nào thì không có
 * class nào để mà bị gỡ, và không có class nào để mà thiếu CSS.
 *
 * Đó đúng là hình dạng đã trả giá ở `AlignClass`: `.some(...)` báo thành công, nút sáng lên, ảnh
 * đứng yên. Nên bài này chạy **lệnh thật trên editor thật** rồi đọc `getHTML()`.
 *
 * ## Vế khứ hồi mới là vế đắt
 *
 * Nội dung đi một vòng *soạn → lưu → mở lại*. `parseHTML` sai thì lượt mở lại **mất màu**, và mất
 * âm thầm: người soạn mở bài cũ, thấy trắng đen, tưởng mình chưa từng tô.
 */

const editors: Editor[] = [];

function dungEditor(content = '<p>Xin chào</p>'): Editor {
  const editor = new Editor({ extensions: EXTENSIONS_SOAN_THAO, content });
  editors.push(editor);
  return editor;
}

afterEach(() => {
  editors.splice(0).forEach((editor) => editor.destroy());
});

describe('Bảng màu — mọi class tra được màu và nhãn', () => {
  const MOI_LOP = [...TEXT_COLOR_CLASSES, ...TEXT_BG_CLASSES, ...CELL_BG_CLASSES];

  it('⚠ có đủ 18 class để kiểm — chặn xanh-trên-tập-rỗng (luật 7)', () => {
    expect(MOI_LOP).toHaveLength(18);
    expect(new Set(MOI_LOP).size).toBe(18);
  });

  it('⭐⭐ mọi class tra ra một mã màu CÓ THẬT trong `editorColors`', () => {
    // Một class gõ sai chỉ hiện ra dưới dạng ô màu trong suốt trên thanh công cụ — trông như lựa
    // chọn "bỏ màu", ⛔ không như một lỗi. Đây là chỗ duy nhất bắt được nó.
    const hong = MOI_LOP.filter((lop) => maMauCuaClass(lop) === undefined);
    expect(hong, 'Class không tra được màu ⇒ thanh công cụ vẽ một ô trong suốt').toEqual([]);
  });

  it('mọi class tra ra một nhãn tiếng Việt — nút không nhãn thì trình đọc màn hình đọc "button"', () => {
    expect(MOI_LOP.filter((lop) => nhanSacCuaClass(lop) === undefined)).toEqual([]);
  });

  it('⭐ phép tra phân biệt được hai trạng thái (luật 9)', () => {
    expect(maMauCuaClass('sn-fg-do')).toMatch(/^#[0-9a-f]{6}$/i);
    expect(maMauCuaClass('sn-fg-khong-ton-tai')).toBeUndefined();
    expect(nhanSacCuaClass('sn-bg-khong-ton-tai')).toBeUndefined();
  });

  it('ba nhóm trên thanh công cụ khớp đúng ba danh sách của hợp đồng', () => {
    expect(NHOM_MAU.map((n) => n.ma)).toEqual(['fg', 'bg', 'cell']);
    expect(NHOM_MAU[0].lop).toEqual(TEXT_COLOR_CLASSES);
    expect(NHOM_MAU[1].lop).toEqual(TEXT_BG_CLASSES);
    expect(NHOM_MAU[2].lop).toEqual(CELL_BG_CLASSES);
  });
});

describe('Lệnh màu chạy thật trên editor', () => {
  it('⭐⭐ `setTextColor` sinh ra `<span class="sn-fg-…">`, ⛔ không phải `style`', () => {
    const editor = dungEditor();
    editor.commands.selectAll();
    expect(editor.chain().setTextColor('sn-fg-do').run()).toBe(true);

    const html = editor.getHTML();
    expect(html).toContain('sn-fg-do');
    // ⛔ `style` bị HtmlSanitizer gỡ ⇒ có `style` là mất màu lúc lưu, đúng lỗi T20.1.
    expect(html).not.toContain('style=');
  });

  it('⭐ màu chữ và màu nền chồng nhau cho ra MỘT `<span>` hai class, ⛔ không lồng nhau', () => {
    const editor = dungEditor();
    editor.commands.selectAll();
    editor.chain().setTextColor('sn-fg-do').run();
    editor.commands.selectAll();
    editor.chain().setTextBg('sn-bg-vang').run();

    const html = editor.getHTML();
    expect(html).toContain('sn-fg-do');
    expect(html).toContain('sn-bg-vang');
    // Lồng nhau thì mỗi lượt lưu–mở lại thêm một tầng, và nội dung phình dần.
    expect(html).not.toMatch(/<span[^>]*>\s*<span/);
  });

  it('⭐⭐ bỏ cả hai màu thì mark biến mất — ⛔ không để lại `<span>` rỗng', () => {
    const editor = dungEditor();
    editor.commands.selectAll();
    editor.chain().setTextColor('sn-fg-do').run();
    editor.commands.selectAll();
    editor.chain().setTextColor(null).run();

    // `<span>` rỗng là rác tích tụ qua mỗi lượt sửa, và bộ khử trùng ⛔ không dọn hộ.
    expect(editor.getHTML()).not.toContain('<span');
  });

  it('⚠ ĐO ĐƯỢC: `setTextColor` trả `true` cả khi vùng chọn rỗng — nhánh cảnh báo hầu như không chạy', () => {
    // Bản đầu của bài này khẳng định `false`, và nó ĐỎ. Sự thật đo được: `setMark` trên vùng chọn
    // thu gọn ghi vào `storedMarks` và trả `true` — chữ gõ tiếp sẽ mang màu. Đó là hành vi ĐÚNG của
    // TipTap, ⛔ không phải khuyết tật.
    //
    // ⇒ Hệ quả phải ghi ra: nhánh `message.warning('Chọn (bôi đen) đoạn chữ…')` trong
    //   `RichTextEditor.tsx` là lưới phòng thân cho các bản TipTap sau, ⛔ KHÔNG phải một chốt chặn
    //   đang chạy. Ghi ở đây thay vì để cái xanh của bài kiểm đọc như một lời bảo đảm (luật 28).
    const editor = dungEditor('<p></p>');
    expect(editor.chain().setTextColor('sn-fg-do').run()).toBe(true);
  });

  it('⭐⭐ `setCellBg` NGOÀI bảng trả `false` — đây mới là nhánh cảnh báo chạy thật', () => {
    // Vế phân biệt hai trạng thái (luật 9): nút "Màu nền ô" bị vô hiệu khi con trỏ ngoài bảng, và
    // khẳng định này chứng minh trạng thái vô hiệu ấy có căn cứ, ⛔ không phải một lựa chọn tuỳ ý.
    const editor = dungEditor('<p>chỉ là đoạn văn</p>');
    editor.commands.selectAll();
    expect(editor.chain().setCellBg('sn-cell-vang').run()).toBe(false);
  });
});

describe('Màu nền ô bảng', () => {
  const BANG = '<table><tbody><tr><td>ô A</td><td>ô B</td></tr></tbody></table>';

  it('⭐ tên nút khai trong `CELL_BG_TYPES` phải CÓ THẬT trong schema', () => {
    // Bài học `AlignClass`: `addGlobalAttributes` cho một type không có trong schema bị bỏ qua
    // **lặng lẽ** — ô không hề có thuộc tính để ghi, và lệnh vẫn báo thành công.
    const schema = getSchema(EXTENSIONS_SOAN_THAO);
    expect(CELL_BG_TYPES).not.toHaveLength(0);
    const thieu = CELL_BG_TYPES.filter((ten) => schema.nodes[ten] === undefined);
    expect(thieu, 'Tên nút bảng đã đổi ở phiên bản TipTap mới').toEqual([]);
  });

  it('⭐⭐ `setCellBg` đặt class lên chính `<td>`, ⛔ không lên `<span>` bên trong', () => {
    const editor = dungEditor(BANG);
    // Đặt con trỏ vào ô đầu.
    editor.commands.setTextSelection(3);
    expect(editor.chain().setCellBg('sn-cell-vang').run()).toBe(true);

    const html = editor.getHTML();
    // Lên `<span>` thì chữ có nền còn phần ô trống bên phải vẫn trắng — lộ ra ngay.
    expect(html).toMatch(/<td[^>]*class="[^"]*sn-cell-vang/);
    expect(html).not.toMatch(/<span[^>]*sn-cell-vang/);
  });
});

describe('⭐⭐ Khứ hồi — mở lại bài cũ phải giữ nguyên màu', () => {
  it('màu chữ và màu nền đọc lại được từ HTML đã lưu', () => {
    const daLuu = '<p><span class="sn-fg-lam sn-bg-vang">Đoạn có màu</span></p>';
    const editor = dungEditor(daLuu);

    const html = editor.getHTML();
    expect(html, 'parseHTML sai ⇒ mở bài cũ thấy trắng đen, và mất âm thầm').toContain('sn-fg-lam');
    expect(html).toContain('sn-bg-vang');
  });

  it('màu nền ô đọc lại được', () => {
    const daLuu = '<table><tbody><tr><td class="sn-cell-luc">ô</td></tr></tbody></table>';
    expect(dungEditor(daLuu).getHTML()).toContain('sn-cell-luc');
  });

  it('⭐⭐ `<span>` KHÔNG mang màu ⛔ không sinh ra `<span>` RỖNG — chỗ `getAttrs` phải trả `false`', () => {
    // ⚠ Bản đầu của bài này đòi `sn-nho` sống sót, và nó ĐỎ. Sự thật đo được: TipTap vốn đã chuẩn
    //   hoá mọi `<span>` trơn đi từ trước — `editorRoundTrip.test.ts` khai đúng điều đó qua
    //   `CHI_CO_O_BO_LOC = ['span', 'thead']` (những thẻ chỉ tồn tại ở phía bộ lọc, cho nội dung
    //   DÁN từ Word, chứ trình soạn thảo ⛔ không sinh ra).
    //
    // Thứ bài này thật sự canh là hệ quả của `getAttrs`: trả `{}` thay vì `false` thì mọi span trơn
    // biến thành một mark màu RỖNG và được vẽ lại thành `<span>` không class — rác tích tụ qua mỗi
    // lượt lưu–mở, và bộ khử trùng ⛔ không dọn hộ vì `span` nằm trong danh sách cho phép.
    const html = dungEditor('<p><span class="sn-nho">chú thích nhỏ</span></p>').getHTML();
    expect(html).not.toContain('<span');
    expect(html, 'chữ phải còn — mất chữ là một lỗi khác, nặng hơn').toContain('chú thích nhỏ');
  });
});
