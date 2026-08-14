package com.songnhue.core.common.ratelimit;

import java.time.Duration;

/**
 * Ba nhóm hạn mức theo conventions.md §4.5.
 *
 * <p>Giá trị để ở đây (hằng số trong mã) chứ không ở bảng {@code settings} — khác với đa số tham số
 * khác của hệ thống. Lý do: đây là <b>chốt chặn bảo mật</b>, không phải tham số nghiệp vụ. Nếu để
 * Admin sửa được qua UI thì tài khoản Admin bị chiếm sẽ tự nới hạn mức đăng nhập trước khi dò mật
 * khẩu. Muốn đổi thì phải qua review mã nguồn và deploy.
 *
 * <p>Riêng chính sách khoá tài khoản (số lần sai, thời gian khoá — M5.15) <i>là</i> tham số cấu
 * hình và nằm ở bảng {@code settings}. Hai thứ khác nhau: rate limit chặn ở tầng hạ tầng theo IP,
 * lockout chặn ở tầng nghiệp vụ theo tài khoản.
 */
public enum RateLimitPolicy {

    /** Đăng nhập: 5 lượt / 15 phút — chặn dò mật khẩu. */
    LOGIN("login", 5, Duration.ofMinutes(15)),

    /** API thường: 100 lượt / phút cho mỗi người dùng hoặc IP. */
    API("api", 100, Duration.ofMinutes(1)),

    /** Kết xuất báo cáo: 10 lượt / giờ — mỗi lượt tốn nhiều tài nguyên. */
    EXPORT("export", 10, Duration.ofHours(1));

    private final String prefix;
    private final int limit;
    private final Duration window;

    RateLimitPolicy(String prefix, int limit, Duration window) {
        this.prefix = prefix;
        this.limit = limit;
        this.window = window;
    }

    public int limit() {
        return limit;
    }

    public Duration window() {
        return window;
    }

    /** Khoá đếm: gồm cả tên bucket để ba nhóm không đè lên nhau. */
    public String key(String identity) {
        return prefix + ":" + identity;
    }
}
