package com.songnhue.core.domain.notification;

/**
 * Kênh gửi thông báo. Khớp ràng buộc {@code ck_notification_recipients_channel}.
 *
 * <p>v1 bật {@link #IN_APP} và {@link #EMAIL}; {@link #SMS} và {@link #WEB_PUSH} có sẵn chỗ nhưng
 * tắt theo cấu hình (chốt B7 — bỏ SMS ở v1). Giữ hằng số ở đây thay vì thêm sau: bảng đã có ràng
 * buộc cho cả bốn, và thêm giá trị vào enum mà quên migration là INSERT bị từ chối lúc chạy.
 */
public enum NotificationChannel {
    IN_APP,
    EMAIL,
    SMS,
    WEB_PUSH
}
