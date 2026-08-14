package com.songnhue.core.infra.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.org.OrgUnit;

@Repository
public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    /** Tra cứu từ API luôn đi qua {@code public_id} — cấm lộ id chạy số (§4.2 chống IDOR). */
    Optional<OrgUnit> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<OrgUnit> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    List<OrgUnit> findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc();

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
}
