package com.songnhue.core.domain.notification;

/** Mức độ thông báo. Khớp ràng buộc {@code ck_notifications_severity} trong DB. */
public enum NotificationSeverity {
    INFO,
    WARNING,
    DANGER,
    CRITICAL
}
