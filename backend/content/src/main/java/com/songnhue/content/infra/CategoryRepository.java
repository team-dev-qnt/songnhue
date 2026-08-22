package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.content.domain.Category;

/** Truy vấn danh mục nội dung — CN-01.2. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<Category> findBySlugAndDeletedAtIsNull(String slug);

    List<Category> findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc();

    /**
     * Đếm bài viết còn nằm trong danh mục — nguồn cho luật chặn xoá (T13.9).
     *
     * <p>Lọc {@code deleted_at IS NULL} của bài viết: bài đã xoá mềm không được tính là "còn bài", nếu
     * không thì một danh mục từng chứa bài sẽ vĩnh viễn không xoá được.
     */
    @Query(
            value =
                    """
                    SELECT count(*)
                    FROM article_categories ac
                             JOIN articles a ON a.id = ac.article_id AND a.deleted_at IS NULL
                    WHERE ac.category_id = :categoryId
                    """,
            nativeQuery = true)
    long countLiveArticles(@Param("categoryId") Long categoryId);

    /** Danh mục con trực tiếp — dùng để chặn xoá khi cây bên dưới chưa rỗng. */
    long countByParentIdAndDeletedAtIsNull(Long parentId);
}
