import { describe, expect, it } from 'vitest';

import { xemTruocDuoc } from './xemTruocDuoc';

/**
 * Bảng chân trị của `xemTruocDuoc` — T84.6.
 *
 * ⚠ Vế quan trọng nhất ⛔ phải *"PDF thì được"* mà là **`taiDuoc = false` ⇒ SAI với MỌI loại**:
 * một tệp chưa quét virus xong trả 409 `SYS-0009` ở mọi đường đọc, nên một nút *Xem trước* hiện ra
 * cho nó là một nút chắc chắn hỏng.
 */
describe('xemTruocDuoc', () => {
  it('⭐ PDF và ảnh xem trước được', () => {
    expect(xemTruocDuoc('application/pdf', true)).toBe(true);
    expect(xemTruocDuoc('image/png', true)).toBe(true);
    expect(xemTruocDuoc('image/jpeg', true)).toBe(true);
  });

  it('⛔ định dạng trình duyệt ⛔ dựng được thì KHÔNG — khung trắng đọc như "hệ thống hỏng"', () => {
    expect(
      xemTruocDuoc('application/vnd.openxmlformats-officedocument.wordprocessingml.document', true),
    ).toBe(false);
    expect(xemTruocDuoc('application/zip', true)).toBe(false);
    expect(xemTruocDuoc('video/mp4', true)).toBe(false);
  });

  it('⭐⭐ chưa quét xong ⇒ SAI với mọi loại — vế phân biệt thật của hàm này', () => {
    expect(xemTruocDuoc('application/pdf', false)).toBe(false);
    expect(xemTruocDuoc('image/png', false)).toBe(false);
  });

  it('⛔ thiếu contentType ⇒ SAI, ⛔ đoán theo thứ gì khác', () => {
    expect(xemTruocDuoc(null, true)).toBe(false);
    expect(xemTruocDuoc(undefined, true)).toBe(false);
    expect(xemTruocDuoc('', true)).toBe(false);
  });

  it('⚠ so ⛔ phân biệt hoa thường — máy chủ có thể trả `APPLICATION/PDF`', () => {
    expect(xemTruocDuoc('APPLICATION/PDF', true)).toBe(true);
    expect(xemTruocDuoc('Image/PNG', true)).toBe(true);
  });
});
