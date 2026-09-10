package com.songnhue.hr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Chức vụ chuẩn hoá — CN-04.2 (gạch đầu dòng "Danh mục chức vụ chuẩn hóa").
 *
 * <h2>Vì sao {@link BaseEntity} chứ ⛔ không {@code ScopedEntity}</h2>
 *
 * <p>"Trưởng phòng" ở Xí nghiệp 1 và ở Xí nghiệp 2 là <b>cùng một chức vụ</b>. Bọc phạm vi đơn vị
 * vào danh mục này là chia nó thành N bản sao mà ⛔ không ai đồng bộ được, rồi báo cáo cơ cấu
 * (BCNS-05) sẽ đếm cùng một chức danh thành nhiều dòng. Tiền lệ cùng lý do:
 * {@code OperationStatusCode} của MOD-02.
 *
 * <p>⚠ Hệ quả phải biết: {@code PositionService} ⛔ <b>không</b> bọc {@code ScopeGuard} — và điều đó
 * phải được nói ra ở chính service, ⛔ không để người đọc sau tự đoán.
 */
@Entity
@Table(name = "positions")
@Audited(module = "hr", entityType = "Chức vụ")
public class Position extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * "Nhóm" của đặc tả — ⛔ CỐ Ý là chuỗi tự do, ⛔ không enum. Đặc tả ⛔ không liệt kê giá trị nào,
     * và quy tắc 16 xếp danh mục do khách vận hành vào loại <b>dữ liệu</b>: bịa một danh sách ở đây
     * là ép Công ty phải deploy để thêm một nhóm.
     */
    @Column(name = "position_group", length = 100)
    private String positionGroup;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPositionGroup() {
        return positionGroup;
    }

    public void setPositionGroup(String positionGroup) {
        this.positionGroup = positionGroup;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
