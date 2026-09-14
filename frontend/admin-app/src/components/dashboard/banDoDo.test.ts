import { describe, expect, it } from 'vitest';

import { chieuDai, dienTich, khoangCach, nhanChieuDai, nhanDienTich } from './banDoDo';

/**
 * Công cụ đo bản đồ — CN-02.4 / M2.12.
 *
 * ⛔⛔ Bài này chạy trên những khoảng cách **đã biết đáp án**, ⛔ không so với chính công thức đang
 * kiểm. Một số đo sai trên bản đồ trông y hệt một số đo đúng — người dùng ⛔ không có cách nào biết
 * tuyến kênh vừa đo dài 9,2 km hay 10,0 km.
 */
describe('khoangCach — haversine, ⛔ không phải Pythagore', () => {
  it('⭐ một độ VĨ TUYẾN ≈ 111,2 km ở mọi vĩ độ', () => {
    const d = khoangCach({ lat: 20, lng: 105 }, { lat: 21, lng: 105 });
    expect(d).toBeGreaterThan(111_000);
    expect(d).toBeLessThan(111_400);
  });

  it('⛔⛔ một độ KINH TUYẾN ở vĩ độ 21° NGẮN HƠN ~7% — đây là vế Pythagore làm sai', () => {
    // Đo bằng hình học phẳng trên (lat, lng) thì hai phép đo dưới đây BẰNG NHAU. Chúng ⛔ không.
    const theoVi = khoangCach({ lat: 20, lng: 105 }, { lat: 21, lng: 105 });
    const theoKinh = khoangCach({ lat: 21, lng: 105 }, { lat: 21, lng: 106 });
    expect(theoKinh).toBeLessThan(theoVi);
    // cos(21°) ≈ 0,9336 ⇒ ngắn hơn khoảng 6,6%.
    expect(theoKinh / theoVi).toBeGreaterThan(0.92);
    expect(theoKinh / theoVi).toBeLessThan(0.94);
  });

  it('hai điểm trùng nhau ⇒ 0, ⛔ không phải NaN', () => {
    expect(khoangCach({ lat: 21, lng: 105 }, { lat: 21, lng: 105 })).toBe(0);
  });
});

describe('chieuDai / dienTich — các ca BIÊN phải trả 0, ⛔ không NaN', () => {
  it('⛔ dưới 2 điểm ⇒ chiều dài 0', () => {
    expect(chieuDai([])).toBe(0);
    expect(chieuDai([{ lat: 21, lng: 105 }])).toBe(0);
  });

  it('⛔ dưới 3 điểm ⇒ diện tích 0 — một đoạn thẳng ⛔ không có diện tích', () => {
    expect(dienTich([{ lat: 21, lng: 105 }])).toBe(0);
    expect(
      dienTich([
        { lat: 21, lng: 105 },
        { lat: 21, lng: 106 },
      ]),
    ).toBe(0);
  });

  it('⭐ ô vuông 1° × 1° ở xích đạo ≈ 12.360 km² — đáp án ĐỘC LẬP với công thức', () => {
    const s = dienTich([
      { lat: 0, lng: 0 },
      { lat: 0, lng: 1 },
      { lat: 1, lng: 1 },
      { lat: 1, lng: 0 },
    ]);
    expect(s / 1e6).toBeGreaterThan(12_000);
    expect(s / 1e6).toBeLessThan(12_400);
  });

  it('⛔ chiều quay ⛔ không đổi kết quả — dấu bị bỏ bằng `Math.abs`', () => {
    const thuan = [
      { lat: 20, lng: 105 },
      { lat: 20, lng: 106 },
      { lat: 21, lng: 106 },
    ];
    const nguoc = [...thuan].reverse();
    expect(dienTich(nguoc)).toBeCloseTo(dienTich(thuan), 0);
  });
});

describe('nhãn hiển thị — đổi đơn vị ở MỘT chỗ', () => {
  it('dưới 1 km hiện mét, từ 1 km hiện km', () => {
    expect(nhanChieuDai(950)).toBe('950 m');
    expect(nhanChieuDai(1000)).toBe('1.00 km');
  });

  it('⚠ ngưỡng diện tích là 1 ha = 10.000 m² — đơn vị ruộng đất người dùng thật sự dùng', () => {
    expect(nhanDienTich(9_999)).toBe('9999 m²');
    expect(nhanDienTich(10_000)).toBe('1.00 ha');
  });
});
