import { describe, expect, it } from 'vitest';

import { mauTrangThaiHopLe } from './mauTrangThai';

describe('mauTrangThaiHopLe — màu badge do Công ty tự đặt', () => {
  it('nhận đúng dạng #rrggbb, giữ nguyên giá trị', () => {
    expect(mauTrangThaiHopLe('#1a7f37')).toBe('#1a7f37');
    expect(mauTrangThaiHopLe('#FFFFFF')).toBe('#FFFFFF');
    expect(mauTrangThaiHopLe('  #0d6efd  ')).toBe('#0d6efd');
  });

  it('⛔ mọi hình dạng khác → null, kể cả chuỗi đúng 7 ký tự', () => {
    // Cột `color_hex` khai length = 7. Ràng buộc độ dài không nói gì về hình dạng.
    expect(mauTrangThaiHopLe('red;x:1')).toBeNull();
    expect(mauTrangThaiHopLe('#12345')).toBeNull();
    expect(mauTrangThaiHopLe('#1234567')).toBeNull();
    expect(mauTrangThaiHopLe('rgb(1,2,3)')).toBeNull();
    expect(mauTrangThaiHopLe('')).toBeNull();
    expect(mauTrangThaiHopLe(null)).toBeNull();
    expect(mauTrangThaiHopLe(undefined)).toBeNull();
  });

  it('kiểm chứng ngược: vị từ không phải "luôn null"', () => {
    expect(mauTrangThaiHopLe('#000000')).not.toBeNull();
  });
});
