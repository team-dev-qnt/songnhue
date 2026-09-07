package com.songnhue.content.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

import com.songnhue.content.application.BannerService;
import com.songnhue.content.domain.Banner;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Banner/Carousel — {@code /api/v1/cms/banners/**} (CN-01.5).
 *
 * <p>Tham số trình chiếu (thời gian dừng, hiệu ứng, autoplay) không nằm ở đây mà ở cấu hình giao
 * diện: chúng là một giá trị cho cả trang, không phải thuộc tính của từng ảnh.
 */
@RestController
@RequestMapping("/api/v1/cms/banners")
@Tag(name = "01-cms · Banner", description = "Ảnh carousel trang chủ, có lịch hiển thị")
public class BannerController {

    private final BannerService banners;

    public BannerController(BannerService banners) {
        this.banners = banners;
    }

    /**
     * @param visibleNow đang thật sự hiển thị tại thời điểm gọi hay không — giá trị dẫn xuất từ
     *     {@code active} và khoảng lịch, trả sẵn để giao diện không phải tính lại luật đó lần thứ hai
     */
    public record BannerView(
            UUID publicId,
            String title,
            String description,
            UUID imageAttachmentPublicId,
            String linkUrl,
            boolean openNewTab,
            Integer sortOrder,
            boolean active,
            Instant startAt,
            Instant endAt,
            boolean visibleNow) {

        static BannerView of(Banner banner, Instant now) {
            return new BannerView(
                    banner.getPublicId(),
                    banner.getTitle(),
                    banner.getDescription(),
                    banner.getImageAttachmentPublicId(),
                    banner.getLinkUrl(),
                    banner.isOpenNewTab(),
                    banner.getSortOrder(),
                    banner.isActive(),
                    banner.getStartAt(),
                    banner.getEndAt(),
                    banner.isVisibleAt(now));
        }
    }

    public record BannerRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 500) String description,
            @Size(max = 1000) String linkUrl,
            boolean openNewTab,
            boolean active,
            Instant startAt,
            Instant endAt) {}

    public record ReorderRequest(@NotEmpty List<UUID> publicIds) {}

    public record ImageUrl(String url) {}

    @GetMapping
    @Operation(summary = "Toàn bộ banner, kể cả đang tắt và đã hết hạn")
    @RequirePermission("cms:banner:manage")
    public List<BannerView> list() {
        Instant now = Instant.now();
        return banners.listAll().stream().map(b -> BannerView.of(b, now)).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo banner — ảnh đi cùng lượt tạo vì bản ghi không ảnh thì vô dụng")
    @RequirePermission("cms:banner:manage")
    public BannerView create(@RequestParam("title") String title, @RequestPart("file") MultipartFile file) {
        return BannerView.of(banners.create(title, tenTep(file), noiDung(file)), Instant.now());
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa thông tin và lịch hiển thị")
    @RequirePermission("cms:banner:manage")
    public BannerView update(@PathVariable UUID publicId, @Valid @RequestBody BannerRequest request) {
        Banner saved = banners.update(
                publicId,
                request.title(),
                request.description(),
                request.linkUrl(),
                request.openNewTab(),
                request.active(),
                request.startAt(),
                request.endAt());
        return BannerView.of(saved, Instant.now());
    }

    @PostMapping(path = "/{publicId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Đổi ảnh của một banner")
    @RequirePermission("cms:banner:manage")
    public BannerView replaceImage(@PathVariable UUID publicId, @RequestPart("file") MultipartFile file) {
        return BannerView.of(banners.replaceImage(publicId, tenTep(file), noiDung(file)), Instant.now());
    }

    @GetMapping("/{publicId}/image-url")
    @Operation(summary = "Đường dẫn xem ảnh (hạn ngắn) cho màn hình quản trị")
    @RequirePermission("cms:banner:manage")
    public ImageUrl imageUrl(@PathVariable UUID publicId) {
        return new ImageUrl(banners.imageUrl(publicId));
    }

    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đổi thứ tự trình chiếu")
    @RequirePermission("cms:banner:manage")
    public void reorder(@Valid @RequestBody ReorderRequest request) {
        banners.reorder(request.publicIds());
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá banner")
    @RequirePermission("cms:banner:manage")
    public void delete(@PathVariable UUID publicId) {
        banners.delete(publicId);
    }

    // -------------------------------------------------------------------------

    private static String tenTep(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("file", "FILE_EMPTY", "");
        }
        return file.getOriginalFilename();
    }

    private static byte[] noiDung(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003, e)
                    .withDetail("file", "FILE_READ_FAILED", file.getOriginalFilename());
        }
    }
}
