package com.songnhue.hydro.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.songnhue.hydro.domain.AlertConditionType;
import com.songnhue.hydro.domain.AlertEventStatus;

/**
 * DTO của máy cảnh báo ngưỡng — WS-33.
 *
 * <h2>⭐⭐ Mọi {@link BigDecimal} ra dây đều {@code @JsonFormat(STRING)}</h2>
 *
 * <p>Đây là bài học T28.27 / V2, đã cắn <b>hai lần</b> ở hai nửa của cùng một đường: Jackson serialize
 * {@code BigDecimal} thành <b>số JSON</b>, và {@code JSON.parse} của trình duyệt biến {@code 2.30}
 * thành {@code 2.3} — thang đo của loại chỉ số biến mất, dù CSDL giữ đúng. Với ngưỡng cảnh báo thì
 * hậu quả nặng hơn một cột hiển thị sai: người vận hành đọc lại con số mình vừa nhập và thấy nó khác
 * đi, rồi sửa lại — sửa vào một chỗ vốn không sai.
 */
public final class HydroAlertDtos {

    private HydroAlertDtos() {}

    // ------------------------------------------------------------------ mức cảnh báo (T33.1)

    public record AlertLevelView(
            UUID id,
            String code,
            String name,
            String colorToken,
            Integer severityRank,
            boolean active,
            String description) {}

    /**
     * @param colorToken ⛔ khoá {@code design-tokens}, ⛔ không phải mã hex — mẫu ở đây chặn sớm, và
     *     {@code ck_alert_levels_color_token} chặn ở tầng CSDL. Hai tầng cố ý
     */
    public record AlertLevelRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 60) String colorToken,
            @NotNull @Min(1) @Max(999) Integer severityRank,
            Boolean active,
            @Size(max = 500) String description) {}

    // ------------------------------------------------------------------ ngưỡng (T33.2)

    public record AlertRuleView(
            UUID id,
            UUID stationId,
            String stationCode,
            String stationName,
            String measurementTypeCode,
            String measurementTypeName,
            String unit,
            UUID alertLevelId,
            String alertLevelCode,
            String alertLevelName,
            String colorToken,
            AlertConditionType conditionType,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal thresholdValue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal thresholdValueHigh,
            Integer delayMinutes,
            boolean active,
            String note) {}

    /**
     * @param thresholdValueHigh ⛔ chỉ có nghĩa với {@code OUT_OF_RANGE}; loại khác gửi lên cũng bị
     *     bỏ, và {@code ck_alert_rules_high_paired} chặn ở tầng CSDL. ⚠ Bỏ <b>im lặng</b> ở đây là
     *     chấp nhận được vì DTO là mô tả của một loại điều kiện, ⛔ không phải một trường độc lập —
     *     khác hẳn N1 (14 trường validate rồi ghi 7), nơi trường bị rơi là trường người dùng chủ ý
     *     nhập
     */
    public record AlertRuleRequest(
            @NotNull UUID stationId,
            @NotBlank String measurementTypeCode,
            @NotNull UUID alertLevelId,
            @NotNull AlertConditionType conditionType,
            @NotNull BigDecimal thresholdValue,
            BigDecimal thresholdValueHigh,
            @Min(0) @Max(1440) Integer delayMinutes,
            Boolean active,
            @Size(max = 500) String note) {}

    /** Sửa: bộ ba (điểm đo × loại chỉ số × mức) là <b>bất biến</b> — xem {@code AlertRuleService.update}. */
    public record AlertRuleUpdateRequest(
            @NotNull AlertConditionType conditionType,
            @NotNull BigDecimal thresholdValue,
            BigDecimal thresholdValueHigh,
            @Min(0) @Max(1440) Integer delayMinutes,
            Boolean active,
            @Size(max = 500) String note) {}

    // ------------------------------------------------------------------ lịch sử cảnh báo (T33.11)

    /**
     * @param daXacNhan ⚠ {@code false} = cảnh báo <b>chưa từng gửi cho ai</b> (chưa giữ đủ
     *     {@code delayMinutes}). Cột này phải hiện trên màn hình
     * @param dongBoiNguoi ⚠ {@code false} + {@code DA_XU_LY} = máy tự đóng vì giá trị về dưới
     *     ngưỡng, ⛔ không phải "đã có người xử lý"
     */
    public record AlertEventView(
            UUID id,
            UUID stationId,
            String stationCode,
            String stationName,
            String measurementTypeName,
            String unit,
            String alertLevelCode,
            String alertLevelName,
            String colorToken,
            AlertConditionType conditionType,
            AlertEventStatus status,
            Instant startedAt,
            Instant confirmedAt,
            Instant endedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal triggerValue,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal peakValue,
            Instant peakAt,
            String reason,
            boolean daXacNhan,
            boolean dongBoiNguoi,
            String note) {}

    public record AlertEventCloseRequest(boolean falseAlarm, @Size(max = 500) String note) {}

    /** Điểm đo chưa cấu hình ngưỡng nào — nửa <b>đọc</b> của {@code HYD-2003} (T33.6). */
    public record StationWithoutThresholdView(UUID id, String code, String name, String orgUnitName) {}
}
