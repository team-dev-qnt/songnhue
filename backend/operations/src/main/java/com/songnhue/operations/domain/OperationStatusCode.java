package com.songnhue.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Danh mục mã tình hình vận hành do Công ty quản lý — CN-02.11.
 *
 * <p>⛔ <b>KHÔNG thuộc phạm vi đơn vị.</b> Bảng này do Công ty vận hành chung (giống categories),
 * gắn phạm vi vào đây là Xí nghiệp này không thấy mã của Xí nghiệp kia.
 */
@Entity
@Table(name = "operation_status_codes")
@Audited(module = "ops")
public class OperationStatusCode extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "has_parameter", nullable = false)
    private boolean hasParameter;

    @Column(name = "parameter_unit", length = 50)
    private String parameterUnit;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapped_status", length = 20)
    private OperationalStatus mappedStatus;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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

    public boolean isHasParameter() {
        return hasParameter;
    }

    public void setHasParameter(boolean hasParameter) {
        this.hasParameter = hasParameter;
    }

    public String getParameterUnit() {
        return parameterUnit;
    }

    public void setParameterUnit(String parameterUnit) {
        this.parameterUnit = parameterUnit;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public OperationalStatus getMappedStatus() {
        return mappedStatus;
    }

    public void setMappedStatus(OperationalStatus mappedStatus) {
        this.mappedStatus = mappedStatus;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
