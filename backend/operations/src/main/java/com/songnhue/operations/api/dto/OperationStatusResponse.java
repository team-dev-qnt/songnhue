package com.songnhue.operations.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

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
        /**
         * ⚠⚠ Ra dây dưới dạng <b>CHUỖI</b> — <b>nửa còn lại</b> của T28.27.
         *
         * <p>Đợt ấy vá {@code PublicOperationStatusService.OperationStatusRow} cho cổng công khai và
         * dừng ở đó. Cùng một giá trị đi ra bằng <i>hai</i> đường, và đường quản trị — nơi trực ban
         * đọc lại chính con số mình vừa nhập — ở lại nguyên trạng suốt từ đó: {@code 2.30} tuần tự
         * hoá thành số JSON {@code 2.3}, {@code JSON.parse} cho ra {@code double}, và Drawer lịch sử
         * hiện <b>2,3</b>. Đúng luật 12: <i>bảo đảm phải đặt ở chỗ dữ liệu đi qua, không ở một nơi
         * gọi</i> — ở đây không đặt được một chỗ nên phải đếm đủ <b>cả hai</b> đường ra.
         *
         * <p>{@code admin-app/shared/api-types.ts} khai {@code parameterValue: string | null} từ
         * trước. Khai kiểu là một <b>lời khẳng định</b>, không phải một phép đo — suốt thời gian ấy
         * dây gửi số còn kiểu nói chuỗi, và không cổng kiểm nào đọc thân phản hồi của đường quản trị
         * để thấy hai vế nói khác nhau. {@code OperationStatusHttpTest.giaTriThamSoRaDayLaChuoi} là
         * phép đo đó.
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal parameterValue,
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
