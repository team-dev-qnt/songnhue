package com.songnhue.hydro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Loại chỉ số quan trắc — CN-03.1.
 *
 * <p>Danh mục toàn hệ thống, không thuộc phạm vi đơn vị nào ⇒ {@link BaseEntity}, không phải
 * {@code ScopedEntity}: "mực nước" không phải dữ liệu của riêng Xí nghiệp nào.
 *
 * <p>⚠ {@link #getUnit()} là đơn vị <b>đã chuẩn hoá trong CSDL</b>, không phải đơn vị nguồn trả về.
 * Nguồn bhh40 trả mực nước bằng <b>cm</b>; chỗ duy nhất được phép quy đổi là adapter (WS-30). Thêm
 * một cột "đơn vị nguồn" ở đây là mời mỗi truy vấn tự nhớ quy đổi, và sớm muộn có một truy vấn quên.
 */
@Entity
@Table(name = "measurement_types")
@Audited(module = "hyd", entityType = "Loại chỉ số quan trắc")
public class MeasurementType extends BaseEntity {

    /** Mã nghiệp vụ: {@code MUC_NUOC} / {@code LUONG_MUA} / {@code LUU_LUONG}. */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Đơn vị ĐÃ CHUẨN HOÁ ({@code m}, {@code mm}, {@code m³/s}). */
    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    /** Số chữ số thập phân khi lưu và hiển thị. Mực nước 3 (tới mm), lượng mưa 1. */
    @Column(name = "value_scale", nullable = false)
    private Short valueScale = 3;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "description", length = 500)
    private String description;

    protected MeasurementType() {}

    public MeasurementType(String code, String name, String unit, short valueScale) {
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.valueScale = valueScale;
    }

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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Short getValueScale() {
        return valueScale;
    }

    public void setValueScale(Short valueScale) {
        this.valueScale = valueScale;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
