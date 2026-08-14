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

    // --- Giá trị dự phòng, khớp đúng seed migration ---------------------------
    public static final int DEFAULT_PASSWORD_MIN_LENGTH = 10;
    public static final int DEFAULT_MAX_FAILED_ATTEMPTS = 5;
    public static final int DEFAULT_FAILED_WINDOW_MINUTES = 15;
    public static final int DEFAULT_LOCKOUT_MINUTES = 15;

    private SettingKeys() {}
}
