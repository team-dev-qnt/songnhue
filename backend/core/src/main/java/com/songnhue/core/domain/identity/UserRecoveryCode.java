package com.songnhue.core.domain.identity;

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
 * Mã khôi phục dùng thay mã TOTP khi mất điện thoại (bảng {@code user_recovery_codes}).
 *
 * <p>Không có mã này thì mất máy = mất tài khoản, và lối thoát duy nhất là nhờ Admin gỡ 2FA — tức là
 * mở ra một đường vòng qua 2FA mà kẻ tấn công cũng dùng được bằng cách gọi điện giả danh. Mã khôi
 * phục biến việc đó thành thao tác tự phục vụ, không phải nhờ vả ai.
 *
 * <p>Chỉ lưu SHA-256; mã gốc hiện đúng một lần lúc đăng ký. Mỗi mã dùng được một lần
 * ({@code usedAt}).
 */
@Entity
@Table(name = "user_recovery_codes")
public class UserRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Cột {@code CHAR(64)} — khai kiểu JDBC để {@code ddl-auto: validate} không báo lệch varchar. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UserRecoveryCode() {
        // JPA
    }

    public UserRecoveryCode(Long userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void markUsed(Instant when) {
        this.usedAt = when;
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
