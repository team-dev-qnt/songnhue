package com.songnhue.operations.api;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.operations.application.ConstructionDocumentService;
import com.songnhue.operations.application.importer.ConstructionImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Tài liệu công trình và nhập danh mục hàng loạt — CN-02.3 (T17.7) + T17.9.
 *
 * <p>Hai nhóm chức năng ở chung một controller vì cùng nhận tệp tải lên và cùng thuộc màn hình hồ sơ
 * công trình. Chúng khác quyền: đọc/ghi tài liệu đi bằng {@code ops:document:*}, còn nhập danh mục
 * là thao tác <b>ghi hàng loạt vào chính danh mục</b> nên phải có {@code ops:construction:create}.
 */
@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "02-ops · Tài liệu công trình", description = "Hồ sơ, bản vẽ, ảnh hiện trạng và nhập danh mục")
public class ConstructionDocumentController {

    private final ConstructionDocumentService documents;
    private final ConstructionImportService importer;

    public ConstructionDocumentController(ConstructionDocumentService documents, ConstructionImportService importer) {
        this.documents = documents;
        this.importer = importer;
    }

    @GetMapping("/constructions/{publicId}/documents")
    @Operation(summary = "Tài liệu của một công trình, kèm dung lượng đã dùng")
    @RequirePermission("ops:document:view")
    public ConstructionDtos.DocumentList list(@PathVariable UUID publicId) {
        List<ConstructionDtos.DocumentView> items = documents.list(publicId).stream()
                .map(ConstructionDocumentController::toView)
                .toList();
        return new ConstructionDtos.DocumentList(documents.usedBytes(publicId), items);
    }

    /**
     * Tải một tài liệu lên.
     *
     * @param docType nhãn loại tài liệu — cũng là khoá đánh số phiên bản: tải lại cùng loại thì thành
     *     phiên bản kế tiếp, bản cũ giữ nguyên (CN-02.3 "versioning tự động")
     */
    @PostMapping(path = "/constructions/{publicId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải tài liệu công trình (hạn mức 500MB mỗi công trình)")
    @RequirePermission("ops:document:upload")
    public ConstructionDtos.DocumentView upload(
            @PathVariable UUID publicId,
            @RequestPart("file") MultipartFile file,
            @RequestParam String docType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {

        return toView(
                documents.upload(publicId, docType, file.getOriginalFilename(), doc(file), issuedDate, expiryDate));
    }

    @GetMapping("/constructions/{publicId}/documents/{attachmentId}/download-url")
    @Operation(summary = "Đường dẫn tải có hạn — chỉ cấp sau khi đã kiểm quyền xem công trình")
    @RequirePermission("ops:document:view")
    public DownloadUrl downloadUrl(@PathVariable UUID publicId, @PathVariable UUID attachmentId) {
        return new DownloadUrl(documents.downloadUrl(publicId, attachmentId));
    }

    public record DownloadUrl(String url) {}

    @DeleteMapping("/constructions/{publicId}/documents/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm một tài liệu")
    @RequirePermission("ops:document:delete")
    public void delete(@PathVariable UUID publicId, @PathVariable UUID attachmentId) {
        documents.delete(publicId, attachmentId);
    }

    // === Nhập danh mục hàng loạt (T17.9) =====================================

    /**
     * Chạy khô — <b>không ghi một dòng nào</b>.
     *
     * <p>Đây là bước bắt buộc trong quy trình nhập, không phải một tiện ích thêm: tệp danh mục do
     * Công ty lập có hàng trăm dòng, và nhập thẳng rồi phát hiện sai ở dòng 180 nghĩa là phải dọn tay
     * 179 dòng đã vào.
     */
    @PostMapping(path = "/constructions/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Chạy khô tệp nhập — đếm sẽ thêm/sửa bao nhiêu và liệt kê lỗi từng dòng")
    @RequirePermission("ops:construction:create")
    public ConstructionImportService.ImportReport previewImport(@RequestPart("file") MultipartFile file) {
        return importer.preview(doc(file));
    }

    @PostMapping(path = "/constructions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nhập thật — còn dòng lỗi thì không dòng nào được ghi (OPS-2016)")
    @RequirePermission("ops:construction:create")
    public ConstructionImportService.ImportReport applyImport(@RequestPart("file") MultipartFile file) {
        return importer.apply(doc(file));
    }

    /**
     * Đọc nội dung tệp tải lên.
     *
     * <p>Lỗi đọc quy về {@code SYS-0003} chứ không để {@link IOException} chui ra: một tệp hỏng giữa
     * chừng là lỗi của dữ liệu vào, không phải sự cố hệ thống, và trả 500 cho nó sẽ làm cảnh báo vận
     * hành kêu vì chuyện người dùng gửi nhầm tệp.
     */
    private static byte[] doc(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.SYS_0003, e.getMessage());
        }
    }

    private static ConstructionDtos.DocumentView toView(AttachmentRef ref) {
        return new ConstructionDtos.DocumentView(
                ref.publicId(),
                ref.originalName(),
                ref.purpose(),
                ref.contentType(),
                ref.sizeBytes(),
                ref.fileVersion(),
                ref.downloadable(),
                ref.createdAt(),
                ref.validFrom(),
                ref.validUntil());
    }
}
