import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

/**
 * **Tên tác giả trên trang bài viết của cổng** — T84.4.
 *
 * <h3>Canh CẤU TRÚC, ⛔ canh chữ (luật 2)</h3>
 *
 * Bài này hỏi hai điều mà một khẳng định kiểu `toContain('✍')` ⛔ trả lời được:
 *
 * <ol>
 *   <li>trang có một <b>đầu đọc</b> cho `authorName` — backend trả một trường mà ⛔ màn hình nào
 *       đọc là nửa vòng chạy hoàn hảo cho ra số không (luật 27);
 *   <li>đầu đọc ấy <b>đứng sau một điều kiện</b> — `null` nghĩa tài khoản đã xoá mềm, và một dấu
 *       gạch hay chữ "Đang cập nhật" ở đó là bịa một giá trị cho ô chưa có nguồn (quy tắc 16).
 * </ol>
 */

const TRANG = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '../app/bai-viet/[slug]/page.tsx'),
  'utf8',
);

describe('Tác giả trên trang bài viết (T84.4)', () => {
  it('⚠ chống tập rỗng: đọc được tệp trang và nó có dải meta', () => {
    expect(TRANG.length).toBeGreaterThan(2000);
    expect(TRANG).toContain('article.publishedAt');
  });

  it('⭐⭐ có đầu đọc `article.authorName` — nếu ⛔, backend trả một trường ⛔ ai dùng', () => {
    expect(TRANG).toContain('article.authorName');
  });

  it('⭐⭐ chỉ vẽ khi CÓ giá trị — ⛔ dấu gạch, ⛔ "Đang cập nhật"', () => {
    // Cấu trúc: `{article.authorName ? ( … ) : null}`. Một bản viết thẳng `{article.authorName}`
    // sẽ render chuỗi rỗng kèm biểu tượng ✍ đứng trơ — trông như một lỗi dữ liệu.
    expect(TRANG).toMatch(/\{article\.authorName \?/);
    expect(TRANG).not.toMatch(/authorName\s*(\?\?|\|\|)\s*['"]/);
  });

  it('⛔ ⛔ trộn tác giả với Nguồn tin — hai thứ khác nhau, cùng có mặt trên một bài', () => {
    // `docNguonBaiViet` là chân bài ("Nguồn: …"); tác giả nằm ở DẢI META đầu bài. Nếu ai đó nối
    // `authorName` vào hàm nguồn thì bài này đỏ — và đó đúng là lỗi T26.63 ở dạng mới.
    expect(TRANG).not.toMatch(/docNguonBaiViet\([^)]*authorName/);
  });
});
