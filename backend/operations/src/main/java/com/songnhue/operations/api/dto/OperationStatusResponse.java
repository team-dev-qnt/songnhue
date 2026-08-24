package com.songnhue.operations.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.songnhue.operations.domain.ConstructionOperationStatus;
import com.songnhue.operations.domain.OperationStatusCode;

/**
 * Một dòng lịch sử tình hình vận hành — CN-02.11.
 *
 * <p>Trả kèm {@code name} và {@code colorHex} của mã: bảng lịch sử hiển thị badge màu, mà bắt giao
 * diện tự tra danh mục để tô màu thì màn hình nhập liệu phải nắm quyền đọc danh mục quản trị.
 */
public record OperationStatusResponse(
        UUID publicId,
        String operationCode,
        String operationName,
        String colorHex,
        BigDecimal parameterValue,
        String parameterUnit,
        String note,
        OffsetDateTime effectiveAt) {

    public static OperationStatusResponse from(ConstructionOperationStatus entity) {
        OperationStatusCode code = entity.getOperationCode();
        return new OperationStatusResponse(
                entity.getPublicId(),
                code.getCode(),
                code.getName(),
                code.getColorHex(),
                entity.getParameterValue(),
                code.getParameterUnit(),
                entity.getNote(),
                entity.getEffectiveAt());
    }
}
