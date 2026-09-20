import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { TEN_VAI_TRO, tenVaiTro } from './vaiTro';

/**
 * **Danh sách MÃ vai trò phải khớp migration seed** — luật 14.
 *
 * Bài này canh đúng một vế: *có đủ 12 mã, ⛔ thừa ⛔ thiếu*. Nó **⛔** canh tên hiển thị, vì tên là
 * thứ Công ty sửa được trên giao diện (xem javadoc `vaiTro.ts`) — canh tên sẽ làm CI đỏ vào ngày
 * họ đổi một nhãn, tức phạt người dùng vì đã dùng đúng tính năng ta cấp.
 *
 * ⚠ **Giới hạn tự khai (luật 28)**: bộ lọc đường dẫn của CI cho job frontend là
 * `^(frontend/|deploy/|.github/workflows/)` — sửa **riêng** tệp SQL này ⛔ chạy job frontend, nên
 * bài kiểm chỉ đỏ ở lượt PR nào có đụng `frontend/`. Nó vẫn đáng có: thêm một vai trò là việc
 * kéo theo màn hình, quyền và tài liệu, tức gần như chắc chắn cùng PR với một thay đổi frontend.
 */
const SEED = join(
  dirname(new URL(import.meta.url).pathname),
  '../../../../../backend/core/src/main/resources/db/migration/core/V202608131007__core_seed_rbac.sql',
);

/**
 * ⚠⚠ **⛔ cắt khối SQL ở dấu `;` đầu tiên** — mô tả của `CONTENT_MANAGER` là
 * *"Duyệt, xuất bản, gỡ bài**;** quản lý danh mục…"*, tức có một dấu `;` **bên trong chuỗi**.
 *
 * Bản đầu của hàm này dùng `VALUES([\s\S]*?);` và bóc ra **5/12** mã. Nó ⛔ ném, ⛔ báo — chỉ
 * lặng lẽ trả về một tập nhỏ hơn, và một bộ canh so hai tập mà vế trái bị cắt cụt sẽ đỏ với chẩn
 * đoán **sai** (*"FE thừa 7 mã"*) trong khi FE hoàn toàn đúng.
 *
 * ⇒ Chặn khối bằng **câu lệnh kế tiếp** thay vì bằng dấu chấm phẩy. Và vế
 * `toBeGreaterThanOrEqual(12)` ở bài đầu chính là thứ bắt được — luật 7 ở dạng rẻ nhất.
 */
function maTrongSeed(): string[] {
  const sql = readFileSync(SEED, 'utf8');
  const dau = sql.indexOf('INSERT INTO roles (code, name, description, is_system)');
  expect(
    dau,
    '⛔ ⛔ tìm thấy khối `INSERT INTO roles` — migration đã đổi hình dạng?',
  ).toBeGreaterThanOrEqual(0);
  const sau = sql.indexOf('INSERT INTO', dau + 1);
  const khoi = sql.slice(dau, sau > 0 ? sau : undefined);
  return [...khoi.matchAll(/\('([A-Z_]+)',/g)].map((m) => m[1]);
}

describe('danh mục vai trò khớp seed', () => {
  it('⚠ đọc được tệp seed và bóc ra mã — chống xanh trên tập rỗng (luật 7)', () => {
    expect(maTrongSeed().length).toBeGreaterThanOrEqual(12);
  });

  it('⭐⭐ mã vai trò ở FE và ở seed khớp nhau từng cái một', () => {
    expect([...maTrongSeed()].sort()).toEqual(Object.keys(TEN_VAI_TRO).sort());
  });

  it('⚠ mã lạ trả về chính nó, ⛔ trả chuỗi rỗng (quy tắc 16)', () => {
    expect(tenVaiTro('KHONG_CO_THAT')).toBe('KHONG_CO_THAT');
    expect(tenVaiTro('TECHNICIAN')).toBe('Cán bộ kỹ thuật');
  });
});
