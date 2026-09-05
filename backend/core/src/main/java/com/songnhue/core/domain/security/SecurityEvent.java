package com.songnhue.core.domain.security;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Sự kiện bảo mật (bảng {@code security_events}) — luồng riêng, <b>tách khỏi audit nghiệp vụ</b>.
 *
 * <p>Tách vì hai thứ trả lời hai câu hỏi khác nhau và có người đọc khác nhau. Audit trả lời "ai đã
 * sửa bản ghi nào" — đọc khi tranh chấp số liệu. Security event trả lời "có ai đang thử phá cửa
 * không" — đọc bằng cảnh báo tự động, gần thời gian thực (M5.16). Trộn chung thì tín hiệu tấn công
 * chìm nghỉm giữa hàng nghìn dòng sửa dữ liệu bình thường.
 *
 * <p>Append-only: role {@code songnhue_app} không có UPDATE/DELETE trên bảng này (migration V…1006).
 */
@Entity
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** Null khi chưa xác định được người dùng (đăng nhập sai tên tài khoản chẳng hạn). */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** JSON tự do — ⛔ TUYỆT ĐỐI không đưa mật khẩu, token hay secret vào đây. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private String detail;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected SecurityEvent() {
        // JPA
    }

    public SecurityEvent(SecurityEventType type, String username, Long userId) {
        this.eventType = type.name();
        this.severity = type.severity().name();
        this.username = username;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
