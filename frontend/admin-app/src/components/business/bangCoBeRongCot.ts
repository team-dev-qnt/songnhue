import { mergeAttributes } from '@tiptap/core';
import { Table, TableCell, TableHeader } from '@tiptap/extension-table';
import { TableMap } from '@tiptap/pm/tables';
import type { Node as ProseMirrorNode } from '@tiptap/pm/model';
import { LOP_KHUNG_BANG, TABLE_CELL_MIN_WIDTH_PX } from '@songnhue/design-tokens/editor-schema';

/**
 * Bảng **giữ được bề rộng cột** qua một vòng lưu–mở — T41.14.
 *
 * ## ⛔⛔ Vì sao phải thay node của `TableKit` chứ ⛔ cấu hình nó
 *
 * TipTap ghi bề rộng qua **hai** đường và `HtmlSanitizer` gỡ **cả hai**: `style` trên
 * `<table>`/`<col>` (jsoup chỉ cho `class` qua trên mọi thẻ), và `colwidth` trên `td`/`th`
 * (`Safelist.relaxed()` cho `td`/`th` đúng `abbr, axis, colspan, rowspan, width`). Cả hai đường
 * đều **đứt** — đó là lý do `resizable` bị tắt ở T41.3: nó gỡ một tay nắm nói dối.
 *
 * Đường sống duy nhất là **`<col width="N">`**: jsoup cho `col[span,width]` qua mà ⛔ phải sửa
 * Safelist, và `colgroup`/`col` đã nằm trong `EDITOR_TAGS` từ WS-41. Nhưng `Table.renderHTML` của
 * kit gọi `createColGroup`, và hàm ấy phát `style` ⇒ ⛔ có tuỳ chọn nào đổi được. ⇒ Node riêng.
 *
 * ## ⛔⛔ `TableKit.configure({ table: false })`, ⛔ `Table.extend()` chồng lên
 *
 * Hai extension cùng tên `table` ném `RangeError: Adding different instances of a keyed plugin`
 * và **editor ⛔ dựng được**. Phải tắt bản của kit trước, rồi mới thêm bản của mình —
 * `editorExtensions.ts` làm đúng thứ tự ấy.
 *
 * ## ⛔⛔ Dựng colgroup bằng `TableMap`, ⛔ bằng `node.firstChild`
 *
 * `createColGroup` chỉ đọc **hàng đầu**. Bề rộng của một cột có thể được lưu trên ô của hàng
 * khác (người dùng kéo ở đâu thì `columnResizing` ghi ở đó), nên đọc mỗi hàng đầu là **đánh rơi**
 * những cột ấy. `TableMap` cho biết ô nào **bắt đầu** ở cột nào — đó đúng là câu hỏi cần trả lời.
 *
 * ## ⚠ Giới hạn khai ra (luật 28)
 *
 * Bề rộng lưu theo **pixel**, đúng như `columnResizing` sinh ra. Một bảng kéo trên màn hình rộng
 * rồi đọc trên điện thoại vẫn rộng đúng chừng ấy pixel — và đó là lý do cổng công khai phải bọc
 * bảng trong khung cuộn thay vì ép `width: 100%`.
 */

/** Bề rộng cột `i`, lấy từ ô **bắt đầu** ở cột ấy — `undefined` = người dùng chưa kéo cột này. */
function beRongTungCot(node: ProseMirrorNode): (number | undefined)[] {
  const map = TableMap.get(node);
  const ra: (number | undefined)[] = new Array(map.width).fill(undefined);

  for (let hang = 0; hang < map.height; hang += 1) {
    for (let cot = 0; cot < map.width; cot += 1) {
      const o = map.map[hang * map.width + cot];
      // Ô trải qua nhiều cột/hàng xuất hiện nhiều lần trong `map`; chỉ xử lý lượt nó BẮT ĐẦU.
      const batDau =
        (cot === 0 || map.map[hang * map.width + cot - 1] !== o) &&
        (hang === 0 || map.map[(hang - 1) * map.width + cot] !== o);
      if (!batDau) {
        continue;
      }
      const nutO = node.nodeAt(o);
      const colwidth = nutO?.attrs.colwidth as number[] | null | undefined;
      if (!colwidth) {
        continue;
      }
      // `colwidth` là một mảng, một phần tử cho MỖI cột ô ấy trải qua.
      for (let j = 0; j < colwidth.length && cot + j < map.width; j += 1) {
        if (colwidth[j] && ra[cot + j] === undefined) {
          ra[cot + j] = Math.max(colwidth[j], TABLE_CELL_MIN_WIDTH_PX);
        }
      }
    }
  }
  return ra;
}

/**
 * Chỉ số **CỘT** của một ô trong DOM — ⛔ phải chỉ số con của hàng.
 *
 * ⛔⛔ Đây là khuyết tật mà dòng nợ đã **đo được**: `parseColgroupWidth` của TipTap dùng
 * `Array.from(row.children).indexOf(element)`. Với một bảng có `colspan`, hai con số ấy lệch nhau
 * từ ô gộp trở đi ⇒ các cột sau nhận bề rộng của cột khác, và bảng mất bề rộng sau đúng một vòng
 * lưu–mở (đo: 120px biến mất).
 *
 * ⚠ Phải dựng cả lưới chứ ⛔ cộng dồn `colspan` trong một hàng: một ô `rowspan` của hàng trên
 * **chiếm chỗ** ở hàng dưới, nên phép cộng dồn một hàng cho ra chỉ số lệch ở mọi hàng bên dưới ô
 * gộp dọc.
 */
function chiSoCot(o: HTMLElement): number | null {
  const hang = o.parentElement;
  const bang = o.closest('table');
  if (!hang || !bang) {
    return null;
  }
  const cacHang = Array.from(bang.querySelectorAll('tr'));
  // `chiem[h]` = tập cột đã bị một ô `rowspan` của hàng trên chiếm chỗ.
  const chiem: Set<number>[] = cacHang.map(() => new Set<number>());

  for (let h = 0; h < cacHang.length; h += 1) {
    let cot = 0;
    for (const con of Array.from(cacHang[h].children)) {
      while (chiem[h].has(cot)) {
        cot += 1;
      }
      if (con === o) {
        return cot;
      }
      const colspan = Number(con.getAttribute('colspan') ?? 1) || 1;
      const rowspan = Number(con.getAttribute('rowspan') ?? 1) || 1;
      for (let r = 1; r < rowspan; r += 1) {
        for (let c = 0; c < colspan; c += 1) {
          chiem[h + r]?.add(cot + c);
        }
      }
      cot += colspan;
    }
  }
  return null;
}

/** Bề rộng của ô, đọc từ `<col>` **đúng cột** — thay `parseColwidth` của TipTap. */
function doBeRongO(o: HTMLElement): number[] | null {
  const tho = o.getAttribute('colwidth');
  if (tho) {
    return tho
      .split(',')
      .map((w) => parseInt(w, 10))
      .filter((w) => !Number.isNaN(w));
  }
  const bang = o.closest('table');
  const cot = chiSoCot(o);
  if (!bang || cot === null) {
    return null;
  }
  const colspan = Number(o.getAttribute('colspan') ?? 1) || 1;
  const cols = bang.querySelectorAll('colgroup > col');
  const ra: number[] = [];
  for (let j = 0; j < colspan; j += 1) {
    const w = cols[cot + j]?.getAttribute('width');
    ra.push(w ? parseInt(w, 10) : 0);
  }
  return ra.some((w) => w > 0) ? ra : null;
}

/** Ghi đè đúng MỘT thuộc tính `colwidth`; phần còn lại giữ nguyên của kit. */
function voiColwidthDungCot<T extends typeof TableCell | typeof TableHeader>(nut: T) {
  return nut.extend({
    addAttributes() {
      return {
        ...this.parent?.(),
        colwidth: {
          default: null,
          parseHTML: (element: HTMLElement) => doBeRongO(element),
          // ⛔ Phát ra DOM: `Safelist.relaxed()` gỡ `colwidth` khỏi `td`/`th`, nên ghi nó vào đây
          //   là ghi một thứ ⛔ bao giờ quay về. Bề rộng đi đường `<col width>` ở `renderHTML`
          //   của bảng — MỘT nơi ghi, ⛔ hai (luật 14).
          renderHTML: () => ({}),
        },
      };
    },
  });
}

/**
 * Bảng phát `<colgroup><col width="N">` và bọc trong khung cuộn.
 *
 * ⚠ Khung cuộn nằm trong **HTML đã lưu**, ⛔ phải một sản phẩm của NodeView: cổng công khai dựng
 * HTML thô nên nó ⛔ có `.tableWrapper` mà TipTap tạo lúc soạn. Thiếu khung ấy thì cổng buộc phải
 * đặt `display: block` lên chính `<table>` — và quy tắc đó **phá vỡ ngữ cảnh định dạng bảng**, nên
 * `<colgroup>` vô tác dụng **kể cả khi sống sót qua bộ lọc**. Đó là vế thứ hai của T41.14.
 */
const BangCoBeRong = Table.extend({
  renderHTML({ node, HTMLAttributes }) {
    const beRong = beRongTungCot(node);
    const cols = beRong.map((w) => (w === undefined ? ['col', {}] : ['col', { width: String(w) }]));
    return [
      'div',
      { class: LOP_KHUNG_BANG },
      [
        'table',
        mergeAttributes(this.options.HTMLAttributes, HTMLAttributes),
        ['colgroup', {}, ...cols],
        ['tbody', 0],
      ],
    ];
  },
});

/**
 * Ba node thay cho bản của `TableKit` — dùng cùng `TableKit.configure({ table: false, cell: false,
 * header: false })`.
 *
 * ⚠ `tableRow` vẫn lấy của kit: nó ⛔ đụng gì tới bề rộng, và thay một node ⛔ cần thay là thêm
 * một chỗ phải theo dõi khi nâng TipTap.
 */
export const BANG_GIU_BE_RONG_COT = [
  BangCoBeRong.configure({ resizable: true, cellMinWidth: TABLE_CELL_MIN_WIDTH_PX }),
  voiColwidthDungCot(TableCell),
  voiColwidthDungCot(TableHeader),
];
