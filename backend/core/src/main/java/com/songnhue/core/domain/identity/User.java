package com.songnhue.core.domain.identity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Tài khoản đăng nhập (bảng {@code users}, migration V…1002).
 *
 * <p>Cố ý <b>không</b> ánh xạ quan hệ tới {@code roles} bằng {@code @ManyToMany}: bảng nối
 * {@code user_roles} có thêm {@code granted_at}/{@code granted_by} mà {@code @ManyToMany} không ghi
 * được, và mỗi request chỉ cần <i>tập mã quyền</i> chứ không cần cả đồ thị đối tượng. Quyền được nạp
 * bằng một truy vấn phẳng ở {@code UserAuthorityRepository}.
 *
 * <p>Cũng không ánh xạ {@code org_unit_id} thành quan hệ tới {@code OrgUnit}: WS-5 chỉ cần id và
 * materialized path để lọc phạm vi, kéo cả entity đơn vị vào chỉ thêm truy vấn thừa cho mọi request.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    /**
     * Giá trị hash không thể khớp bất kỳ mật khẩu nào (BCrypt luôn bắt đầu bằng {@code $2}). Đây là
     * giá trị migration seed cho tài khoản chưa kích hoạt.
     */
    public static final String NO_PASSWORD = "!";

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = UserStatus.PENDING_ACTIVATION.name();

    /** Cờ ép 2FA cho từng tài khoản, cộng thêm vào luật theo vai trò (Admin/Admin HR — G12). */
    @Column(name = "two_factor_required", nullable = false)
    private boolean twoFactorRequired;

    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount;

    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Cột {@code inet} của Postgres — khai báo kiểu JDBC để Hibernate không hiểu nhầm là varchar. */
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "last_login_ip")
    private String lastLoginIp;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(Instant passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status.name();
    }

    public boolean isTwoFactorRequired() {
        return twoFactorRequired;
    }

    public void setTwoFactorRequired(boolean twoFactorRequired) {
        this.twoFactorRequired = twoFactorRequired;
    }

    public short getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(short failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public Instant getLastFailedLoginAt() {
        return lastFailedLoginAt;
    }

    public void setLastFailedLoginAt(Instant lastFailedLoginAt) {
        this.lastFailedLoginAt = lastFailedLoginAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    // -------------------------------------------------------------------------

    /** Đang bị khoá tạm do đăng nhập sai nhiều lần (§4.1). */
    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Được phép đăng nhập không — <b>chỉ</b> {@code ACTIVE}.
     *
     * <p>{@code PENDING_ACTIVATION} cũng bị chặn ở đây dù mật khẩu {@code '!'} vốn đã không khớp:
     * chặn tường minh thì về sau ai đó lỡ đặt mật khẩu cho tài khoản chưa kích hoạt cũng không mở ra
     * đường đăng nhập ngoài ý muốn.
     */
    public boolean canAuthenticate() {
        return UserStatus.ACTIVE.name().equals(status) && !isDeleted();
    }
}
