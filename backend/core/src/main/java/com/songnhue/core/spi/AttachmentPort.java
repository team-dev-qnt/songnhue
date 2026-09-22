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

    /**
     * Đường dẫn có hạn để <b>hiện trong trang</b> ({@code inline}) — T84.6.
     *
     * <p>⛔⛔ {@link #downloadUrl} ký {@code attachment; filename=…}, và một phản hồi mang disposition
     * ấy <b>⛔ dựng được trong {@code <iframe>}</b>: trình duyệt từ chối khung và chuyển sang luồng
     * tải về. Mọi nút <i>"Xem trước"</i> trỏ vào nó cho ra một khung trắng ⛔ lý do.
     *
     * <p>⚠ Nơi gọi phải tự giới hạn loại tệp — xem javadoc {@code AttachmentService#inlineUrl}.
     */
    String inlineUrl(UUID publicId);

    /**
     * URL có hạn để trình duyệt lấy <b>THẲNG từ kho</b> một tệp công khai — T84.11 (video).
     *
     * <p>⛔⛔ Khác {@link #readForPublic} ở chỗ quyết định: đường kia phát byte <b>qua ứng dụng</b>,
     * ⛔ hỗ trợ HTTP Range (đo: 0 kết quả toàn backend), nên người xem ⛔ tua được và mỗi lượt xem
     * giữ một luồng Tomcat suốt thời gian PHÁT. MinIO có sẵn cả hai.
     *
     * <p>Ba phép lọc y hệt {@code readForPublic}; rỗng cho mọi lý do từ chối.
     *
     * @param ttl hạn của URL. ⚠ Phải dài hơn thời lượng xem: một cú <b>tua</b> gọi lại ĐÚNG URL cũ,
     *     nên TTL 10 phút kiểu {@link #downloadUrl} sẽ chết giữa video
     */
    Optional<String> publicStreamUrl(UUID publicId, List<String> allowedOwnerTypes, java.time.Duration ttl);

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

    /**
     * Đọc nội dung một tệp <b>cho người dùng NỘI BỘ đã được nơi gọi phân quyền</b> — CN-04.5
     * (tải cả hồ sơ dạng ZIP).
     *
     * <h2>⛔⛔ Vì sao chữ ký có {@code ownerType} và {@code ownerId} chứ ⛔ KHÔNG chỉ một UUID</h2>
     *
     * <p>Một {@code read(UUID)} trần sẽ đọc được <b>mọi</b> tệp trong kho: bảng {@code attachments}
     * là bảng <b>dùng chung</b> cho bài viết, công trình, hồ sơ CBNV và cấu hình cổng. Nơi gọi kiểm
     * quyền trên <i>bản ghi</i> rồi truyền một UUID <i>tệp</i> bất kỳ là một lỗ IDOR <b>trông y hệt
     * mã đúng</b> — đúng câu mà {@code HoSoTaiLieuService.thuocHoSo} đã phải viết ra để tự nhắc.
     *
     * <p>⇒ Cổng này <b>tự kiểm</b> tệp có thuộc đúng bản ghi ấy ⛔ không. Nơi gọi đã chứng minh
     * quyền trên {@code (ownerType, ownerId)}; phần còn lại thành một tính chất <b>cấu trúc</b>,
     * ⛔ không phải một lời dặn.
     *
     * <p>⚠ Trả {@link java.util.Optional#empty()} cho <b>cả hai</b> ca *"⛔ không có"* và *"có mà
     * ⛔ không thuộc bản ghi này"* — phân biệt được là nói cho người hỏi biết UUID nào có thật.
     *
     * <p>⚠⚠ Luồng trả về là luồng <b>đang mở tới kho</b>: nơi gọi phải đóng nó. Đây là lựa chọn có
     * chủ đích thay cho {@code byte[]} — một hồ sơ CBNV đủ bảy thư mục có thể vài chục MB, và nạp
     * trọn vào heap để nén là đúng thứ VPS 2 nhân đang phải tiết kiệm (T28.35).
     */
    Optional<AttachmentContent> readForOwner(String ownerType, Long ownerId, UUID publicId);

    /** Danh sách tệp của một bản ghi, mới nhất trước. */
    List<AttachmentRef> refsOf(String ownerType, Long ownerId);

    /**
     * Đặt ngày lập / ngày hết hiệu lực của tài liệu — CN-02.3.
     *
     * <p>Tách khỏi {@link #upload} vì đây là siêu dữ liệu nghiệp vụ: sửa được sau khi tệp đã vào kho,
     * còn nội dung tệp thì không. Một hồ sơ hoàn công lập năm 2018 vẫn được số hoá hôm nay, nên
     * "ngày lập" không suy ra được từ ngày tải lên.
     */
    AttachmentRef setValidity(UUID publicId, java.time.LocalDate validFrom, java.time.LocalDate validUntil);

    void delete(UUID publicId);

    /**
     * Dung lượng bản ghi đang dùng (byte) — để giao diện hiện "đã dùng 120/500 MB".
     *
     * <p>Hạn mức khai bằng tham số {@code limits.attachment.quota-mb.<LOẠI_CHỦ_SỞ_HỮU>}; không khai
     * thì loại đó không giới hạn.
     */
    long usedBytes(String ownerType, Long ownerId);
}
