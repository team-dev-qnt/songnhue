package com.songnhue.core.infra.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.identity.User;

/**
 * Truy vấn phẳng phục vụ phân quyền — cố ý dùng SQL gốc thay vì ánh xạ quan hệ.
 *
 * <p>Nếu khai báo {@code @ManyToMany} thì mỗi lần nạp quyền Hibernate kéo về cả đối tượng
 * {@code Role} và {@code Permission} rồi ta lại vứt đi, chỉ giữ mã. Ở đây cần đúng một tập chuỗi cho
 * mỗi request, nên một truy vấn trả về cột mã là vừa đủ và rẻ nhất.
 *
 * <p>Kế thừa {@code Repository} chứ không phải {@code JpaRepository}: lớp này không quản lý vòng đời
 * entity nào, chỉ đọc.
 */
@Repository
public interface UserAuthorityRepository extends org.springframework.data.repository.Repository<User, Long> {

    /**
     * Toàn bộ mã quyền của một tài khoản, gộp từ mọi vai trò đang hiệu lực.
     *
     * <p>Lọc {@code r.active} và {@code r.deleted_at IS NULL} ngay trong câu truy vấn: vô hiệu hoá
     * một vai trò phải có tác dụng tức thì với mọi người đang giữ vai trò đó, chứ không phải chờ ai
     * đó nhớ gỡ từng người ra khỏi vai trò.
     */
    @Query(
            value =
                    """
                    SELECT DISTINCT p.code
                      FROM user_roles ur
                      JOIN roles r ON r.id = ur.role_id
                      JOIN role_permissions rp ON rp.role_id = r.id
                      JOIN permissions p ON p.id = rp.permission_id
                     WHERE ur.user_id = :userId
                       AND r.active = TRUE
                       AND r.deleted_at IS NULL
                    """,
            nativeQuery = true)
    List<String> findPermissionCodes(@Param("userId") Long userId);

    @Query(
            value =
                    """
                    SELECT r.code
                      FROM user_roles ur
                      JOIN roles r ON r.id = ur.role_id
                     WHERE ur.user_id = :userId
                       AND r.active = TRUE
                       AND r.deleted_at IS NULL
                    """,
            nativeQuery = true)
    List<String> findRoleCodes(@Param("userId") Long userId);

    /**
     * Materialized path của đơn vị, VD {@code /1/4/9/} — tham số cho bộ lọc phạm vi tầng 3.
     *
     * <p>Người ở nút gốc có path {@code /1/}, mà path của MỌI đơn vị đều bắt đầu bằng {@code /1/} →
     * họ tự nhiên thấy toàn bộ dữ liệu, không cần thêm một cờ "bỏ qua phạm vi" nào cả. Cờ như vậy
     * chính là thứ hay bị bật nhầm.
     */
    @Query(value = "SELECT ou.path FROM org_units ou WHERE ou.id = :orgUnitId", nativeQuery = true)
    Optional<String> findOrgUnitPath(@Param("orgUnitId") Long orgUnitId);

    /**
     * Access token còn hiệu lực không — một truy vấn cho cả hai điều kiện thu hồi.
     *
     * <p>Gộp làm một để mỗi request chỉ tốn một vòng tới DB. Trả {@code TRUE} nghĩa là: family của
     * phiên vẫn còn ít nhất một mắt xích sống (chưa đăng xuất, chưa bị thu hồi từ xa, chưa phát hiện
     * dùng lại token) <b>và</b> {@code jti} này chưa nằm trong denylist.
     *
     * <p>Cố ý KHÔNG cache: sai ở đây nghĩa là một tài khoản đã bị khoá vẫn thao tác được thêm vài
     * chục giây. Với 200 người dùng nội bộ, hai chỉ mục này rẻ hơn nhiều so với rủi ro đó.
     */
    @Query(
            value =
                    """
                    SELECT EXISTS (
                               SELECT 1 FROM sessions s
                                WHERE s.family_id = CAST(:familyId AS uuid)
                                  AND s.revoked_at IS NULL
                                  AND s.expires_at > now()
                           )
                       AND NOT EXISTS (
                               SELECT 1 FROM token_denylist d
                                WHERE d.jti = CAST(:jti AS uuid)
                           )
                    """,
            nativeQuery = true)
    boolean isAccessTokenStillValid(@Param("familyId") UUID familyId, @Param("jti") UUID jti);
}
