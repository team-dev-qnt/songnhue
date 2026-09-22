import { Node, mergeAttributes } from '@tiptap/react';

/**
 * Video **tải lên thư viện media** rồi chèn vào bài — CN-01.1, T84.14.
 *
 * <h3>⛔ Vì sao là một node THỨ HAI chứ ⛔ mở rộng `VideoEmbed`</h3>
 *
 * `videoEmbed` sở hữu thẻ `<iframe>` (video NHÚNG của YouTube/Vimeo). Node này sở hữu `<video>`
 * (tệp của chính Công ty). Hai thẻ DOM khác nhau ⇒ `parseHTML` của chúng ⛔ tranh nhau, và hai
 * `Node.create` cùng tên sẽ ném `RangeError: Adding different instances of a keyed plugin` —
 * đúng lỗi đã chặn phương án "cứu bề rộng cột" ở WS-41 (Q1).
 *
 * <p>Chúng cũng khác nhau ở chốt chặn backend: `<iframe>` lọc theo **TÊN MIỀN**
 * (`MIEN_NHUNG_VIDEO`), `<video>` lọc theo **TIỀN TỐ ĐƯỜNG DẪN** (`/api/v1/public/videos/`).
 * Gộp làm một là phải nhớ hai luật trong một chỗ.
 *
 * <h3>⛔ KHÔNG có `addNodeView`</h3>
 *
 * Giống `VideoEmbed`: TipTap dựng thẳng `renderHTML` vào DOM soạn thảo, nên người soạn thấy một
 * `<video>` **thật, phát được** ngay trong khung — ⛔ phải một ô giữ chỗ. ⚠ Đó cũng chính là lý do
 * CSP của **admin** phải khai `media-src`: khung soạn thảo tải video y như cổng.
 *
 * <h3>Vì sao KHÔNG có `poster`</h3>
 *
 * ⛔ Có gì sinh ra ảnh đại diện cho video — luật 15: một thuộc tính LUÔN rỗng bày ra giao diện một
 * lời hứa ⛔ có nguồn. Mở lại cùng lúc dựng bộ sinh ảnh đại diện, ⛔ trước.
 */

declare module '@tiptap/react' {
  interface Commands<ReturnType> {
    videoTep: {
      /** Chèn một video đã có trong thư viện. Trả `false` khi `publicId` rỗng. */
      insertVideoTep: (publicId: string) => ReturnType;
    };
  }
}

/**
 * Đường dẫn ỔN ĐỊNH của một video — ⛔ phải presigned URL.
 *
 * ⚠⚠ HTML của bài viết sống hàng THÁNG, presigned URL sống một GIỜ. Nhúng thẳng URL ký sẵn là mọi
 * video chết sau `TTL_VIDEO` — đúng bài học §10.1 đã trả giá cho ảnh. Endpoint `/public/videos/{id}`
 * đúc URL mới ở **mỗi lượt xem** rồi trả 302.
 *
 * ⛔ Chuỗi này phải khớp `HtmlSanitizer.TIEN_TO_VIDEO_NOI_BO` — lệch một ký tự là bộ lọc gỡ cả thẻ
 * và video biến mất ở lượt Lưu, ⛔ một dòng lỗi (`videoTep.test.ts` canh hai bên).
 */
export const DUONG_VIDEO = '/api/v1/public/videos/';

export const VideoTep = Node.create({
  name: 'videoTep',
  group: 'block',
  atom: true,
  draggable: true,

  addAttributes() {
    return { src: { default: null } };
  },

  parseHTML() {
    // ⚠ BẮT BUỘC: `editorRoundTrip.test.ts` đòi mọi thẻ khai trong `EDITOR_TAGS` đọc ngược được vào
    //   cây TipTap. Thiếu nó thì mở lại một bài đã lưu là video biến mất khỏi khung soạn thảo, và
    //   cú Lưu kế tiếp xoá nó khỏi CSDL.
    return [{ tag: 'video[src]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'video',
      mergeAttributes(HTMLAttributes, {
        // `atom: true` ⇒ ⛔ có giao diện nào khác để bấm phát.
        controls: 'true',
        // ⛔ `preload="auto"`: ba video trong một bài là ba tệp ĐẦY ĐỦ được kéo về lúc mở trang.
        preload: 'metadata',
        // ⚠ Thiếu nó thì iOS ép video chạy TOÀN MÀN HÌNH — một bài tin mở ra là một video chiếm
        //   hết màn hình điện thoại. Safelist của backend gỡ thuộc tính ⛔ khai, nên nó phải có ở
        //   cả hai phía (`HtmlSanitizer` khai `playsinline`).
        playsinline: 'true',
        // ⛔ `autoplay`/`loop`/`muted` — cùng lý lẽ `VideoEmbed` từ chối `autoplay` trong `allow`:
        //    video tự chạy giữa bài tin là thứ người đọc phải đi tìm nút tắt, thường trong giờ làm.
      }),
    ];
  },

  addCommands() {
    return {
      insertVideoTep:
        (publicId: string) =>
        ({ commands }) => {
          // ⛔ Trả `false` chứ ⛔ chèn một thẻ ⛔ nguồn: nơi gọi báo được cho người dùng thay vì
          //    giả vờ đã làm, và một `<video>` ⛔ src là một ô đen giữa bài trông y hệt lỗi tải.
          if (!publicId) {
            return false;
          }
          return commands.insertContent({
            type: this.name,
            attrs: { src: `${DUONG_VIDEO}${publicId}` },
          });
        },
    };
  },
});
