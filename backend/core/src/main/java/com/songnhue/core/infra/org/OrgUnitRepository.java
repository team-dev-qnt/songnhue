package com.songnhue.core.infra.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.common.tree.MaterializedPath;
import com.songnhue.core.domain.org.OrgUnit;

@Repository
public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    /** Tra cứu từ API luôn đi qua {@code public_id} — cấm lộ id chạy số (§4.2 chống IDOR). */
    Optional<OrgUnit> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<OrgUnit> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    List<OrgUnit> findAllByDeletedAtIsNullOrderByPathAsc();

    /**
     * Thứ tự <b>hiển thị</b>: cha trước con, anh em theo {@code sort_order} — T26.25.
     *
     * <p>Đặt phép sắp ở đây chứ không ở từng nơi gọi (quy tắc 12). Xem
     * {@link MaterializedPath#sortForDisplay} để biết vì sao {@code ORDER BY path, sort_order} của
     * SQL không bao giờ so tới {@code sort_order}.
     */
    default List<OrgUnit> findAllForDisplay() {
        return MaterializedPath.sortForDisplay(
                findAllByDeletedAtIsNullOrderByPathAsc(), OrgUnit::getPath, OrgUnit::getSortOrder);
    }

    /** Đơn vị gốc — dùng khi cần path gốc mà chưa biết id. */
    Optional<OrgUnit> findFirstByParentIdIsNullAndDeletedAtIsNull();

    boolean existsByParentIdAndDeletedAtIsNull(Long parentId);

    /**
     * Cả cây con, <b>tính cả chính nó</b>.
     *
     * <p>{@code LIKE :path || '%'} chạy trên chỉ mục {@code ix_org_units_path}
     * ({@code varchar_pattern_ops}). Dấu {@code /} cuối path là thứ giữ cho {@code /1/4/} không khớp
     * nhầm {@code /1/40/} — xem {@code MaterializedPath}.
     */
    @Query(
            value = "SELECT * FROM org_units WHERE path LIKE :path || '%' AND deleted_at IS NULL "
                    + "ORDER BY path, sort_order",
            nativeQuery = true)
    List<OrgUnit> findSubtree(@Param("path") String path);

    @Query(
            value = "SELECT count(*) FROM org_units WHERE path LIKE :path || '%' AND deleted_at IS NULL",
            nativeQuery = true)
    long countSubtree(@Param("path") String path);

    /**
     * Chuyển cả cây con sang vị trí mới — một câu lệnh, không tải entity nào lên.
     *
     * <p>⚠ Dùng {@code substring} chứ <b>không</b> {@code replace}: path có thể chứa lặp lại tiền tố
     * ({@code /1/4/1/4/9/} với tiền tố {@code /1/4/}), {@code replace} sẽ thay cả hai chỗ và đẩy nút
     * sang nhánh khác. Cùng lý do đã ghi ở {@code MaterializedPath.reparent}.
     *
     * <p>{@code depth} tính lại từ số dấu {@code /}: {@code /1/} có 2 dấu và depth 0, nên
     * {@code depth = số dấu '/' - 2}. Tính lại thay vì cộng/trừ độ lệch — cộng dồn thì một lần sai là
     * sai vĩnh viễn, còn tính lại thì luôn tự khớp với path.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
            UPDATE org_units
               SET path  = :newPrefix || substring(path FROM length(:oldPrefix) + 1),
                   depth = length(:newPrefix || substring(path FROM length(:oldPrefix) + 1))
                           - length(replace(:newPrefix || substring(path FROM length(:oldPrefix) + 1), '/', ''))
                           - 2
             WHERE path LIKE :oldPrefix || '%'
               AND deleted_at IS NULL
            """,
            nativeQuery = true)
    int reparentSubtree(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix);

    /** Người đứng đầu và cấp phó của một tập đơn vị — nguồn người nhận cảnh báo G11 (T6.7). */
    @Query(
            value =
                    """
            SELECT DISTINCT u.id FROM org_units ou
              JOIN users u ON u.id IN (ou.head_user_id, ou.deputy_user_id)
             WHERE ou.id IN (:orgUnitIds)
               AND ou.deleted_at IS NULL
               AND u.deleted_at IS NULL
               AND u.status = 'ACTIVE'
            """,
            nativeQuery = true)
    List<Long> findActiveHeadAndDeputyUserIds(@Param("orgUnitIds") List<Long> orgUnitIds);

    /**
     * Trưởng/phó của một đơn vị <b>và của mọi đơn vị CHA nó</b> — chuỗi lãnh đạo (T80.1).
     *
     * <h2>⚠ Vì sao ⛔ bóc {@code path} ở Java rồi gọi {@link #findActiveHeadAndDeputyUserIds}</h2>
     *
     * <p>Làm thế thì nơi gọi phải tự cắt {@code /1/4/9/} thành các id — một phép phân tích chuỗi
     * nằm <b>ngoài</b> CSDL, tức hai nơi cùng phải nhớ định dạng của {@code path}. Luật 14: chỗ nào
     * con người phải nhớ hai nơi thì chỗ đó sai được trong im lặng.
     *
     * <p>⭐ {@code con.path LIKE cha.path || '%'} là phép <i>cha-hoặc-chính-nó</i>, và hai dấu gạch
     * bao quanh chính là thứ giữ nó đúng: {@code /1/40/} ⛔ khớp {@code /1/4/%}. Thiếu dấu gạch
     * cuối thì Xí nghiệp 40 rơi vào tầm của trưởng Xí nghiệp 4 — một lỗi ⛔ có triệu chứng cho tới
     * ngày Công ty có đủ đơn vị để số hiệu chạm nhau.
     */
    @Query(
            value =
                    """
            SELECT DISTINCT u.id
              FROM org_units con
              JOIN org_units cha ON con.path LIKE cha.path || '%'
              JOIN users u ON u.id IN (cha.head_user_id, cha.deputy_user_id)
             WHERE con.id = :orgUnitId
               AND con.deleted_at IS NULL
               AND cha.deleted_at IS NULL
               AND u.deleted_at IS NULL
               AND u.status = 'ACTIVE'
            """,
            nativeQuery = true)
    List<Long> findActiveLeaderUserIdsUpTheTree(@Param("orgUnitId") Long orgUnitId);

    /**
     * Một đơn vị <b>và mọi đơn vị cha</b> của nó — chuỗi đi LÊN (T80.5).
     *
     * <p>Dùng cho phép tra uỷ quyền: trưởng Xí nghiệp A uỷ quyền cho ai đó thì người ấy phải duyệt
     * được cả đơn của các <b>Tổ đội trực thuộc</b> A — tức một hàng uỷ quyền gắn ở A phải khớp một
     * đơn gắn ở Tổ đội. Soi từ phía đơn thì câu hỏi là <i>"những đơn vị nào phủ đơn này"</i>, và
     * đáp án là chính chuỗi này.
     *
     * <p>⚠ Cùng phép so {@code con.path LIKE cha.path || '%'} với
     * {@link #findActiveLeaderUserIdsUpTheTree} — <b>cố ý</b>: hai câu trả lời hai câu hỏi khác
     * nhau về cùng một quan hệ, và chúng phải ⛔ bao giờ lệch nhau (luật 14).
     */
    @Query(
            value =
                    """
            SELECT cha.id
              FROM org_units con
              JOIN org_units cha ON con.path LIKE cha.path || '%'
             WHERE con.id = :orgUnitId
               AND con.deleted_at IS NULL
               AND cha.deleted_at IS NULL
            """,
            nativeQuery = true)
    List<Long> findOrgUnitIdChainUp(@Param("orgUnitId") Long orgUnitId);
}
