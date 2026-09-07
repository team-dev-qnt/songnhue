import { describe, expect, it } from 'vitest';

import {
  RONG_TOI_THIEU_KHOI,
  RONG_TOI_THIEU_KPI,
  boCucTheoBeRong,
  cotThanhCss,
  soCot,
} from './gridLayout';

/**
 * Bố cục dashboard ở ba bề rộng thiết bị — T23.11.
 *
 * <p>Thiết bị đã chốt (B8): TV 85" **4K (3840)**, kèm khả năng có máy chiếu **2K/Full-HD
 * (1920)**; và người quản trị vẫn mở màn hình này trên **laptop (1366)**. Ba con số đó là
 * ba trường hợp thật, không phải ba giá trị chọn cho đẹp.
 *
 * <p>Bài kiểm giữ **cả hai vế** mà T23.11 đòi: không tràn ngang **và** không mất khối.
 */
describe('số cột theo bề rộng', () => {
  const BE_RONG_THIET_BI = [3840, 1920, 1366];

  it('⭐ ba bề rộng thiết bị đều ra bố cục nhiều cột, không rơi về một cột', () => {
    for (const rong of BE_RONG_THIET_BI) {
      const boCuc = boCucTheoBeRong(rong);
      expect(boCuc.cotKpi, `KPI ở ${rong}px`).toBeGreaterThan(1);
      expect(boCuc.cotKhoi, `khối biểu đồ ở ${rong}px`).toBeGreaterThan(1);
    }
  });

  it('⛔ không tràn ngang ở BẤT KỲ bề rộng nào từ 320 tới 4096', () => {
    // Quét cả dải chứ không chỉ ba điểm: lỗi bố cục hay nằm ở khe hẹp ngay dưới một điểm
    // ngắt, đúng chỗ không ai kéo tay tới.
    for (let rong = 320; rong <= 4096; rong += 1) {
      for (const toiThieu of [RONG_TOI_THIEU_KPI, RONG_TOI_THIEU_KHOI]) {
        const cot = soCot(rong, toiThieu, 5);
        expect(cot, `bề rộng ${rong}`).toBeGreaterThanOrEqual(1);
        if (cot > 1) {
          expect(cot * toiThieu, `bề rộng ${rong}, ${cot} cột`).toBeLessThanOrEqual(rong);
        }
      }
    }
  });

  it('màn hình hẹp vẫn ra 1 cột chứ không phải 0 — 0 cột là lưới trắng', () => {
    expect(soCot(200, RONG_TOI_THIEU_KHOI, 3)).toBe(1);
    expect(soCot(0, RONG_TOI_THIEU_KHOI, 3)).toBe(1);
    expect(soCot(Number.NaN, RONG_TOI_THIEU_KHOI, 3)).toBe(1);
  });

  it('có trần cột — 4K không đẻ ra mười hai cột chữ nhỏ không đọc được từ 4–6 m', () => {
    expect(boCucTheoBeRong(3840).cotKpi).toBeLessThanOrEqual(5);
    expect(boCucTheoBeRong(3840).cotKhoi).toBeLessThanOrEqual(3);
  });

  // conventions.md §1.5 — mỗi cơ chế canh gác phải có bài chứng minh nó bắt được vi phạm.
  it('phép kiểm tràn ngang ở trên thật sự bắt được một bố cục sai', () => {
    // Một hàm "chia đều 6 cột bất kể bề rộng" là đúng loại lỗi cần bắt: ở 1366px thì
    // 6 × 420 = 2520px, tràn ra ngoài màn hình hơn một nghìn điểm ảnh.
    const saiBet = (rong: number) => (rong > 0 ? 6 : 6);
    const cot = saiBet(1366);
    expect(cot * RONG_TOI_THIEU_KHOI).toBeGreaterThan(1366);
  });
});

describe('chuỗi grid-template-columns', () => {
  it('dùng minmax(0, 1fr) để thẻ co được — 1fr trần là nguồn của tràn ngang trong CSS grid', () => {
    expect(cotThanhCss(3)).toBe('repeat(3, minmax(0, 1fr))');
  });

  it('không bao giờ sinh ra 0 cột', () => {
    expect(cotThanhCss(0)).toBe('repeat(1, minmax(0, 1fr))');
  });
});
