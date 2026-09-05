package com.songnhue.core.domain.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Access token bị thu hồi trước hạn (bảng {@code token_denylist}).
 *
 * <p>JWT vốn không thu hồi được — đã ký là dùng được tới lúc hết hạn. Với access token 30' thì
 * khoảng trống đó quá dài cho hai tình huống: đổi mật khẩu (nghi bị lộ) và khoá tài khoản. Denylist
 * là cách bịt lại: {@code jti} của token bị ghi vào đây, {@code AuthFilter} kiểm mỗi request.
 *
 * <p>Nằm ở DB chứ không phải Redis vì v1 không có Redis (architecture-review.md §3). Bảng luôn nhỏ:
 * chỉ chứa token <i>còn hạn</i>, hết hạn thì job dọn đi — bản ghi hết hạn có giữ lại cũng vô nghĩa
 * vì bản thân token đã không dùng được nữa.
 *
 * <p>Đăng xuất thường KHÔNG cần ghi vào đây: thu hồi family ở bảng {@code sessions} đã đủ giết access
 * token của phiên đó. Denylist dành cho việc thu hồi cả những token chưa biết thuộc phiên nào.
 */
@Entity
@Table(name = "token_denylist")
public class TokenDenylistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jti", nullable = false)
    private UUID jti;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Sau mốc này bản ghi vô dụng (token tự hết hạn) — job dọn dẹp quét theo cột này. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TokenDenylistEntry() {
        // JPA
    }

    public TokenDenylistEntry(UUID jti, Long userId, Instant expiresAt, SessionRevokeReason reason) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.reason = reason.name();
    }

    public Long getId() {
        return id;
    }

    public UUID getJti() {
        return jti;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
