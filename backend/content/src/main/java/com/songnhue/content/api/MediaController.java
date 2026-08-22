package com.songnhue.content.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.songnhue.content.application.MediaService;
import com.songnhue.content.domain.MediaFolder;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.AttachmentRef;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Thư viện media — {@code /api/v1/cms/media/**} (CN-01.3).
 *
 * <p>Tệp media <b>không có bảng riêng</b>: chúng là {@code attachments} với
 * {@code owner_type = 'MEDIA_FOLDER'} (điểm nghiệp vụ 8). API ở đây chỉ là cửa vào có ngữ cảnh thư
 * mục; mọi việc kiểm định dạng, bóc EXIF, hạn mức dung lượng do pattern P3 của Core làm.
 */
@RestController
@RequestMapping("/api/v1/cms/media")
@Tag(name = "01-cms · Thư viện media", description = "Thư mục và tệp dùng cho nội dung cổng")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    public record FolderRequest(@NotBlank @Size(max = 255) String name, UUID parentId) {}

    public record FolderNode(UUID publicId, String name, UUID parentPublicId, Short depth, Integer sortOrder) {}

    public record MediaFile(UUID publicId, String originalName, String contentType, long sizeBytes, Instant createdAt) {

        static MediaFile of(AttachmentRef ref) {
            return new MediaFile(
                    ref.publicId(), ref.originalName(), ref.contentType(), ref.sizeBytes(), ref.createdAt());
        }
    }

    // ---- Thư mục -------------------------------------------------------------

    @GetMapping("/folders")
    @Operation(summary = "Cây thư mục (tối đa 3 cấp)")
    @RequirePermission("cms:media:manage")
    public List<FolderNode> folders() {
        List<MediaFolder> all = media.tree();
        return all.stream().map(f -> toNode(f, all)).toList();
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo thư mục")
    @RequirePermission("cms:media:manage")
    public FolderNode createFolder(@Valid @RequestBody FolderRequest request) {
        MediaFolder saved = media.createFolder(request.name(), request.parentId());
        return toNode(saved, media.tree());
    }

    @PutMapping("/folders/{publicId}")
    @Operation(summary = "Đổi tên thư mục")
    @RequirePermission("cms:media:manage")
    public FolderNode renameFolder(@PathVariable UUID publicId, @Valid @RequestBody FolderRequest request) {
        return toNode(media.renameFolder(publicId, request.name()), media.tree());
    }

    @DeleteMapping("/folders/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá thư mục — chỉ khi đã rỗng")
    @RequirePermission("cms:media:manage")
    public void deleteFolder(@PathVariable UUID publicId) {
        media.deleteFolder(publicId);
    }

    // ---- Tệp -----------------------------------------------------------------

    @GetMapping("/folders/{folderId}/files")
    @Operation(summary = "Tệp trong một thư mục, lọc theo loại và khoảng ngày")
    @RequirePermission("cms:media:manage")
    public List<MediaFile> files(
            @PathVariable UUID folderId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return media.filesIn(folderId, type, from, to).stream()
                .map(MediaFile::of)
                .toList();
    }

    /**
     * Tải một tệp lên.
     *
     * <p>⚠ Đọc toàn bộ vào bộ nhớ vì {@code AttachmentPort} nhận {@code byte[]} — chấp nhận được với
     * ảnh và tài liệu, nhưng <b>video 500MB thì không</b>. Giới hạn kích thước request của Spring và
     * của nginx phải đặt khớp trần lớn nhất, và đường tải video nên chuyển sang luồng trực tiếp lên
     * MinIO khi Công ty thật sự dùng tới nó — CN-01.3 vốn khuyến nghị nhúng YouTube.
     *
     * <p>Tên tệp lấy từ {@code originalFilename} chỉ để hiển thị; định dạng thật xác định bằng magic
     * bytes ở tầng dưới, và tên lưu xuống kho là chuỗi ngẫu nhiên.
     */
    @PostMapping(path = "/folders/{folderId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải tệp lên thư mục")
    @RequirePermission("cms:media:manage")
    public MediaFile upload(@PathVariable UUID folderId, @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("file", "FILE_EMPTY", "");
        }
        try {
            return MediaFile.of(media.upload(folderId, file.getOriginalFilename(), file.getBytes()));
        } catch (IOException e) {
            // Luồng tải lên đứt giữa chừng. Không phải lỗi của người dùng theo nghĩa dữ liệu sai,
            // nhưng cũng không phải lỗi hệ thống — nói rõ là tệp chưa lên tới nơi.
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003, e)
                    .withDetail("file", "FILE_READ_FAILED", file.getOriginalFilename());
        }
    }

    @GetMapping("/files/{publicId}/url")
    @Operation(summary = "Đường dẫn tải về (hạn ngắn) — nút 'Sao chép URL'")
    @RequirePermission("cms:media:manage")
    public DownloadUrl url(@PathVariable UUID publicId) {
        return new DownloadUrl(media.downloadUrl(publicId));
    }

    public record DownloadUrl(String url) {}

    /**
     * Bài viết đang dùng tệp này.
     *
     * <p>Giao diện gọi <b>trước</b> khi hiện hộp thoại xoá, để nói được "3 bài đang dùng ảnh này"
     * thay vì để người dùng bấm Xoá rồi mới nhận lỗi.
     */
    @GetMapping("/files/{publicId}/usages")
    @Operation(summary = "Bài viết đang dùng tệp — hỏi trước khi xoá")
    @RequirePermission("cms:media:manage")
    public List<String> usages(@PathVariable UUID publicId) {
        return media.articlesUsing(publicId);
    }

    @DeleteMapping("/files/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá tệp — chặn khi còn bài viết tham chiếu")
    @RequirePermission("cms:media:manage")
    public void deleteFile(@PathVariable UUID publicId) {
        media.deleteFile(publicId);
    }

    // -------------------------------------------------------------------------

    private static FolderNode toNode(MediaFolder f, List<MediaFolder> all) {
        UUID parentPublicId = f.getParentId() == null
                ? null
                : all.stream()
                        .filter(x -> x.getId().equals(f.getParentId()))
                        .map(MediaFolder::getPublicId)
                        .findFirst()
                        .orElse(null);
        return new FolderNode(f.getPublicId(), f.getName(), parentPublicId, f.getDepth(), f.getSortOrder());
    }
}
