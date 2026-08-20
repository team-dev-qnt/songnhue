import { Extension } from '@tiptap/react';

import { ALIGN_CLASSES } from './editorSchema';

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
 * Không lỗi, không cảnh báo. Đây đúng là loại hỏng mà `EditorVocabularyTest` sinh ra để
 * chặn, chỉ khác là nó xảy ra ở thuộc tính chứ không ở thẻ.
 *
 * `class` thì `HtmlSanitizer` cho qua trên mọi thẻ, nên đường này đi được — với điều kiện
 * cổng công khai có định nghĩa ba class đó trong CSS.
 */

const NHOM_AP_DUNG = ['paragraph', 'heading', 'image', 'figure'];

export type AlignValue = 'left' | 'center' | 'right';

const CLASS_THEO_GIA_TRI: Record<AlignValue, string> = {
  left: ALIGN_CLASSES[0],
  center: ALIGN_CLASSES[1],
  right: ALIGN_CLASSES[2],
};

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
        types: NHOM_AP_DUNG,
        attributes: {
          align: {
            default: null as AlignValue | null,

            /**
             * Đọc lại khi mở bài cũ.
             *
             * ⚠ Phải đọc từ `class`, và phải **giữ nguyên các class khác** khi ghi lại —
             * nội dung cũ có thể mang class do nơi khác đặt, xoá sạch là mất định dạng mà
             * người dùng không hiểu vì sao.
             */
            parseHTML: (element: HTMLElement): AlignValue | null => {
              const found = (Object.keys(CLASS_THEO_GIA_TRI) as AlignValue[]).find((value) =>
                element.classList.contains(CLASS_THEO_GIA_TRI[value]),
              );
              return found ?? null;
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
        ({ commands }) =>
          NHOM_AP_DUNG.some((type) => commands.updateAttributes(type, { align: value })),
      unsetAlign:
        () =>
        ({ commands }) =>
          NHOM_AP_DUNG.every((type) => commands.resetAttributes(type, 'align')),
    };
  },
});
