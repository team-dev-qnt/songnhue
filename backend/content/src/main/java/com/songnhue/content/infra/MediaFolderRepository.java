package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.content.domain.MediaFolder;
import com.songnhue.core.common.tree.MaterializedPath;

/** Truy vấn thư mục media — CN-01.3. */
public interface MediaFolderRepository extends JpaRepository<MediaFolder, Long> {

    Optional<MediaFolder> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    List<MediaFolder> findAllByDeletedAtIsNullOrderByPathAsc();

    /**
     * Thứ tự <b>hiển thị</b>: cha trước con, anh em theo {@code sort_order} — T26.25.
     *
     * <p>Đặt phép sắp ở đây chứ không ở từng nơi gọi (quy tắc 12). Xem
     * {@link MaterializedPath#sortForDisplay} để biết vì sao {@code ORDER BY path, sort_order} của
     * SQL không bao giờ so tới {@code sort_order}.
     */
    default List<MediaFolder> findAllForDisplay() {
        return MaterializedPath.sortForDisplay(
                findAllByDeletedAtIsNullOrderByPathAsc(), MediaFolder::getPath, MediaFolder::getSortOrder);
    }

    long countByParentIdAndDeletedAtIsNull(Long parentId);
}
