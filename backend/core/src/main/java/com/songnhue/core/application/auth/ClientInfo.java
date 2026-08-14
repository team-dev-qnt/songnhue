package com.songnhue.core.application.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

/**
 * Thông tin về nơi phát ra request — đi vào {@code sessions} và {@code security_events}.
 *
 * <p>Tách thành record để tầng application <b>không</b> phải nhận {@code HttpServletRequest}. Kéo
 * đối tượng của servlet xuống tầng nghiệp vụ là thứ khiến về sau không gọi lại được các dịch vụ này
 * từ job nền hay từ test.
 *
 * @param ipAddress IP nguồn — cột kiểu {@code inet}, sai định dạng là DB từ chối cả câu lệnh
 * @param userAgent chuỗi trình duyệt, đã cắt cho vừa cột 500 ký tự
 * @param deviceLabel nhãn thiết bị hiển thị ở màn hình quản lý phiên (M5.14)
 */
public record ClientInfo(String ipAddress, String userAgent, String deviceLabel) {

    private static final int USER_AGENT_MAX = 500;
    private static final int DEVICE_LABEL_MAX = 120;

    public static ClientInfo unknown() {
        return new ClientInfo(null, null, null);
    }

    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = trim(request.getHeader("User-Agent"), USER_AGENT_MAX);
        return new ClientInfo(clientIp(request), userAgent, trim(deviceLabelOf(userAgent), DEVICE_LABEL_MAX));
    }

    /**
     * IP thật của client.
     *
     * <p>Chỉ lấy phần tử đầu của {@code X-Forwarded-For} và chỉ tin được khi đứng sau nginx của mình
     * (§4.5) — nginx phải <b>ghi đè</b> header này chứ không nối thêm, nếu không thì client tự đặt
     * header là ghi được IP giả vào nhật ký bảo mật.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Nhãn thiết bị dễ đọc, đủ để người dùng nhận ra phiên nào là của mình khi đăng xuất từ xa.
     *
     * <p>Cố ý thô sơ: mục đích là "máy Windows ở cơ quan" hay "điện thoại", không phải nhận dạng
     * chính xác. Kéo cả thư viện phân tích User-Agent về chỉ để hiển thị một dòng chữ là không đáng.
     */
    private static String deviceLabelOf(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        String ua = userAgent.toLowerCase(java.util.Locale.ROOT);
        String platform = "Thiết bị khác";
        if (ua.contains("android")) {
            platform = "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad")) {
            platform = "iOS";
        } else if (ua.contains("windows")) {
            platform = "Windows";
        } else if (ua.contains("mac os")) {
            platform = "macOS";
        } else if (ua.contains("linux")) {
            platform = "Linux";
        }

        String browser = "";
        if (ua.contains("edg/")) {
            browser = " · Edge";
        } else if (ua.contains("chrome/") && !ua.contains("chromium")) {
            browser = " · Chrome";
        } else if (ua.contains("firefox/")) {
            browser = " · Firefox";
        } else if (ua.contains("safari/") && !ua.contains("chrome/")) {
            browser = " · Safari";
        }
        return platform + browser;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
