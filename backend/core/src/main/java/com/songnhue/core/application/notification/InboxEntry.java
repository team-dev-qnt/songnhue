package com.songnhue.core.application.notification;

import java.time.Instant;

import com.songnhue.core.domain.notification.NotificationSeverity;

/**
 * Một dòng trong hộp thư — đã ghép nội dung thông báo với trạng thái đọc của người dùng.
 *
 * <p>Tiêu đề và nội dung nằm ở bảng {@code notifications}, còn "đã đọc chưa" nằm ở
 * {@code notification_recipients}. Trả entity thô thì màn hình hộp thư phải gọi thêm một lượt cho
 * mỗi dòng — bài toán N+1 kinh điển, mà hộp thư là chỗ mở nhiều nhất trong ngày.
 *
 * @param recipientId id dòng người nhận, dùng cho lệnh đánh dấu đã đọc
 */
public record InboxEntry(
        Long recipientId,
        String title,
        String body,
        String linkUrl,
        NotificationSeverity severity,
        String eventType,
        boolean broadcast,
        Instant createdAt,
        Instant readAt) {}
