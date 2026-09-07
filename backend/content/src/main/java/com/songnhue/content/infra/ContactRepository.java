package com.songnhue.content.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.content.domain.Contact;
import com.songnhue.content.domain.ContactStatus;

/** Truy vấn liên hệ — CN-01.4. */
public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Page<Contact> findAllByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<Contact> findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(ContactStatus status, Pageable pageable);

    long countByStatusAndDeletedAtIsNull(ContactStatus status);

    /**
     * Còn liên hệ nào đang gán phân loại này không — dùng trước khi xoá mềm một phân loại.
     *
     * <p>⚠ Đếm cả liên hệ ở mọi trạng thái, kể cả {@code LUU_TRU}: một bản ghi lưu trữ vẫn phải đọc
     * lại được phân loại của nó, nếu không thì báo cáo theo phân loại nói dối về quá khứ.
     */
    long countByCategoryIdAndDeletedAtIsNull(Long categoryId);

    /**
     * Liên hệ <b>quá hạn xử lý</b> — T36.4.
     *
     * <p>⚠ Ba trạng thái được coi là <b>đã xong</b> và ⛔ không nhắc nữa: {@code DA_PHAN_HOI},
     * {@code DONG}, {@code LUU_TRU}. Ba trạng thái còn lại ({@code MOI}, {@code DA_DOC},
     * {@code DANG_XU_LY}) đều là "còn người đang chờ".
     *
     * <p>⛔ Viết bằng danh sách trạng thái <b>CÒN MỞ</b> chứ ⛔ không bằng
     * {@code NOT IN (đã xong)}: một trạng thái mới thêm vào enum sẽ <i>lặng lẽ</i> rơi vào nhóm
     * "còn mở" ở cách viết thứ hai và sinh ra thư nhắc cho một thứ ⛔ không ai chờ. Ở cách viết này
     * nó rơi ra ngoài — và một thư nhắc thiếu dễ phát hiện hơn hẳn một thư nhắc thừa.
     *
     * <p>⚠ Sắp theo {@code created_at ASC}: cái chờ lâu nhất lên đầu danh sách trong thư nhắc.
     */
    @Query(
            """
            SELECT c FROM Contact c
             WHERE c.deletedAt IS NULL
               AND c.status IN (com.songnhue.content.domain.ContactStatus.MOI,
                                com.songnhue.content.domain.ContactStatus.DA_DOC,
                                com.songnhue.content.domain.ContactStatus.DANG_XU_LY)
               AND c.createdAt < :hanChot
             ORDER BY c.createdAt ASC
            """)
    List<Contact> quaHan(@Param("hanChot") Instant hanChot, Pageable pageable);
}
