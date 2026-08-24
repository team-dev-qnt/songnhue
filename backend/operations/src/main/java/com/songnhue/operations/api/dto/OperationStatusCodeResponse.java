package com.songnhue.operations.api.dto;

import java.util.UUID;

import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.domain.OperationalStatus;

/**
 * Phản hồi danh mục mã tình hình vận hành.
 *
 * <p>⛔ <b>Không có trường {@code id}.</b> Bản trước trả cả khoá nội bộ, và giao diện dùng đúng nó để
 * dựng đường dẫn {@code PUT/DELETE} — nghĩa là khoá tự tăng đi ra tới trình duyệt rồi quay lại. Đây
 * là điều {@code BaseEntity} đã ghi cấm ngay ở javadoc trường {@code publicId} từ WS-4.
 */
public record OperationStatusCodeResponse(
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
