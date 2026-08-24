package com.songnhue.core.api.attachment;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.songnhue.core.application.attachment.AttachmentService;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.domain.attachment.Attachment;
import com.songnhue.core.domain.attachment.AttachmentStatus;
import com.songnhue.core.domain.attachment.ScanStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Tệp đính kèm — {@code /api/v1/attachments/**} (pattern P3).
 *
 * <p>Endpoint tải lên nằm ở đây thay vì ở từng module: kiểm magic bytes, mã hoá lại ảnh, đặt tên
 * ngẫu nhiên và xếp việc quét virus phải giống hệt nhau ở mọi chỗ. Mỗi module tự làm một bản là một
 * chỗ có thể quên bước quét.
 *
 * <p>Quyền dùng {@code ops:document:upload}: Phase 0 mới có hồ sơ công trình cần đính kèm. Phase 1
 * thêm ảnh bài viết sẽ bổ sung mã quyền riêng cho CMS — cùng một endpoint, chọn quyền theo
 * {@code ownerType}.
 */
@RestController
@RequestMapping("/api/v1/attachments")
@Tag(name = "00-core · Tệp đính kèm", description = "Tải lên, tải xuống và quản lý tài liệu")
public class AttachmentController {

    /** Định dạng cho phép ở Phase 0 — ảnh và tài liệu văn phòng. */
    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải tệp lên — trả về bản ghi ở trạng thái CHỜ QUÉT, chưa tải xuống được")
    @RequirePermission("ops:document:upload")
    public AttachmentDtos.AttachmentView upload(
            @RequestParam String ownerType,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String purpose,
            @RequestParam MultipartFile file)
            throws IOException {

        Attachment saved = attachmentService.upload(
                ownerType, ownerId, purpose, file.getOriginalFilename(), file.getBytes(), ALLOWED_TYPES);
        return AttachmentDtos.AttachmentView.of(saved);
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Thông tin một tệp")
    @RequirePermission("ops:document:view")
    public AttachmentDtos.AttachmentView get(@PathVariable UUID publicId) {
        return AttachmentDtos.AttachmentView.of(attachmentService.get(publicId));
    }

    @GetMapping("/{publicId}/download-url")
    @Operation(summary = "Đường dẫn tải có hạn 10 phút — từ chối nếu tệp chưa quét xong")
    @RequirePermission("ops:document:view")
    public AttachmentDtos.DownloadUrl downloadUrl(@PathVariable UUID publicId) {
        return new AttachmentDtos.DownloadUrl(attachmentService.downloadUrl(publicId));
    }

    @GetMapping
    @Operation(summary = "Danh sách tệp của một đối tượng, bản mới nhất lên đầu")
    @RequirePermission("ops:document:view")
    public List<AttachmentDtos.AttachmentView> list(@RequestParam String ownerType, @RequestParam Long ownerId) {
        return attachmentService.listOf(ownerType, ownerId).stream()
                .map(AttachmentDtos.AttachmentView::of)
                .toList();
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm bản ghi — tệp trên kho vẫn giữ để nhật ký không trỏ vào khoảng không")
    @RequirePermission("ops:document:upload")
    public void delete(@PathVariable UUID publicId) {
        attachmentService.delete(publicId);
    }

    /** DTO của API tệp đính kèm. */
    public static final class AttachmentDtos {

        private AttachmentDtos() {}

        /** @param url có hạn ngắn và bỏ qua phân quyền — không lưu lại, không chia sẻ */
        public record DownloadUrl(String url) {}

        public record AttachmentView(
                UUID publicId,
                String originalName,
                String contentType,
                long sizeBytes,
                int fileVersion,
                AttachmentStatus status,
                ScanStatus scanStatus,
                LocalDate validFrom,
                LocalDate validUntil,
                boolean downloadable) {

            public static AttachmentView of(Attachment a) {
                return new AttachmentView(
                        a.getPublicId(),
                        a.getOriginalName(),
                        a.getContentType(),
                        a.getSizeBytes(),
                        a.getFileVersion(),
                        a.getStatus(),
                        a.getScanStatus(),
                        a.getValidFrom(),
                        a.getValidUntil(),
                        a.isDownloadable());
            }
        }
    }
}
