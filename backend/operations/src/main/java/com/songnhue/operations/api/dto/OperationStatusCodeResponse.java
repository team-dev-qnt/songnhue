package com.songnhue.operations.api.dto;

import java.util.UUID;

import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.domain.OperationalStatus;

/** Phản hồi danh mục mã tình hình vận hành — không lộ trường nội bộ. */
public record OperationStatusCodeResponse(
        Long id,
        UUID publicId,
        String code,
        String name,
        boolean hasParameter,
        String parameterUnit,
        String colorHex,
        OperationalStatus mappedStatus,
        int sortOrder,
        boolean active) {

    public static OperationStatusCodeResponse from(OperationStatusCode entity) {
        return new OperationStatusCodeResponse(
                entity.getId(),
                entity.getPublicId(),
                entity.getCode(),
                entity.getName(),
                entity.isHasParameter(),
                entity.getParameterUnit(),
                entity.getColorHex(),
                entity.getMappedStatus(),
                entity.getSortOrder(),
                entity.isActive());
    }
}
