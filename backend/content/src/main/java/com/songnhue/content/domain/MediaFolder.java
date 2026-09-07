package com.songnhue.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Thư mục của thư viện media — CN-01.3.
 *
 * <p>⭐ <b>Chỉ là thư mục.</b> Không có entity tệp đi kèm: tệp media là dòng {@code attachments} với
 * {@code owner_type = 'MEDIA_FOLDER'} và {@code owner_id} trỏ vào đây (điểm nghiệp vụ 8). Dựng thêm
 * một entity tệp riêng là có hai nơi kiểm định dạng, hai nơi tính dung lượng, hai nơi xoá mềm — và
 * chúng lệch nhau theo thời gian, lặng lẽ.
 *
 * <p>{@code path}/{@code depth} là giá trị dẫn xuất, chỉ service đặt. Cây tối đa 3 cấp.
 */
@Entity
@Table(name = "media_folders")
@Audited(module = "cms", entityType = "Thư mục media")
public class MediaFolder extends BaseEntity {

    /** Ảnh / Video / Tài liệu → con → cháu. */
    public static final int MAX_DEPTH = 2;

    /** Khớp {@code attachments.owner_type} — sai chuỗi này là tệp không thuộc thư mục nào. */
    public static final String OWNER_TYPE = "MEDIA_FOLDER";

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "depth", nullable = false)
    private Short depth = 0;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    protected MediaFolder() {}

    public MediaFolder(String name, Long parentId) {
        this.name = name;
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
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

    /** Chỉ {@code MediaService} gọi — vị trí trong cây là giá trị dẫn xuất, không phải dữ liệu nhập. */
    public void placeAt(Long parentId, String path, short depth) {
        this.parentId = parentId;
        this.path = path;
        this.depth = depth;
    }
}
