import { describe, expect, it } from 'vitest';

import { boCucHangNav, CHO_TRONG_TOI_THIEU, type TrangThaiHang } from './hangDieuHuong';

/**
 * Luật của hàng điều hướng khi mở tìm kiếm — **quyết theo chỗ trống đo được**.
 *
 * <p>Bản 01/09 lượt một hoán đổi cứng ở mọi bề rộng, dựa trên một con số đã lỗi thời (tám nhãn
 * chiếm 1150,6/1184px — nay chỉ còn bảy nhãn, 852px). Hệ quả: ô nhập dàn trải che mất điều
 * hướng ngay cả ở màn hình rộng 280px thừa chỗ. Bài này canh luật mới ở cả hai phía.
 */

/** Chỗ trống đo được 01/09 ở các bề rộng thật, dùng làm dữ liệu kiểm. */
const CHO_TRONG_DO_DUOC = {
  desktop1280len: 280,
  laptop1152: 200,
  hep1024: 72,
};

const MOI_TRANG_THAI: TrangThaiHang[] = [
  // đóng tìm kiếm — chỗ trống không ảnh hưởng gì
  { vuaKhung: null, moTimKiem: false, choTrong: null },
  { vuaKhung: true, moTimKiem: false, choTrong: 280 },
  { vuaKhung: false, moTimKiem: false, choTrong: 0 },
  // mở tìm kiếm
  { vuaKhung: null, moTimKiem: true, choTrong: null },
  { vuaKhung: true, moTimKiem: true, choTrong: 280 },
  { vuaKhung: true, moTimKiem: true, choTrong: 72 },
  { vuaKhung: false, moTimKiem: true, choTrong: 0 },
];

/** Lớp hiển thị có thể làm phần tử HIỆN ở ít nhất một bề rộng không. */
function coTheHien(lop: string): boolean {
  return lop !== 'hidden';
}

describe('boCucHangNav — đóng tìm kiếm thì giữ nguyên hành vi cũ', () => {
  it('ba trạng thái đóng trả đúng bộ lớp cũ, và menu vẫn `flex-1`', () => {
    expect(boCucHangNav({ vuaKhung: null, moTimKiem: false, choTrong: null })).toEqual({
      menu: 'hidden flex-1 lg:flex',
      nutNganKeo: 'lg:hidden',
      vungNganKeo: 'lg:hidden',
      oTimTrenHang: false,
      oTimCanhMenu: false,
    });
    expect(boCucHangNav({ vuaKhung: true, moTimKiem: false, choTrong: 280 })).toEqual({
      menu: 'flex flex-1',
      nutNganKeo: 'hidden',
      vungNganKeo: 'hidden',
      oTimTrenHang: false,
      oTimCanhMenu: false,
    });
    expect(boCucHangNav({ vuaKhung: false, moTimKiem: false, choTrong: 0 })).toEqual({
      menu: 'hidden',
      nutNganKeo: 'flex',
      vungNganKeo: 'block',
      oTimTrenHang: false,
      oTimCanhMenu: false,
    });
  });

  it('⭐ `vuaKhung: null` (SSR / chưa đo / không JS) vẫn giữ ngưỡng `lg` tĩnh làm đường lui', () => {
    // Mất nhánh này là cổng không có JS mất luôn điều hướng — không lỗi, không cổng kiểm nào đỏ.
    const r = boCucHangNav({ vuaKhung: null, moTimKiem: false, choTrong: null });
    expect(r.menu).toContain('lg:flex');
    expect(r.nutNganKeo).toContain('lg:hidden');
  });
});

describe('boCucHangNav — mở tìm kiếm: ĐỦ chỗ thì menu ở nguyên', () => {
  it('⭐⭐ desktop 280px trống ⇒ menu VẪN HIỆN, ô nhập đứng cạnh', () => {
    const r = boCucHangNav({
      vuaKhung: true,
      moTimKiem: true,
      choTrong: CHO_TRONG_DO_DUOC.desktop1280len,
    });
    expect(r.oTimTrenHang).toBe(true);
    expect(r.oTimCanhMenu, 'phải đứng cạnh menu, không chiếm trọn hàng').toBe(true);
    expect(coTheHien(r.menu), 'menu bị ẩn ⇒ đúng lỗi QuanTran báo: che mất điều hướng').toBe(true);
  });

  it('⭐⭐ menu BỎ `flex-1` khi ô nhập hiện — nếu không, hai bên cùng đòi giãn', () => {
    // Đây là mắt xích làm ô nhập "chỉ lấy phần còn lại": menu co về bề rộng tự nhiên, phần
    // dôi rơi cho form (`flex-1 min-w-0`). Giữ `flex-1` ở cả hai là chia đôi hàng.
    const r = boCucHangNav({ vuaKhung: true, moTimKiem: true, choTrong: 280 });
    expect(r.menu).toBe('flex');
    expect(r.menu).not.toContain('flex-1');
  });

  it('laptop 1152 (200px trống) đúng bằng ngưỡng ⇒ vẫn đứng cạnh menu', () => {
    expect(CHO_TRONG_DO_DUOC.laptop1152).toBe(CHO_TRONG_TOI_THIEU);
    const r = boCucHangNav({
      vuaKhung: true,
      moTimKiem: true,
      choTrong: CHO_TRONG_DO_DUOC.laptop1152,
    });
    expect(r.oTimCanhMenu).toBe(true);
  });
});

describe('boCucHangNav — mở tìm kiếm: CHẬT thì hoán đổi (hành vi mobile giữ nguyên)', () => {
  it('⭐⭐ 1024 (72px trống) ⇒ hoán đổi: menu VÀ nút ngăn kéo đều ẩn', () => {
    const r = boCucHangNav({
      vuaKhung: true,
      moTimKiem: true,
      choTrong: CHO_TRONG_DO_DUOC.hep1024,
    });
    expect(r.oTimCanhMenu).toBe(false);
    expect(coTheHien(r.menu)).toBe(false);
    expect(coTheHien(r.nutNganKeo)).toBe(false);
    expect(r.oTimTrenHang).toBe(true);
  });

  it('⭐ mobile (`vuaKhung: false`) ⇒ luôn hoán đổi, bất kể chỗ trống', () => {
    // QuanTran: *"Đối với mobile keep behavior như hiện tại"*. Ở đây menu vốn đã nằm trong
    // ngăn kéo, nên "đứng cạnh menu" không có nghĩa gì — và nút ngăn kéo phải nhường chỗ.
    for (const con of [0, 72, 200, 400]) {
      const r = boCucHangNav({ vuaKhung: false, moTimKiem: true, choTrong: con });
      expect(r.oTimCanhMenu, `choTrong=${con}`).toBe(false);
      expect(coTheHien(r.nutNganKeo), `choTrong=${con}: nút ngăn kéo còn hiện`).toBe(false);
    }
  });

  it('⭐ chưa đo được (`choTrong: null`) ⇒ hoán đổi, KHÔNG đoán là đủ chỗ', () => {
    // Hướng hỏng an toàn: đoán "đủ chỗ" khi chưa đo là dựng ra đúng thanh tràn mà
    // `vuaThanhNgang` sinh ra để tránh.
    const r = boCucHangNav({ vuaKhung: true, moTimKiem: true, choTrong: null });
    expect(r.oTimCanhMenu).toBe(false);
  });

  it('ngưỡng cắt đúng ở CHO_TRONG_TOI_THIEU, không lệch một pixel', () => {
    const canh = (con: number) =>
      boCucHangNav({ vuaKhung: true, moTimKiem: true, choTrong: con }).oTimCanhMenu;
    expect(canh(CHO_TRONG_TOI_THIEU - 1)).toBe(false);
    expect(canh(CHO_TRONG_TOI_THIEU)).toBe(true);
  });
});

describe('Bất biến chung', () => {
  it('phủ đủ 7 tổ hợp, không trùng', () => {
    expect(
      new Set(MOI_TRANG_THAI.map((t) => `${t.vuaKhung}/${t.moTimKiem}/${t.choTrong}`)).size,
    ).toBe(7);
  });

  it('ô tìm kiếm ở trên hàng KHI VÀ CHỈ KHI `moTimKiem`', () => {
    for (const t of MOI_TRANG_THAI) {
      expect(boCucHangNav(t).oTimTrenHang).toBe(t.moTimKiem);
    }
  });

  it('⭐ `oTimCanhMenu` không bao giờ đúng khi tìm kiếm đang đóng', () => {
    for (const t of MOI_TRANG_THAI.filter((x) => !x.moTimKiem)) {
      expect(boCucHangNav(t).oTimCanhMenu).toBe(false);
    }
  });

  it('⭐⭐ KIỂM CHỨNG NGƯỢC: bản hoán đổi cứng (lượt một) phải VI PHẠM luật mới', () => {
    // Luật 1 + 29. Nếu bản cũ vẫn qua được bộ canh này thì bộ canh không canh gì.
    // `banLuotMot` chính là mã đã bị QuanTran bác: ẩn menu ở MỌI bề rộng khi mở tìm kiếm.
    const banLuotMot = () => ({ menu: 'hidden', oTimCanhMenu: false });

    const duChoMaVanAn = MOI_TRANG_THAI.filter(
      (t) => t.moTimKiem && t.vuaKhung === true && (t.choTrong ?? 0) >= CHO_TRONG_TOI_THIEU,
    ).filter((t) => {
      const cu = banLuotMot();
      const moi = boCucHangNav(t);
      // Bản cũ ẩn menu ở chính trạng thái mà bản mới giữ menu ⇒ hai bản KHÁC nhau ở đây.
      return !coTheHien(cu.menu) && coTheHien(moi.menu);
    });

    // Khẳng định về SỐ LƯỢNG — không chia sẻ giả định nào với `coTheHien`.
    expect(
      duChoMaVanAn,
      'bản lượt một và bản mới cho cùng kết quả ⇒ bản vá không đổi gì',
    ).toHaveLength(1);
  });
});
