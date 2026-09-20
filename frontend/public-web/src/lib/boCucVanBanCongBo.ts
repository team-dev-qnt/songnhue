import { lienKetAnToan } from '@/lib/lienKetAnToan';

/**
 * Bố cục khối "Công bố thông tin" trên trang chủ — T79.1.
 *
 * <h2>Vì sao là MỘT hàm chứ ⛔ hai câu điều kiện trong JSX</h2>
 *
 * Khối ấy là một lưới 12 cột: bảng văn bản bên trái, thẻ *Hệ thống văn bản điều hành* bên phải.
 * Thẻ bên phải render **có điều kiện** (địa chỉ rỗng ⇒ ⛔ hiện), nên bề rộng bên trái **phụ thuộc**
 * điều kiện ấy — hai quyết định, một sự thật.
 *
 * <p>Bản trước tách chúng ra: thẻ hỏi {@link lienKetAnToan}, còn bề rộng ghim `lg:col-span-8` **vô
 * điều kiện**. Hệ quả đo được: địa chỉ rỗng ⇒ thẻ biến mất ⇒ **4/12 bề rộng bỏ trống** bên phải,
 * trang chủ hụt một phần ba mà ⛔ dòng nào báo. Cơ chế ẩn có mặt từ đầu và **⛔ ai đi qua** — đúng
 * luật 7.
 *
 * <p>⇒ Gộp về một hàm thuần: *"⛔ hiện thẻ"* và *"bề rộng bên trái"* ra cùng một lượt tính, nên
 * chúng ⛔ thể trôi ra khỏi nhau (luật 12 — đặt bảo đảm ở chỗ dữ liệu ĐI QUA, ⛔ ở nơi gọi).
 *
 * <p>⚠ Hai tên lớp viết **nguyên vẹn** trong mã, ⛔ ghép chuỗi: Tailwind quét nguồn theo văn bản
 * nên một lớp dựng lúc chạy (`lg:col-span-${n}`) sẽ ⛔ được sinh ra, và CSS hỏng **trong im lặng**.
 */
export interface BoCucVanBanCongBo {
  /** Thẻ "Hệ thống văn bản điều hành" có hiện ⛔ — địa chỉ rỗng hoặc ⛔ hợp lệ ⇒ `false`. */
  hienTheHeThong: boolean;
  /** Lớp bề rộng của khối bảng văn bản, khớp với {@link hienTheHeThong}. */
  cotBangVanBan: 'lg:col-span-8' | 'lg:col-span-12';
}

export function boCucVanBanCongBo(docSystemUrl: string | null | undefined): BoCucVanBanCongBo {
  const hienTheHeThong = lienKetAnToan(docSystemUrl) !== null;
  return {
    hienTheHeThong,
    cotBangVanBan: hienTheHeThong ? 'lg:col-span-8' : 'lg:col-span-12',
  };
}
