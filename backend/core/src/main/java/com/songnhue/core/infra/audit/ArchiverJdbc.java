package com.songnhue.core.infra.audit;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bọc {@link JdbcTemplate} chạy bằng vai trò {@code songnhue_archiver} vào một <b>kiểu riêng</b>.
 *
 * <p>⚠⚠ Kiểu riêng này không phải để cho đẹp — nó là cách duy nhất an toàn. Spring Boot tự cấu hình
 * cả {@code DataSource} lẫn {@code JdbcTemplate} kèm điều kiện {@code @ConditionalOnMissingBean}, nên
 * <b>khai thêm một bean thuộc đúng hai kiểu đó sẽ làm Boot ngừng tạo bản chính</b>. Khai
 * {@code DataSource} → cả ứng dụng mất kết nối chính; sửa xong, khai {@code JdbcTemplate} → mọi nơi
 * dùng JDBC (ghi nhật ký kiểm toán, bảng nối vai trò) lặng lẽ chuyển sang chạy bằng vai trò
 * archiver.
 *
 * <p>Cả hai lần đều đổ vỡ ở nơi <i>khác hẳn</i> nguyên nhân: {@code permission denied for table jobs}
 * rồi {@code permission denied for table audit_logs} — không dòng nào nhắc tới DataSource hay
 * JdbcTemplate. Đóng gói vào một kiểu mà Boot không biết tới thì tình huống đó không xảy ra được
 * nữa.
 *
 * @param jdbc chỉ {@code AuditArchiveHandler} dùng — đây là đường duy nhất có quyền xoá nhật ký
 */
public record ArchiverJdbc(JdbcTemplate jdbc) {}
