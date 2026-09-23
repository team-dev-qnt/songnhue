package com.songnhue.core.spi;

import java.util.UUID;

/**
 * Một tài khoản, nhìn từ ngoài module {@code core} — T84.1.
 *
 * <p>Sinh ra vì {@code articles.author_user_id} là khoá ngoại tới {@code users}, mà đặc tả ĐÃ KÝ đòi
 * <b>in họ tên tác giả ra cổng công khai</b> (<i>"Tác giả | Dropdown (User) | Có | Tự động điền tài
 * khoản đang đăng nhập, cho phép thay đổi"</i>). Trước lượt này {@code UserDirectoryPort} ⛔ có một
 * hàm nào trả {@code fullName}, nên nửa ĐỌC của cặp đọc–ghi chưa bao giờ tồn tại: {@code SaveRequest}
 * nhận {@code authorPublicId} từ 19/08/2026 còn {@code ArticleDetail} ⛔ trả gì (luật 27).
 *
 * <p>Cố ý <b>mỏng</b>, theo đúng ranh giới mà {@link EmployeeRef} đã vạch: chỉ những trường đã hiện
 * công khai ở nơi khác. {@code users.full_name} là cột {@code NOT NULL} (V202608131002), đã hiện trên
 * mọi màn hình quản trị và trên danh bạ nội bộ, và ⛔ phải trường 🔒.
 *
 * <h2>⛔⛔ Vì sao KHÔNG có {@code username} — một quyết định bảo mật, ⛔ phải tiết kiệm</h2>
 *
 * <p>Nơi gọi duy nhất của {@code danhSachTheoQuyen} là endpoint danh sách tác giả, gác bằng
 * {@code cms:article:view}. Đo trong seed RBAC: mã quyền ấy <b>VIEWER và EXECUTIVE cũng có</b>. Trả
 * {@code username} ở đó là trao một danh sách <b>tên đăng nhập hợp lệ</b> cho hai vai trò chỉ-đọc —
 * nửa đầu của một cặp thông tin xác thực, phát cho đúng những người ⛔ cần nó.
 *
 * <p>⚠ Cái giá <b>chấp nhận có ý thức</b>: hai người trùng họ tên thì ô chọn ⛔ phân biệt được. Đây là
 * một khiếm khuyết đã biết, ⛔ phải một chỗ sót. Ngày nó thành vấn đề thật thì cách vá đúng là thêm
 * <b>đơn vị</b> (đã công khai trên danh bạ) chứ ⛔ phải thêm một trường nhạy cảm hơn.
 *
 * <p>⛔ Không email, ⛔ số điện thoại, ⛔ vai trò, ⛔ trạng thái tài khoản — cùng dải cấm
 * {@link EmployeeRef} đã liệt.
 *
 * @param id khoá nội bộ — cần vì {@code articles.author_user_id} lưu đúng khoá đó. ⛔ Không bao giờ ra
 *     tới API; tầng DTO đổi sang {@code publicId}
 * @param publicId định danh ổn định, thứ duy nhất trong record này được phép ra API
 * @param fullName họ tên hiển thị — thứ đặc tả đòi in ra cổng
 */
public record UserRef(Long id, UUID publicId, String fullName) {}
