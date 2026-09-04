package com.songnhue.hydro.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.hydro.application.AlertEventService;
import com.songnhue.hydro.domain.CanhBaoRow;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Lịch sử cảnh báo ngưỡng — {@code /api/v1/hyd/alerts/**} (T33.11).
 *
 * <p>⚠ Quyền ở FE <b>chỉ để ẩn/hiện</b>. Nút "Đã xử lý" / "Báo động giả" gác bằng
 * {@code @RequirePermission("hyd:alert:handle")} <b>ở đây</b> — §9.10.4.
 */
@RestController
@RequestMapping("/api/v1/hyd/alerts")
@Tag(name = "03-hyd · Cảnh báo ngưỡng", description = "Lịch sử cảnh báo và thao tác đóng")
public class AlertEventController {

    private final AlertEventService events;

    public AlertEventController(AlertEventService events) {
        this.events = events;
    }

    /**
     * @param dangMo {@code true} = chỉ cảnh báo còn mở; {@code false} = chỉ đã đóng; bỏ trống = tất
     *     cả. ⚠ Dùng {@code Boolean} chứ ⛔ không {@code boolean}: {@code false} mặc định sẽ biến ô
     *     lọc "tất cả" thành ô lọc "đã đóng" mà ⛔ không ai thấy
     */
    @GetMapping
    @Operation(summary = "Lịch sử cảnh báo, mới nhất trước")
    @RequirePermission("hyd:alert:view")
    public Page<HydroAlertDtos.AlertEventView> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) UUID diemDoId,
            @RequestParam(required = false) Boolean dangMo,
            @RequestParam(required = false) Instant tu,
            @RequestParam(required = false) Instant den) {
        return events.trang(diemDoId, dangMo, tu, den, PageUtils.toPageable(page, size, null, Set.of()))
                .map(AlertEventController::toView);
    }

    /**
     * Đóng bằng tay.
     *
     * <p>⛔ Endpoint này ⛔ <b>không</b> tạo bản ghi khắc phục — T33.10. Nút <i>"Tạo bản ghi khắc
     * phục"</i> trên màn hình dẫn sang biểu mẫu của MOD-02 với {@code alertEventPublicId} điền sẵn,
     * và người dùng quyết định. Cảnh báo tự sinh bản ghi bảo trì là đổ rác vào sổ gốc của cả MOD-02.
     */
    @PostMapping("/{publicId}/dong")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đóng cảnh báo — đã xử lý hoặc báo động giả (HYD-2011 nếu đã đóng)")
    @RequirePermission("hyd:alert:handle")
    public void dong(@PathVariable UUID publicId, @Valid @RequestBody HydroAlertDtos.AlertEventCloseRequest request) {
        events.dong(publicId, request.falseAlarm(), request.note());
    }

    static HydroAlertDtos.AlertEventView toView(CanhBaoRow r) {
        return new HydroAlertDtos.AlertEventView(
                r.id(),
                r.stationId(),
                r.stationCode(),
                r.stationName(),
                r.measurementTypeName(),
                r.unit(),
                r.levelCode(),
                r.levelName(),
                r.colorToken(),
                r.conditionType(),
                r.status(),
                r.startedAt(),
                r.confirmedAt(),
                r.endedAt(),
                r.triggerValue(),
                r.peakValue(),
                r.peakAt(),
                r.reason(),
                r.daXacNhan(),
                r.dongBoiNguoi(),
                r.note());
    }
}
