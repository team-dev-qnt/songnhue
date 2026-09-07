/**
 * Bố cục **Nhóm 1** của trang chủ — slider 8/12 bên trái, cột tin 4/12 bên phải.
 *
 * <h2>Vì sao là một hàm, không phải hai chuỗi lớp viết thẳng trong JSX</h2>
 *
 * Cột tin dùng một mẹo CSS có điều kiện, và điều kiện ấy **không nhìn thấy được** khi đọc JSX:
 * `lg:absolute lg:inset-0` loại thẻ con khỏi phép tính chiều cao nội tại của tổ tiên, nên ô lưới
 * ấy đóng góp **0** vào chiều cao hàng — hàng do **cột slider** định. Mẹo đúng khi slider có
 * khung `aspect-[16/9]`; **sai hẳn** khi nó không có.
 *
 * Tách ra đây để phép quyết định ấy kiểm được bằng bốn dòng, trong CI, không cần trình duyệt.
 * Bộ đo Playwright canh đúng lỗi này (`e2e/boCucTrangChu.spec.ts`) **chưa nằm trong CI** (T38.10),
 * và bài TIỀN ĐỀ của nó đòi ≥8 bài viết ⇒ nó không bao giờ chạy tới đúng trạng thái làm lộ lỗi.
 *
 * <h2>⛔ Lỗi đã trả giá — production 7/9/2026</h2>
 *
 * Bảng `banners` rỗng ⇒ `AnhCarousel` trả `khiRong` **trần**, mất luôn `tiLeKhung`. Cột slider co
 * còn chiều cao tự nhiên của ô gạch chéo (đo trên DOM thật: `px-4 py-8`, ~90px). Thẻ tin bị
 * `inset-0` ép xuống 90px trong khi nội dung cần ~200px, và không có `overflow: hidden` nào chặn
 * ⇒ nó **vẽ tràn xuống đè lên** khối Tin theo chuyên mục ngay dưới.
 *
 * Cùng một image `sha256:e9e2794f…` chạy ở cả hai máy chủ: staging có banner nên không lộ,
 * production rỗng nên lộ. Đây là lỗi phụ thuộc **dữ liệu**, không phải lỗi build — và đó là lý do
 * nó đi qua mọi cổng kiểm.
 *
 * ⛔ **Không** chữa bằng cách giữ khung 16:9 cho trạng thái rỗng: thế là trang chủ ôm một ô gạch
 * chéo 785×442 rỗng, tức đổi một lỗi bố cục lấy một lỗi thiết kế.
 */
export interface LopCotTin {
  /** Ô lưới 4/12. */
  ngoai: string;
  /** Thẻ bọc trực tiếp `HomeNewsColumn`. Rỗng = không mẹo nào, chảy tự nhiên. */
  trong: string;
}

/**
 * @param coKhungSlider slider có thật sự dựng ra một khung tỉ lệ không — tức
 *   `banners.slice(0, soAnhSlider).length > 0`. ⚠ Đây là câu hỏi về **thứ sẽ được render**, không
 *   phải về `banners.length`: `site.slider.max-items = 0` cũng cho ra một slider rỗng.
 */
export function lopCotTin(coKhungSlider: boolean): LopCotTin {
  if (!coKhungSlider) {
    // Không có khung để đo theo ⇒ bỏ hẳn mẹo. Hai cột chảy tự nhiên, `items-stretch` của lưới
    // vẫn cho chúng cao bằng nhau, và không gì tràn ra ngoài hàng.
    return { ngoai: 'lg:col-span-4', trong: '' };
  }
  return { ngoai: 'lg:relative lg:col-span-4', trong: 'lg:absolute lg:inset-0' };
}
