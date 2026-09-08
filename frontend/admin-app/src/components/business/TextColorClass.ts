import { Mark, mergeAttributes } from '@tiptap/react';
import { TEXT_BG_CLASSES, TEXT_COLOR_CLASSES } from 'design-tokens/editor-schema';

/**
 * Màu chữ và màu nền chữ bằng **class** — T41.15, yêu cầu ĐÃ KÝ (đặc tả dòng 92).
 *
 * <h3>Vì sao không dùng `@tiptap/extension-color` + `@tiptap/extension-highlight`</h3>
 *
 * Cả hai phát ra `style="color: …"` / `style="background-color: …"`, mà `HtmlSanitizer` của backend
 * **gỡ sạch `style`**. Hậu quả y hệt lỗi căn lề đã trả giá ở T20.1: người soạn bấm màu, thấy màu
 * hiện lên trong trình soạn thảo, bấm Lưu, hệ thống báo thành công — và bài lên cổng **đen trắng**.
 *
 * Ở đây lý do cấm `style` còn nặng hơn căn lề: `color:#fff` trên nền trắng là **chữ tàng hình**.
 * Một bài đã qua duyệt có thể mang một đoạn mà người duyệt ⛔ không nhìn thấy trên màn hình, trong
 * khi công cụ tìm kiếm vẫn đọc được.
 *
 * <h3>Một mark mang HAI thuộc tính, ⛔ không phải hai mark</h3>
 *
 * Chữ vừa đỏ vừa bôi vàng là một yêu cầu bình thường. Hai mark riêng thì ProseMirror sinh
 * `<span class="sn-fg-do"><span class="sn-bg-vang">…</span></span>` — lồng nhau, và mỗi lượt
 * lưu–mở lại thêm một tầng. Một mark hai thuộc tính cho ra đúng một `<span>` với hai class.
 *
 * <h3>⚠ `parseHTML` phải TỪ CHỐI span không mang màu</h3>
 *
 * `{ tag: 'span' }` trần sẽ nhận **mọi** `<span>`, kể cả `sn-nho` của chú thích nhỏ — và biến nó
 * thành một mark màu rỗng, tức là mọi span khác trong kho nội dung cũ bị nuốt. `getAttrs` trả
 * `false` là cách ProseMirror hiểu *"quy tắc này không áp cho thẻ ấy"*.
 */

export type TextColorClass = (typeof TEXT_COLOR_CLASSES)[number];
export type TextBgClass = (typeof TEXT_BG_CLASSES)[number];

const MAU_CHU: readonly string[] = TEXT_COLOR_CLASSES;
const MAU_NEN: readonly string[] = TEXT_BG_CLASSES;

const TEN = 'textColorClass';

function timClass(element: HTMLElement, trong: readonly string[]): string | null {
  for (const className of Array.from(element.classList)) {
    if (trong.includes(className)) {
      return className;
    }
  }
  return null;
}

declare module '@tiptap/react' {
  interface Commands<ReturnType> {
    textColorClass: {
      /** `null` = bỏ màu chữ. Bỏ cả hai thuộc tính thì mark bị gỡ hẳn. */
      setTextColor: (value: TextColorClass | null) => ReturnType;
      setTextBg: (value: TextBgClass | null) => ReturnType;
    };
  }
}

export const TextColorClass = Mark.create({
  name: TEN,

  addAttributes() {
    return {
      // ⚠ `renderHTML` của TỪNG thuộc tính trả rỗng: cả hai gộp lại thành một chuỗi `class` duy
      //   nhất ở `renderHTML` của mark. Để mỗi cái tự trả `{ class: … }` thì `mergeAttributes` nối
      //   chúng bằng dấu cách ở một số bản và ghi đè nhau ở bản khác — hành vi ⛔ không đáng phụ thuộc.
      fg: {
        default: null as string | null,
        parseHTML: (element: HTMLElement) => timClass(element, MAU_CHU),
        renderHTML: () => ({}),
      },
      bg: {
        default: null as string | null,
        parseHTML: (element: HTMLElement) => timClass(element, MAU_NEN),
        renderHTML: () => ({}),
      },
    };
  },

  parseHTML() {
    return [
      {
        tag: 'span',
        getAttrs: (element) => {
          const el = element as HTMLElement;
          const fg = timClass(el, MAU_CHU);
          const bg = timClass(el, MAU_NEN);
          // ⛔ `false` chứ không phải `{}`: span không mang màu phải để quy tắc khác xử lý.
          return fg === null && bg === null ? false : { fg, bg };
        },
      },
    ];
  },

  renderHTML({ HTMLAttributes, mark }) {
    const lop = [mark.attrs.fg, mark.attrs.bg].filter(Boolean).join(' ');
    return ['span', mergeAttributes(HTMLAttributes, lop ? { class: lop } : {}), 0];
  },

  addCommands() {
    return {
      setTextColor:
        (value: TextColorClass | null) =>
        ({ commands, editor }) => {
          const bg = (editor.getAttributes(TEN).bg as string | null) ?? null;
          // Không còn màu nào thì gỡ hẳn mark — để lại một `<span>` rỗng là rác tích tụ qua mỗi
          // lượt sửa, và bộ khử trùng ⛔ không dọn hộ (`span` nằm trong danh sách cho phép).
          return value === null && bg === null
            ? commands.unsetMark(TEN)
            : commands.setMark(TEN, { fg: value, bg });
        },
      setTextBg:
        (value: TextBgClass | null) =>
        ({ commands, editor }) => {
          const fg = (editor.getAttributes(TEN).fg as string | null) ?? null;
          return value === null && fg === null
            ? commands.unsetMark(TEN)
            : commands.setMark(TEN, { fg, bg: value });
        },
    };
  },
});
