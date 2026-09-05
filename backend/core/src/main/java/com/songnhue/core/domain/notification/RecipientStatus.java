package com.songnhue.core.domain.notification;

/** Trạng thái gửi tới một người trên một kênh. Khớp {@code ck_notification_recipients_status}. */
public enum RecipientStatus {
    PENDING,
    SENT,
    FAILED,
    /** Kênh đang tắt theo cấu hình, hoặc người nhận thiếu thông tin liên hệ (VD không có email). */
    SKIPPED
}
