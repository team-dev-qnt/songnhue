package com.songnhue.core.domain.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một lượt gửi: (thông báo, người nhận, kênh).
 *
 * <p>Trạng thái để ở đây chứ không ở {@link Notification} vì mỗi kênh hỏng độc lập — email có thể
 * thất bại trong khi thông báo trên giao diện vẫn tới nơi. Gộp trạng thái vào thông báo thì một
 * người không nhận được email sẽ khiến cả thông báo bị đánh dấu hỏng cho mọi người.
 *
 * <p>Chỉ mục duy nhất {@code uq_notification_recipients (notification_id, user_id, channel)} là chốt
 * khử trùng lặp cuối cùng: bộ tìm người nhận theo G11 hợp nhiều nguồn (nhóm Ban điều hành ∪ người
 * phụ trách công trình) nên một người rất dễ xuất hiện hai lần.
 */
@Entity
@Table(name = "notification_recipients")
public class NotificationRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecipientStatus status = RecipientStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected NotificationRecipient() {}

    public NotificationRecipient(Long notificationId, Long userId, NotificationChannel channel) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.channel = channel;
    }

    public void markSent() {
        this.status = RecipientStatus.SENT;
        this.sentAt = Instant.now();
        this.attempts++;
        this.errorMessage = null;
    }

    public void markFailed(String error) {
        this.status = RecipientStatus.FAILED;
        this.attempts++;
        this.errorMessage = error == null || error.length() <= 500 ? error : error.substring(0, 500);
    }

    /** Kênh đang tắt, hoặc người nhận không có địa chỉ trên kênh đó. Không phải lỗi. */
    public void markSkipped(String reason) {
        this.status = RecipientStatus.SKIPPED;
        this.errorMessage = reason;
    }

    public void markRead() {
        if (readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public RecipientStatus getStatus() {
        return status;
    }

    public short getAttempts() {
        return attempts;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
