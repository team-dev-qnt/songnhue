package com.songnhue.core.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu entity cần ghi nhật ký kiểm toán tự động (conventions.md §4.3, quy tắc 9 của dự án).
 *
 * <p>Gắn annotation này là đủ — {@code AuditEventListener} bắt mọi thao tác tạo/sửa/xoá ở tầng
 * Hibernate và ghi lại giá trị cũ/mới. <b>Không phải viết lời gọi ghi log ở service</b>: đó chính là
 * thứ hay bị quên đúng ở nhánh xử lý ngoại lệ hoặc ở màn hình sửa nhanh, và nhật ký thiếu bản ghi
 * còn tệ hơn không có nhật ký — nó tạo cảm giác an tâm sai.
 *
 * <p>Không gắn cho entity hạ tầng ({@code Job}, {@code UserSession}, {@code SecurityEvent}): những
 * thứ đó thay đổi liên tục do máy, ghi vào nhật ký nghiệp vụ chỉ làm loãng thứ người ta cần tìm. Sự
 * kiện bảo mật đã có luồng riêng ở bảng {@code security_events}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Mã module ghi vào {@code audit_logs.module} — một trong: core, cms, ops, hyd, hr, adm. */
    String module();

    /** Tên loại đối tượng hiển thị trên màn hình tra cứu. Mặc định lấy tên lớp. */
    String entityType() default "";

    /**
     * Trường <b>không</b> được ghi giá trị vào nhật ký.
     *
     * <p>Dùng cho dữ liệu nhạy cảm: hash mật khẩu, secret TOTP, khoá API. Nhật ký kiểm toán được
     * nhiều người xem hơn bảng gốc, và nó lưu 5 năm — đưa bí mật vào đây là mở rộng phạm vi lộ lọt
     * mà không ai để ý. Trường bị loại vẫn được ghi nhận là "có thay đổi", chỉ giá trị bị thay bằng
     * {@code ***}.
     */
    String[] excludeFields() default {};
}
