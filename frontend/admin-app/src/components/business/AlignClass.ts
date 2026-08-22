import { Extension, isNodeActive } from '@tiptap/react';
import { ALIGN_CLASSES } from 'design-tokens/editor-schema';

/**
 * Căn lề bằng **class**, không bằng thuộc tính `style` — T20.1.
 *
 * <h3>Vì sao không dùng `@tiptap/extension-text-align` nguyên bản</h3>
 *
 * Bản gốc phát ra `style="text-align: center"`. `HtmlSanitizer` của backend **không cho
 * `style` đi qua**, và đó là lựa chọn đúng chứ không phải thiếu sót: `style` mở đường cho
 * `position: fixed` phủ kín trang, hoặc chữ trắng trên nền trắng để giấu nội dung trong
 * một bài đã được duyệt.
 *
 * Nên nếu dùng bản gốc thì hậu quả là: người soạn bấm "căn giữa", nhìn thấy nó căn giữa
 * trong trình soạn thảo, bấm Lưu, hệ thống báo thành công — và bài lên cổng vẫn căn trái.
 *
 * <h3>⚠⚠ Tên nút phải là tên THẬT trong schema — bản đầu sai đúng chỗ này</h3>
 *
 * Danh sách này từng ghi `'image'` và `'figure'`. Cả hai **không tồn tại**: nút ảnh do
 * `FigureImage` đăng ký mang tên `figureImage`. Hậu quả gồm hai tầng, và tầng nào cũng im:
 *
 * 1. `addGlobalAttributes` cho một type không có trong schema thì bị **bỏ qua lặng lẽ** —
 *    nút ảnh không hề có thuộc tính `align`, nên dù lệnh có chạy cũng không có chỗ để ghi.
 * 2. Bản cũ trả về `NHOM_AP_DUNG.some(type => commands.updateAttributes(type, …))`.
 *    `.some` **dừng ở phần tử đầu tiên trả `true`**, mà `'paragraph'` luôn trả `true` kể cả
 *    khi vùng chọn không có đoạn văn nào. Lệnh báo thành công, nút sáng lên, ảnh đứng yên.
 *
 * Nay áp đúng **một** nút — nút đang được chọn — và trả `false` khi không có nút nào hợp lệ,
 * để nơi gọi báo được cho người dùng thay vì giả vờ đã làm. `alignClass.test.ts` đối chiếu
 * từng tên trong danh sách với schema thật; sai tên là **CI đỏ**, không phải một nút vô tác
 * dụng nằm im tới khi có người dùng thật bấm vào.
 */

/**
 * Thứ tự **có ý nghĩa**: nút cấp khối đứng trước.
 *
 * Khi người dùng chọn một tấm ảnh, vùng chọn là `NodeSelection` trỏ vào chính nút ảnh —
 * nhưng nút ảnh nằm trong tài liệu cùng với các đoạn văn xung quanh. Duyệt từ `paragraph`
 * trở xuống thì đoạn văn kế bên có thể khớp trước và ăn mất lệnh.
 */
const NHOM_AP_DUNG = ['figureImage', 'videoEmbed', 'heading', 'paragraph'] as const;

/** Xuất ra để `alignClass.test.ts` đối chiếu với schema — xem lý do ở phần tài liệu trên. */
export const ALIGN_TYPES: readonly string[] = NHOM_AP_DUNG;

export type AlignValue = 'left' | 'center' | 'right';

const CLASS_THEO_GIA_TRI: Record<AlignValue, string> = {
  left: ALIGN_CLASSES[0],
  center: ALIGN_CLASSES[1],
  right: ALIGN_CLASSES[2],
};

/** Ánh xạ ngược, để đọc lại được class đã ghi trong nội dung cũ. */
const GIA_TRI_THEO_CLASS = new Map<string, AlignValue>(
  (Object.keys(CLASS_THEO_GIA_TRI) as AlignValue[]).map((value) => [
    CLASS_THEO_GIA_TRI[value],
    value,
  ]),
);

declare module '@tiptap/react' {
  interface Commands<ReturnType> {
    alignClass: {
      setAlign: (value: AlignValue) => ReturnType;
      unsetAlign: () => ReturnType;
    };
  }
}

export const AlignClass = Extension.create({
  name: 'alignClass',

  addGlobalAttributes() {
    return [
      {
        types: [...NHOM_AP_DUNG],
        attributes: {
          align: {
            default: null as AlignValue | null,

            /**
             * Đọc lại khi mở bài cũ.
             *
             * ⚠ Phải đọc từ `class`, và phải **giữ nguyên các class khác** khi ghi lại —
             * nội dung cũ có thể mang class do nơi khác đặt (bề ngang ảnh chẳng hạn), xoá
             * sạch là mất định dạng mà người dùng không hiểu vì sao.
             */
            parseHTML: (element: HTMLElement): AlignValue | null => {
              for (const className of Array.from(element.classList)) {
                const found = GIA_TRI_THEO_CLASS.get(className);
                if (found) {
                  return found;
                }
              }
              return null;
            },

            renderHTML: (attributes: Record<string, unknown>) => {
              const value = attributes.align as AlignValue | null;
              if (!value) {
                return {};
              }
              return { class: CLASS_THEO_GIA_TRI[value] };
            },
          },
        },
      },
    ];
  },

  addCommands() {
    return {
      setAlign:
        (value: AlignValue) =>
        ({ state, commands }) => {
          const type = NHOM_AP_DUNG.find((name) => isNodeActive(state, name));
          return type !== undefined && commands.updateAttributes(type, { align: value });
        },
      unsetAlign:
        () =>
        ({ state, commands }) => {
          const type = NHOM_AP_DUNG.find((name) => isNodeActive(state, name));
          return type !== undefined && commands.resetAttributes(type, 'align');
        },
    };
  },
});
