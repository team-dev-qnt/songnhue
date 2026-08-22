package com.songnhue.core.application.settings;

/**
 * Khoá tham số bảo mật trong bảng {@code settings} (seed ở migration V…1009, nhóm {@code SECURITY}).
 *
 * <p>Gom thành hằng số thay vì rải chuỗi khắp nơi: gõ nhầm một ký tự trong tên khoá thì
 * {@code SettingService} lặng lẽ trả về giá trị dự phòng, hệ thống vẫn chạy — và không ai biết chính
 * sách mật khẩu Admin vừa đặt trên UI thực ra chẳng có tác dụng gì.
 *
 * <p>Giá trị {@code DEFAULT_*} ở đây <b>không phải</b> nơi cấu hình. Chúng chỉ là lưới an toàn khi
 * bảng {@code settings} thiếu dòng, và cố ý đặt đúng bằng giá trị seed để hai nguồn không mâu thuẫn.
 */
public final class SettingKeys {

    // --- Chính sách mật khẩu (M5.15) -----------------------------------------
    public static final String PASSWORD_MIN_LENGTH = "security.password.min-length";
    public static final String PASSWORD_REQUIRE_LETTER_AND_DIGIT = "security.password.require-letter-and-digit";
    public static final String PASSWORD_MAX_AGE_DAYS = "security.password.max-age-days";

    // --- Khoá tài khoản khi đăng nhập sai (§4.1) ------------------------------
    public static final String LOGIN_MAX_FAILED_ATTEMPTS = "security.login.max-failed-attempts";
    public static final String LOGIN_FAILED_WINDOW_MINUTES = "security.login.failed-window-minutes";
    public static final String LOGIN_LOCKOUT_MINUTES = "security.login.lockout-minutes";

    // --- Giờ hành chính, dùng cho cảnh báo đăng nhập bất thường (M5.16, F5) ---
    public static final String OFFICE_HOURS_START = "security.office-hours.start";
    public static final String OFFICE_HOURS_END = "security.office-hours.end";

    // --- Cây tổ chức (T6.1) ---------------------------------------------------
    public static final String ORG_TREE_MAX_DEPTH = "org.tree.max-depth";

    // --- Sao lưu & bảo trì (WS-7) ---------------------------------------------
    /** Chặn mọi thao tác ghi. Bật tự động trong lúc khôi phục dữ liệu (T7.6, M5.11). */
    public static final String MAINTENANCE_MODE = "system.maintenance-mode";

    public static final String BACKUP_RETENTION_DAYS = "backup.retention-days";
    public static final String BACKUP_SCHEDULE_ENABLED = "backup.schedule-enabled";
    /** Ngưỡng coi bản sao lưu gần nhất là quá cũ — nguồn cho cảnh báo duy nhất của backup (T7.3). */
    public static final String BACKUP_STALE_HOURS = "backup.stale-hours";

    // --- Cảnh báo tình hình vận hành (CN-02.11) -------------------------------
    public static final String OPS_OPERATION_STATUS_STALE_DAYS = "ops.operation-status.stale-days";

    // --- Giá trị dự phòng, khớp đúng seed migration ---------------------------
    public static final int DEFAULT_PASSWORD_MIN_LENGTH = 10;
    public static final int DEFAULT_MAX_FAILED_ATTEMPTS = 5;
    public static final int DEFAULT_FAILED_WINDOW_MINUTES = 15;
    public static final int DEFAULT_LOCKOUT_MINUTES = 15;

    /**
     * Số cấp tối đa của cây tổ chức. Spec yêu cầu <b>tối thiểu 5 cấp</b> (Công ty → phòng/Xí nghiệp →
     * tổ đội → …); để 8 là dư biên mà vẫn chặn được vòng lặp sinh cây vô hạn do lỗi lập trình.
     */
    public static final int DEFAULT_ORG_TREE_MAX_DEPTH = 8;

    private SettingKeys() {}
}
