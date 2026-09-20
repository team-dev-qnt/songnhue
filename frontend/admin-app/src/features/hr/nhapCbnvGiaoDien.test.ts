import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { boChuThich } from '@/testsupport/boChuThich';

/**
 * **Màn hình hồ sơ CBNV phải có đường NHẬP TỪ TỆP — T68.23 (G6-a).**
 *
 * Backend có ba endpoint và một bộ đọc; thiếu nút ở giao diện thì cả cơ chế ấy là một nửa cặp đọc–ghi
 * (luật 27) — đúng hình dạng đã đo ở `T61.18`: endpoint có, phân quyền có, bài kiểm xanh, và người dùng
 * ⛔ có gì để bấm.
 *
 * ⚠ Soi mã nguồn chứ ⛔ render cả trang: trang này treo trên sáu truy vấn; một bản giả nửa vời chỉ chứng
 * minh chính giả định của người viết bài kiểm. Thứ cần khẳng định là **ba đường dẫn** và **nhánh quyền**.
 */
const TRANG = join(process.cwd(), 'src/features/hr/EmployeesPage.tsx');

describe('Nhập danh sách CBNV — giao diện (T68.23)', () => {
  const ma = boChuThich(readFileSync(TRANG, 'utf8'));

  it('⛔ mount ImportModal với ĐÚNG ba đường dẫn của hồ sơ CBNV', () => {
    expect(ma).toContain('<ImportModal');
    for (const duong of [
      "xemTruoc: '/hr/employees/import/preview'",
      "nhap: '/hr/employees/import'",
      "mau: '/hr/employees/import/template'",
    ]) {
      expect(ma, `thiếu ${duong}`).toContain(duong);
    }
  });

  it('⛔ nút "Nhập từ tệp" nằm trong nhánh quyền hr:employee:create — ⛔ bày cho người ⛔ có quyền', () => {
    expect(ma).toMatch(/const coThem = hasPermission\('hr:employee:create'\)/);
    const nhanh = ma.slice(ma.indexOf('coThem ? ('), ma.indexOf('<DataTable'));
    expect(nhanh, 'nút nhập phải nằm trong nhánh coThem').toContain('Nhập từ tệp');
  });

  it('⭐ câu mô tả nói ra hai điều dễ hiểu sai nhất: ô trống GIỮ NGUYÊN, và tệp ⛔ chứa trường bảo mật', () => {
    const moTa = ma.slice(ma.indexOf('moTa="', ma.indexOf('<ImportModal')));
    expect(moTa.slice(0, 400)).toContain('GIỮ NGUYÊN');
    expect(moTa.slice(0, 400)).toMatch(/CCCD|bảo mật/);
  });
});
