package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.content.domain.MediaFolder;

/** Truy vấn thư mục media — CN-01.3. */
public interface MediaFolderRepository extends JpaRepository<MediaFolder, Long> {

    Optional<MediaFolder> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    List<MediaFolder> findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc();

    long countByParentIdAndDeletedAtIsNull(Long parentId);
}
