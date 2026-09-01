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
 * <h2>⭐⭐ Bất biến: hoán đổi, không chèn thêm</h2>
 *
 * Yêu cầu QuanTran 01/09: *"khi click vào icon search trên thanh navigation, open search bar
 * ngay trên thanh navigation, không đặt riêng 1 thẻ search bar như hiện tại"*.
 *
 * <p>Bản trước dựng ô tìm kiếm thành **một hàng riêng** bên dưới thanh nav, và có lý do đo
 * được hẳn hoi ghi trong `PortalNav`: ở khung 1232px, tám nhãn cấp 1 đã chiếm **1150,6 /
 * 1184px** khả dụng — nhét thêm một ô nhập 240px vào hàng ấy là đẩy thanh vào ngăn kéo ngay
 * trên màn hình desktop rộng nhất.
 *
 * <p>Con số ấy vẫn đúng, nhưng nó chỉ ràng buộc khi menu và ô nhập **cùng tồn tại**. Hoán đổi
 * thì chúng không bao giờ gặp nhau: mở tìm kiếm ⇒ ẩn menu **và** ẩn nút ngăn kéo, ô nhập lấy
 * trọn hàng; đóng ⇒ trả lại y như cũ. Ngân sách bề rộng không bị đụng tới.
 *
 * <h2>⭐ Vì sao ẩn CẢ nút ngăn kéo, không chỉ ẩn menu</h2>
 *
 * Số học ở bề rộng nhỏ nhất phải đỡ (320px):
 *
 * <pre>
 *   còn nút ngăn kéo: 320 − 32 (px-4) − 98 (☰ + chữ "Danh mục") − 8 − 44 (X) = 138px
 *                     vỏ pill chiếm 92px  ⇒ còn  46px để gõ  ✗ không dùng được
 *   ẩn nút ngăn kéo:  320 − 32 − 8 − 44                                      = 236px
 *                     vỏ pill chiếm 92px  ⇒ còn 144px để gõ  ✓
 * </pre>
 *
 * Ẩn cả hai còn làm luật đồng nhất ở mọi bề rộng — *hàng nav hiển thị điều hướng HOẶC tìm
 * kiếm, không bao giờ cả hai* — và chính sự đồng nhất ấy là thứ kiểm được bằng một khẳng định.
 */
export interface TrangThaiHang {
  /**
   * Thanh ngang có vừa khung không — kết quả đo của `ResizeObserver`.
   * `null` = chưa đo được (SSR, lượt vẽ đầu, không có JS) ⇒ rơi về ngưỡng `lg` tĩnh.
   */
  vuaKhung: boolean | null;
  moTimKiem: boolean;
}

export interface BoCucHang {
  /** Lớp hiển thị của `<ul>` menu cấp 1. */
  menu: string;
  /** Lớp hiển thị của nút mở ngăn kéo. */
  nutNganKeo: string;
  /** Lớp hiển thị của vùng ngăn kéo. */
  vungNganKeo: string;
  /** Ô nhập tìm kiếm có chiếm hàng nav không. */
  oTimTrenHang: boolean;
}

export function boCucHangNav({ vuaKhung, moTimKiem }: TrangThaiHang): BoCucHang {
  if (moTimKiem) {
    // ⛔ `'hidden'` trần, KHÔNG kèm biến thể theo điểm ngắt. Một chuỗi như `'hidden lg:flex'`
    //    sẽ cho menu hiện lại từ 1024px trở lên và bất biến "không bao giờ cùng hiện" vỡ
    //    đúng ở bề rộng mà ngân sách 1150,6/1184px đang căng nhất.
    return { menu: 'hidden', nutNganKeo: 'hidden', vungNganKeo: 'hidden', oTimTrenHang: true };
  }

  const chuaDo = vuaKhung === null;
  return {
    menu: chuaDo ? 'hidden lg:flex' : vuaKhung ? 'flex' : 'hidden',
    nutNganKeo: chuaDo ? 'lg:hidden' : vuaKhung ? 'hidden' : 'flex',
    vungNganKeo: chuaDo ? 'lg:hidden' : vuaKhung ? 'hidden' : 'block',
    oTimTrenHang: false,
  };
}
