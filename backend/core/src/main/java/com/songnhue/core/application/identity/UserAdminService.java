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
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
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

    /**
     * Quyền mở được chính màn hình ma trận phân quyền.
     *
     * <p>⛔ Ghi ở đây thay vì dùng thẳng chuỗi ở chỗ kiểm: bất biến số 4 ({@code ADM-2016}) và
     * annotation {@code @RequirePermission} trên endpoint <b>phải nói cùng một mã</b>. Hai chuỗi rời
     * nhau là đúng chỗ luật 14 mô tả — gõ lệch một ký tự thì bất biến chống tự-khoá im lặng ⛔ không
     * còn canh gì, mà endpoint vẫn chạy bình thường.
     */
    static final String QUYEN_SUA_PHAN_QUYEN = "adm:role:manage";

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
        userAdmin.replaceRoles(user.getId(), roleCodes, nguoiDangThaoTac());

        // Trả nợ WS-5: không có dòng này thì quyền mới (và quyền vừa bị gỡ) chỉ có hiệu lực sau
        // tối đa 30 giây — đúng loại lỗi nghiệm thu "gỡ quyền rồi mà vẫn làm được".
        authorities.invalidate(user.getPublicId());

        log.info("Gán lại vai trò cho {}: {}", user.getUsername(), roleCodes);
    }

    /**
     * Thay toàn bộ quyền của một vai trò — <b>T27.31, CN-05.2</b>.
     *
     * <h2>⭐ Ba mảnh nằm ngủ từ Phase 0, mỗi mảnh viết sẵn cho đúng lượt này</h2>
     *
     * <p>Đo 08/09/2026 trước khi viết một dòng nào:
     *
     * <ul>
     *   <li>{@code adm:role:manage} có <b>đúng 1</b> lượt xuất hiện trong toàn kho — dòng miễn kiểm
     *       {@code RbacMatrixTest:140}. Một quyền ⛔ chưa endpoint nào đòi.
     *   <li>{@code roles.is_system} là <b>một cột ⛔ không ai đọc</b> (luật 15). Bảo đảm "Admin ⛔
     *       không sửa quyền, ⛔ không xoá được" chỉ tồn tại trong <b>một dòng chú thích SQL</b>
     *       ({@code V202608131007:126}) — ⛔ chưa từng được mã nào ép. Đây là người đọc đầu tiên.
     *   <li>{@link AuthorityLoader#invalidateAll()} có <b>0 nơi gọi</b>, và javadoc của nó ghi thẳng
     *       <i>"Gọi khi sửa quyền của một vai trò"</i>. Nó được viết cho lượt này rồi nằm đó 26 ngày.
     * </ul>
     *
     * <h2>⛔⛔ Bốn bất biến — và vì sao KHÔNG bất biến nào trong số đó là thừa</h2>
     *
     * <ol>
     *   <li><b>Vai trò phải tồn tại</b> ⇒ {@code SYS-0004}. Nếu ⛔ không kiểm, câu {@code DELETE …
     *       WHERE role_id = (SELECT id …)} xoá 0 dòng và {@code INSERT} chèn 0 dòng: màn hình báo
     *       <i>lưu thành công</i> cho một vai trò ⛔ không tồn tại (luật 9).
     *   <li><b>⛔ Không sửa vai trò hệ thống</b> ⇒ {@code ADM-2014}. {@code SUPER_ADMIN} là <b>lối
     *       thoát cuối cùng</b> của hệ: mọi bất biến khác ở đây đều có thể bị lách bằng một chuỗi
     *       thao tác đủ dài, còn cái này thì ⛔ không.
     *   <li><b>Mọi mã quyền phải có trong danh mục</b> ⇒ {@code ADM-2015}. ⚠ {@link
     *       UserAdminRepository#replaceRoles} cố ý <i>bỏ qua trong im lặng</i> mã ⛔ không có thật;
     *       ở đây ⛔ <b>không</b> làm vậy — một mã gõ sai lặng lẽ biến mất là cách tạo ra một vai trò
     *       thiếu quyền mà ⛔ không ai biết thiếu từ bao giờ.
     *   <li><b>⛔ Không tự gỡ mất quyền quản trị phân quyền của CHÍNH MÌNH</b> ⇒ {@code ADM-2016}.
     *       Đây là bất biến <b>duy nhất</b> ⛔ không suy ra được từ lược đồ, và là bất biến dễ mất
     *       nhất khi đọc lướt: người đang sửa vai trò mình đang mang, bỏ dấu tick {@code
     *       adm:role:manage}, bấm lưu ⇒ <b>⛔ không ai gỡ lại được nữa</b>, kể cả chính họ. Cửa thoát
     *       còn lại là một tài khoản {@code SUPER_ADMIN} — mà ⛔ không gì bảo đảm hệ thống đang có
     *       một tài khoản như vậy còn đăng nhập được.
     * </ol>
     *
     * <h2>⚠ Vì sao {@code invalidateAll()} chứ ⛔ không phải {@code invalidate(ai đó)}</h2>
     *
     * <p>Sửa quyền của một <i>vai trò</i> ảnh hưởng tới <b>mọi</b> người mang vai trò ấy, và
     * {@code AuthorityLoader} đánh khoá cache theo <i>người dùng</i> — ⛔ không có đường nào đi từ
     * vai trò về danh sách người ngoài một câu truy vấn nữa. Xoá sạch một cache tối đa 1000 mục,
     * TTL 30 giây, trên hệ 200 người dùng nội bộ: rẻ hơn hẳn một câu {@code JOIN} chạy đúng lúc
     * người quản trị đang chờ màn hình phản hồi.
     *
     * <p>⛔ Với ≥2 node thì lượt xoá này chỉ có tác dụng ở node nhận request — node kia vẫn chờ hết
     * TTL 30 giây. Đây ⛔ không phải nợ mới: đúng điều {@code AuthorityLoader} javadoc đã ghi và
     * {@code architecture-review.md} §6.4 đã liệt kê.
     *
     * @return số quyền thật sự đã gắn — người gọi ⛔ không cần, nhưng bài kiểm thì cần một con số
     *     đếm được thay vì một lời khẳng định (luật 32)
     */
    @Transactional
    public int replacePermissionsOfRole(String roleCode, List<String> permissionCodes) {
        boolean heThong = userAdmin
                .laVaiTroHeThong(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        if (heThong) {
            throw new PermissionDeniedException(ErrorCode.ADM_2014, roleCode);
        }

        List<String> maLa = userAdmin.maQuyenKhongCoThat(permissionCodes);
        if (!maLa.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.ADM_2015, String.join(", ", maLa));
        }

        AuthContext.current().ifPresent(nguoiSua -> {
            boolean tuGoQuyenCuaMinh = nguoiSua.roles().contains(roleCode)
                    && nguoiSua.hasAllPermissions(QUYEN_SUA_PHAN_QUYEN)
                    && !permissionCodes.contains(QUYEN_SUA_PHAN_QUYEN);
            if (tuGoQuyenCuaMinh) {
                throw new BusinessRuleException(ErrorCode.ADM_2016, roleCode);
            }
        });

        List<String> khongTrung = permissionCodes.stream().distinct().toList();
        int daGan = userAdmin.replacePermissionsOfRole(roleCode, khongTrung, nguoiDangThaoTac());
        if (daGan != khongTrung.size()) {
            // ⛔ Về nguyên tắc không tới được: `maQuyenKhongCoThat` vừa chạy xong ở trên. Nếu tới
            //   được thì có ai đó vừa xoá một quyền khỏi danh mục giữa hai câu lệnh — thà vỡ ra và
            //   rollback còn hơn ghi xuống một tập quyền khuyết mà màn hình báo là đã lưu.
            throw new IllegalStateException(
                    "Gắn được %d/%d quyền cho vai trò %s".formatted(daGan, khongTrung.size(), roleCode));
        }

        authorities.invalidateAll();
        log.info("Đặt lại quyền cho vai trò {}: {} quyền", roleCode, daGan);
        return daGan;
    }

    /** Toàn bộ danh mục quyền — cột phải của màn hình ma trận cần cả quyền vai trò CHƯA có. */
    @Transactional(readOnly = true)
    public List<PermissionSummary> permissionCatalog() {
        return userAdmin.listPermissions();
    }

    /** Id người đang thao tác cho cột {@code granted_by} — {@code null} khi lượt gọi đến từ nền. */
    private Long nguoiDangThaoTac() {
        return AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
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
