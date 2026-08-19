package com.songnhue.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một mục của menu điều hướng — CN-01.5.
 *
 * <p>Header và Footer dùng chung bảng này, phân biệt bằng {@link MenuPosition}. Chúng là <b>hai cây
 * độc lập</b>: một mục con phải cùng vị trí với cha, và ràng buộc đó được thi hành bằng khoá ngoại
 * ghép {@code (parent_id, position)} ở CSDL chứ không bằng trí nhớ của tầng service.
 *
 * <p>{@code path}/{@code depth} là giá trị dẫn xuất, chỉ {@code MenuService} đặt.
 */
@Entity
@Table(name = "menu_items")
@Audited(module = "cms", entityType = "Mục menu")
public class MenuItem extends BaseEntity {

    /** Mục cha → mục con → mục cháu. Sâu hơn nữa thì không thao tác được bằng chuột. */
    public static final int MAX_DEPTH = 2;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 20)
    private MenuPosition position;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private MenuLinkType linkType;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "open_new_tab", nullable = false)
    private boolean openNewTab;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "depth", nullable = false)
    private Short depth = 0;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected MenuItem() {}

    public MenuItem(MenuPosition position, String label, MenuLinkType linkType) {
        this.position = position;
        this.label = label;
        this.linkType = linkType;
    }

    /**
     * Đặt đích của mục, xoá sạch những đích không thuộc loại đang chọn.
     *
     * <p>Dọn hai cột kia là phần quan trọng: đổi một mục từ Danh mục sang URL mà để nguyên
     * {@code categoryId} thì ràng buộc CSDL sẽ từ chối, và người dùng nhận một lỗi kỹ thuật cho một
     * thao tác hoàn toàn hợp lệ.
     */
    public void pointTo(MenuLinkType linkType, Long categoryId, Long articleId, String url) {
        this.linkType = linkType;
        this.categoryId = linkType == MenuLinkType.CATEGORY ? categoryId : null;
        this.articleId = linkType == MenuLinkType.ARTICLE ? articleId : null;
        this.url = (linkType == MenuLinkType.URL || linkType == MenuLinkType.EXTERNAL_DOC) ? url : null;
    }

    /** Chỉ {@code MenuService} gọi — vị trí trong cây là giá trị dẫn xuất, không phải dữ liệu nhập. */
    public void placeAt(Long parentId, String path, short depth) {
        this.parentId = parentId;
        this.path = path;
        this.depth = depth;
    }

    public MenuPosition getPosition() {
        return position;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public MenuLinkType getLinkType() {
        return linkType;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public String getUrl() {
        return url;
    }

    public boolean isOpenNewTab() {
        return openNewTab;
    }

    public void setOpenNewTab(boolean openNewTab) {
        this.openNewTab = openNewTab;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
