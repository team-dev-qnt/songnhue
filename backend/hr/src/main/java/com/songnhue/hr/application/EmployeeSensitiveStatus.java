package com.songnhue.hr.application;

/**
 * Ô nào của phần 🔒 đã có dữ liệu — <b>⛔ không kèm giá trị nào</b>.
 *
 * <p>Đây là thứ DUY NHẤT màn hình hồ sơ được biết về phần nhạy cảm khi người xem ⛔ không có
 * {@code hr:employee:view-sensitive}. Cùng khuôn với {@code ApiSourceView.credentialDaCauHinh}:
 * người vận hành cần phân biệt <i>"chưa nhập"</i> với <i>"⛔ không được xem"</i> — hai trạng thái ấy
 * mà trông giống nhau thì sẽ có người nhập đè lên dữ liệu đang có (T50.1 là một sự cố đúng hình
 * dạng ấy).
 *
 * <p>⚠ Lương và hệ số gộp thành <b>một</b> cờ: tách ra là để lộ thêm một bit về cơ cấu lương của
 * một người mà ⛔ không phục vụ quyết định nào trên màn hình.
 */
public record EmployeeSensitiveStatus(
        boolean cccdDaCo, boolean luongDaCo, boolean taiKhoanDaCo, boolean mstDaCo, boolean bhxhDaCo) {

    public static EmployeeSensitiveStatus trong() {
        return new EmployeeSensitiveStatus(false, false, false, false, false);
    }
}
