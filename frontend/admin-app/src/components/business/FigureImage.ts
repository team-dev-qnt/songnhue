import { Node, mergeAttributes } from '@tiptap/react';

/**
 * Ảnh trong bài **kèm chú thích** — CN-01.1 yêu cầu "ảnh inline (căn lề, caption)".
 *
 * <h3>Vì sao viết riêng thay vì dùng `@tiptap/extension-image`</h3>
 *
 * Bản gốc phát ra một thẻ `<img>` trần. Chú thích ảnh khi đó chỉ còn hai đường: nhét vào
 * `alt` (trình duyệt không hiển thị, và `alt` là mô tả cho người khiếm thị — dùng sai chỗ),
 * hoặc để người soạn tự gõ một đoạn văn bên dưới (không dính với ảnh, kéo ảnh đi chỗ khác
 * là chú thích ở lại).
 *
 * `<figure>` + `<figcaption>` là cặp thẻ HTML sinh ra đúng cho việc này, và **cả hai đã nằm
 * trong danh sách cho phép của `HtmlSanitizer`** — nên đường này đi được tới cổng công khai.
 *
 * <h3>Ảnh trỏ vào đâu</h3>
 *
 * `src` là đường dẫn **ổn định vĩnh viễn** `/api/v1/public/files/{publicId}`, không phải
 * presigned URL. Trang trên cổng dựng sẵn và sống hàng giờ; một URL hết hạn sau 10 phút sẽ
 * làm vỡ ảnh trên đúng những trang được xem nhiều nhất.
 */

export interface FigureImageAttrs {
  src: string;
  alt: string | null;
  caption: string | null;
}

declare module '@tiptap/react' {
  interface Commands<ReturnType> {
    figureImage: {
      insertFigureImage: (attrs: FigureImageAttrs) => ReturnType;
    };
  }
}

export const FigureImage = Node.create({
  name: 'figureImage',
  group: 'block',
  // Nút nguyên khối: con trỏ không đi vào trong. Chú thích sửa bằng ô nhập riêng, vì cho
  // gõ trực tiếp vào figcaption kéo theo cả bộ xử lý phím mà giá trị mang lại không đáng.
  atom: true,
  draggable: true,

  addAttributes() {
    return {
      src: { default: null },
      alt: { default: null },
      caption: { default: null },
    };
  },

  parseHTML() {
    return [
      {
        tag: 'figure',
        getAttrs: (element) => {
          const img = (element as HTMLElement).querySelector('img');
          if (!img) {
            // Trả `false` = "thẻ này không phải của tôi", để TipTap thử quy tắc khác thay vì
            // nuốt mất một `<figure>` chứa thứ khác (ví dụ khối nhúng video).
            return false;
          }
          return {
            src: img.getAttribute('src'),
            alt: img.getAttribute('alt'),
            caption: (element as HTMLElement).querySelector('figcaption')?.textContent ?? null,
          };
        },
      },
    ];
  },

  renderHTML({ HTMLAttributes, node }) {
    const { src, alt, caption } = node.attrs as FigureImageAttrs;
    const children: unknown[] = [['img', { src, alt: alt ?? '', loading: 'lazy' }]];
    if (caption && caption.trim().length > 0) {
      children.push(['figcaption', {}, caption]);
    }
    // `mergeAttributes` giữ lại class căn lề do `AlignClass` gắn vào.
    return ['figure', mergeAttributes(HTMLAttributes), ...children] as never;
  },

  addCommands() {
    return {
      insertFigureImage:
        (attrs: FigureImageAttrs) =>
        ({ commands }) =>
          commands.insertContent({ type: this.name, attrs }),
    };
  },
});
