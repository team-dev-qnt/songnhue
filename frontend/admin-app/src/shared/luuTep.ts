/**
 * Lưu một `Blob` về máy người dùng — **một** bản cho cả kho.
 *
 * <h2>⛔⛔ Vì sao phải là một hàm chung</h2>
 *
 * Đo ngày 14/09/2026: bốn nơi cùng dựng `URL.createObjectURL` → thẻ `<a>` → `click()` —
 * `SettingsPage` (xuất cấu hình), `ContactsPage` (xuất liên hệ), `useXuatBaoCao` (báo cáo thuỷ
 * văn) và màn hình báo cáo nhân sự đang viết. Bốn bản chép của cùng một đoạn là bốn chỗ phải nhớ
 * cùng một điều (luật 14), và điều phải nhớ ở đây **⛔ không hiển nhiên**:
 *
 * - ⛔ `URL.revokeObjectURL` bắt buộc — thiếu nó là giữ nguyên cả blob trong bộ nhớ tab cho tới
 *   lúc đóng tab. Một bản trích ngang vài nghìn dòng, bấm mười lượt, là mười bản còn nằm đó.
 * - ⛔ `appendChild` trước `click()` — Firefox bỏ qua lượt bấm trên một thẻ ⛔ **không** nằm trong
 *   tài liệu. Ba trong bốn bản chép hiện tại ⛔ không có bước này.
 * - ⛔ ⛔ **Không** `window.open(url)`: endpoint đòi header `Authorization`, mà một tab mới ⛔ không
 *   mang theo — người dùng nhận một tab trắng và ⛔ không có lỗi nào.
 *
 * @param tenTep tên tệp. ⚠ Với tệp do **máy chủ** sinh, hãy truyền tên lấy từ
 *   `Content-Disposition` (xem `api.getTep`) — FE tự đặt tên là hai nơi cùng đặt tên cho một bản
 *   báo cáo, và người dùng lưu lại rồi gửi đi cái tên ấy.
 */
export function luuTep(blob: Blob, tenTep: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = tenTep;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
