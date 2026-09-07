package com.songnhue.core.domain.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một thông báo — pattern P4 (implement.md §2).
 *
 * <p>Nội dung thông báo tách khỏi <b>danh sách người nhận</b> ({@link NotificationRecipient}): cùng
 * một sự kiện gửi tới nhiều người trên nhiều kênh, và mỗi cặp (người, kênh) có trạng thái gửi riêng.
 * Gộp làm một bảng thì nội dung bị nhân bản theo số người nhận, và sửa tiêu đề sau khi gửi trở thành
 * việc không làm được nhất quán.
 *
 * <p>Không kế thừa {@code BaseEntity}: thông báo đã gửi thì không sửa, không xoá mềm, không có khoá
 * lạc quan. Gửi nhầm thì gửi thông báo đính chính — sửa lại nội dung người ta đã đọc là điều tệ hơn.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    /** Mã sự kiện nghiệp vụ, VD {@code MAINTENANCE_ASSIGNED}, {@code HYDRO_THRESHOLD_EXCEEDED}. */
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private NotificationSeverity severity = NotificationSeverity.INFO;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "ref_type", length = 50)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    /** TRUE = thông báo hệ thống do Admin gửi (M5.13), phân biệt với cảnh báo nghiệp vụ tự sinh. */
    @Column(name = "is_broadcast", nullable = false)
    private boolean broadcast;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    protected Notification() {}

    public Notification(String eventType, String title, NotificationSeverity severity) {
        this.eventType = eventType;
        this.title = title;
        this.severity = severity;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getEventType() {
        return eventType;
    }

    public NotificationSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
