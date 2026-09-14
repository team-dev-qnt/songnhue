package com.songnhue.core.spi;

/**
 * Một người đứng đầu đơn vị, dạng module nghiệp vụ đọc được — CN-04.1.
 *
 * <h2>⛔⛔ Ba trường, và ⛔ KHÔNG có số điện thoại / email</h2>
 *
 * <p>Sơ đồ tổ chức cần <b>tên</b> và <b>chức danh</b> để vẽ lên nút; nó ⛔ không cần liên hệ. Bảng
 * {@code org_unit_leaders} <b>có</b> hai cột ấy và cổng công khai <b>có</b> công bố chúng, nhưng
 * thêm vào đây là bày ra một trường ⛔ không ai đọc — luật 15 — và mỗi trường thừa ở một cổng SPI
 * là một trường sẽ lọt vào response của màn hình kế tiếp mà ⛔ không ai xét lại.
 *
 * <h2>⛔ {@code title} là ô TỰ DO, ⛔ không phải một thang bậc</h2>
 *
 * <p>T50.6 đã trả giá cho chuyện này: ⛔ <b>không</b> suy tầng lãnh đạo bằng cách so chuỗi
 * <i>"Chủ tịch" &gt; "Tổng Giám đốc" &gt; "Phó TGĐ"</i>. Thứ tự hiển thị lấy nguyên
 * {@code sortOrder} mà Công ty tự sắp, và cây lãnh đạo trong một nút là <b>PHẲNG</b>.
 */
public record OrgUnitLeaderRef(String fullName, String title, int sortOrder) {}
