package com.songnhue.core.infra.identity;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Thao tác trên bảng nối {@code user_roles} — T6.15.
 *
 * <p>Dùng JDBC chứ không ánh xạ {@code @ManyToMany}: bảng nối có {@code granted_at}/{@code granted_by}
 * mà {@code @ManyToMany} không ghi được, và đó chính là dấu vết "ai cấp quyền này, lúc nào" — thứ
 * quan trọng nhất trong toàn bộ bảng.
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
     */
    public void replaceRoles(Long userId, List<String> roleCodes) {
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            log.warn("Tài khoản {} nay không còn vai trò nào — sẽ không thao tác được gì", userId);
            return;
        }
        for (String code : roleCodes) {
            jdbc.update(
                    "INSERT INTO user_roles (user_id, role_id, granted_at) "
                            + "SELECT ?, r.id, now() FROM roles r WHERE r.code = ?",
                    userId,
                    code);
        }
    }

    /** Danh mục vai trò cho màn hình phân quyền. */
    public List<RoleSummary> listRoles() {
        return jdbc.query(
                "SELECT r.code, r.name, r.description, "
                        + "(SELECT count(*) FROM role_permissions rp WHERE rp.role_id = r.id) AS permission_count "
                        + "FROM roles r ORDER BY r.code",
                (rs, rowNum) -> new RoleSummary(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("permission_count")));
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

    public record RoleSummary(String code, String name, String description, int permissionCount) {}
}
