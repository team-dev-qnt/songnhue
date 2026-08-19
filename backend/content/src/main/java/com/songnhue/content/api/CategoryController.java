package com.songnhue.content.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.content.application.CategoryService;
import com.songnhue.content.domain.Category;
import com.songnhue.core.common.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục nội dung — {@code /api/v1/cms/categories/**} (CN-01.2).
 *
 * <p>Một quyền duy nhất {@code cms:category:manage} cho cả đọc lẫn ghi, đúng như ma trận phân quyền
 * đã seed: danh mục là cấu hình cổng, không phải dữ liệu vận hành hằng ngày, nên tách quyền đọc/ghi
 * chỉ thêm ô trống trong bảng phân quyền mà không ai dùng.
 */
@RestController
@RequestMapping("/api/v1/cms/categories")
@Tag(name = "01-cms · Danh mục nội dung", description = "Cây danh mục của cổng thông tin")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    public record SaveRequest(@NotBlank @Size(max = 255) String name, @Size(max = 255) String slug, UUID parentId) {}

    public record CategoryNode(
            UUID publicId,
            String name,
            String slug,
            UUID parentPublicId,
            Short depth,
            Integer sortOrder,
            boolean visible) {}

    @GetMapping
    @Operation(summary = "Toàn bộ cây danh mục")
    @RequirePermission("cms:category:manage")
    public List<CategoryNode> tree() {
        List<Category> all = categories.tree();
        return all.stream().map(c -> toNode(c, all)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm danh mục")
    @RequirePermission("cms:category:manage")
    public CategoryNode create(@Valid @RequestBody SaveRequest request) {
        Category saved = categories.create(request.name(), request.slug(), request.parentId());
        return toNode(saved, categories.tree());
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Đổi tên / slug")
    @RequirePermission("cms:category:manage")
    public CategoryNode rename(@PathVariable UUID publicId, @Valid @RequestBody SaveRequest request) {
        Category saved = categories.rename(publicId, request.name(), request.slug());
        return toNode(saved, categories.tree());
    }

    @PutMapping("/{publicId}/parent")
    @Operation(summary = "Chuyển sang danh mục cha khác — cây con đi theo")
    @RequirePermission("cms:category:manage")
    public CategoryNode move(@PathVariable UUID publicId, @RequestBody MoveRequest request) {
        Category saved = categories.move(publicId, request.newParentId());
        return toNode(saved, categories.tree());
    }

    public record MoveRequest(UUID newParentId) {}

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá danh mục — chặn khi còn bài viết hoặc còn danh mục con")
    @RequirePermission("cms:category:manage")
    public void delete(@PathVariable UUID publicId) {
        categories.delete(publicId);
    }

    /**
     * Dịch khoá cha sang {@code publicId}.
     *
     * <p>Phải tra ngược trong danh sách vì entity chỉ giữ khoá nội bộ, mà API thì không được lộ khoá
     * chạy số (§4.2). Danh mục của một cổng thông tin đếm bằng chục nên duyệt danh sách là đủ; đây
     * cũng là cây được tải nguyên khối để dựng giao diện.
     */
    private static CategoryNode toNode(Category c, List<Category> all) {
        UUID parentPublicId = c.getParentId() == null
                ? null
                : all.stream()
                        .filter(x -> x.getId().equals(c.getParentId()))
                        .map(Category::getPublicId)
                        .findFirst()
                        .orElse(null);
        return new CategoryNode(
                c.getPublicId(),
                c.getName(),
                c.getSlug(),
                parentPublicId,
                c.getDepth(),
                c.getSortOrder(),
                c.isVisible());
    }
}
