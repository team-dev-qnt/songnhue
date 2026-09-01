/**
 * Hàng điều hướng hiển thị cái gì — menu, nút ngăn kéo, hay ô tìm kiếm.
 *
 * <h2>Vì sao là một hàm thuần, không phải ba biểu thức trong JSX</h2>
 *
 * `vitest.config.mts` cố ý không dựng DOM, nên thứ duy nhất kiểm được ở kho này là **hàm
 * thuần**. Ba chuỗi lớp CSS nằm rải trong JSX thì không bài kiểm nào chạm tới; gom vào đây thì
 * bất biến quan trọng nhất — *menu và ô nhập KHÔNG BAO GIỜ cùng hiện* — kiểm được bằng cách vét
 * cạn sáu tổ hợp. Cùng khuôn với {@code vuaThanhNgang}, {@code menuCap1KeTiep}, {@code coTuChay}.
 *
 * <h2>⭐⭐ Quyết theo CHỖ TRỐNG ĐO ĐƯỢC, không theo một luật cứng</h2>
 *
 * Yêu cầu QuanTran 01/09 (lượt hai): *"khi click vào thì phần input không được thành 1 thẻ dev
 * dàn trải che mất navigation... Chỉ hiển thị ở phần diện tích còn lại trên thanh navigation.
 * Đối với mobile keep behavior như hiện tại"*.
 *
 * <p>⚠ Lượt một hoán đổi cứng — mở tìm kiếm là ẩn menu ở MỌI bề rộng — dựa vào một con số
 * chép lại từ `PortalNav`: *"tám nhãn cấp 1 chiếm 1150,6/1184px"*. **Con số ấy đã lỗi thời.**
 * Đo lại 01/09 trên cây menu đang chạy (G14, **bảy** nhãn):
 *
 * <pre>
 *   bề rộng ≥1280   khung trong 1184 · thước 852 · nút 44  ⇒ còn trống 280px
 *   1152            khung trong 1104 · thước 852 · nút 44  ⇒ còn trống 200px
 *   1024            khung trong  976 · thước 852 · nút 44  ⇒ còn trống  72px
 * </pre>
 *
 * Tức ở desktop **có thừa chỗ** cho ô nhập đứng cạnh menu; chỉ khi khung hẹp lại thì mới hết.
 * Nên luật đúng không phải "luôn hoán đổi" mà là **hỏi phép đo**: đủ chỗ ⇒ ô nhập lấy phần
 * còn lại, menu ở nguyên; không đủ ⇒ hoán đổi như cũ (đúng hành vi mobile QuanTran muốn giữ).
 *
 * <p>⛔ Đây là lý do con số nào cũng phải kèm NGÀY ĐO. Một hằng số chép lại từ chú thích cũ
 * trông y hệt một hằng số vừa đo, và nó đã dẫn tới một bản vá đúng cơ chế mà sai hành vi.
 *
 * <h2>⭐ Ở chế độ HOÁN ĐỔI thì ẩn cả nút ngăn kéo, không chỉ ẩn menu</h2>
 *
 * Số học ở bề rộng nhỏ nhất phải đỡ (320px):
 *
 * <pre>
 *   còn nút ngăn kéo: 320 − 32 (px-4) − 98 (☰ + chữ "Danh mục") − 8 − 44 (X) = 138px
 *                     vỏ pill chiếm 92px  ⇒ còn  46px để gõ  ✗ không dùng được
 *   ẩn nút ngăn kéo:  320 − 32 − 8 − 44                                      = 236px
 *                     vỏ pill chiếm 92px  ⇒ còn 144px để gõ  ✓
 * </pre>
 */
export interface TrangThaiHang {
  /**
   * Thanh ngang có vừa khung không — kết quả đo của `ResizeObserver`.
   * `null` = chưa đo được (SSR, lượt vẽ đầu, không có JS) ⇒ rơi về ngưỡng `lg` tĩnh.
   */
  vuaKhung: boolean | null;
  moTimKiem: boolean;
  /**
   * Bề rộng CÒN TRỐNG trên hàng nav sau khi trừ menu và nút kính lúp, tính bằng px.
   * `null` = chưa đo (SSR, lượt vẽ đầu, không có JS).
   */
  choTrong: number | null;
}

export interface BoCucHang {
  /** Lớp hiển thị **và** cách co giãn của `<ul>` menu cấp 1. */
  menu: string;
  /** Lớp hiển thị của nút mở ngăn kéo. */
  nutNganKeo: string;
  /** Lớp hiển thị của vùng ngăn kéo. */
  vungNganKeo: string;
  /** Ô nhập tìm kiếm có nằm trên hàng nav không. */
  oTimTrenHang: boolean;
  /**
   * `true` = ô nhập đứng CẠNH menu, lấy phần còn lại.
   * `false` = ô nhập chiếm trọn hàng, menu tạm ẩn (hành vi mobile).
   */
  oTimCanhMenu: boolean;
}

/**
 * Chỗ trống tối thiểu để ô nhập đứng cạnh menu — **đo, không đoán**.
 *
 * Vỏ pill chiếm: `pl-3.5` 14 + kính lúp 16 + `gap-2` 8 + nút "Tìm" ~48 + `pr-1.5` 6 ≈ **92px**.
 * Lấy 200 ⇒ còn ít nhất **~108px** để gõ, đủ thấy khoảng 15–18 ký tự.
 *
 * <p>Đối chiếu số đo 01/09: ≥1280 → 280px (đạt) · 1152 → 200px (vừa đủ) · 1024 → 72px (không
 * đạt, rơi về hoán đổi). Tức ngưỡng này cắt đúng chỗ khung bắt đầu chật thật.
 */
export const CHO_TRONG_TOI_THIEU = 200;

export function boCucHangNav({ vuaKhung, moTimKiem, choTrong }: TrangThaiHang): BoCucHang {
  if (!moTimKiem) {
    const chuaDo = vuaKhung === null;
    return {
      // `flex-1` để menu chiếm hết hàng khi không có ô nhập — giữ nguyên hành vi cũ.
      menu: chuaDo ? 'hidden flex-1 lg:flex' : vuaKhung ? 'flex flex-1' : 'hidden',
      nutNganKeo: chuaDo ? 'lg:hidden' : vuaKhung ? 'hidden' : 'flex',
      vungNganKeo: chuaDo ? 'lg:hidden' : vuaKhung ? 'hidden' : 'block',
      oTimTrenHang: false,
      oTimCanhMenu: false,
    };
  }

  // Đủ chỗ ⇒ ô nhập đứng cạnh menu. Menu bỏ `flex-1` để co về bề rộng TỰ NHIÊN của nó, phần
  // dôi ra rơi cho ô nhập (`flex-1 min-w-0` ở form) — không cần tính px bằng tay, flexbox chia.
  const duChoCanhMenu = vuaKhung === true && choTrong !== null && choTrong >= CHO_TRONG_TOI_THIEU;

  if (duChoCanhMenu) {
    return {
      menu: 'flex',
      nutNganKeo: 'hidden',
      vungNganKeo: 'hidden',
      oTimTrenHang: true,
      oTimCanhMenu: true,
    };
  }

  // Không đủ chỗ ⇒ hoán đổi: ô nhập lấy trọn hàng, menu và nút ngăn kéo tạm ẩn.
  // ⛔ `'hidden'` trần, KHÔNG kèm biến thể theo điểm ngắt: một chuỗi như `'hidden lg:flex'` sẽ
  //    cho menu hiện lại từ 1024px trở lên đúng lúc phép đo vừa kết luận là KHÔNG đủ chỗ.
  return {
    menu: 'hidden',
    nutNganKeo: 'hidden',
    vungNganKeo: 'hidden',
    oTimTrenHang: true,
    oTimCanhMenu: false,
  };
}
