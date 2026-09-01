import { describe, expect, it } from 'vitest';

import { boCucHangNav, type TrangThaiHang } from './hangDieuHuong';

/**
 * Bất biến của hàng điều hướng: **menu và ô nhập tìm kiếm không bao giờ cùng hiện**.
 *
 * <p>Đây không phải thẩm mỹ mà là ràng buộc số học: ở khung 1232px tám nhãn cấp 1 chiếm
 * 1150,6/1184px khả dụng. Cho cả hai cùng hiện là đẩy thanh vào ngăn kéo ngay trên màn hình
 * desktop rộng nhất — và triệu chứng ấy **không đỏ ở đâu cả**, nó chỉ trông xấu.
 */

/** Sáu tổ hợp — vét cạn, không chọn mẫu. */
const MOI_TRANG_THAI: TrangThaiHang[] = [
  { vuaKhung: null, moTimKiem: false },
  { vuaKhung: true, moTimKiem: false },
  { vuaKhung: false, moTimKiem: false },
  { vuaKhung: null, moTimKiem: true },
  { vuaKhung: true, moTimKiem: true },
  { vuaKhung: false, moTimKiem: true },
];

/** Lớp hiển thị có thể làm phần tử HIỆN ở ít nhất một bề rộng không. */
function coTheHien(lop: string): boolean {
  // `'hidden'` trần = ẩn ở mọi bề rộng. Mọi chuỗi khác (kể cả `'hidden lg:flex'`) đều có thể
  // hiện ở đâu đó — và đó chính là điều bất biến cấm khi ô tìm kiếm đang mở.
  return lop !== 'hidden';
}

describe('boCucHangNav — vét cạn sáu tổ hợp', () => {
  it('phủ đủ 6 tổ hợp, không trùng', () => {
    // Luật 7: nếu danh sách trên bị cắt còn một phần tử, mọi khẳng định dưới vẫn xanh.
    expect(new Set(MOI_TRANG_THAI.map((t) => `${t.vuaKhung}/${t.moTimKiem}`)).size).toBe(6);
  });

  it('⭐⭐ mở tìm kiếm ⇒ menu VÀ nút ngăn kéo đều ẩn ở MỌI bề rộng', () => {
    for (const t of MOI_TRANG_THAI.filter((x) => x.moTimKiem)) {
      const r = boCucHangNav(t);
      expect(coTheHien(r.menu), `vuaKhung=${t.vuaKhung}: menu còn hiện được`).toBe(false);
      expect(coTheHien(r.nutNganKeo), `vuaKhung=${t.vuaKhung}: nút ngăn kéo còn hiện được`).toBe(
        false,
      );
      expect(r.oTimTrenHang).toBe(true);
    }
  });

  it('⭐ đóng tìm kiếm ⇒ trả lại đúng hành vi cũ, không sót trạng thái nào', () => {
    expect(boCucHangNav({ vuaKhung: null, moTimKiem: false })).toEqual({
      menu: 'hidden lg:flex',
      nutNganKeo: 'lg:hidden',
      vungNganKeo: 'lg:hidden',
      oTimTrenHang: false,
    });
    expect(boCucHangNav({ vuaKhung: true, moTimKiem: false })).toEqual({
      menu: 'flex',
      nutNganKeo: 'hidden',
      vungNganKeo: 'hidden',
      oTimTrenHang: false,
    });
    expect(boCucHangNav({ vuaKhung: false, moTimKiem: false })).toEqual({
      menu: 'hidden',
      nutNganKeo: 'flex',
      vungNganKeo: 'block',
      oTimTrenHang: false,
    });
  });

  it('⭐ `vuaKhung: null` (SSR / chưa đo / không JS) vẫn giữ ngưỡng `lg` tĩnh làm đường lui', () => {
    // Mất nhánh này là cổng không có JS mất luôn điều hướng — không lỗi, không cổng kiểm nào đỏ.
    const r = boCucHangNav({ vuaKhung: null, moTimKiem: false });
    expect(r.menu).toContain('lg:flex');
    expect(r.nutNganKeo).toContain('lg:hidden');
  });

  it('ô tìm kiếm chiếm hàng KHI VÀ CHỈ KHI `moTimKiem`', () => {
    for (const t of MOI_TRANG_THAI) {
      expect(boCucHangNav(t).oTimTrenHang).toBe(t.moTimKiem);
    }
  });

  it('⭐⭐ KIỂM CHỨNG NGƯỢC: một bản bỏ qua `moTimKiem` phải làm bất biến ĐỎ', () => {
    // Luật 1 + 29. Bất biến ở trên chỉ có nghĩa nếu nó phân biệt được bản đúng với bản sai.
    // `banHong` chính là mã trước 01/09: ba chuỗi lớp không hề đọc `moTimKiem`.
    const banHong = ({ vuaKhung }: TrangThaiHang) => {
      const chuaDo = vuaKhung === null;
      return {
        menu: chuaDo ? 'hidden lg:flex' : vuaKhung ? 'flex' : 'hidden',
        nutNganKeo: chuaDo ? 'lg:hidden' : vuaKhung ? 'hidden' : 'flex',
      };
    };

    const viPham = MOI_TRANG_THAI.filter((t) => t.moTimKiem).filter((t) => {
      const r = banHong(t);
      return coTheHien(r.menu) || coTheHien(r.nutNganKeo);
    });
    // Khẳng định về SỐ LƯỢNG, không về hình dạng chuỗi — nó không chia sẻ giả định nào với
    // `coTheHien`. Bản hỏng phải vi phạm ở CẢ BA trạng thái đang mở tìm kiếm.
    expect(viPham, 'bản hỏng lọt qua bất biến ⇒ bất biến không canh gì').toHaveLength(3);
  });
});
