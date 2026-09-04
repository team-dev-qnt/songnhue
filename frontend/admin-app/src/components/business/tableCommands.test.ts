import { Editor } from '@tiptap/core';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { afterEach, describe, expect, it } from 'vitest';

import { boChuThich } from '@/testsupport/boChuThich';

import { EXTENSIONS_SOAN_THAO } from './editorExtensions';
import { LENH_BANG, LENH_CAN_NOI_DOI, trangThaiBang } from './tableCommands';

/**
 * **Lệnh bảng phải tồn tại thật, và `can()` phải nói thật** — WS-41 (T41.6).
 *
 * <h3>Hai lớp lỗi mà tệp này đóng</h3>
 *
 * <ol>
 *   <li><b>Tên lệnh không tồn tại</b> — TipTap **bỏ qua lặng lẽ**. Đây đúng là lỗi `AlignClass` đã
 *       trả giá: bản đầu khai `'image'`/`'figure'`, hai tên không có trong schema, và nút vẫn sáng
 *       lên như đã làm xong việc.
 *   <li><b>`can()` trả `true` cho lệnh sẽ không làm gì</b> — nút sáng, bấm, im lặng. Tệ hơn lớp (1)
 *       vì nó trông hoàn toàn bình thường.
 * </ol>
 */

const editors: Editor[] = [];

function moBang(html: string): Editor {
  const editor = new Editor({ extensions: EXTENSIONS_SOAN_THAO, content: html });
  editors.push(editor);
  return editor;
}

/** Bảng `hang × cot`, con trỏ đặt vào ô đầu bằng LỆNH (jsdom không bấm chuột vào ô được). */
function bang(hang: number, cot: number, withHeaderRow = true): Editor {
  const editor = moBang('<p></p>');
  editor.commands.insertTable({ rows: hang, cols: cot, withHeaderRow });
  return editor;
}

afterEach(() => {
  editors.splice(0).forEach((editor) => editor.destroy());
});

describe('LENH_BANG', () => {
  it('⭐⭐ mọi tên lệnh đều CÓ THẬT trong `editor.commands`', () => {
    const editor = bang(3, 3);
    const khongTonTai = LENH_BANG.filter(
      (lenh) => typeof (editor.commands as unknown as Record<string, unknown>)[lenh] !== 'function',
    );

    expect(
      khongTonTai,
      'Tên lệnh không tồn tại thì TipTap bỏ qua lặng lẽ — nút sáng lên như đã làm xong việc. ' +
        'Đây đúng là lỗi `AlignClass` từng mắc với `image`/`figure`.',
    ).toEqual([]);
  });

  it('⛔ kiểm chứng ngược: một tên bịa PHẢI bị bắt', () => {
    // Luật 1 — không có bài này thì một lỗi gõ trong phép lọc cho bài trên xanh mãi mãi.
    const editor = bang(2, 2);
    const gia = [...LENH_BANG, 'deleteCellSelection'] as readonly string[];
    const bat = gia.filter(
      (lenh) => typeof (editor.commands as unknown as Record<string, unknown>)[lenh] !== 'function',
    );
    expect(bat).toEqual(['deleteCellSelection']);
  });

  it('⭐⭐ ĐO ba lệnh mà `can()` vô dụng — thay vì khẳng định trong chú thích', () => {
    // Ba lệnh này `can()` trả `true` vô điều kiện, nên KHÔNG dùng để bật/tắt nút được. Đo chúng
    // ở đây thay vì viết vào chú thích: nếu bản TipTap sau sửa lại thì bài này ĐỎ và ta biết mình
    // được phép cải thiện giao diện — một chú thích thì im lặng lạc hậu.
    const trong = moBang('<p>không có bảng nào</p>');
    const can = trong.can() as unknown as Record<string, () => boolean>;

    expect(can.insertTable(), 'insertTable luôn true').toBe(true);
    expect(can.fixTables(), 'fixTables luôn true — và nó là no-op thật').toBe(true);
    // Đối chứng: một lệnh mà `can()` NÓI THẬT, để bài này phân biệt được hai trạng thái (quy tắc 9).
    expect(can.addRowAfter(), 'addRowAfter nói thật: ngoài bảng thì false').toBe(false);
  });

  it('⭐⭐ ĐO hai nút "nói dối": `can()` bảo được, `run()` bảo không', () => {
    // Đây là lý do `LENH_CAN_NOI_DOI` tồn tại. Chốt chặn của prosemirror-tables nằm BÊN TRONG
    // `if (dispatch)`, mà `can()` chạy với dispatch rỗng ⇒ nó không bao giờ vào nhánh ấy.
    const motHang = bang(1, 3);
    const can = motHang.can() as unknown as Record<string, () => boolean>;

    expect(can.deleteRow(), 'can() nói ĐƯỢC').toBe(true);
    expect(motHang.commands.deleteRow(), 'run() nói KHÔNG').toBe(false);
    expect(dem(motHang.getHTML(), 'tr'), 'và bảng đứng yên').toBe(1);

    const motCot = bang(3, 1);
    const can2 = motCot.can() as unknown as Record<string, () => boolean>;
    expect(can2.deleteColumn()).toBe(true);
    expect(motCot.commands.deleteColumn()).toBe(false);

    expect(
      [...LENH_CAN_NOI_DOI].sort(),
      'danh sách phải khớp đúng hai lệnh vừa đo — thừa hay thiếu đều làm nút sai',
    ).toEqual(['deleteColumn', 'deleteRow']);
  });
});

describe('trangThaiBang', () => {
  it('ngoài bảng ⇒ `null`', () => {
    expect(trangThaiBang(moBang('<p>một đoạn văn</p>'))).toBeNull();
    expect(trangThaiBang(null)).toBeNull();
  });

  it('⭐⭐ `hangTieuDe` đọc từ HÌNH HỌC, không từ vị trí con trỏ', () => {
    // ⚠ Đây là bài quan trọng nhất của tệp. Nếu đọc bằng `isActive('tableHeader')` thì con trỏ ở
    // hàng thân sẽ báo `false`, nút hiện "tắt", và cú bấm kế tiếp **XOÁ hàng tiêu đề đang có** —
    // người dùng bấm một nút họ tưởng là "bật".
    const editor = bang(3, 3, true);

    // Con trỏ đang ở ô đầu (hàng tiêu đề) — dễ đúng.
    expect(trangThaiBang(editor)?.hangTieuDe, 'con trỏ ở hàng tiêu đề').toBe(true);

    // Đưa con trỏ xuống CUỐI tài liệu bảng — vẫn phải báo `true`.
    editor.commands.setTextSelection(editor.state.doc.content.size - 4);
    expect(
      trangThaiBang(editor)?.hangTieuDe,
      'con trỏ ở hàng thân, nhưng bảng VẪN có hàng tiêu đề',
    ).toBe(true);
  });

  it('bảng không có hàng tiêu đề ⇒ `hangTieuDe` là `false`', () => {
    expect(trangThaiBang(bang(3, 3, false))?.hangTieuDe).toBe(false);
  });

  it('⭐ `chayDuoc` phủ đủ 11 lệnh — thiếu một khoá là một nút không bao giờ bật', () => {
    const trangThai = trangThaiBang(bang(3, 3));
    expect(Object.keys(trangThai?.chayDuoc ?? {}).sort()).toEqual([...LENH_BANG].sort());
  });

  it('⭐ gộp ô rồi tách lại — vòng khép kín, không chỉ "nút có tồn tại"', () => {
    const editor = bang(3, 3, false);
    // Chọn hai ô đầu bằng LỆNH (jsdom không kéo chuột được).
    const doc = editor.state.doc;
    // ⚠ Vị trí **TRƯỚC** ô, không phải trong ô: `CellSelection` giải `$anchorCell.node(-1)` và
    //   đòi nó là nút `table`. Dùng `pos + 1` thì `node(-1)` ra `tableRow` và prosemirror-tables
    //   ném `RangeError: Not a table node: tableRow` — đã đo.
    const viTriO: number[] = [];
    doc.descendants((nut, pos) => {
      if (nut.type.name === 'tableCell') viTriO.push(pos);
      return true;
    });
    editor.commands.setCellSelection({ anchorCell: viTriO[0], headCell: viTriO[1] });

    expect(editor.commands.mergeCells(), 'gộp được').toBe(true);
    expect(editor.getHTML()).toContain('colspan="2"');

    expect(editor.commands.splitCell(), 'tách lại được').toBe(true);
    expect(editor.getHTML()).not.toContain('colspan="2"');
  });
});

describe('EditorTableBar — canh bằng cấu trúc', () => {
  it('⛔ không được chạm thẳng vào `editor` — nó chỉ nhận dữ liệu và một hàm', () => {
    // Giữ ranh giới này là điều kiện để thanh công cụ dựng được trong bài kiểm mà không cần một
    // `Editor` sống, và để mọi phép quyết định nằm ở `tableCommands.ts` (chỗ kiểm được headless).
    const nguon = boChuThich(
      readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'EditorTableBar.tsx'), 'utf8'),
    );

    expect(nguon.length, 'đọc hụt tệp thì bài này xanh mà không canh gì').toBeGreaterThan(500);
    expect(nguon).not.toContain('editor.');
    // Kiểm chứng ngược: phép hỏi bắt được một chuỗi vi phạm.
    expect('const x = editor.can();').toContain('editor.');
  });
});

function dem(html: string, the: string): number {
  return (html.match(new RegExp(`<${the}[\\s>]`, 'g')) ?? []).length;
}
