package com.songnhue.core.spi;

/**
 * Kênh gửi thông báo. Bản sao có chủ đích của enum tầng domain — lý do ở {@link NotifySeverity}.
 *
 * <p>v1 bật {@code IN_APP} và {@code EMAIL} (chốt B7). {@code SMS} có sẵn trong danh sách nhưng
 * adapter mặc định tắt: kênh đang tắt thì bị bỏ qua, <b>không phải lỗi</b>.
 */
public enum NotifyChannel {
    IN_APP,
    EMAIL,
    SMS,
    WEB_PUSH
}
