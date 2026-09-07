package com.songnhue.content.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.content.application.ContactCategoryService;
import com.songnhue.content.domain.ContactCategory;
import com.songnhue.core.common.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục phân loại liên hệ — CN-01.4, quy tắc 16.
 *
 * <h2>Vì sao có màn hình CRUD cho một danh mục chỉ vài dòng</h2>
 *
 * <p>Vì đây là danh mục <b>của Công ty</b>, không phải của lập trình viên. Một enum trong mã nghĩa
 * là thêm mục "Kiến nghị về giá nước" phải chờ một lượt deploy — và trong lúc chờ, cán bộ sẽ gán
 * bừa vào một mục gần đúng, làm hỏng chính con số mà việc phân loại sinh ra để có.
 *
 * <p>⛔ Danh mục ra đời <b>rỗng</b>. Màn hình nói thẳng là rỗng; ⛔ không seed giá trị mẫu.
 */
@RestController
@RequestMapping("/api/v1/cms/contact-categories")
@Tag(name = "01-cms · Liên hệ", description = "Phân loại liên hệ — danh mục do Công ty tự vận hành")
public class ContactCategoryController {

    private final ContactCategoryService danhMuc;

    public ContactCategoryController(ContactCategoryService danhMuc) {
        this.danhMuc = danhMuc;
    }

    public record CategoryView(UUID publicId, String code, String name, boolean active, int sortOrder) {
        static CategoryView of(ContactCategory c) {
            return new CategoryView(c.getPublicId(), c.getCode(), c.getName(), c.isActive(), c.getSortOrder());
        }
    }

    /** ⚠ {@code code} chỉ đọc lúc tạo — xem {@code ContactCategoryService#capNhat}. */
    public record CategoryForm(String code, String name, Boolean active, Integer sortOrder) {}

    @GetMapping
    @Operation(summary = "Danh sách phân loại, gồm cả phân loại đã tắt")
    @RequirePermission("cms:contact:manage")
    public List<CategoryView> list() {
        return danhMuc.danhSach().stream().map(CategoryView::of).toList();
    }

    @PostMapping
    @Operation(summary = "Thêm một phân loại")
    @RequirePermission("cms:contact:manage")
    public CategoryView create(@RequestBody CategoryForm form) {
        return CategoryView.of(danhMuc.tao(form.code(), form.name(), form.sortOrder() == null ? 0 : form.sortOrder()));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa tên, thứ tự, bật/tắt — ⛔ không đổi được mã")
    @RequirePermission("cms:contact:manage")
    public CategoryView update(@PathVariable UUID publicId, @RequestBody CategoryForm form) {
        return CategoryView.of(danhMuc.capNhat(
                publicId,
                form.name(),
                form.active() == null || form.active(),
                form.sortOrder() == null ? 0 : form.sortOrder()));
    }

    @DeleteMapping("/{publicId}")
    @Operation(summary = "Xoá — chỉ khi chưa liên hệ nào gán; đã dùng thì tắt thay vì xoá")
    @RequirePermission("cms:contact:manage")
    public ResponseEntity<Void> delete(@PathVariable UUID publicId) {
        danhMuc.xoa(publicId);
        return ResponseEntity.noContent().build();
    }
}
