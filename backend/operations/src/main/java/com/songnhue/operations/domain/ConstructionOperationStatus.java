package com.songnhue.operations.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * Tình hình vận hành của công trình (bảng log append-only) — CN-02.11.
 *
 * <p>⚠ <b>CÓ thuộc phạm vi đơn vị.</b> Sao chép {@code org_unit_id} từ công trình lúc ghi nhận.
 */
@Entity
@Table(name = "construction_operation_status")
@Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)
@Audited(module = "ops")
public class ConstructionOperationStatus extends ScopedEntity {

    @Column(name = "construction_id", nullable = false)
    private Long constructionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_code_id", nullable = false)
    private OperationStatusCode operationCode;

    @Column(name = "parameter_value", precision = 10, scale = 2)
    private BigDecimal parameterValue;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "effective_at", nullable = false)
    private OffsetDateTime effectiveAt;

    public Long getConstructionId() {
        return constructionId;
    }

    public void setConstructionId(Long constructionId) {
        this.constructionId = constructionId;
    }

    public OperationStatusCode getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(OperationStatusCode operationCode) {
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
