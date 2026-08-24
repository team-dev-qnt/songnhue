package com.songnhue.core.domain.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một mắt xích của chuỗi refresh token (bảng {@code sessions}).
 *
 * <p><b>Mô hình "family"</b> (conventions.md §4.1): một lần đăng nhập sinh ra một {@code familyId}.
 * Mỗi lần làm mới token, bản ghi cũ bị đánh dấu {@code ROTATED} và một bản ghi mới cùng family được
 * thêm vào. Nhờ vậy phát hiện được token bị đánh cắp: nếu một refresh token <i>đã xoay</i> lại được
 * dùng lần nữa thì hoặc kẻ trộm dùng bản sao, hoặc chủ nhân dùng lại bản đã bị trộm — cả hai trường
 * hợp đều phải <b>thu hồi toàn bộ family</b>, không cách nào phân biệt được ai là ai.
 *
 * <p>Access token mang {@code fid} = familyId. Đăng xuất từ xa (M5.14) thu hồi cả family nên access
 * token của phiên đó chết ngay lập tức, không phải chờ hết 30 phút.
 *
 * <p>Chỉ lưu <b>SHA-256</b> của refresh token. Bản gốc chỉ tồn tại trong cookie httpOnly của trình
 * duyệt — lộ cả bảng {@code sessions} cũng không dựng lại được token nào.
 */
@Entity
@Table(name = "sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    /** Cột {@code CHAR(64)} — khai kiểu JDBC để {@code ddl-auto: validate} không báo lệch varchar. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "parent_session_id")
    private Long parentSessionId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 50)
    private String revokedReason;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public void setFamilyId(UUID familyId) {
        this.familyId = familyId;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public void setRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    public Long getParentSessionId() {
        return parentSessionId;
    }

    public void setParentSessionId(Long parentSessionId) {
        this.parentSessionId = parentSessionId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
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

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        this.deviceLabel = deviceLabel;
    }

    // -------------------------------------------------------------------------

    /** Đánh dấu đã xoay: bản ghi này hết dùng được, nhưng family vẫn sống nhờ bản ghi con. */
    public void markRotated(Instant when) {
        this.rotatedAt = when;
        this.revokedAt = when;
        this.revokedReason = SessionRevokeReason.ROTATED.name();
    }

    public void revoke(SessionRevokeReason reason, Instant when) {
        this.revokedAt = when;
        this.revokedReason = reason.name();
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /**
     * Đã xoay rồi mà token vẫn được dùng lại — dấu hiệu token bị đánh cắp.
     *
     * <p>Phân biệt với "đã thu hồi vì lý do khác" (đăng xuất, đổi mật khẩu): chỉ trạng thái ROTATED
     * mới kích hoạt thu hồi cả family, vì chỉ nó chứng tỏ có <i>hai</i> bên cùng giữ token.
     */
    public boolean isReuseOfRotatedToken() {
        return rotatedAt != null;
    }
}
