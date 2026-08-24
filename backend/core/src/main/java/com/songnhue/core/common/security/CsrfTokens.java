package com.songnhue.core.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.songnhue.core.common.util.HashUtils;

/**
 * Cơ chế CSRF double-submit (conventions.md §4.1).
 *
 * <p><b>Vì sao cần dù access token đi trong header {@code Authorization}.</b> Refresh token nằm ở
 * cookie, mà cookie thì trình duyệt tự gửi kèm — nên riêng {@code /auth/refresh} và
 * {@code /auth/logout} là bị CSRF thật sự. Một trang độc hại chỉ cần dụ người dùng đang đăng nhập
 * mở lên là làm mới được token thay họ. Đặt kiểm cho <i>mọi</i> request thay đổi dữ liệu thay vì
 * chỉ hai đường dẫn đó: rẻ, và không phụ thuộc vào việc sau này ai đó thêm endpoint dùng cookie mà
 * quên bật.
 *
 * <p><b>Cách hoạt động.</b> Máy chủ đặt một chuỗi ngẫu nhiên vào cookie <b>không</b> httpOnly, FE
 * đọc được và gửi lại trong header {@code X-CSRF-Token}. Trang của kẻ tấn công gửi được cookie
 * (trình duyệt tự đính kèm) nhưng <b>không đọc được</b> nó do same-origin policy, nên không dựng
 * được header khớp.
 *
 * <p>{@code SameSite=Strict} trên cả hai cookie là lớp chặn thứ hai. Không dựa vào riêng nó vì
 * SameSite còn có ngoại lệ tuỳ trình duyệt và tuỳ cách điều hướng.
 */
public final class CsrfTokens {

    public static final String HEADER = "X-CSRF-Token";

    /** Tên theo quy ước Angular/axios để thư viện FE tự gắn header được. */
    public static final String COOKIE = "XSRF-TOKEN";

    public static final String REFRESH_COOKIE = "refresh_token";

    /** Đường dẫn hẹp: cookie refresh chỉ được gửi tới đúng nhóm endpoint cần nó. */
    public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private static final int TOKEN_BYTES = 32;

    private CsrfTokens() {}

    public static String issue(HttpServletResponse response, boolean secureCookie, long maxAgeSeconds) {
        String token = HashUtils.randomToken(TOKEN_BYTES);
        // ⚠ httpOnly = FALSE có chủ đích: FE bắt buộc phải ĐỌC được để gửi lại trong header.
        //   Đây không phải sơ suất — bí mật thật sự (refresh token) vẫn nằm ở cookie httpOnly riêng.
        response.addHeader("Set-Cookie", cookie(COOKIE, token, "/", maxAgeSeconds, false, secureCookie));
        return token;
    }

    public static void clear(HttpServletResponse response, boolean secureCookie) {
        response.addHeader("Set-Cookie", cookie(COOKIE, "", "/", 0, false, secureCookie));
        response.addHeader("Set-Cookie", cookie(REFRESH_COOKIE, "", REFRESH_COOKIE_PATH, 0, true, secureCookie));
    }

    public static void setRefreshCookie(
            HttpServletResponse response, String value, long maxAgeSeconds, boolean secureCookie) {
        response.addHeader(
                "Set-Cookie", cookie(REFRESH_COOKIE, value, REFRESH_COOKIE_PATH, maxAgeSeconds, true, secureCookie));
    }

    public static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Tự dựng chuỗi {@code Set-Cookie} thay vì dùng {@link Cookie}.
     *
     * <p>API {@code Cookie} của Servlet không có {@code SameSite} — thuộc tính bắt buộc theo §4.1.
     * Đặt bằng {@code setAttribute("SameSite", …)} thì phụ thuộc vào container hiện thực ra sao;
     * ghi thẳng header là chắc chắn và đọc ra cũng thấy ngay đủ các cờ.
     */
    private static String cookie(
            String name, String value, String path, long maxAgeSeconds, boolean httpOnly, boolean secure) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(name).append('=').append(value).append("; Path=").append(path);
        sb.append("; Max-Age=").append(maxAgeSeconds);
        sb.append("; SameSite=Strict");
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (secure) {
            // Local chạy http:// nên không đặt Secure — đặt vào là trình duyệt bỏ luôn cookie
            sb.append("; Secure");
        }
        return sb.toString();
    }
}
