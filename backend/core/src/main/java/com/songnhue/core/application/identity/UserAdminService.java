package com.songnhue.core.application.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.notification.NotificationRequest;
import com.songnhue.core.application.notification.NotificationService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.identity.UserStatus;
import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationSeverity;
import com.songnhue.core.infra.identity.UserAdminRepository;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.infra.org.OrgUnitRepository;
import com.songnhue.core.spi.UserDirectoryPort;

/**
 * Quản trị tài khoản (CN-05.1) — <b>lát cắt dọc nghiệm thu Phase 0</b> (T6.15).
 *
 * <p>Chức năng này đi qua đủ mọi thứ nền tảng mà WS-4 → WS-6 đã dựng, nên nó vừa là tính năng thật
 * vừa là bằng chứng nền tảng chạy được:
 *
 * <ul>
 *   <li><b>Phân quyền tầng 2</b> — mỗi endpoint khai {@code @RequirePermission} riêng.
 *   <li><b>Nhật ký kiểm toán</b> — tự động, vì {@code User} mang {@code @Audited}; hash mật khẩu bị
 *       che nhưng vẫn thấy trường đó có đổi.
 *   <li><b>Thông báo</b> — người bị khoá/mở khoá tài khoản được báo.
 *   <li><b>Cache phân quyền</b> — đổi vai trò là gọi {@link AuthorityLoader#invalidate} ngay.
 * </ul>
 *
 * <p><b>Đổi phân quyền phải có hiệu lực ngay</b> (trả nợ WS-5). Không gọi {@code invalidate} thì
 * người vừa bị gỡ quyền vẫn thao tác được tới hết TTL cache 30 giây — với màn hình phân quyền chi
 * tiết như MOD-05, "gỡ quyền rồi mà vẫn làm được" là một lỗi nghiệm thu.
 */
@Service
public class UserAdminService implements UserDirectoryPort {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository users;
    private final UserAdminRepository userAdmin;
    private final OrgUnitRepository orgUnits;
    private final PasswordPolicyService passwordPolicy;
    private final AuthorityLoader authorities;
    private final NotificationService notifications;

    public UserAdminService(
            UserRepository users,
            UserAdminRepository userAdmin,
            OrgUnitRepository orgUnits,
            PasswordPolicyService passwordPolicy,
            AuthorityLoader authorities,
            NotificationService notifications) {
        this.users = users;
        this.userAdmin = userAdmin;
        this.orgUnits = orgUnits;
        this.passwordPolicy = passwordPolicy;
        this.authorities = authorities;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return users.findAllByDeletedAtIsNullOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public User get(UUID publicId) {
        return require(publicId);
    }

    /**
     * Tạo tài khoản với mật khẩu tạm.
     *
     * <p>{@code mustChangePassword} bật sẵn: người tạo tài khoản biết mật khẩu tạm, nên nó phải chết
     * ngay sau lần đăng nhập đầu (§4.1).
     */
    @Transactional
    public User create(String username, String fullName, String email, UUID orgUnitPublicId, String temporaryPassword) {
        if (users.findActiveByUsername(username).isPresent()) {
            throw new ConflictException(ErrorCode.SYS_0005);
        }
        Long orgUnitId = orgUnits.findByPublicIdAndDeletedAtIsNull(orgUnitPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004))
                .getId();

        // ⭐ "temporaryPassword" — ĐÚNG tên trường của `CreateUserRequest`. Bản trước để
        //    PasswordPolicyService ghi cứng "newPassword", nên mọi lỗi 422 ở màn hình Thêm tài
        //    khoản trỏ vào một trường không có trên biểu mẫu và biến mất không dấu vết.
        passwordPolicy.validate(temporaryPassword, username, "temporaryPassword");

        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setOrgUnitId(orgUnitId);
        user.setPasswordHash(passwordPolicy.hash(temporaryPassword));
        user.setMustChangePassword(true);
        user.setStatus(UserStatus.ACTIVE);

        User saved = users.save(user);
        log.info("Tạo tài khoản {} thuộc đơn vị {}", username, orgUnitId);
        return saved;
    }

    @Transactional
    public User update(UUID publicId, String fullName, String email, String phone) {
        User user = require(publicId);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        return users.save(user);
    }

    /**
     * Khoá hoặc mở khoá tài khoản.
     *
     * <p>Khoá xong phải <b>vô hiệu cache phân quyền</b> ngay, nếu không người bị khoá vẫn dùng được
     * access token hiện có tới khi nó hết hạn.
     */
    @Transactional
    public User setStatus(UUID publicId, UserStatus status) {
        User user = require(publicId);
        user.setStatus(status);
        User saved = users.save(user);

        authorities.invalidate(saved.getPublicId());
        notifyStatusChange(saved, status);

        log.info("Đổi trạng thái tài khoản {} sang {}", saved.getUsername(), status);
        return saved;
    }

    /**
     * Gán lại toàn bộ vai trò cho một tài khoản.
     *
     * <p>Thay cả tập chứ không thêm/bớt từng cái: màn hình phân quyền hiển thị trạng thái mong muốn,
     * và gửi nguyên trạng thái đó lên thì không có chuyện hai người sửa cùng lúc rồi ra kết quả lai.
     */
    @Transactional
    public void assignRoles(UUID publicId, List<String> roleCodes) {
        User user = require(publicId);
        userAdmin.replaceRoles(user.getId(), roleCodes);

        // Trả nợ WS-5: không có dòng này thì quyền mới (và quyền vừa bị gỡ) chỉ có hiệu lực sau
        // tối đa 30 giây — đúng loại lỗi nghiệm thu "gỡ quyền rồi mà vẫn làm được".
        authorities.invalidate(user.getPublicId());

        log.info("Gán lại vai trò cho {}: {}", user.getUsername(), roleCodes);
    }

    @Transactional(readOnly = true)
    public List<String> rolesOf(UUID publicId) {
        return userAdmin.findRoleCodes(require(publicId).getId());
    }

    /** Danh mục vai trò kèm số quyền — cột trái của màn hình phân quyền. */
    @Transactional(readOnly = true)
    public List<RoleSummary> roleCatalog() {
        return userAdmin.listRoles();
    }

    /** Mã quyền của một vai trò — nguồn dựng ma trận phân quyền. */
    @Transactional(readOnly = true)
    public List<String> permissionsOfRole(String roleCode) {
        return userAdmin.permissionsOfRole(roleCode);
    }

    @Transactional
    public void delete(UUID publicId) {
        User user = require(publicId);
        user.markDeleted(Instant.now());
        users.save(user);
        authorities.invalidate(user.getPublicId());
    }

    private User require(UUID publicId) {
        return users.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private void notifyStatusChange(User user, UserStatus status) {
        boolean disabled = status != UserStatus.ACTIVE;
        notifications.notify(new NotificationRequest(
                disabled ? "ACCOUNT_DISABLED" : "ACCOUNT_ENABLED",
                disabled ? "Tài khoản của bạn đã bị khoá" : "Tài khoản của bạn đã được mở khoá",
                "Trạng thái tài khoản %s vừa được chuyển sang %s.".formatted(user.getUsername(), status),
                disabled ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
                null,
                "User",
                user.getId(),
                List.of(),
                List.of(user.getId()),
                null,
                List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)));
    }

    // ---- Hợp đồng cho module nghiệp vụ (core.spi) ----------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> internalIdOf(UUID publicId) {
        return users.findByPublicIdAndDeletedAtIsNull(publicId).map(User::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> publicIdOf(Long internalId) {
        if (internalId == null) {
            return Optional.empty();
        }
        return users.findById(internalId).filter(u -> !u.isDeleted()).map(User::getPublicId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, UUID> publicIdsOf(java.util.Collection<Long> internalIds) {
        if (internalIds == null || internalIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return users.findAllById(internalIds).stream()
                .filter(u -> !u.isDeleted())
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getPublicId));
    }
}
