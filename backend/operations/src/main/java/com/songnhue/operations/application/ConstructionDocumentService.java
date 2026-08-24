package com.songnhue.operations.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.AttachmentUploadCommand;
import com.songnhue.operations.domain.Construction;

/**
 * Tài liệu và hình ảnh công trình — CN-02.3 / T17.7.
 *
 * <h2>⛔ Không có bảng tài liệu riêng</h2>
 *
 * Tệp nằm ở {@code attachments} với {@code owner_type = 'CONSTRUCTION'}. Dựng bảng thứ hai nghĩa là
 * hai chỗ kiểm magic bytes, hai chỗ quét virus, hai chỗ tính hạn mức — và chúng sẽ lệch nhau
 * ({@code architecture-review.md} §10.6). Thư viện media của CMS đã đi đúng đường này ở WS-14.
 *
 * <h2>Phạm vi đơn vị đi nhờ công trình</h2>
 *
 * Mọi lời gọi ở đây bắt đầu bằng việc tra công trình qua {@link ConstructionService#get} — tức là đã
 * đi qua bộ lọc tầng 3 và {@code ScopeGuard}. Người của Xí nghiệp khác hỏi tài liệu của một công
 * trình không thuộc mình sẽ dừng ngay ở bước đó với {@code AUTH-3002}, chứ không phải sau khi đã đọc
 * được danh sách tệp.
 */
@Service
public class ConstructionDocumentService {

    private static final Logger log = LoggerFactory.getLogger(ConstructionDocumentService.class);

    /** Khớp {@code attachments.owner_type} và khoá hạn mức {@code limits.attachment.quota-mb.CONSTRUCTION}. */
    public static final String OWNER_TYPE = "CONSTRUCTION";

    /**
     * Định dạng nhận cho hồ sơ công trình — CN-02.3.
     *
     * <p>⛔ Không có {@code image/svg+xml}: SVG chỉ được nhận ở màn hình cấu hình nhận diện cổng
     * (điểm nghiệp vụ 7). DWG đi kèm hai MIME vì trình duyệt và AutoCAD không thống nhất; cả hai đều
     * được {@code FileValidator} kiểm bằng magic bytes chứ không tin đuôi tệp.
     */
    private static final List<String> DINH_DANG_NHAN = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/vnd.dwg",
            "application/acad");

    private final ConstructionService constructions;
    private final AttachmentPort attachments;

    public ConstructionDocumentService(ConstructionService constructions, AttachmentPort attachments) {
        this.constructions = constructions;
        this.attachments = attachments;
    }

    @Transactional(readOnly = true)
    public List<AttachmentRef> list(UUID constructionPublicId) {
        Construction ct = constructions.get(constructionPublicId);
        return attachments.refsOf(OWNER_TYPE, ct.getId());
    }

    /**
     * Dung lượng đã dùng / hạn mức, để giao diện hiện "đã dùng 120/500 MB".
     *
     * <p>Hiện con số này ngay từ đầu chứ không đợi lượt tải thất bại: hạn mức 500MB/công trình
     * (CN-02.3) chỉ có ích khi người dùng thấy mình đang ở đâu trong đó.
     */
    @Transactional(readOnly = true)
    public long usedBytes(UUID constructionPublicId) {
        Construction ct = constructions.get(constructionPublicId);
        return attachments.usedBytes(OWNER_TYPE, ct.getId());
    }

    /**
     * Tải một tài liệu lên.
     *
     * @param docType nhãn loại tài liệu (Quy trình vận hành, Hồ sơ hoàn công…) — cũng là khoá đánh số
     *     phiên bản: tải lại cùng loại thì thành phiên bản kế tiếp, bản cũ giữ nguyên
     */
    @Transactional
    public AttachmentRef upload(
            UUID constructionPublicId,
            String docType,
            String originalName,
            byte[] content,
            LocalDate issuedDate,
            LocalDate expiryDate) {

        Construction ct = constructions.get(constructionPublicId);
        AttachmentRef ref = attachments.upload(
                new AttachmentUploadCommand(OWNER_TYPE, ct.getId(), docType, originalName, content, DINH_DANG_NHAN));

        // Ngày lập / ngày hết hiệu lực là siêu dữ liệu nghiệp vụ, đặt sau khi tệp đã vào kho.
        AttachmentRef daDatHan = (issuedDate == null && expiryDate == null)
                ? ref
                : attachments.setValidity(ref.publicId(), issuedDate, expiryDate);

        log.info("Công trình {} nhận tài liệu '{}' loại {}", ct.getCode(), originalName, docType);
        return daDatHan;
    }

    /** Đường dẫn tải có hạn — chỉ cấp sau khi đã kiểm quyền xem công trình. */
    @Transactional(readOnly = true)
    public String downloadUrl(UUID constructionPublicId, UUID attachmentPublicId) {
        thuocCongTrinh(constructionPublicId, attachmentPublicId);
        return attachments.downloadUrl(attachmentPublicId);
    }

    @Transactional
    public void delete(UUID constructionPublicId, UUID attachmentPublicId) {
        thuocCongTrinh(constructionPublicId, attachmentPublicId);
        attachments.delete(attachmentPublicId);
    }

    /**
     * Bắt buộc tệp phải thuộc đúng công trình mà người gọi vừa được kiểm quyền.
     *
     * <p>⛔ Thiếu bước này thì một người có quyền xem <i>một</i> công trình bất kỳ sẽ tải được
     * <i>mọi</i> tệp trong hệ thống, chỉ cần đoán {@code publicId} — kể cả hồ sơ nhân sự, vì
     * {@code attachments} là bảng dùng chung. Kiểm quyền ở tham số thứ nhất mà không ràng buộc tham
     * số thứ hai là một lỗ IDOR trông y hệt mã đúng.
     */
    private void thuocCongTrinh(UUID constructionPublicId, UUID attachmentPublicId) {
        Construction ct = constructions.get(constructionPublicId);
        boolean cua = attachments.refsOf(OWNER_TYPE, ct.getId()).stream()
                .anyMatch(ref -> ref.publicId().equals(attachmentPublicId));
        if (!cua) {
            throw new ResourceNotFoundException(ErrorCode.SYS_0004);
        }
    }
}
