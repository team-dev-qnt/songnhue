package com.songnhue.operations.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OperationStatusBatchItemRequest {

    @NotNull
    private Long constructionId;

    @NotBlank
    private String operationCode;

    private BigDecimal parameterValue;

    private String note;

    @NotNull
    private OffsetDateTime effectiveAt;

    public Long getConstructionId() {
        return constructionId;
    }

    public void setConstructionId(Long constructionId) {
        this.constructionId = constructionId;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public BigDecimal getParameterValue() {
        return parameterValue;
    }

    public void setParameterValue(BigDecimal parameterValue) {
        this.parameterValue = parameterValue;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(OffsetDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }
}
