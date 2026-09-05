package com.songnhue.content.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.songnhue.content.application.MenuService;
import com.songnhue.content.domain.MenuLinkType;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Menu điều hướng — {@code /api/v1/cms/menus/**} (CN-01.5).
 *
 * <p>Vị trí nằm trên đường dẫn ({@code /menus/HEADER}) chứ không phải tham số truy vấn: Header và
 * Footer là hai cây tách biệt, và đường dẫn nói ra điều đó rõ hơn.
 */
@RestController
@RequestMapping("/api/v1/cms/menus")
@Tag(name = "01-cms · Menu điều hướng", description = "Hai cây menu độc lập: Header và Footer")
public class MenuController {

    private final MenuService menus;

    public MenuController(MenuService menus) {
        this.menus = menus;
    }

    /**
     * @param parentId {@code null} = mục gốc của menu
     * @param linkType quyết định trường đích nào bắt buộc — {@code CATEGORY} cần {@code categoryId},
     *     {@code ARTICLE} cần {@code articleId}, {@code URL}/{@code EXTERNAL_DOC} cần {@code url},
     *     {@code NONE} không cần gì. Ràng buộc kiểm ở tầng service và ở cả CSDL
     */
    public record MenuRequest(
            @NotBlank @Size(max = 255) String label,
            @NotNull MenuLinkType linkType,
            UUID parentId,
            UUID categoryId,
            UUID articleId,
            @Size(max = 1000) String url,
            boolean openNewTab,
            boolean active) {

        MenuService.Target target() {
            return new MenuService.Target(linkType, categoryId, articleId, url);
        }
    }

    public record ReorderRequest(@NotEmpty List<UUID> publicIds) {}

    @GetMapping("/{position}")
    @Operation(summary = "Cây menu của một vị trí — cha luôn đứng trước con")
    @RequirePermission("cms:layout:manage")
    public List<MenuService.MenuNode> tree(@PathVariable MenuPosition position) {
        return menus.tree(position);
    }

    @PostMapping("/{position}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm mục vào menu")
    @RequirePermission("cms:layout:manage")
    public MenuService.MenuNode create(@PathVariable MenuPosition position, @Valid @RequestBody MenuRequest request) {
        return menus.create(position, request.parentId(), request.label(), request.target());
    }

    /**
     * Đổi thứ tự — khai <b>trước</b> {@code /items/{publicId}} có chủ đích.
     *
     * <p>Spring khớp theo độ cụ thể chứ không theo thứ tự khai, nên thực ra không bắt buộc; đặt cạnh
     * nhau để người đọc thấy ngay là {@code reorder} không phải một {@code publicId}.
     */
    @PutMapping("/items/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đổi thứ tự các mục trong cùng một cấp")
    @RequirePermission("cms:layout:manage")
    public void reorder(@Valid @RequestBody ReorderRequest request) {
        menus.reorder(request.publicIds());
    }

    @PutMapping("/items/{publicId}")
    @Operation(summary = "Sửa một mục")
    @RequirePermission("cms:layout:manage")
    public MenuService.MenuNode update(@PathVariable UUID publicId, @Valid @RequestBody MenuRequest request) {
        return menus.update(publicId, request.label(), request.target(), request.openNewTab(), request.active());
    }

    @DeleteMapping("/items/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá một mục — chỉ khi không còn mục con")
    @RequirePermission("cms:layout:manage")
    public void delete(@PathVariable UUID publicId) {
        menus.delete(publicId);
    }

    /**
     * Tải logo cho một mục của dải "Liên kết website" (CR-21).
     *
     * <p>⛔ Ràng buộc "chỉ vị trí {@code LIEN_KET}" nằm ở {@code MenuService.uploadLogo}, không ở
     * đây: đường này là một trong các đường vào, còn service là chỗ dữ liệu đi qua (quy tắc 12).
     *
     * <p>⛔ Không nhận SVG — xem {@code MenuService.DINH_DANG_LOGO}. Logo cơ quan cấp trên là tệp
     * lấy từ nơi khác về, và SVG là một tài liệu chạy được script.
     */
    @PostMapping(path = "/items/{publicId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải logo cho mục Liên kết cổng TTĐT — nhận PNG, JPEG, WebP")
    @RequirePermission("cms:layout:manage")
    public MenuService.MenuNode uploadLogo(@PathVariable UUID publicId, @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("file", "FILE_EMPTY", "");
        }
        try {
            return menus.uploadLogo(publicId, file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003, e)
                    .withDetail("file", "FILE_READ_FAILED", file.getOriginalFilename());
        }
    }

    @DeleteMapping("/items/{publicId}/logo")
    @Operation(summary = "Gỡ logo — mục quay về thẻ chữ")
    @RequirePermission("cms:layout:manage")
    public MenuService.MenuNode removeLogo(@PathVariable UUID publicId) {
        return menus.removeLogo(publicId);
    }
}
