package com.songnhue.core.infra.attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.attachment.Attachment;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /** Bản mới nhất lên đầu — màn hình tài liệu hiển thị phiên bản hiện hành trước. */
    List<Attachment> findByOwnerTypeAndOwnerIdAndDeletedAtIsNullOrderByFileVersionDesc(String ownerType, Long ownerId);

    /**
     * Số phiên bản lớn nhất trong cùng {@code (owner, purpose)}.
     *
     * <p>Tính cả bản đã xoá mềm: tái dùng số phiên bản của bản đã xoá sẽ làm lịch sử tài liệu có hai
     * "phiên bản 3" khác nhau, và không ai phân biệt được bản nào là bản nào.
     */
    @Query(
            """
            SELECT max(a.fileVersion) FROM Attachment a
             WHERE a.ownerType = :ownerType AND a.ownerId = :ownerId
               AND (:purpose IS NULL OR a.purpose = :purpose)
            """)
    Optional<Integer> findMaxVersion(
            @Param("ownerType") String ownerType, @Param("ownerId") Long ownerId, @Param("purpose") String purpose);
}
