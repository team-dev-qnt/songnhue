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
import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.auth.SecurityEventService;
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
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserAdminRepository;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.infra.org.OrgUnitRepository;
import com.songnhue.core.spi.EmployeeDirectoryPort;
import com.songnhue.core.spi.EmployeeRef;
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
    private final EmployeeDirectoryPort employees;
    private final SecurityEventService securityEvents;

    public UserAdminService(
            UserRepository users,
            UserAdminRepository userAdmin,
            OrgUnitRepository orgUnits,
            PasswordPolicyService passwordPolicy,
            AuthorityLoader authorities,
            NotificationService notifications,
            EmployeeDirectoryPort employees,
            SecurityEventService securityEvents) {
        this.users = users;
        this.userAdmin = userAdmin;
        this.orgUnits = orgUnits;
        this.passwordPolicy = passwordPolicy;
        this.authorities = authorities;
        this.notifications = notifications;
        this.employees = employees;
        this.securityEvents = securityEvents;
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
     * Liên kết — hoặc gỡ liên kết — tài khoản với một hồ sơ CBNV. <b>T51.8 · CN-05.1</b>.
     *
     * <h2>⭐ Nửa GHI của một cặp đọc–ghi đã thiếu 28 ngày</h2>
     *
     * <p>Cột {@code users.employee_id} ra đời cùng lược đồ định danh ({@code V202608131002:86}), có
     * chỉ mục, có chú thích, có getter/setter trên {@link User} — và <b>0 đường ghi trong toàn
     * kho</b> (đo 10/09/2026). Đặc tả đặt nó đích danh ở CN-05.1: <i>"liên kết tài khoản với hồ sơ
     * nhân viên (MOD-04, {@code users.employee_id})"</i>. Đây là câu lệnh ghi đầu tiên của nó.
     *
     * <h2>⛔⛔ Vì sao KHÔNG tự liên kết chính mình được ({@code ADM-2018})</h2>
     *
     * <p>Cột này ⛔ không phải một trường hồ sơ, nó là <b>một quyền</b>: vế thứ hai của CN-04.7 suy
     * quyền tự đọc CCCD/lương/số tài khoản thẳng từ nó. Cửa gác ở đây là {@code adm:user:manage} —
     * quyền mà <b>ADMIN có</b>, trong khi {@code hr:employee:view-sensitive} thì đặc tả loại trừ
     * ADMIN <b>tường minh</b> ({@code V202608131007:169}). ⇒ Cho tự liên kết là mở một đường vòng
     * <b>ba cú bấm</b> quanh đúng dòng loại trừ ấy.
     *
     * <p>⚠⚠ Điều này ⛔ <b>không</b> đóng được mọi đường: đo cùng ngày, vai trò ADMIN có
     * {@code is_system = FALSE} và ADMIN mang {@code adm:role:manage}, nên ADMIN vẫn tự thêm được
     * {@code hr:employee:view-sensitive} vào vai trò của chính mình — một lỗ <b>có sẵn từ trước</b>,
     * ⛔ không do lượt này tạo ra. Ghi ở {@code master-tracking.md} T54.4 kèm số đo. Điều lượt này
     * bảo đảm hẹp hơn và đo được: <b>đường mới mở ra ⛔ không rộng thêm một chút nào</b>.
     *
     * <h2>Ba thứ phải xảy ra cùng lượt, và vì sao từng thứ</h2>
     *
     * <ol>
     *   <li>{@link AuthorityLoader#invalidate} — {@code employeeId} nay đi trong
     *       {@link AuthenticatedUser}, tức nằm trong cache TTL 30 giây. Thiếu dòng này thì người vừa
     *       bị <b>GỠ</b> liên kết vẫn đọc được trường 🔒 của hồ sơ cũ thêm nửa phút. Đúng loại lỗi
     *       nghiệm thu <i>"gỡ quyền rồi mà vẫn làm được"</i> mà WS-5 đã trả giá.
     *   <li>{@link SecurityEventType#ACCOUNT_EMPLOYEE_LINK_CHANGED} — {@code audit_logs} <b>có</b>
     *       ghi lượt sửa {@link User}, nhưng một thao tác <i>cấp quyền</i> phải nằm ở nhật ký bảo
     *       mật, nơi có chuông và có mức nguy hiểm. Nó cũng là <b>nửa còn lại</b> của
     *       {@code HR_SENSITIVE_FIELDS_READ}: dòng "ai đọc" một mình ⛔ không nói được <i>người ấy
     *       có quyền đọc từ bao giờ và ai cho</i>.
     *   <li>{@code saveAndFlush} — để {@code uq_users_employee_id} nổ <b>bên trong</b> giao dịch
     *       này. Với {@code save()} thì lượt flush rơi ra ngoài phương thức và ngoại lệ hiện ra ở
     *       một tầng ⛔ không còn biết mình đang liên kết ai.
     * </ol>
     *
     * @param employeePublicId hồ sơ cần liên kết, hoặc {@code null} để <b>gỡ</b> liên kết
     * @return tài khoản sau khi cập nhật
     */
    @Transactional
    public User lienKetHoSo(UUID userPublicId, UUID employeePublicId, ClientInfo client) {
        User user = require(userPublicId);

        AuthContext.current().ifPresent(nguoiThaoTac -> {
            if (nguoiThaoTac.userId().equals(user.getId())) {
                throw new PermissionDeniedException(ErrorCode.ADM_2018);
            }
        });

        if (employeePublicId == null) {
            return goLienKet(user, client);
        }

        // ⛔ `PermissionDeniedException` (AUTH-3002) của ScopeGuard đi thẳng ra ngoài — xem javadoc
        //    của EmployeeDirectoryPort. Rỗng ở đây nghĩa là hồ sơ ⛔ không tồn tại.
        EmployeeRef hoSo = employees
                .timTheoPublicId(employeePublicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        users.findByEmployeeIdAndDeletedAtIsNull(hoSo.id())
                .filter(khac -> !khac.getId().equals(user.getId()))
                .ifPresent(khac -> {
                    throw new ConflictException(ErrorCode.ADM_2017, hoSo.code(), khac.getUsername());
                });

        if (hoSo.id().equals(user.getEmployeeId())) {
            return user; // ⛔ Không ghi nhật ký bảo mật cho một lượt ⛔ không đổi gì.
        }

        user.setEmployeeId(hoSo.id());
        User daLuu = users.saveAndFlush(user);

        authorities.invalidate(daLuu.getPublicId());
        ghiSuKienLienKet(daLuu, hoSo.code(), "GAN", client);
        log.info("Liên kết tài khoản {} với hồ sơ CBNV {}", daLuu.getUsername(), hoSo.code());
        return daLuu;
    }

    /**
     * Hồ sơ CBNV mà một tài khoản đang liên kết — nửa ĐỌC, cho màn hình quản trị tài khoản.
     *
     * @return rỗng khi tài khoản chưa liên kết, hoặc hồ sơ đã bị xoá mềm
     */
    @Transactional(readOnly = true)
    public Optional<EmployeeRef> hoSoNhanSuCua(User user) {
        return employees.timTheoId(user.getEmployeeId());
    }

    /**
     * Hồ sơ CBNV của <b>một danh sách</b> tài khoản — <b>một</b> câu truy vấn, ⛔ không N+1.
     *
     * <p>{@link #list()} trả mọi tài khoản nội bộ trong một lượt, và chốt C3 sắp đẩy con số ấy lên
     * bằng số CBNV của Công ty. Gọi {@link #hoSoNhanSuCua(User)} trong vòng lặp render là một câu
     * truy vấn cho mỗi hàng.
     *
     * @return khoá là {@code users.id}; tài khoản chưa liên kết ⛔ không có mặt trong map
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, EmployeeRef> hoSoNhanSuCua(List<User> danhSach) {
        List<Long> ids = danhSach.stream()
                .map(User::getEmployeeId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        java.util.Map<Long, EmployeeRef> theoHoSo = employees.timTheoIds(ids);

        java.util.Map<Long, EmployeeRef> theoTaiKhoan = new java.util.HashMap<>();
        for (User u : danhSach) {
            EmployeeRef ref = u.getEmployeeId() == null ? null : theoHoSo.get(u.getEmployeeId());
            if (ref != null) {
                theoTaiKhoan.put(u.getId(), ref);
            }
        }
        return theoTaiKhoan;
    }

    private User goLienKet(User user, ClientInfo client) {
        Long cu = user.getEmployeeId();
        if (cu == null) {
            return user;
        }
        // ⚠ Đọc MÃ nhân viên TRƯỚC khi xoá khoá: sau lượt xoá thì ⛔ không còn đường nào đi từ tài
        //   khoản về hồ sơ, và dòng nhật ký sẽ chỉ nói "vừa gỡ một liên kết" mà ⛔ không nói gỡ ai.
        //   Hồ sơ có thể đã xoá mềm ⇒ `timTheoId` rỗng ⇒ ghi "(không còn hồ sơ)" thay vì bỏ trắng.
        String ma = employees.timTheoId(cu).map(EmployeeRef::code).orElse("(không còn hồ sơ)");

        user.setEmployeeId(null);
        User daLuu = users.saveAndFlush(user);

        authorities.invalidate(daLuu.getPublicId());
        ghiSuKienLienKet(daLuu, ma, "GO", client);
        log.info("Gỡ liên kết hồ sơ CBNV {} khỏi tài khoản {}", ma, daLuu.getUsername());
        return daLuu;
    }

    /** ⛔ Chỉ ghi tên tài khoản và MÃ nhân viên — ⛔ không họ tên, ⛔ không một trường 🔒 nào. */
    private void ghiSuKienLienKet(User user, String employeeCode, String hanhDong, ClientInfo client) {
        securityEvents.record(
                SecurityEventType.ACCOUNT_EMPLOYEE_LINK_CHANGED,
                AuthContext.current().map(AuthenticatedUser::username).orElse(null),
                AuthContext.current().map(AuthenticatedUser::userId).orElse(null),
                client == null ? ClientInfo.unknown() : client,
                "{\"targetUsername\":\"%s\",\"employeeCode\":\"%s\",\"action\":\"%s\"}"
                        .formatted(user.getUsername(), employeeCode, hanhDong));
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
     *   <li>{@code adm:role:manage} có <b>đúng 1</b> lượt xuất hiện trong <b>mã nguồn</b> — dòng
     *       miễn kiểm {@code RbacMatrixTest:140}. Một quyền ⛔ chưa endpoint nào đòi. ⚠ Phép đếm đầu
     *       ghi <i>"toàn kho"</i> và sai: {@code git grep} cho <b>3 lượt ở 2 tệp</b>, hai lượt kia ở
     *       {@code .claude/master-tracking.md} — {@code rg} mặc định bỏ qua thư mục ẩn.
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
