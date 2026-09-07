import { type Node as ProseMirrorNode } from '@tiptap/pm/model';
import { Node, mergeAttributes } from '@tiptap/react';
import { IMAGE_WIDTH_CLASSES } from 'design-tokens/editor-schema';

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

export type ImageWidth = (typeof IMAGE_WIDTH_CLASSES)[number];

export interface FigureImageAttrs {
  src: string;
  alt: string | null;
  caption: string | null;
  width: ImageWidth | null;
}

/**
 * Thuộc tính chỉ sống trong lúc soạn, **không bao giờ được đi vào HTML lưu xuống**.
 *
 * ⚠ `src` của một ảnh đang tải là `blob:` — địa chỉ chỉ có nghĩa trong đúng tab đang mở.
 * Lưu nó xuống thì `HtmlSanitizer` gỡ thuộc tính `src` (giao thức `blob:` không có trong
 * danh sách cho phép) và bài viết còn lại **một thẻ ảnh rỗng, không lời giải thích nào**.
 * Nên `RichTextEditor` báo số ảnh đang tải ra ngoài và màn hình soạn bài **khoá nút Lưu**.
 */
interface TransientAttrs {
  /** Khoá để tìm lại đúng nút này khi lượt tải xong — vị trí trong tài liệu đã đổi mất rồi. */
  uploadId: string | null;
  uploading: boolean;
}

declare module '@tiptap/react' {
  interface Commands<ReturnType> {
    figureImage: {
      insertFigureImage: (
        attrs: Partial<FigureImageAttrs & TransientAttrs> & { src: string },
        pos?: number,
      ) => ReturnType;
      /** Đặt lại thuộc tính cho **đúng một** nút tìm theo `uploadId`. */
      resolveFigureUpload: (uploadId: string, attrs: Partial<FigureImageAttrs>) => ReturnType;
      /** Gỡ nút ảnh có `uploadId` này — dùng khi lượt tải hỏng. */
      dropFigureUpload: (uploadId: string) => ReturnType;
    };
  }
}

export const FigureImage = Node.create({
  name: 'figureImage',
  group: 'block',
  // Nút nguyên khối: con trỏ không đi vào trong. Chú thích sửa bằng ô nhập riêng trên thanh
  // công cụ, vì cho gõ trực tiếp vào figcaption kéo theo cả bộ xử lý phím mà giá trị mang
  // lại không đáng.
  atom: true,
  draggable: true,

  addAttributes() {
    return {
      src: { default: null },
      alt: { default: null },
      caption: { default: null },
      width: {
        default: null,
        parseHTML: (element: HTMLElement) =>
          IMAGE_WIDTH_CLASSES.find((c) => element.classList.contains(c)) ?? null,
        renderHTML: (attributes: Record<string, unknown>) => {
          const value = attributes.width as ImageWidth | null;
          return value ? { class: value } : {};
        },
      },
      uploadId: { default: null, renderHTML: () => ({}) },
      /**
       * ⚠ Phải ra tới DOM thì CSS mới tô được ô giữ chỗ — `renderHTML` dùng chung cho cả DOM
       * của trình soạn thảo lẫn chuỗi `getHTML()`, không tách được hai đường.
       *
       * Nên chọn `data-*` chứ **không** chọn class: nếu vì lý do nào đó một bản nháp đang tải
       * dở vẫn lọt xuống backend, `HtmlSanitizer` gỡ mọi thuộc tính ngoài danh sách — trong
       * khi `class` thì nó cho qua trên mọi thẻ. Đây là lớp chặn thứ hai sau việc khoá nút Lưu.
       */
      uploading: {
        default: false,
        renderHTML: (attributes: Record<string, unknown>) =>
          attributes.uploading ? { 'data-uploading': 'true' } : {},
      },
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
    // `mergeAttributes` giữ lại class căn lề (`AlignClass`) và class bề ngang (ở trên).
    return ['figure', mergeAttributes(HTMLAttributes), ...children] as never;
  },

  addCommands() {
    return {
      insertFigureImage:
        (attrs, pos) =>
        ({ commands }) =>
          // `insertContentAt` với vị trí cụ thể phục vụ kéo-thả: người dùng thả ảnh xuống
          // đâu thì ảnh nằm đúng đó, chứ không nhảy về chỗ con trỏ đang đứng trước đó.
          pos === undefined
            ? commands.insertContent({ type: this.name, attrs })
            : commands.insertContentAt(pos, { type: this.name, attrs }),

      resolveFigureUpload:
        (uploadId, attrs) =>
        ({ tr, state, dispatch }) => {
          const found = timTheoUploadId(state.doc, uploadId);
          if (found === null) {
            // Người dùng đã xoá ô giữ chỗ trong lúc chờ tải. Không phải lỗi — chỉ là không
            // còn gì để cập nhật.
            return false;
          }
          if (dispatch) {
            tr.setNodeMarkup(found.pos, undefined, {
              ...found.attrs,
              ...attrs,
              uploadId: null,
              uploading: false,
            });
          }
          return true;
        },

      dropFigureUpload:
        (uploadId) =>
        ({ tr, state, dispatch }) => {
          const found = timTheoUploadId(state.doc, uploadId);
          if (found === null) {
            return false;
          }
          if (dispatch) {
            tr.delete(found.pos, found.pos + found.nodeSize);
          }
          return true;
        },
    };
  },
});

/**
 * Tìm nút ảnh mang `uploadId` này.
 *
 * ⚠ Phải quét lại tài liệu chứ **không** dùng lại vị trí lúc chèn: giữa lúc chèn ô giữ chỗ và
 * lúc lượt tải xong, người dùng vẫn gõ tiếp — mọi ký tự phía trên đều làm vị trí trôi đi. Ghi
 * nhớ một con số vị trí là cách chắc chắn để **ghi đè nhầm vào một nút khác**.
 */
function timTheoUploadId(doc: ProseMirrorNode, uploadId: string): ViTriNut | null {
  let found: ViTriNut | null = null;
  doc.descendants((node, pos) => {
    if (found !== null) {
      return false;
    }
    if (node.type.name === 'figureImage' && node.attrs.uploadId === uploadId) {
      found = { pos, nodeSize: node.nodeSize, attrs: node.attrs };
    }
    return true;
  });
  return found;
}

interface ViTriNut {
  pos: number;
  nodeSize: number;
  attrs: Record<string, unknown>;
}
