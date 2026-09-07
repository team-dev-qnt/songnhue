/**
 * Điều kiện để một slider TỰ CHẠY — tách riêng khỏi React để có thể kiểm bằng bốn dòng.
 *
 * <h2>Vì sao là một hàm chứ không phải một biểu thức trong `useEffect`</h2>
 *
 * Bốn điều kiện dưới đây phải đúng ở **hai** slider của trang chủ (ảnh hoạt động và thư viện
 * ảnh cạnh video), và cả hai đọc cùng bộ khoá `site.slider.*`. Chép biểu thức sang chỗ thứ hai
 * là dựng ngay tình huống luật 14 cấm: người sau sửa một bên, bên kia trôi lại, và không có gì
 * đỏ vì `useEffect` không kiểm được bằng một phép khẳng định rẻ tiền.
 *
 * ⚠ `intervalSeconds <= 0` phải **dừng hẳn**, không rơi về một mặc định. Đặt 0 ở màn hình cấu
 * hình là ý định tắt tự chạy; *"thấy 0 thì dùng 5"* biến một ô cấu hình thành ô vô nghĩa
 * (luật 15 ở dạng ngược: một công tắc có người đọc nhưng đọc sai vẫn là một công tắc chết).
 */
export interface DieuKienTuChay {
  autoplay: boolean;
  /** Con trỏ hoặc tiêu điểm bàn phím đang nằm trong slider. */
  tamDung: boolean;
  soAnh: number;
  intervalSeconds: number;
}

export function coTuChay({ autoplay, tamDung, soAnh, intervalSeconds }: DieuKienTuChay): boolean {
  return autoplay && !tamDung && soAnh > 1 && intervalSeconds > 0;
}

/**
 * Hiệu ứng chuyển ảnh — `site.slider.effect`. **T36.11**.
 *
 * <h2>⛔⛔ Vì sao khoá này QUAY LẠI, và vì sao chỉ quay lại ở lượt này</h2>
 *
 * `V202608271032` **XOÁ** `site.slider.effect` với lý do đúng: *"chưa từng có nơi đọc"* (quy
 * tắc 15). Ghi chú T36.11 trong sổ dặn thẳng — *"muốn có Fade thì dựng NƠI ĐỌC trước, rồi mới
 * seed lại khoá; đừng seed lại rồi hẹn làm sau"*.
 *
 * Lượt này làm đúng thứ tự ấy: hàm này + prop `hieuUng` của `AnhCarousel` + hai nơi gọi ra đời
 * **trước**, migration `V202609071069` seed lại khoá **trong cùng một commit**.
 *
 * ⚠ Và bộ canh `PortalSettingsReadTest.khoaDaGoKhongConNoiDoc` — vốn khẳng định khoá này ⛔
 * **không** được đọc ở đâu — phải sửa cùng lượt, nếu không nó đỏ vì một lý do đã hết đúng.
 *
 * <h2>Hai giá trị, và mặc định là `FADE` vì đó là hành vi ĐANG CHẠY</h2>
 *
 * Khoá vắng ⇒ `FADE` — đúng thứ cổng đang làm từ WS-16 (`transition-opacity duration-700`).
 * Mặc định `SLIDE` là đổi diện mạo trang chủ bằng một lượt deploy mà ⛔ không ai bấm gì.
 */
export type HieuUngSlider = 'FADE' | 'SLIDE';

/**
 * Đọc `site.slider.effect`; giá trị lạ ⇒ `FADE`.
 *
 * ⚠ ⛔ **Không** ép hoa/thường ở nơi gọi mà ép ở đây — cùng lý lẽ với `docSo`/`docBool`: đặt
 * phép chuyển đổi ở **chỗ dữ liệu đi qua** (quy tắc 12 ở dạng nhỏ nhất). Người quản trị gõ
 * `"fade"` thường thì nó vẫn phải chạy, chứ ⛔ không rơi về mặc định trong im lặng.
 *
 * ⛔ Một giá trị lạ ⛔ **không** ném: đây là dữ liệu người vận hành gõ vào một ô chữ, và một
 * trang chủ trắng vì gõ nhầm một ký tự là cái giá sai. Nó rơi về `FADE` — hành vi đang chạy.
 */
export function docHieuUngSlider(raw: string | undefined): HieuUngSlider {
  return raw?.trim().toUpperCase() === 'SLIDE' ? 'SLIDE' : 'FADE';
}
