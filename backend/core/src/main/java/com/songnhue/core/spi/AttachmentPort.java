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

    /**
     * Đọc nội dung tệp để phục vụ <b>người xem chưa đăng nhập</b> — WS-16/T16.6.
     *
     * <p>⛔ <b>Vì sao không dùng presigned URL cho ảnh trang công khai.</b> Presigned URL sống 10
     * phút, còn trang ISR sống hàng giờ: trang dựng lúc 9h vẫn nằm trong bộ đệm lúc 11h, và mọi ảnh
     * trong đó đã chết. Triệu chứng là ảnh hỏng hàng loạt vào một thời điểm không ai đụng gì tới hệ
     * thống ({@code architecture-review.md} §10.1).
     *
     * <p>⛔⛔ <b>Vì sao bắt khai {@code allowedOwnerTypes}, và vì sao lọc ở đây chứ không ở nơi
     * gọi.</b> Một endpoint công khai nhận {@code publicId} rồi trả bất kỳ tệp nào là <i>toàn bộ kho
     * tài liệu</i> — gồm hồ sơ nhân sự và hợp đồng — nằm sau một UUID đoán được bằng cách thử. Chốt
     * chặn phải nằm cùng chỗ với việc đọc, không nằm ở nơi gọi: nơi gọi có thể quên, và cái quên đó
     * không có triệu chứng nào cho tới khi có người thử.
     *
     * <p>Tệp chưa quét xong hoặc đã bị cách ly cũng không trả — cổng công khai là nơi cuối cùng được
     * phép phát tán một tệp chưa kiểm.
     *
     * @param allowedOwnerTypes các loại chủ sở hữu được coi là nội dung công khai, VD
     *     {@code MEDIA_FOLDER}, {@code BANNER}, {@code SITE_CONFIG}
     * @return rỗng khi không tồn tại, <b>hoặc</b> khi thuộc loại không công khai — cố ý không phân
     *     biệt, vì phân biệt được là nói cho người hỏi biết UUID nào có thật
     */
    Optional<AttachmentContent> readForPublic(UUID publicId, List<String> allowedOwnerTypes);

    /** Danh sách tệp của một bản ghi, mới nhất trước. */
    List<AttachmentRef> refsOf(String ownerType, Long ownerId);

    void delete(UUID publicId);

    /**
     * Dung lượng bản ghi đang dùng (byte) — để giao diện hiện "đã dùng 120/500 MB".
     *
     * <p>Hạn mức khai bằng tham số {@code limits.attachment.quota-mb.<LOẠI_CHỦ_SỞ_HỮU>}; không khai
     * thì loại đó không giới hạn.
     */
    long usedBytes(String ownerType, Long ownerId);
}
