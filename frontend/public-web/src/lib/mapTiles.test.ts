import { describe, expect, it } from 'vitest';

import { dungCsp } from './csp';
import { TILE_ATTRIBUTION, TILE_HOST, TILE_URL } from './mapTiles';

/**
 * **Nguồn ô bản đồ phải nằm trong `img-src` của CSP.**
 *
 * <h2>Vì sao đây là một cặp phải nhớ ở hai nơi (luật 14)</h2>
 *
 * URL ô nằm trong mã component, danh sách host nằm trong CSP. Không ai buộc hai
 * bên khớp nhau, và khi lệch thì hỏng **im lặng theo cách khó chịu nhất**: bản đồ vẫn dựng, vẫn
 * kéo thả, vẫn hiện dấu vị trí — chỉ là nền toàn màu xám vì mọi ô ảnh bị CSP chặn. Lỗi duy nhất
 * xuất hiện ở console trình duyệt, nơi không cổng kiểm nào nhìn tới.
 *
 * <p>⚠ Bài này canh **quan hệ**, không canh một chuỗi cố định: đổi sang nguồn ô khác thì sửa
 * `TILE_HOST` và CSP là nó lại xanh. Chốt cứng tên OpenStreetMap ở đây là biến một lựa chọn hạ
 * tầng thành một bài kiểm phải sửa.
 */
/**
 * ⭐ Đổi 20/09/2026 (T73.7): CSP chuyển từ `next.config.ts` sang `src/lib/csp.ts` để gắn được nonce.
 *
 * ⛔⛔ Và bài này **đỏ ngay lượt `ci-local` đầu sau lượt chuyển** — `chiThiImgSrc()` trả chuỗi RỖNG
 * vì nó còn `readFileSync('next.config.ts')`. Đó đúng là công dụng của một cặp luật 14: một nửa đi,
 * nửa kia kêu. ⚠ Và thứ làm nó kêu ĐÚNG CÂU là vế **chống tập rỗng** ngay dưới — ⛔ có nó thì lỗi
 * đọc thành *"thiếu host tile"* chứ ⛔ phải *"⛔ đọc được CSP"*, và người sửa đi thêm host vào một
 * chuỗi ⛔ tồn tại (§11.19).
 *
 * ⇒ Nay đọc **giá trị ĐÃ GIẢI** từ chính hàm dựng CSP (luật 3), ⛔ grep một tệp nguồn nữa.
 */
function chiThiImgSrc(): string {
  return (
    dungCsp('nonce-cua-bai-kiem')
      .split('; ')
      .find((d) => d.startsWith('img-src')) ?? ''
  );
}

describe('Nguồn ô bản đồ khớp CSP', () => {
  it('⚠ đọc được chỉ thị img-src — bài kiểm soi chuỗi rỗng thì xanh mà không canh gì (luật 7)', () => {
    expect(chiThiImgSrc()).not.toBe('');
    expect(chiThiImgSrc()).toContain("'self'");
  });

  it('⭐ host của ô bản đồ được CSP cho phép', () => {
    expect(
      chiThiImgSrc(),
      `\`img-src\` không có \`${TILE_HOST}\`. Bản đồ sẽ dựng bình thường nhưng nền toàn xám — ` +
        'mọi ô ảnh bị chặn, và lỗi chỉ hiện ở console trình duyệt.',
    ).toContain(TILE_HOST);
  });

  it('⭐ URL ô dựng TỪ host, không phải một chuỗi chép tay thứ hai', () => {
    expect(TILE_URL.startsWith(TILE_HOST)).toBe(true);
    expect(TILE_URL).toContain('{z}/{x}/{y}');
  });

  it('⛔ ghi công OpenStreetMap không được bỏ — điều khoản sử dụng, không phải thẩm mỹ', () => {
    expect(TILE_ATTRIBUTION).toContain('OpenStreetMap');
    expect(TILE_ATTRIBUTION).toContain('openstreetmap.org/copyright');
  });

  it('⛔ kiểm chứng ngược: phép bóc `img-src` PHÂN BIỆT được CSP đủ host với CSP thiếu host', () => {
    // ⚠ Phá trên một BẢN SAO của chuỗi đã giải, ⛔ phá tệp nguồn: bài kiểm ⛔ được để lại dấu vết,
    //   và một lượt `sed` vào tệp thật là đúng thứ luật 10 dặn phải khôi phục rồi ĐO lại.
    const bocImgSrc = (csp: string) => csp.split('; ').find((d) => d.startsWith('img-src')) ?? '';

    const day = dungCsp('nonce-cua-bai-kiem');
    expect(
      bocImgSrc(day),
      'tiền đề: bản ĐỦ phải chứa host — ⛔ thì vế dưới xanh vì lý do sai',
    ).toContain(TILE_HOST);

    const thieu = day.replace(` ${TILE_HOST}`, '');
    expect(
      bocImgSrc(thieu),
      'phép bóc phải THẤY sự vắng mặt; một bộ bóc trả chuỗi rỗng ở cả hai bản thì ⛔ canh gì (luật 9)',
    ).not.toContain(TILE_HOST);
    expect(bocImgSrc(thieu), 'và nó vẫn phải bóc ra được chỉ thị, ⛔ phải rỗng').toContain(
      "'self'",
    );
  });

  it('⛔ CSP KHÔNG được nới `img-src` bằng ký tự đại diện', () => {
    expect(chiThiImgSrc()).not.toContain('*');
    expect(chiThiImgSrc()).not.toContain('http:');
  });
});
