import { Node, mergeAttributes } from '@tiptap/react';

/**
 * Nhúng video YouTube/Vimeo — CN-01.1.
 *
 * <h3>Chuẩn hoá đường dẫn ngay tại chỗ dán</h3>
 *
 * Người soạn sẽ dán đường dẫn họ đang nhìn trên trình duyệt — `youtube.com/watch?v=…` hoặc
 * `youtu.be/…` — chứ không phải đường nhúng. Dán nguyên như vậy vào `<iframe>` thì YouTube
 * trả về trang xem đầy đủ kèm thanh điều hướng và phần gợi ý video, nằm giữa bài của cơ
 * quan nhà nước.
 *
 * Nên đổi sang dạng nhúng **ở đây**, lúc dán, chứ không phải để backend đoán. Backend chỉ
 * kiểm tên miền; nó không có nhiệm vụ hiểu cấu trúc URL của từng nhà cung cấp.
 *
 * ⚠ Chọn `youtube-nocookie.com`: bản thường đặt cookie theo dõi ngay khi trang tải, kể cả
 * khi người đọc không bấm phát. Với cổng thông tin của cơ quan nhà nước thì đó là chuyện
 * không nên có, và người đọc không có cách nào từ chối.
 */

declare module '@tiptap/react' {
  interface Commands<ReturnType> {
    videoEmbed: {
      insertVideo: (url: string) => ReturnType;
    };
  }
}

/**
 * Đổi một đường dẫn người dùng dán sang đường nhúng.
 *
 * @returns `null` khi không nhận ra nhà cung cấp — nơi gọi báo lỗi ngay thay vì chèn một
 *   khung mà backend sẽ lặng lẽ gỡ lúc lưu
 */
export function toEmbedUrl(raw: string): string | null {
  const text = raw.trim();
  if (text.length === 0) {
    return null;
  }
  let url: URL;
  try {
    url = new URL(text);
  } catch {
    return null;
  }
  if (url.protocol !== 'https:') {
    // http:// bị chặn ở backend (safelist chỉ nhận https). Từ chối tại đây để người dùng
    // biết ngay, thay vì lưu xong mới thấy video biến mất.
    return null;
  }

  const host = url.hostname.toLowerCase().replace(/^www\./, '');

  if (host === 'youtu.be') {
    const id = url.pathname.slice(1);
    return id ? `https://www.youtube-nocookie.com/embed/${id}` : null;
  }
  if (host === 'youtube.com' || host === 'youtube-nocookie.com') {
    if (url.pathname.startsWith('/embed/')) {
      return `https://www.youtube-nocookie.com${url.pathname}`;
    }
    const id = url.searchParams.get('v');
    return id ? `https://www.youtube-nocookie.com/embed/${id}` : null;
  }
  if (host === 'vimeo.com') {
    const id = url.pathname.split('/').filter(Boolean)[0];
    return id && /^\d+$/.test(id) ? `https://player.vimeo.com/video/${id}` : null;
  }
  if (host === 'player.vimeo.com') {
    return url.pathname.startsWith('/video/') ? `https://player.vimeo.com${url.pathname}` : null;
  }
  return null;
}

export const VideoEmbed = Node.create({
  name: 'videoEmbed',
  group: 'block',
  atom: true,
  draggable: true,

  addAttributes() {
    return { src: { default: null } };
  },

  parseHTML() {
    return [{ tag: 'iframe[src]' }];
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'iframe',
      mergeAttributes(HTMLAttributes, {
        width: '560',
        height: '315',
        loading: 'lazy',
        allowfullscreen: 'true',
        // Cố ý KHÔNG có `autoplay` trong `allow`: video tự chạy giữa một bài tin là thứ
        // người đọc phải đi tìm nút tắt, thường trong lúc đang ở nơi làm việc.
        allow: 'accelerometer; clipboard-write; encrypted-media; picture-in-picture',
      }),
    ];
  },

  addCommands() {
    return {
      insertVideo:
        (url: string) =>
        ({ commands }) => {
          const src = toEmbedUrl(url);
          if (!src) {
            return false;
          }
          return commands.insertContent({ type: this.name, attrs: { src } });
        },
    };
  },
});
