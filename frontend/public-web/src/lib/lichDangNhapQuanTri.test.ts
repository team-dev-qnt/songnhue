import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from './boChuThich';

/**
 * **Bộ đo tương thích quản trị đăng nhập MỘT lần mỗi dự án — T61.29 (WS-72).**
 *
 * `e2e/tuong-thich/quanTri.spec.ts` ⛔ chạy trong CI (chốt 15/09: chạy tay, ⛔ chặn PR). Nếu nó quay
 * lại kiểu *mỗi màn hình tự đăng nhập*, lỗi chỉ lộ ra ở lượt đo kế tiếp trên staging: ~90/120 bài đỏ
 * vì hạn mức đường xác thực (nginx `api_auth` 20 lượt/phút theo IP), sau khi đã tốn một tài khoản đo
 * và một buổi. Bài này canh bằng ĐẾM (luật 29), trên mã đã bỏ chú thích — javadoc của spec nhắc tên các
 * lời gọi ấy.
 */
describe('e2e/tuong-thich/quanTri.spec.ts — một lượt đăng nhập', () => {
  const nguon = boChuThich(
    readFileSync(join(process.cwd(), 'e2e/tuong-thich/quanTri.spec.ts'), 'utf8'),
  );

  it('⛔ đúng MỘT chỗ điền mật khẩu', () => {
    expect(nguon.match(/\.fill\(matKhau\)/g) ?? []).toHaveLength(1);
  });

  it('⛔ đúng HAI page.goto — trang đăng nhập đo riêng + lượt đăng nhập; mỗi màn hình tải lại là thêm một lượt /auth/refresh', () => {
    expect(nguon.match(/page\.goto\(/g) ?? []).toHaveLength(2);
  });
});
