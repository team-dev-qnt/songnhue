package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.content.domain.MenuItem;
import com.songnhue.content.domain.MenuPosition;

/** Truy vấn menu điều hướng — CN-01.5. */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    Optional<MenuItem> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /** Sắp theo {@code path} rồi tới {@code sortOrder}: cha luôn đứng trước con, anh em đúng thứ tự. */
    List<MenuItem> findByPositionAndDeletedAtIsNullOrderByPathAscSortOrderAsc(MenuPosition position);

    long countByParentIdAndDeletedAtIsNull(Long parentId);

    long countByCategoryIdAndDeletedAtIsNull(Long categoryId);

    long countByArticleIdAndDeletedAtIsNull(Long articleId);
}
