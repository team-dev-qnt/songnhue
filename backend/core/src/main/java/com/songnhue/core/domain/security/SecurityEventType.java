package com.songnhue.core.domain.security;

/**
 * Danh mục sự kiện bảo mật + mức nghiêm trọng đi kèm.
 *
 * <p>Mức nghiêm trọng gắn ngay vào loại sự kiện chứ không để nơi gọi tự chọn: cùng một loại sự kiện
 * mà chỗ này ghi WARNING chỗ kia ghi INFO thì luật cảnh báo bên Grafana (WS-7) không viết được.
 *
 * <p>⚠ Thêm loại mới thì phải xem lại luật cảnh báo ở {@code docs/runbook/} — sự kiện DANGER/CRITICAL
 * mà không ai nhận thông báo thì ghi cũng như không.
 */
public enum SecurityEventType {

    // --- Đăng nhập ------------------------------------------------------------
    LOGIN_SUCCESS(Severity.INFO),
    LOGIN_FAILED(Severity.WARNING),
    /** Vượt ngưỡng số lần sai → khoá tạm (M5.16 — cảnh báo Admin gần thời gian thực). */
    LOGIN_LOCKED(Severity.DANGER),
    /** Đăng nhập ngoài khung giờ hành chính đọc từ `settings` (M5.16, chốt F5). */
    LOGIN_OUTSIDE_OFFICE_HOURS(Severity.WARNING),
    LOGIN_DISABLED_ACCOUNT(Severity.WARNING),

    // --- Token & phiên --------------------------------------------------------
    /** ⚠ Nghiêm trọng nhất nhóm này: gần như chắc chắn refresh token đã bị đánh cắp (§4.1). */
    REFRESH_REUSE_DETECTED(Severity.CRITICAL),
    SESSION_REVOKED(Severity.INFO),
    LOGOUT(Severity.INFO),

    // --- Mật khẩu & 2FA -------------------------------------------------------
    PASSWORD_CHANGED(Severity.INFO),
    TWO_FACTOR_ENROLLED(Severity.INFO),
    TWO_FACTOR_FAILED(Severity.WARNING),
    TWO_FACTOR_RECOVERY_USED(Severity.DANGER),

    // --- Phân quyền -----------------------------------------------------------
    /** Thiếu permission — tầng 2 chặn (AUTH-3001). */
    ACCESS_DENIED_PERMISSION(Severity.WARNING),
    /** Truy cập dữ liệu ngoài đơn vị — tầng 3 chặn (AUTH-3002). Đáng ngờ hơn hẳn thiếu quyền. */
    ACCESS_DENIED_SCOPE(Severity.DANGER),
    CSRF_REJECTED(Severity.DANGER),

    // --- Quản trị -------------------------------------------------------------
    /** Kích hoạt tài khoản quản trị tối cao bằng lệnh bootstrap (T5.7). */
    ADMIN_BOOTSTRAP(Severity.DANGER),

    // --- Sao lưu & khôi phục (WS-7) -------------------------------------------
    /** Bật/tắt chế độ bảo trì — cả hệ thống ngừng nhận ghi (T7.6). */
    MAINTENANCE_MODE_CHANGED(Severity.DANGER),
    BACKUP_CREATED(Severity.INFO),
    /**
     * ⚠ Sao lưu hỏng là sự kiện <b>nghiêm trọng</b>, không phải cảnh báo thường. Không có PITR
     * (architecture-review.md §6.5) nên bản dump đêm là đường phục hồi duy nhất — mất nó là hệ thống
     * đang chạy không lưới an toàn, dù mọi thứ khác vẫn xanh.
     */
    BACKUP_FAILED(Severity.CRITICAL),
    /** Bắt đầu ghi đè toàn bộ CSDL bằng một bản dump (M5.11). */
    DATABASE_RESTORE_STARTED(Severity.CRITICAL),
    DATABASE_RESTORE_FINISHED(Severity.CRITICAL),
    DATABASE_RESTORE_FAILED(Severity.CRITICAL);

    private final Severity severity;

    SecurityEventType(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }

    /** Khớp CHECK {@code ck_security_events_severity}. */
    public enum Severity {
        INFO,
        WARNING,
        DANGER,
        CRITICAL
    }
}
