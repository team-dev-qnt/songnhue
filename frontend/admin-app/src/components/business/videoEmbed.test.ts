import { describe, expect, it } from 'vitest';

import { toEmbedUrl } from './VideoEmbed';

/**
 * Chuẩn hoá đường dẫn video.
 *
 * ⚠ Mỗi bài khẳng định **cả hai vế** khi có thể: đường hợp lệ ra đúng dạng nhúng, và đường
 * không hợp lệ trả `null` — chứ không phải lọt qua rồi bị backend gỡ lặng lẽ lúc lưu. Đó là
 * khác biệt giữa "người dùng biết ngay" và "video biến mất sau khi bấm Lưu".
 */
describe('toEmbedUrl', () => {
  it('đổi đường xem YouTube sang đường nhúng không đặt cookie theo dõi', () => {
    expect(toEmbedUrl('https://www.youtube.com/watch?v=dQw4w9WgXcQ')).toBe(
      'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ',
    );
  });

  it('nhận cả đường rút gọn youtu.be', () => {
    expect(toEmbedUrl('https://youtu.be/abc123')).toBe(
      'https://www.youtube-nocookie.com/embed/abc123',
    );
  });

  it('đường nhúng sẵn vẫn được chuẩn hoá về bản nocookie', () => {
    expect(toEmbedUrl('https://www.youtube.com/embed/abc123')).toBe(
      'https://www.youtube-nocookie.com/embed/abc123',
    );
  });

  it('Vimeo: đổi đường xem sang player.vimeo.com', () => {
    expect(toEmbedUrl('https://vimeo.com/123456789')).toBe(
      'https://player.vimeo.com/video/123456789',
    );
  });

  it('⛔ http:// bị từ chối ngay — backend chỉ nhận https, để lọt là mất video lúc lưu', () => {
    expect(toEmbedUrl('http://www.youtube.com/watch?v=abc')).toBeNull();
  });

  it('⛔ nhà cung cấp không nằm trong danh sách thì trả null', () => {
    expect(toEmbedUrl('https://video.example.com/xem/123')).toBeNull();
  });

  it('⛔ tên miền giả dạng hậu tố không lọt', () => {
    expect(toEmbedUrl('https://evilyoutube.com/watch?v=abc')).toBeNull();
    expect(toEmbedUrl('https://vimeo.com.kegian.net/123')).toBeNull();
  });

  it('⛔ chuỗi không phải URL, chuỗi rỗng, và YouTube thiếu mã video', () => {
    expect(toEmbedUrl('không phải đường dẫn')).toBeNull();
    expect(toEmbedUrl('   ')).toBeNull();
    expect(toEmbedUrl('https://www.youtube.com/')).toBeNull();
  });

  it('Vimeo có mã không phải số thì từ chối — đường /channels/… không nhúng được', () => {
    expect(toEmbedUrl('https://vimeo.com/channels/staffpicks')).toBeNull();
  });
});
