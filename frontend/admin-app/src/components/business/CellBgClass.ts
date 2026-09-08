import { Extension } from '@tiptap/react';
import { CELL_BG_CLASSES } from 'design-tokens/editor-schema';

/**
 * Màu nền ô bảng bằng **class** — T41.15, yêu cầu ĐÃ KÝ (đặc tả dòng 98).
 *
 * <h3>⚠ Đây là thuộc tính của NÚT, ⛔ không phải một mark trên chữ</h3>
 *
 * Đặt nhầm tầng là lỗi im lặng: một mark bọc chữ bên trong ô sẽ cho ra
 * `<td><span class="sn-cell-vang">Nội dung</span></td>` — chữ có nền, **ô vẫn trắng**, và phần ô
 * trống bên phải chữ lộ ra ngay. Nên nó phải là {@code addGlobalAttributes} trên
 * {@code tableCell}/{@code tableHeader}.
 *
 * <h3>Vì sao dùng `setCellAttribute` chứ không `updateAttributes`</h3>
 *
 * Người dùng tô màu bằng cách <b>quét nhiều ô</b>. Vùng chọn khi ấy là `CellSelection` của
 * prosemirror-tables, mà `updateAttributes('tableCell', …)` chỉ chạm nút đang ở con trỏ ⇒ tô một
 * hàng thì chỉ ô đầu đổi màu, các ô còn lại đứng yên. `setCellAttribute` do chính extension bảng
 * cung cấp và nó duyệt đúng tập ô đang chọn.
 *
 * ⚠ Ghi chú cũ ở T41.15 nói *"`setCellAttribute` phát `style` ⇒ mất lúc lưu"*. Chính xác hơn:
 * `setCellAttribute` ghi vào một **thuộc tính của nút**, còn thuộc tính ấy render ra gì là do nơi
 * khai nó quyết định. Bản gốc của TipTap khai `backgroundColor` render thành `style`; bản dưới đây
 * khai `cellBg` render thành `class` — cùng một lệnh, khác kết quả trên dây.
 */

export type CellBgClass = (typeof CELL_BG_CLASSES)[number];

const NEN_O: readonly string[] = CELL_BG_CLASSES;

/** ⚠ Tên nút của prosemirror-tables — sai tên thì `addGlobalAttributes` bị bỏ qua **lặng lẽ**. */
const NHOM_AP_DUNG = ['tableCell', 'tableHeader'] as const;

/** Xuất ra để bài kiểm đối chiếu với schema thật — khuôn `ALIGN_TYPES` của `AlignClass`. */
export const CELL_BG_TYPES: readonly string[] = NHOM_AP_DUNG;

declare module '@tiptap/react' {
  interface Commands<ReturnType> {
    cellBgClass: {
      /** `null` = bỏ màu nền ô. */
      setCellBg: (value: CellBgClass | null) => ReturnType;
    };
  }
}

export const CellBgClass = Extension.create({
  name: 'cellBgClass',

  addGlobalAttributes() {
    return [
      {
        types: [...NHOM_AP_DUNG],
        attributes: {
          cellBg: {
            default: null as string | null,

            /**
             * Đọc lại khi mở bài cũ.
             *
             * ⚠ Phải **giữ nguyên các class khác** — ô có thể mang class do nơi khác đặt, và một
             * lượt ghi đè `class` là mất định dạng mà người dùng ⛔ không hiểu vì sao (cùng bài học
             * `AlignClass`).
             */
            parseHTML: (element: HTMLElement): string | null => {
              for (const className of Array.from(element.classList)) {
                if (NEN_O.includes(className)) {
                  return className;
                }
              }
              return null;
            },

            renderHTML: (attributes: Record<string, unknown>) => {
              const value = attributes.cellBg as string | null;
              return value ? { class: value } : {};
            },
          },
        },
      },
    ];
  },

  addCommands() {
    return {
      setCellBg:
        (value: CellBgClass | null) =>
        ({ commands }) =>
          commands.setCellAttribute('cellBg', value),
    };
  },
});
