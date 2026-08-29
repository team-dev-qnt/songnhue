import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { TILE_ATTRIBUTION, TILE_HOST, TILE_URL } from './mapTiles';

/**
 * **Nguồn ô bản đồ phải nằm trong `img-src` của CSP.**
 *
 * <h2>Vì sao đây là một cặp phải nhớ ở hai nơi (luật 14)</h2>
 *
 * URL ô nằm trong mã component, danh sách host nằm trong `next.config.ts`. Không ai buộc hai
 * bên khớp nhau, và khi lệch thì hỏng **im lặng theo cách khó chịu nhất**: bản đồ vẫn dựng, vẫn
 * kéo thả, vẫn hiện dấu vị trí — chỉ là nền toàn màu xám vì mọi ô ảnh bị CSP chặn. Lỗi duy nhất
 * xuất hiện ở console trình duyệt, nơi không cổng kiểm nào nhìn tới.
 *
 * <p>⚠ Bài này canh **quan hệ**, không canh một chuỗi cố định: đổi sang nguồn ô khác thì sửa
 * `TILE_HOST` và CSP là nó lại xanh. Chốt cứng tên OpenStreetMap ở đây là biến một lựa chọn hạ
 * tầng thành một bài kiểm phải sửa.
 */
const CSP_NGUON = readFileSync(join(process.cwd(), 'next.config.ts'), 'utf8');

function chiThiImgSrc(): string {
  const m = /"img-src ([^"]*)"/.exec(CSP_NGUON);
  return m?.[1] ?? '';
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

  it('⛔ kiểm chứng ngược: bài kiểm bắt được một CSP quên mở host ô', () => {
    const cspHong = CSP_NGUON.replace(` ${TILE_HOST}`, '');
    const m = /"img-src ([^"]*)"/.exec(cspHong);
    expect(m?.[1] ?? '').not.toContain(TILE_HOST);
  });

  it('⛔ CSP KHÔNG được nới `img-src` bằng ký tự đại diện', () => {
    expect(chiThiImgSrc()).not.toContain('*');
    expect(chiThiImgSrc()).not.toContain('http:');
  });
});
