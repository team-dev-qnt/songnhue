package com.songnhue.content.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Category;
import com.songnhue.content.infra.CategoryRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.tree.MaterializedPath;
import com.songnhue.core.common.util.VietnameseUtils;

/**
 * Danh mục nội dung — CN-01.2.
 *
 * <p>Dùng lại {@link MaterializedPath} của Core thay vì tự tính chuỗi đường dẫn: chỗ này đã trả giá
 * một lần ở {@code org_units}, và một path sai là sai toàn bộ cây con bên dưới mà không có lỗi nào.
 */
@Service
public class CategoryService {

    /** Giá trị giữ chỗ để qua được ràng buộc NOT NULL trong lượt INSERT đầu — xem {@link #create}. */
    private static final String PATH_TAM = "/";

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<Category> tree() {
        return categories.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc();
    }

    @Transactional(readOnly = true)
    public Category get(UUID publicId) {
        return categories
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    @Transactional
    public Category create(String name, String slug, UUID parentPublicId) {
        Category parent = parentPublicId == null ? null : get(parentPublicId);
        String finalSlug = requireUniqueSlug(slug, name, null);

        Category category = new Category(name, finalSlug, parent == null ? null : parent.getId());

        // ⚠ Bài toán con-gà-quả-trứng: `path` chứa chính id của bản ghi, mà id do CSDL sinh lúc
        //   INSERT, trong khi cột `path` là NOT NULL. Ba bước trong cùng transaction — đặt path tạm,
        //   flush để lấy id, rồi đặt path thật. Giống hệt `OrgUnitService`; bỏ bước đầu thì INSERT
        //   chết vì vi phạm NOT NULL, và đó đúng là lỗi tôi vừa mắc lại.
        category.placeAt(category.getParentId(), PATH_TAM, (short) 0);
        Category saved = categories.saveAndFlush(category);
        place(saved, parent);
        return categories.saveAndFlush(saved);
    }

    @Transactional
    public Category rename(UUID publicId, String name, String slug) {
        Category category = get(publicId);
        category.setName(name);
        category.setSlug(requireUniqueSlug(slug, name, category.getId()));
        return category;
    }

    /**
     * Hiện / ẩn một danh mục trên cổng công khai.
     *
     * <h3>⚠⚠ Vì sao phương thức này phải tồn tại</h3>
     *
     * Cột {@code visible} có từ {@code V202608191016} và được trả ra ở DTO của màn hình quản trị —
     * nhưng cho tới lượt này <b>không endpoint nào ghi được nó</b>. Nghĩa là quản trị viên nhìn
     * thấy trạng thái Hiện/Ẩn của từng danh mục mà không có cách nào đổi: đúng hình dạng quy tắc 15
     * (<i>cột chưa ai đọc/ghi là một lỗi, không phải việc để dành</i>), chỉ khác là ở chiều ghi.
     *
     * <p>Nó thành chuyện gấp ở đợt chỉnh sửa 27/8: CR-01 bỏ mục "Thông báo" khỏi cây nội dung, và
     * migration chọn <b>ẩn</b> thay vì xoá với lý do "ẩn là thao tác quay lui được bằng một cú bấm".
     * Lý do ấy chỉ đúng khi cú bấm đó tồn tại.
     *
     * <p>⚠ Ẩn một danh mục sẽ rút <b>cả nhánh dưới nó</b> khỏi cổng — xem
     * {@code PublicPortalService.categories()}. Đó là hành vi có chủ đích, không phải tác dụng phụ.
     */
    @Transactional
    public Category setVisible(UUID publicId, boolean visible) {
        Category category = get(publicId);
        category.setVisible(visible);
        return category;
    }

    /**
     * Chuyển danh mục sang chỗ khác trong cây.
     *
     * <p>Ba thứ phải kiểm, và thiếu thứ ba là hỏng dữ liệu chứ không chỉ hỏng màn hình: không vượt 3
     * cấp · không tự làm cha của chính mình · <b>và cây con đi theo</b> — mọi hậu duệ phải được tính
     * lại path, nếu không thì chúng trỏ vào một nhánh không còn tồn tại.
     */
    @Transactional
    public Category move(UUID publicId, UUID newParentPublicId) {
        Category category = get(publicId);
        Category newParent = newParentPublicId == null ? null : get(newParentPublicId);

        if (newParent != null && MaterializedPath.wouldCreateCycle(category.getPath(), newParent.getPath())) {
            throw new BusinessRuleException(ErrorCode.SYS_0008, "MOVE", "cha nằm trong chính cây con của nó");
        }

        String oldPrefix = category.getPath();
        place(category, newParent);
        String newPrefix = category.getPath();

        // Cây con phải đi theo. Bỏ bước này thì hậu duệ giữ path cũ và biến mất khỏi mọi truy vấn
        // theo cây — trông y hệt như bị xoá, nhưng dữ liệu vẫn nằm đó.
        for (Category descendant : categories.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc()) {
            if (!descendant.getId().equals(category.getId())
                    && MaterializedPath.isSelfOrDescendant(descendant.getPath(), oldPrefix)) {
                String moved = MaterializedPath.reparent(descendant.getPath(), oldPrefix, newPrefix);
                descendant.placeAt(descendant.getParentId(), moved, (short) MaterializedPath.depthOf(moved));
                requireDepth(descendant.getDepth());
            }
        }
        return category;
    }

    /**
     * Xoá mềm danh mục — CN-01.2 yêu cầu chặn khi còn bài viết.
     *
     * <p>Chặn chứ không tự gỡ bài ra: tự gỡ là lặng lẽ làm mồ côi một đống nội dung rồi báo "xoá
     * thành công". Người dùng phải tự quyết bài đi đâu.
     */
    @Transactional
    public void delete(UUID publicId) {
        Category category = get(publicId);

        if (categories.countLiveArticles(category.getId()) > 0) {
            throw new BusinessRuleException(ErrorCode.CMS_2003);
        }
        if (categories.countByParentIdAndDeletedAtIsNull(category.getId()) > 0) {
            throw new BusinessRuleException(ErrorCode.CMS_2004);
        }
        category.markDeleted(Instant.now());
    }

    // -------------------------------------------------------------------------

    private void place(Category category, Category parent) {
        String path = parent == null
                ? MaterializedPath.rootPath(category.getId())
                : MaterializedPath.childPath(parent.getPath(), category.getId());
        short depth = (short) MaterializedPath.depthOf(path);
        requireDepth(depth);
        category.placeAt(parent == null ? null : parent.getId(), path, depth);
    }

    private static void requireDepth(short depth) {
        if (depth > Category.MAX_DEPTH) {
            throw new BusinessRuleException(ErrorCode.CMS_2005);
        }
    }

    /**
     * Slug trống thì sinh từ tên; trùng thì <b>chặn cứng</b>.
     *
     * <p>Spec ghi "cảnh báo trùng", nhưng slug là địa chỉ công khai — trùng nghĩa là hai danh mục
     * tranh nhau một URL (điểm nghiệp vụ 4). Cảnh báo rồi vẫn cho lưu là để dữ liệu hỏng đi vào CSDL
     * kèm một lời nhắc mà không ai đọc.
     */
    private String requireUniqueSlug(String slug, String name, Long selfId) {
        String candidate = slug == null || slug.isBlank() ? VietnameseUtils.toSlug(name) : VietnameseUtils.toSlug(slug);
        Optional<Category> existing = categories.findBySlugAndDeletedAtIsNull(candidate);
        if (existing.isPresent() && !existing.get().getId().equals(selfId)) {
            throw new BusinessRuleException(ErrorCode.CMS_2001, candidate);
        }
        return candidate;
    }
}
