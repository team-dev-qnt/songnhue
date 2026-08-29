import { describe, expect, it } from 'vitest';

import { coTuChay } from './slider';

const CHAY = { autoplay: true, tamDung: false, soAnh: 5, intervalSeconds: 5 };

describe('Slider chỉ tự chạy khi đủ bốn điều kiện', () => {
  it('⭐ cấu hình mặc định của cổng thì CHẠY', () => {
    expect(coTuChay(CHAY)).toBe(true);
  });

  it.each([
    ['tắt tự chạy ở màn hình cấu hình', { autoplay: false }],
    ['con trỏ đang nằm trên ảnh', { tamDung: true }],
    ['chỉ có một ảnh — không có gì để chuyển', { soAnh: 1 }],
    ['không có ảnh nào', { soAnh: 0 }],
    ['nhịp = 0 ⇒ TẮT HẲN, không rơi về mặc định', { intervalSeconds: 0 }],
    ['nhịp âm do nhập sai cũng phải dừng, không chạy 0ms', { intervalSeconds: -3 }],
  ])('⛔ %s ⇒ KHÔNG chạy', (_ten, doi) => {
    expect(coTuChay({ ...CHAY, ...doi })).toBe(false);
  });

  it('⭐ hai ảnh là đủ để chuyển — biên dưới của `soAnh`', () => {
    // Một khẳng định phải phân biệt được hai trạng thái (luật 9): `> 1` và `>= 1` chỉ khác
    // nhau ở đúng `soAnh = 1`, nên phải có cả hai vế mới nói được điều gì.
    expect(coTuChay({ ...CHAY, soAnh: 2 })).toBe(true);
    expect(coTuChay({ ...CHAY, soAnh: 1 })).toBe(false);
  });
});
