package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng lịch sử cảnh báo, đã ghép sẵn tên để người đọc hiểu — T33.11.
 *
 * <p>⚠ Mọi thứ ngoài con số đều đến từ <b>bảng khai báo</b>, không từ nguồn: tên điểm đo, tên loại
 * chỉ số, đơn vị, tên mức cảnh báo. Nguồn {@code bhh40} chỉ mang một mã {@code F#####} + mốc + cm.
 *
 * @param daXacNhan ⚠ {@code false} nghĩa là cảnh báo <b>chưa từng được gửi cho ai</b> — điều kiện
 *     chưa giữ đủ {@code delay_minutes}. Cột này phải hiện trên màn hình: một dòng lịch sử không
 *     phân biệt được "đã báo động" với "đã theo dõi rồi thôi" là một dòng đọc sai được
 * @param dongBoiNguoi ⚠ {@code false} + trạng thái {@code DA_XU_LY} = máy tự đóng vì giá trị về
 *     dưới ngưỡng. Đây ⛔ không phải "đã có người xử lý"
 */
public record CanhBaoRow(
        UUID id,
        UUID stationId,
        String stationCode,
        String stationName,
        String measurementTypeName,
        String unit,
        String levelCode,
        String levelName,
        String colorToken,
        AlertConditionType conditionType,
        AlertEventStatus status,
        Instant startedAt,
        Instant confirmedAt,
        Instant endedAt,
        BigDecimal triggerValue,
        BigDecimal peakValue,
        Instant peakAt,
        String reason,
        boolean daXacNhan,
        boolean dongBoiNguoi,
        String note) {}
