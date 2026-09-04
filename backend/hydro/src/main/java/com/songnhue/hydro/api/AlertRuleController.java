package com.songnhue.hydro.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hydro.application.AlertRuleForm;
import com.songnhue.hydro.application.AlertRuleService;
import com.songnhue.hydro.domain.AlertRule;
import com.songnhue.hydro.domain.Station;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cấu hình ngưỡng cảnh báo — {@code /api/v1/hyd/alert-rules/**} (T33.2 · T33.11, nghiệm thu G9).
 *
 * <p>⚠ Mọi vai trò có {@code hyd:threshold:manage} phải <b>đi thử hết biểu mẫu</b>, kể cả các ô chọn
 * phụ trợ. Hình dạng T27.20 / T28.25 đã tái phát <b>hai lần</b>: một biểu mẫu mà ô bắt buộc đứng sau
 * một quyền mà vai trò sở hữu biểu mẫu ấy ⛔ không có. Ở đây có <b>ba</b> ô chọn như vậy — điểm đo,
 * loại chỉ số, mức cảnh báo — và cả ba đọc bằng quyền khác nhau.
 */
@RestController
@RequestMapping("/api/v1/hyd/alert-rules")
@Tag(name = "03-hyd · Ngưỡng cảnh báo", description = "Cấu hình ngưỡng theo điểm đo × loại chỉ số × mức")
public class AlertRuleController {

    private final AlertRuleService rules;

    public AlertRuleController(AlertRuleService rules) {
        this.rules = rules;
    }

    @GetMapping
    @Operation(summary = "Danh sách ngưỡng — lọc theo điểm đo nếu có")
    @RequirePermission("hyd:threshold:view")
    public List<HydroAlertDtos.AlertRuleView> list(@RequestParam(required = false) UUID stationId) {
        return rules.list(stationId).stream().map(AlertRuleController::toView).toList();
    }

    /**
     * ⭐ Nửa <b>đọc</b> của {@code HYD-2003} — T33.6.
     *
     * <p>Không có endpoint này thì <i>"chưa cấu hình ngưỡng"</i> là một trạng thái đúng mà ⛔ không
     * ai nhìn thấy: ngày Công ty đưa bộ mức thật, không ai biết còn thiếu điểm nào cho tới lúc một
     * trận lũ đi qua trong im lặng.
     */
    @GetMapping("/chua-cau-hinh")
    @Operation(summary = "Điểm đo chưa cấu hình ngưỡng nào (T33.6)")
    @RequirePermission("hyd:threshold:view")
    public List<HydroAlertDtos.StationWithoutThresholdView> chuaCauHinh() {
        return rules.diemDoChuaCauHinh().stream()
                .map(AlertRuleController::toThieuView)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm ngưỡng")
    @RequirePermission("hyd:threshold:manage")
    public HydroAlertDtos.AlertRuleView create(@Valid @RequestBody HydroAlertDtos.AlertRuleRequest request) {
        return toView(rules.create(new AlertRuleForm(
                request.stationId(),
                request.measurementTypeCode(),
                request.alertLevelId(),
                request.conditionType(),
                request.thresholdValue(),
                request.thresholdValueHigh(),
                request.delayMinutes(),
                request.active(),
                request.note())));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa ngưỡng — bộ ba điểm đo × loại chỉ số × mức là bất biến")
    @RequirePermission("hyd:threshold:manage")
    public HydroAlertDtos.AlertRuleView update(
            @PathVariable UUID publicId, @Valid @RequestBody HydroAlertDtos.AlertRuleUpdateRequest request) {
        return toView(rules.update(
                publicId,
                request.conditionType(),
                request.thresholdValue(),
                request.thresholdValueHigh(),
                request.delayMinutes(),
                request.active(),
                request.note()));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá ngưỡng")
    @RequirePermission("hyd:threshold:manage")
    public void delete(@PathVariable UUID publicId) {
        rules.delete(publicId);
    }

    static HydroAlertDtos.AlertRuleView toView(AlertRule r) {
        return new HydroAlertDtos.AlertRuleView(
                r.getPublicId(),
                r.getStation().getPublicId(),
                r.getStation().getCode(),
                r.getStation().getName(),
                r.getMeasurementType().getCode(),
                r.getMeasurementType().getName(),
                r.getMeasurementType().getUnit(),
                r.getAlertLevel().getPublicId(),
                r.getAlertLevel().getCode(),
                r.getAlertLevel().getName(),
                r.getAlertLevel().getColorToken(),
                r.getConditionType(),
                r.getThresholdValue(),
                r.getThresholdValueHigh(),
                r.getDelayMinutes(),
                r.isActive(),
                r.getNote());
    }

    private static HydroAlertDtos.StationWithoutThresholdView toThieuView(Station s) {
        // ⛔ Tên đơn vị để rỗng chứ ⛔ không bịa: 19/19 điểm đo còn `org_unit_id = NULL` (OI-05).
        //   Một ô rỗng nói "chưa gán đơn vị"; một ô điền bừa nói một điều sai mà không ai kiểm lại.
        return new HydroAlertDtos.StationWithoutThresholdView(s.getPublicId(), s.getCode(), s.getName(), null);
    }
}
