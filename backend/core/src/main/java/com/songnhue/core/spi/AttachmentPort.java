package com.songnhue.core.spi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tệp đính kèm — pattern P3, dùng chung cho media CMS, tài liệu công trình, hồ sơ nhân sự.
 *
 * <p>⛔ <b>Module nghiệp vụ không dựng bảng tệp riêng.</b> Có hai đường tải lên nghĩa là hai chỗ kiểm
 * magic bytes, hai chỗ quét virus, hai chỗ tính hạn mức — và chúng sẽ lệch nhau
 * (architecture-review.md §10.6).
 */
public interface AttachmentPort {

    /**
     * Tải tệp lên. Tệp trả về ở trạng thái <b>chờ quét virus</b> — chưa tải xuống được.
     *
     * @throws com.songnhue.core.common.exception.BusinessRuleException khi sai định dạng hoặc quá
     *     dung lượng cho phép
     */
    AttachmentRef upload(AttachmentUploadCommand command);

    /**
     * Đường dẫn tải có hạn (presigned).
     *
     * <p>⚠ Không dùng cho ảnh trên trang công khai được ISR cache lại: trang sống lâu hơn đường dẫn
     * thì ảnh hỏng hàng loạt sau vài giờ (architecture-review.md §10.1).
     */
    String downloadUrl(UUID publicId);

    Optional<AttachmentRef> findRef(UUID publicId);

    /** Danh sách tệp của một bản ghi, mới nhất trước. */
    List<AttachmentRef> refsOf(String ownerType, Long ownerId);

    void delete(UUID publicId);
}
