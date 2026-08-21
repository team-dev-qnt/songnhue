package com.songnhue.operations.application;

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
import com.songnhue.operations.domain.MaintenanceLog;

/**
 * Biên bản nghiệm thu và ảnh trước / sau của một bản ghi sửa chữa — CN-02.2 / T18.6.
 *
 * <h2>⛔ Không có bảng tệp riêng, và cũng không mượn tệp của công trình</h2>
 *
 * Tệp nằm ở {@code attachments} với {@code owner_type = 'MAINTENANCE_LOG'} — đúng cách mà
 * {@code ConstructionDocumentService} và thư viện media của CMS đang làm (P3). Nhưng
 * {@code owner_id} trỏ <b>bản ghi sửa chữa</b>, không trỏ công trình: một tấm ảnh "sau khi sửa"
 * gắn vào hồ sơ công trình sẽ mất luôn ngữ cảnh là nó thuộc lần sửa nào, và tới lần sửa thứ ba thì
 * tab tài liệu của công trình có chín tấm ảnh không ai phân biệt được.
 *
 * <h2>Phạm vi đơn vị đi nhờ bản ghi</h2>
 *
 * Mọi lời gọi bắt đầu bằng {@link MaintenanceLogService#get} — đã qua bộ lọc tầng 3 và
 * {@code ScopeGuard}. Và mọi lời gọi có hai tham số đều kiểm tệp <b>thuộc đúng bản ghi</b> đã kiểm
 * quyền: {@code attachments} là bảng dùng chung với cả hồ sơ nhân sự, nên kiểm tham số thứ nhất mà
 * bỏ tham số thứ hai là một lỗ IDOR trông y hệt mã đúng.
 */
@Service
public class MaintenanceAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceAttachmentService.class);

    /** Khớp {@code attachments.owner_type}. Không khai khoá hạn mức → loại này không giới hạn. */
    public static final String OWNER_TYPE = "MAINTENANCE_LOG";

    /**
     * Định dạng nhận — biên bản nghiệm thu (PDF/DOC) và ảnh hiện trường.
     *
     * <p>⛔ Không {@code image/svg+xml}: SVG chứa được JavaScript, và chỉ màn hình cấu hình nhận diện
     * cổng mới nhận SVG (điểm nghiệp vụ 7). Ảnh hiện trường thì máy ảnh không sinh ra SVG bao giờ.
     */
    private static final List<String> DINH_DANG_NHAN = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/png",
            "image/webp");

    private final MaintenanceLogService logs;
    private final AttachmentPort attachments;

    public MaintenanceAttachmentService(MaintenanceLogService logs, AttachmentPort attachments) {
        this.logs = logs;
        this.attachments = attachments;
    }

    @Transactional(readOnly = true)
    public List<AttachmentRef> list(UUID logPublicId) {
        return attachments.refsOf(OWNER_TYPE, logs.get(logPublicId).getId());
    }

    /**
     * @param docType nhãn loại tệp — "Biên bản nghiệm thu", "Ảnh trước", "Ảnh sau". Cũng là khoá
     *     đánh số phiên bản: tải lại cùng nhãn thì thành phiên bản kế tiếp, bản cũ giữ nguyên
     */
    @Transactional
    public AttachmentRef upload(UUID logPublicId, String docType, String originalName, byte[] content) {
        MaintenanceLog banGhi = logs.get(logPublicId);
        AttachmentRef ref = attachments.upload(new AttachmentUploadCommand(
                OWNER_TYPE, banGhi.getId(), docType, originalName, content, DINH_DANG_NHAN));
        log.info("Bản ghi {} nhận tệp '{}' loại {}", banGhi.getCode(), originalName, docType);
        return ref;
    }

    @Transactional(readOnly = true)
    public String downloadUrl(UUID logPublicId, UUID attachmentPublicId) {
        thuocBanGhi(logPublicId, attachmentPublicId);
        return attachments.downloadUrl(attachmentPublicId);
    }

    @Transactional
    public void delete(UUID logPublicId, UUID attachmentPublicId) {
        thuocBanGhi(logPublicId, attachmentPublicId);
        attachments.delete(attachmentPublicId);
    }

    private void thuocBanGhi(UUID logPublicId, UUID attachmentPublicId) {
        MaintenanceLog banGhi = logs.get(logPublicId);
        boolean cua = attachments.refsOf(OWNER_TYPE, banGhi.getId()).stream()
                .anyMatch(ref -> ref.publicId().equals(attachmentPublicId));
        if (!cua) {
            throw new ResourceNotFoundException(ErrorCode.SYS_0004);
        }
    }
}
