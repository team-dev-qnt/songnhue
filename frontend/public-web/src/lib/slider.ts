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
