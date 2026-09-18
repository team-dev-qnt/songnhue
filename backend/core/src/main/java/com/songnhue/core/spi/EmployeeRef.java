package com.songnhue.core.spi;

import java.util.UUID;

/**
 * Một cán bộ nhân viên, nhìn từ ngoài module {@code hr} — T51.8.
 *
 * <p>Cố ý <b>mỏng</b>, và ở đây độ mỏng ⛔ không phải chuyện gọn gàng mà là chuyện pháp lý: hồ sơ
 * CBNV là dữ liệu cá nhân theo NĐ 13/2023, còn bảng {@code employee_sensitive} thì mã hoá
 * AES-256-GCM với khoá ngoài CSDL (quy tắc 10). Một {@code record} đi qua ranh giới module là một
 * đường đọc mà {@code EmployeeSensitiveController} — nơi có quyền riêng và có ghi
 * {@code security_events} mỗi lượt đọc — ⛔ không nhìn thấy. ⇒ Ở đây <b>chỉ</b> những trường đã
 * hiện công khai trên danh bạ nội bộ: mã, họ tên, đơn vị.
 *
 * <p>⛔ Thêm ngày sinh, CCCD, lương, số tài khoản hay bất kỳ trường 🔒 nào vào record này là mở một
 * cửa thứ hai vào bảng nhạy cảm nhất của hệ, đi vòng qua cả quyền lẫn nhật ký.
 *
 * @param id khoá nội bộ — cần vì {@code users.employee_id} lưu đúng khoá đó (⛔ không FK, ranh giới
 *     module). ⛔ Không bao giờ ra tới API
 * @param publicId định danh ổn định, dùng ở API và khi đối chiếu giữa hai module
 * @param code mã nhân viên — <i>"không đổi suốt quá trình công tác"</i> theo đặc tả, nên đây là thứ
 *     duy nhất trong record này an toàn để ghi vào nhật ký
 * @param fullName họ tên hiển thị cho người quản trị chọn đúng người
 */
public record EmployeeRef(Long id, UUID publicId, String code, String fullName) {}

// ⚠ Bản nháp đầu có thêm `orgUnitId` "để màn hình liên kết cảnh báo khi tài khoản và hồ sơ thuộc hai
//   đơn vị khác nhau". Bỏ đi vì ⛔ không màn hình nào đọc nó — luật 15: một trường chưa ai đọc là
//   một lỗi, ⛔ không phải việc để dành. Ngày có màn hình cảnh báo ấy thì thêm lại cùng nơi đọc.
