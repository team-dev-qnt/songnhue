package com.songnhue.core.domain.identity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Đăng ký xác thực hai bước bằng TOTP (bảng {@code user_totp}).
 *
 * <p>Secret lưu <b>mã hoá AES-256-GCM</b> qua {@code CryptoService}, khoá nằm ngoài DB. Khác mật
 * khẩu ở chỗ secret TOTP <i>không</i> băm được — máy chủ phải đọc lại được nó để tính mã, nên mã hoá
 * là lớp bảo vệ duy nhất. Lộ bảng này mà không lộ khoá thì vẫn an toàn.
 *
 * <p>{@code lastUsedStep} chống dùng lại mã: một mã TOTP sống 30 giây, đủ để kẻ đứng xem qua vai
 * hoặc bắt được request nhập lại lần nữa. Ghi lại bước thời gian đã dùng và từ chối bước ≤ nó là bịt
 * hẳn khoảng trống đó.
 */
@Entity
@Table(name = "user_totp")
public class UserTotp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "secret_encrypted", nullable = false)
    private String secretEncrypted;

    @Column(name = "key_id", nullable = false, length = 20)
    private String keyId;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt = Instant.now();

    /** Null = mới sinh secret nhưng người dùng chưa nhập đúng mã lần nào → chưa tính là đã bật 2FA. */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public void setSecretEncrypted(String secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Long getLastUsedStep() {
        return lastUsedStep;
    }

    public void setLastUsedStep(Long lastUsedStep) {
        this.lastUsedStep = lastUsedStep;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getVersion() {
        return version;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
