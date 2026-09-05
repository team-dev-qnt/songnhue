package com.songnhue.core.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Địa chỉ cổng công khai và bí mật để yêu cầu nó dựng lại trang — WS-16/T16.5.
 *
 * <p><b>Tính năng bật riêng, không phải cấu hình bắt buộc.</b> Để trống là hợp lệ: khi đó backend
 * không gọi Next.js, và trang công khai tự làm mới theo chu kỳ ISR (vài phút). Bắt buộc phải có thì
 * mọi môi trường dev không chạy `public-web` sẽ không khởi động được, đổi một tiện ích lấy một rào
 * cản.
 *
 * <p>⚠ {@code revalidateSecret} là <b>bí mật</b>: không log, không trả ra API, không nằm trong bản
 * xuất cấu hình (conventions.md §4.7). Nó ở đây chứ không ở bảng {@code settings} chính vì lẽ đó —
 * `settings` có màn hình xem được.
 */
@Component
@ConfigurationProperties(prefix = "app.portal")
public class PortalProperties {

    /** VD {@code http://public-web:3000}. Rỗng = tắt việc gọi dựng lại trang. */
    private String baseUrl = "";

    private String revalidateSecret = "";

    /** Ngắn có chủ đích: đây là việc phụ, không được kéo dài một job chỉ vì cổng đang bận. */
    private int timeoutSeconds = 10;

    /** Đủ cấu hình để gọi hay chưa — cả hai vế đều phải có, thiếu một là Next trả 401 hoặc 503. */
    public boolean isEnabled() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(revalidateSecret);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRevalidateSecret() {
        return revalidateSecret;
    }

    public void setRevalidateSecret(String revalidateSecret) {
        this.revalidateSecret = revalidateSecret;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
