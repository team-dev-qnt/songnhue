import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { Editor } from '@tiptap/core';
import { describe, expect, it } from 'vitest';

import { boChuThich } from '@/testsupport/boChuThich';

import { EXTENSIONS_SOAN_THAO } from './editorExtensions';

/**
 * **Bộ extension chỉ được khai ở MỘT nơi** — T41.2.
 *
 * <h3>Lỗi đã có thật, và nó im lặng theo cách tệ nhất</h3>
 *
 * Trước lượt này danh sách extension được dựng ở **ba** chỗ: `RichTextEditor.tsx` (bản chạy thật),
 * `editorRoundTrip.test.ts` và `alignClass.test.ts`. Ba chỗ **đã lệch**: hai bản kiểm thiếu
 * `StarterKit.configure({ link: { protocols } })`.
 *
 * Nghĩa là hai bộ kiểm về vòng khứ hồi và về căn lề đang khẳng định trên một trình soạn thảo
 * **không tồn tại** — chúng xanh trọn vẹn kể cả khi bản thật hỏng ở đúng phần chúng không dựng.
 * Đây là quy tắc 7 ở dạng tinh vi: cơ chế có chạy, nhưng chạy trên một đối tượng khác.
 *
 * ⚠ Bài này canh **cấu trúc** (chuỗi khai xuất hiện ở mấy tệp), không canh hành vi — nó không
 * chứng minh cấu hình đúng, nó chỉ chặn việc ai đó dựng lại chỗ thứ hai. Giới hạn ghi ra để cái
 * xanh của nó không bị đọc thành một lời bảo đảm rộng hơn (quy tắc 28).
 */

const GOC = join(dirname(fileURLToPath(import.meta.url)), '../..');
const TEP_NAY = 'components/business/editorExtensions.ts';

/**
 * ⚠ Loại đúng MỘT tệp: chính bài kiểm này.
 *
 * Nó mang chuỗi `TableKit.configure(` trong fixture kiểm-chứng-ngược và trong câu thông báo lỗi,
 * mà `boChuThich` chỉ bỏ chú thích chứ không bỏ chuỗi ký tự — nên không loại thì bộ canh bắt
 * chính nó và đỏ vĩnh viễn vì một lý do không liên quan production.
 *
 * ⛔ **Không** loại cả `.test.ts` như `noHardcodedColors.test.ts` làm: chỗ lệch đã xảy ra nằm
 * ĐÚNG trong hai tệp kiểm (`editorRoundTrip`, `alignClass`). Loại hết tệp kiểm là gỡ bộ canh khỏi
 * đúng nơi nó phải chặn.
 */
const TU_MIEN = 'components/business/editorExtensions.test.ts';

function timNguon(thuMuc: string): string[] {
  return readdirSync(thuMuc).flatMap((ten) => {
    const duong = join(thuMuc, ten);
    if (statSync(duong).isDirectory()) return timNguon(duong);
    return ten.endsWith('.ts') || ten.endsWith('.tsx') ? [duong] : [];
  });
}

const MA = timNguon(GOC)
  .map((duong) => ({
    ten: duong.slice(GOC.length + 1),
    nguon: boChuThich(readFileSync(duong, 'utf8')),
  }))
  .filter((m) => m.ten !== TU_MIEN);

describe('Một nguồn cho bộ extension', () => {
  it('⚠ tìm được tệp để soi — bài chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    expect(MA.length).toBeGreaterThan(50);
    expect(MA.some((m) => m.ten === TEP_NAY)).toBe(true);
    // Danh sách tự miễn phải trỏ vào một tệp CÓ THẬT — nếu đổi tên tệp kiểm mà quên sửa hằng
    // này thì bộ canh im lặng miễn trừ một tệp không tồn tại, và tệp thật lại bị bắt.
    expect(timNguon(GOC).map((d) => d.slice(GOC.length + 1))).toContain(TU_MIEN);
  });

  it('⛔ `TableKit.configure(` chỉ được xuất hiện ở `editorExtensions.ts`', () => {
    const pham = MA.filter((m) => m.nguon.includes('TableKit.configure(')).map((m) => m.ten);

    expect(
      pham,
      'Dựng lại danh sách extension ở chỗ thứ hai là dựng lại đúng lỗi vừa sửa: hai bản kiểm ' +
        'từng thiếu `link` config và vì thế khẳng định về một trình soạn thảo không tồn tại. ' +
        'Import `EXTENSIONS_SOAN_THAO` thay vì khai lại.',
    ).toEqual([TEP_NAY]);
  });

  it('⛔ `StarterKit.configure(` cũng chỉ ở đúng tệp ấy', () => {
    const pham = MA.filter((m) => m.nguon.includes('StarterKit.configure(')).map((m) => m.ten);
    expect(pham).toEqual([TEP_NAY]);
  });

  it('⭐ không extension nào trùng tên — trùng tên là editor KHÔNG DỰNG ĐƯỢC', () => {
    // Đo được trên TipTap 3.31: `TableKit.configure({table:{…}})` + một `Table` khai riêng cùng
    // mang `name: 'table'` ⇒ `RangeError: Adding different instances of a keyed plugin
    // (selectingCells$)` ném ngay trong `new Editor(...)`. Không phải một cảnh báo — màn hình
    // soạn bài trắng trơn. Bài này chặn nó ở tầng cấu hình, trước khi ai đó mở trình duyệt.
    const ten = EXTENSIONS_SOAN_THAO.map((e) => e.name);
    expect(new Set(ten).size, `trùng tên trong: ${ten.join(', ')}`).toBe(ten.length);
  });

  it('⭐⭐ gợi ý chỗ nhập THẬT SỰ gắn được — quy tắc CSS trước đây là một quy tắc chết', () => {
    // ⚠⚠ Đo được trước bản vá: 42 extension nạp, **không có** `placeholder`, và DOM khi rỗng
    // không có class `is-editor-empty`. Nghĩa là quy tắc `.ProseMirror p.is-editor-empty::before`
    // trong `richTextEditor.css` **chưa từng chạy một lần nào** — bài mới là một ô trắng trơn.
    // Đúng hình dạng nửa cặp đọc–ghi: có đầu đọc (CSS), không có đầu ghi (extension).
    const el = document.createElement('div');
    document.body.appendChild(el);
    const editor = new Editor({ element: el, extensions: EXTENSIONS_SOAN_THAO, content: '' });

    try {
      // Ba vế, và cần cả ba: extension có nạp · class được gắn · CHỮ có mặt để CSS đọc bằng
      // `attr(data-placeholder)`. Thiếu vế thứ ba thì CSS vẽ ra một chuỗi rỗng — xanh mà câm.
      expect(EXTENSIONS_SOAN_THAO.map((e) => e.name)).toContain('placeholder');
      expect(el.innerHTML).toContain('is-editor-empty');
      expect(el.querySelector('[data-placeholder]')?.getAttribute('data-placeholder')).toBeTruthy();
    } finally {
      editor.destroy();
      el.remove();
    }
  });

  it('⛔ và gợi ý BIẾN MẤT khi đã có nội dung — nếu không thì nó đè lên chữ người dùng', () => {
    const el = document.createElement('div');
    document.body.appendChild(el);
    const editor = new Editor({
      element: el,
      extensions: EXTENSIONS_SOAN_THAO,
      content: '<p>Đã có nội dung</p>',
    });

    try {
      expect(el.innerHTML).not.toContain('is-editor-empty');
    } finally {
      editor.destroy();
      el.remove();
    }
  });

  it('⛔ kiểm chứng ngược: phép lọc bắt được một chỗ khai thứ hai', () => {
    // Luật 1 — không có bài này thì một lỗi gõ trong chuỗi tìm kiếm cho hai bài trên xanh mãi mãi.
    const gia = [
      { ten: 'a.ts', nguon: 'TableKit.configure({})' },
      { ten: 'b.ts', nguon: 'const x = 1;' },
    ];
    expect(gia.filter((m) => m.nguon.includes('TableKit.configure(')).map((m) => m.ten)).toEqual([
      'a.ts',
    ]);

    // …và `boChuThich` phải thật sự bỏ chú thích: một tệp chỉ NHẮC tới `TableKit.configure(`
    // trong javadoc (như chính `editorExtensions.ts` đang làm) không được tính là vi phạm.
    expect(boChuThich('/* TableKit.configure( */ const a = 1;')).not.toContain(
      'TableKit.configure(',
    );
  });
});
