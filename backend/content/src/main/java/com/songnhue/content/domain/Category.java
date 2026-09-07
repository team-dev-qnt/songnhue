package com.songnhue.content.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Danh mục nội dung của cổng thông tin — CN-01.2.
 *
 * <p><b>Không kế thừa {@code ScopedEntity}</b>, cùng lý do với {@link Article}: nội dung cổng là của
 * toàn Công ty. Gắn phạm vi đơn vị vào đây thì Xí nghiệp A không nhìn thấy danh mục do Xí nghiệp B
 * tạo, trong khi cả hai đang cùng dựng một cái cổng.
 *
 * <p>{@code path} và {@code depth} là <b>giá trị dẫn xuất</b> từ vị trí trong cây — chỉ service đặt,
 * không có setter cho mã nghiệp vụ khác. Cây tối đa 3 cấp (depth 0–2), chặn ở cả CSDL lẫn service:
 * ràng buộc CSDL bắt lỗi seed và lỗi lập trình, còn service mới trả được mã lỗi cho người dùng.
 */
@Entity
@Table(name = "categories")
@Audited(module = "cms", entityType = "Danh mục nội dung")
public class Category extends BaseEntity {

    /** Cây tối đa 3 cấp (CN-01.2) → depth chạy 0, 1, 2. */
    public static final int MAX_DEPTH = 2;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, length = 255)
    private String slug;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "description")
    private String description;

    @Column(name = "cover_attachment_public_id")
    private UUID coverAttachmentPublicId;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "depth", nullable = false)
    private Short depth = 0;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * Hiện/Ẩn trên cổng công khai.
     *
     * <p>⚠ Ẩn danh mục <b>không</b> ẩn bài viết trong đó — bài vẫn vào được bằng đường dẫn trực tiếp.
     * Ẩn ở đây nghĩa là "không hiện trong menu và danh sách", đúng như trạng thái Lưu trữ của bài
     * viết. Muốn bài biến mất khỏi cổng thì gỡ bài, không phải ẩn danh mục.
     */
    @Column(name = "visible", nullable = false)
    private boolean visible = true;

    protected Category() {}

    public Category(String name, String slug, Long parentId) {
        this.name = name;
        this.slug = slug;
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getCoverAttachmentPublicId() {
        return coverAttachmentPublicId;
    }

    public void setCoverAttachmentPublicId(UUID coverAttachmentPublicId) {
        this.coverAttachmentPublicId = coverAttachmentPublicId;
    }

    public String getPath() {
        return path;
    }

    public Short getDepth() {
        return depth;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** Chỉ {@code CategoryService} gọi — vị trí trong cây là giá trị dẫn xuất, không phải dữ liệu nhập. */
    public void placeAt(Long parentId, String path, short depth) {
        this.parentId = parentId;
        this.path = path;
        this.depth = depth;
    }
}
