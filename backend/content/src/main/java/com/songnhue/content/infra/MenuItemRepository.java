package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.content.domain.MenuItem;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.core.common.tree.MaterializedPath;

/** Truy vấn menu điều hướng — CN-01.5. */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    Optional<MenuItem> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /**
     * Danh sách thô, sắp theo {@code path} — cha đứng trước con, <b>anh em thì không</b>.
     *
     * <p>⛔ Đây không phải thứ tự hiển thị; gọi {@link #findForDisplay}. Câu này để riêng chỉ vì
     * {@code findForDisplay} cần một đầu vào xác định để sắp lại.
     */
    List<MenuItem> findByPositionAndDeletedAtIsNullOrderByPathAsc(MenuPosition position);

    /**
     * Thứ tự <b>hiển thị</b>: cha trước con, anh em theo {@code sort_order} — T26.25.
     *
     * <p>Phép sắp đặt ở đây chứ không ở từng nơi gọi (quy tắc 12): menu có nhiều đường đọc và tất cả
     * đều phải cho ra cùng một thứ tự. Xem {@link MaterializedPath#sortForDisplay} để biết vì sao
     * {@code ORDER BY path, sort_order} của SQL không làm được việc này.
     */
    default List<MenuItem> findForDisplay(MenuPosition position) {
        return MaterializedPath.sortForDisplay(
                findByPositionAndDeletedAtIsNullOrderByPathAsc(position), MenuItem::getPath, MenuItem::getSortOrder);
    }

    long countByParentIdAndDeletedAtIsNull(Long parentId);

    long countByCategoryIdAndDeletedAtIsNull(Long categoryId);

    long countByArticleIdAndDeletedAtIsNull(Long articleId);
}
