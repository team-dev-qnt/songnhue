import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { TABLE_CELL_MIN_WIDTH_PX } from 'design-tokens/editor-schema';
import { describe, expect, it } from 'vitest';

import { EXTENSIONS_SOAN_THAO } from './editorExtensions';

/**
 * **CSS của trình soạn thảo phải khớp với CẤU HÌNH của trình soạn thảo** — T41.3, T41.4.
 *
 * <h3>Ba lỗ mà bài kiểm này đóng, cả ba đều đã có thật</h3>
 *
 * <ol>
 *   <li><b>Bảng rộng bị cắt cụt không thanh cuộn.</b> `.sn-editor{overflow:hidden}` +
 *       `.sn-editor__body{overflow-y:auto}` (chỉ trục dọc) và **không** quy tắc nào cho
 *       `.tableWrapper`. Bảng 13 cột = 1040px (đo được) vượt khung ~1000px, cột bên phải biến mất
 *       và không có cách nào với tới.
 *   <li><b>Ô đang chọn không tô sáng.</b> Không quy tắc nào cho `.selectedCell` ⇒ người dùng kéo
 *       chuột qua hai ô mà màn hình không đổi gì ⇒ nút "Gộp ô" là một nút mù.
 *   <li><b>Bề rộng ô không có sàn.</b> Không `min-width` ⇒ bảng luôn co vừa khung bằng cách bóp
 *       từng cột, nên cuộn ngang không bao giờ kích hoạt.
 * </ol>
 *
 * <h3>⭐ Bộ canh GHÉP ĐÔI — phần đáng giá nhất của tệp này</h3>
 *
 * Hai thứ phải đi cùng nhau và nằm ở hai tệp khác ngôn ngữ: `resizable` (TypeScript) và các class
 * mà plugin `columnResizing` cần (CSS). Bật `resizable` mà quên CSS thì tay nắm kéo **vô hình**;
 * tắt `resizable` mà để CSS lại thì có quy tắc cho một plugin không còn nạp. Cả hai đều im lặng.
 *
 * ⚠ Bài này đọc **giá trị đã giải** của `options.resizable` — ⛔ không grep chuỗi trong tệp nguồn.
 * Tiền lệ đúng là `buildConfig.test.ts` (nạp chính tệp cấu hình và đọc giá trị), và lý do là
 * quy tắc 3: canh giá trị ĐÃ GIẢI, đừng canh cái trông giống nó.
 *
 * ⚠ Giới hạn ghi ra (quy tắc 28): tệp này chỉ chứng minh *có quy tắc CSS mang tên đó*, không chứng
 * minh nó **đẹp** hay **đúng**. Thứ nó chặn là **quên hẳn** — và quên hẳn đúng là chuyện đã xảy ra.
 */

const CSS = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), 'richTextEditor.css'),
  'utf8',
);

interface QuyTac {
  boChon: string[];
  than: string;
}

/** Khuôn dùng chung với `public-web/src/app/articleContentCss.test.ts` — cùng lý do không thêm phụ thuộc. */
function tachQuyTac(css: string): QuyTac[] {
  const khongChuThich = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const phang = khongChuThich.replace(/@media[^{]*\{([\s\S]*?)\n\}/g, '$1');

  const quyTac: QuyTac[] = [];
  const mau = /([^{}]+)\{([^{}]*)\}/g;
  let khop: RegExpExecArray | null;
  while ((khop = mau.exec(phang)) !== null) {
    quyTac.push({
      boChon: khop[1]
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
      than: khop[2],
    });
  }
  return quyTac;
}

const QUY_TAC = tachQuyTac(CSS);

function quyTacKhop(mau: RegExp): QuyTac[] {
  return QUY_TAC.filter((qt) => qt.boChon.some((bc) => mau.test(bc)));
}

function coKhai(mau: RegExp, thuocTinh?: string): boolean {
  return quyTacKhop(mau).some(
    (qt) => thuocTinh === undefined || new RegExp(`(^|[;{\\s])${thuocTinh}\\s*:`).test(qt.than),
  );
}

/**
 * Giá trị đã giải của `resizable` trong bộ extension THẬT — ⛔ không đọc bằng chuỗi trong tệp nguồn.
 *
 * ⚠ Ném khi không tìm thấy `tableKit`, chứ **không** trả `false`: một bài kiểm im lặng trả "không
 * bật" khi nó không tìm được thứ cần đo là một bài kiểm nói dối (quy tắc 9 — khẳng định phải phân
 * biệt được hai trạng thái).
 */
function resizableDangBat(): boolean {
  const kit = EXTENSIONS_SOAN_THAO.find((e) => e.name === 'tableKit');
  if (!kit) {
    throw new Error(
      'Không tìm thấy `tableKit` trong EXTENSIONS_SOAN_THAO. Nếu bảng chuyển sang khai bằng ' +
        'extension khác thì phải sửa hàm này — ⛔ đừng để nó im lặng báo "không bật".',
    );
  }

  // `TableKit` không khai `addOptions()`, nên `options.table` đúng bằng thứ được truyền vào
  // `configure()`. Thiếu khoá `table` hoặc thiếu `resizable` ⇒ rơi về mặc định của
  // `Table.addOptions()`, và mặc định ấy là `resizable: false` (đã đọc mã 3.31.0).
  // `table: false` ⇒ Table không được nạp, đương nhiên cũng không có tay nắm kéo.
  const table = (kit.options as { table?: false | { resizable?: boolean } }).table;
  return table !== undefined && table !== false && table.resizable === true;
}

describe('CSS trình soạn thảo', () => {
  it('⚠ tách được quy tắc — bài chạy qua tập rỗng thì xanh mà không canh gì (luật 7)', () => {
    expect(QUY_TAC.length).toBeGreaterThan(20);
    expect(QUY_TAC.some((qt) => qt.boChon.includes('.sn-editor'))).toBe(true);
  });

  it('⛔⛔ `.sn-editor` KHÔNG được có `overflow: hidden` — nó giết `position: sticky`', () => {
    const coOverflowHidden = quyTacKhop(/^\.sn-editor$/).some((qt) =>
      /overflow\s*:\s*hidden/.test(qt.than),
    );

    expect(
      coOverflowHidden,
      '`position: sticky` bị giới hạn bởi hộp cuộn gần nhất. `overflow: hidden` biến `.sn-editor` ' +
        'thành một hộp cuộn KHÔNG BAO GIỜ CUỘN, nên thanh công cụ ngữ cảnh của bảng dính vào một ' +
        'khung đứng im và nằm ngoài màn hình. Triệu chứng: CSS trông đúng, và không có gì xảy ra.',
    ).toBe(false);
  });

  it('⭐⭐ bảng rộng phải cuộn ngang được trong khung soạn thảo', () => {
    expect(
      coKhai(/\.tableWrapper$/, 'overflow-x'),
      '`.tableWrapper` là khung do TipTap sinh ra. Thiếu `overflow-x` thì bảng 13 cột (1040px) ' +
        'bị cắt cụt: cột bên phải biến mất và không có thanh cuộn nào để với tới.',
    ).toBe(true);
  });

  it('⭐ ô đang chọn phải tô sáng — nếu không thì nút "Gộp ô" là nút mù', () => {
    expect(coKhai(/\.selectedCell/), 'không có quy tắc nào cho `.selectedCell`').toBe(true);

    // `position: relative` trên ô là điều kiện để lớp phủ `::after` neo ĐÚNG chỗ. Thiếu nó thì
    // lớp phủ bám vào tổ tiên định vị gần nhất và tô ra ngoài ô — bài kiểm chỉ hỏi "có class
    // .selectedCell không" sẽ xanh trọn vẹn trong khi màn hình sai.
    expect(
      coKhai(/\.sn-editor__body (th|td)$/, 'position'),
      '`.selectedCell::after` dùng `position: absolute`; thiếu `position: relative` trên ô thì ' +
        'lớp phủ neo vào tổ tiên khác và tô nhầm chỗ.',
    ).toBe(true);
  });

  it('⭐ ô bảng phải có sàn `min-width`, và đúng bằng hằng dùng chung', () => {
    const oBang = quyTacKhop(/\.sn-editor__body (th|td)$/);
    const than = oBang.map((qt) => qt.than).join('\n');

    expect(oBang.length, 'không tìm thấy quy tắc cho ô bảng').toBeGreaterThan(0);
    expect(
      than,
      `Sàn bề rộng ô phải bằng TABLE_CELL_MIN_WIDTH_PX (${TABLE_CELL_MIN_WIDTH_PX}px). Lệch con ` +
        'số là bảng trong trình soạn thảo và bảng trên cổng cuộn ở hai ngưỡng khác nhau — người ' +
        'soạn thấy vừa khung, bạn đọc thấy tràn.',
    ).toMatch(new RegExp(`min-width\\s*:\\s*${TABLE_CELL_MIN_WIDTH_PX}px`));
  });

  it('⭐ chế độ Xem trước phải cuộn ngang y như cổng — nó KHÔNG có `.tableWrapper`', () => {
    expect(
      coKhai(/\.sn-editor__preview table$/, 'overflow-x'),
      'Xem trước dựng HTML thô nên không có `div.tableWrapper` (khung ấy là sản phẩm của ' +
        'NodeView). Không khai riêng thì bảng nhiều cột tràn khung xem trước — §10.26: "xem ' +
        'trước thấy đẹp, xuất bản, và không ai mở lại trang công khai để đối chiếu".',
    ).toBe(true);
  });

  it('⭐⭐ GHÉP ĐÔI: `resizable` và CSS của tay nắm kéo phải cùng bật hoặc cùng tắt', () => {
    const bat = resizableDangBat();
    const coCssTayNam = coKhai(/\.column-resize-handle$/);
    const coConTro = coKhai(/\.resize-cursor$/);

    expect(
      { resizable: bat, coCssTayNam, coConTro },
      bat
        ? 'Bật `resizable` mà thiếu CSS `.column-resize-handle` + `.resize-cursor` thì tay nắm ' +
            'kéo cột VÔ HÌNH — tính năng bật mà không ai thấy. ⚠ Và trước khi bật lại, đọc T41.14: ' +
            'bề rộng cột còn bị HtmlSanitizer gỡ ở CẢ HAI đường (`style` và `colwidth`).'
        : 'Tắt `resizable` thì plugin `columnResizing` không còn nạp, nên CSS cho ' +
            '`.column-resize-handle`/`.resize-cursor` là quy tắc cho một thứ không tồn tại — gỡ đi.',
    ).toEqual({ resizable: bat, coCssTayNam: bat, coConTro: bat });
  });

  it('⛔ kiểm chứng ngược: bộ tách và phép hỏi bắt được đúng thứ chúng nói là bắt', () => {
    // Luật 1 — không có bài này thì một regex gõ sai vẫn cho mọi bài trên xanh trọn vẹn mãi mãi.
    const mau = tachQuyTac('.a, .b { color: red; }\n.c { overflow: hidden; }');
    expect(mau).toHaveLength(2);
    expect(mau[0].boChon).toEqual(['.a', '.b']);

    // Phép hỏi thuộc tính phải phân biệt được `min-width` với `width` — nếu không thì bài
    // "ô bảng có sàn min-width" sẽ xanh nhờ một khai báo `width` hoàn toàn khác nghĩa.
    const chiCoWidth = tachQuyTac('.x { width: 80px; }');
    expect(/(^|[;{\s])min-width\s*:/.test(chiCoWidth[0].than)).toBe(false);
    expect(/(^|[;{\s])width\s*:/.test(chiCoWidth[0].than)).toBe(true);
  });
});
