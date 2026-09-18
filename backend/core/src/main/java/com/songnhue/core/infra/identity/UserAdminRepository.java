package com.songnhue.core.infra.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.core.application.identity.PermissionSummary;
import com.songnhue.core.application.identity.RoleSummary;

/**
 * Thao tác trên hai bảng nối {@code user_roles} và {@code role_permissions} — T6.15, T27.31.
 *
 * <p>Dùng JDBC chứ không ánh xạ {@code @ManyToMany}: bảng nối có {@code granted_at}/{@code granted_by}
 * mà {@code @ManyToMany} không ghi được, và đó chính là dấu vết "ai cấp quyền này, lúc nào" — thứ
 * quan trọng nhất trong toàn bộ bảng.
 *
 * <h2>⛔ Câu trên là lý do tồn tại của lớp này, và suốt Phase 0–1 nó KHÔNG ĐÚNG</h2>
 *
 * <p>{@code granted_by} có mặt ở <b>cả hai</b> bảng nối từ {@code V202608131002}, và tới trước T27.31
 * (08/09/2026) ⛔ <b>chưa một dòng mã nào ghi vào nó</b> — đo được bằng {@code rg 'granted_by'}: bốn
 * lượt xuất hiện, hai ở migration và hai ở <i>chú thích</i>, ⛔ không lượt nào là một câu {@code
 * INSERT}. Nghĩa là toàn bộ dấu vết cấp quyền của hệ thống là {@code NULL}, trong khi javadoc ngay
 * trên đây khẳng định nó là "thứ quan trọng nhất trong toàn bộ bảng".
 *
 * <p>Đây là luật 27 ở dạng khó thấy nhất: ⛔ không phải một nửa cặp đọc–ghi bị thiếu, mà là <b>một
 * lời giải thích kiến trúc tự nó sai</b> — thứ ⛔ không bộ canh nào bắt được, vì nó là văn xuôi.
 * Nay {@link #replaceRoles} và {@link #replacePermissionsOfRole} đều truyền người thao tác xuống.
 */
@Repository
public class UserAdminRepository {

    private static final Logger log = LoggerFactory.getLogger(UserAdminRepository.class);

    private final JdbcTemplate jdbc;

    public UserAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> findRoleCodes(Long userId) {
        return jdbc.queryForList(
                "SELECT r.code FROM user_roles ur JOIN roles r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? ORDER BY r.code",
                String.class,
                userId);
    }

    /**
     * Thay toàn bộ vai trò của một tài khoản.
     *
     * <p>Xoá hết rồi chèn lại trong <b>cùng một transaction</b> của người gọi: nửa chừng mà hỏng thì
     * rollback trả về đúng tập cũ. Nếu tách hai transaction thì có một khoảnh khắc tài khoản không
     * có vai trò nào — đủ để một request đang chạy nhận 403 không giải thích được.
     *
     * <p>Mã vai trò không tồn tại bị bỏ qua ở câu {@code INSERT … SELECT}: nó lọc theo bảng
     * {@code roles} nên chỉ mã có thật mới được gán.
     *
     * @param grantedBy id người thao tác — {@code null} khi lượt gọi đến từ nền (bootstrap, job)
     */
    public void replaceRoles(Long userId, List<String> roleCodes, Long grantedBy) {
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            log.warn("Tài khoản {} nay không còn vai trò nào — sẽ không thao tác được gì", userId);
            return;
        }
        for (String code : roleCodes) {
            jdbc.update(
                    "INSERT INTO user_roles (user_id, role_id, granted_at, granted_by) "
                            + "SELECT ?, r.id, now(), ? FROM roles r WHERE r.code = ?",
                    userId,
                    grantedBy,
                    code);
        }
    }

    /**
     * Vai trò này có phải vai trò hệ thống không.
     *
     * @return rỗng khi mã vai trò ⛔ không tồn tại — người gọi phân biệt được "⛔ không có" với "có
     *     nhưng ⛔ không sửa được", và đó là hai câu trả lời khác nhau cho người dùng (luật 9)
     */
    public Optional<Boolean> laVaiTroHeThong(String roleCode) {
        return jdbc.queryForList("SELECT is_system FROM roles WHERE code = ?", Boolean.class, roleCode).stream()
                .findFirst();
    }

    /** Toàn bộ danh mục quyền — nguồn dựng các ô đánh dấu của màn hình ma trận phân quyền. */
    public List<PermissionSummary> listPermissions() {
        return jdbc.query(
                "SELECT code, module, name, description FROM permissions ORDER BY module, code",
                (rs, rowNum) -> new PermissionSummary(
                        rs.getString("code"),
                        rs.getString("module"),
                        rs.getString("name"),
                        rs.getString("description")));
    }

    /** Những mã trong {@code codes} <b>không</b> có trong danh mục — rỗng nghĩa là tất cả đều thật. */
    public List<String> maQuyenKhongCoThat(Collection<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        Set<String> coThat = Set.copyOf(jdbc.queryForList("SELECT code FROM permissions", String.class));
        return codes.stream().filter(ma -> !coThat.contains(ma)).sorted().toList();
    }

    /**
     * Thay toàn bộ quyền của một vai trò — T27.31, CN-05.2.
     *
     * <p>Cùng khuôn <i>xoá hết rồi chèn lại trong một transaction</i> như {@link #replaceRoles}, và
     * cùng lý do: nửa chừng mà hỏng thì rollback trả về đúng tập cũ, ⛔ không có khoảnh khắc nào vai
     * trò trống quyền.
     *
     * <p>⛔⛔ <b>Khác</b> {@link #replaceRoles} ở đúng một điểm, và đó là điểm quan trọng: ở đây mã
     * quyền ⛔ <b>không</b> được bỏ qua trong im lặng. {@code INSERT … SELECT … WHERE code = ?} chèn
     * 0 dòng cho một mã gõ sai và <b>thoát bình thường</b> ⇒ màn hình báo "lưu thành công" trong khi
     * quyền ấy ⛔ không tồn tại. Người gọi ({@code UserAdminService}) phải chạy
     * {@link #maQuyenKhongCoThat} <b>trước</b>; ở đây thêm một phép đếm đối chứng để nếu ai đó gọi
     * thẳng repository thì vẫn vỡ ra thay vì trôi qua (luật 9).
     *
     * @return số dòng thật sự chèn được — người gọi so với {@code codes.size()}
     */
    public int replacePermissionsOfRole(String roleCode, List<String> permissionCodes, Long grantedBy) {
        jdbc.update("DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE code = ?)", roleCode);
        int daChen = 0;
        for (String code : permissionCodes) {
            daChen += jdbc.update(
                    "INSERT INTO role_permissions (role_id, permission_id, granted_at, granted_by) "
                            + "SELECT r.id, p.id, now(), ? FROM roles r, permissions p "
                            + "WHERE r.code = ? AND p.code = ?",
                    grantedBy,
                    roleCode,
                    code);
        }
        return daChen;
    }

    /** Danh mục vai trò cho màn hình phân quyền. */
    public List<RoleSummary> listRoles() {
        return jdbc.query(
                "SELECT r.code, r.name, r.description, r.is_system, "
                        + "(SELECT count(*) FROM role_permissions rp WHERE rp.role_id = r.id) AS permission_count "
                        + "FROM roles r ORDER BY r.code",
                (rs, rowNum) -> new RoleSummary(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("permission_count"),
                        rs.getBoolean("is_system")));
    }

    /** Mã quyền của một vai trò — dùng cho ma trận phân quyền. */
    public List<String> permissionsOfRole(String roleCode) {
        return jdbc.queryForList(
                "SELECT p.code FROM role_permissions rp "
                        + "JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id "
                        + "WHERE r.code = ? ORDER BY p.code",
                String.class,
                roleCode);
    }
}
