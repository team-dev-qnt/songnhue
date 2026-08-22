import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { REVALIDATE_SECONDS } from '@/lib/api';

/**
 * Canh cho hai nơi nhớ cùng một con số không lệch nhau.
 *
 * <h3>Vì sao con số bị nhân đôi</h3>
 *
 * Next đọc `export const revalidate` bằng **phân tích tĩnh** và từ chối build khi giá trị
 * không phải literal — nên trang không import được `REVALIDATE_SECONDS`. Cùng lúc đó,
 * `apiGet` cần con số ấy để đặt `next.revalidate` cho từng lượt `fetch`.
 *
 * <h3>Hậu quả nếu lệch, và vì sao nó im lặng</h3>
 *
 * Trang khai 300 giây trong khi `fetch` khai 3600: trang được đánh dấu cũ sau 5 phút, dựng
 * lại, rồi lấy đúng dữ liệu cũ từ bộ đệm của `fetch`. Kết quả là **nội dung đứng yên một
 * tiếng** dù mọi thứ trông như đang hoạt động. Không lỗi, không cảnh báo.
 *
 * Đây là dạng "chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ" —
 * cùng lý do đã dựng `NotificationEnumParityTest` ở WS-12.
 */
describe('revalidate của trang khớp với REVALIDATE_SECONDS', () => {
  const appDir = join(process.cwd(), 'src/app');

  function timTrang(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) return timTrang(full);
      return entry.name === 'page.tsx' ? [full] : [];
    });
  }

  const trangCoKhai = timTrang(appDir)
    .map((file) => ({ file, source: readFileSync(file, 'utf8') }))
    .map(({ file, source }) => ({ file, match: /export const revalidate = (\d+);/.exec(source) }))
    .filter((x): x is { file: string; match: RegExpExecArray } => x.match !== null);

  it('có ít nhất một trang khai revalidate — nếu không thì phép kiểm này vô nghĩa', () => {
    // conventions.md §1.5: mỗi cơ chế canh gác phải chứng minh nó chạy trên tập khác rỗng.
    expect(trangCoKhai.length).toBeGreaterThan(0);
  });

  it.each(trangCoKhai)('$file khai đúng $match.1 giây', ({ match }) => {
    expect(Number(match[1])).toBe(REVALIDATE_SECONDS);
  });
});
