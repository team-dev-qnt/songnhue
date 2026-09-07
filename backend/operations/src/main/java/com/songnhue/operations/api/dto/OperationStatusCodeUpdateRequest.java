package com.songnhue.operations.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.songnhue.operations.domain.OperationalStatus;

public class OperationStatusCodeUpdateRequest implements OperationStatusCodeFields {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private boolean hasParameter;

    private String parameterUnit;

    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    private String colorHex;

    private OperationalStatus mappedStatus;

    @NotNull
    private Integer sortOrder;

    private boolean active;

    // Getters and Setters

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
