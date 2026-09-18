package com.songnhue.hr.api;

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
import com.songnhue.hr.application.SuKienForm;
import com.songnhue.hr.application.TimelineService;
import com.songnhue.hr.domain.EmployeeEventType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Lịch sử công tác — {@code /api/v1/hr/employees/{id}/timeline} (CN-04.4).
 *
 * <p>Lồng dưới hồ sơ vì cùng lý do với {@link LyLichController}: ⛔ không có cách nào gọi mà bỏ qua
 * phép kiểm phạm vi.
 *
 * <p>⚠ Danh sách trả về sắp <b>reverse-chronological theo ngày HIỆU LỰC</b>, đúng đặc tả. ⛔ Đừng
 * để giao diện tự sắp lại theo {@code createdAt} — một quyết định ký tháng 3 có hiệu lực từ tháng 1
 * sẽ nhảy lên đầu và làm sai lịch sử công tác của một con người.
 */
@RestController
@RequestMapping("/api/v1/hr/employees/{hoSoId}/timeline")
@Tag(name = "04-hr · Lịch sử công tác", description = "Timeline 10 loại sự kiện — mỗi sự kiện một hàng")
public class TimelineController {

    private final TimelineService timeline;

    public TimelineController(TimelineService timeline) {
        this.timeline = timeline;
    }

    @GetMapping
    @Operation(summary = "Timeline công tác — mới nhất trước, lọc được theo loại")
    @RequirePermission("hr:employee:view")
    public List<HoSoConDtos.SuKienView> timeline(
            @PathVariable UUID hoSoId, @RequestParam(required = false) EmployeeEventType loai) {
        return timeline.timeline(hoSoId, loai).stream()
                .map(HoSoConDtos.SuKienView::of)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ghi một sự kiện công tác")
    @RequirePermission("hr:employee:update")
    public HoSoConDtos.SuKienView them(
            @PathVariable UUID hoSoId, @Valid @RequestBody HoSoConDtos.SuKienRequest request) {
        return HoSoConDtos.SuKienView.of(timeline.them(hoSoId, toForm(request)));
    }

    @PutMapping("/{suKienId}")
    @Operation(summary = "Sửa một sự kiện — THAY TOÀN PHẦN, có audit giá trị cũ/mới")
    @RequirePermission("hr:employee:update")
    public HoSoConDtos.SuKienView sua(
            @PathVariable UUID hoSoId,
            @PathVariable UUID suKienId,
            @Valid @RequestBody HoSoConDtos.SuKienRequest request) {
        return HoSoConDtos.SuKienView.of(timeline.sua(hoSoId, suKienId, toForm(request)));
    }

    @DeleteMapping("/{suKienId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá một sự kiện (xoá mềm)")
    @RequirePermission("hr:employee:update")
    public void xoa(@PathVariable UUID hoSoId, @PathVariable UUID suKienId) {
        timeline.xoa(hoSoId, suKienId);
    }

    private static SuKienForm toForm(HoSoConDtos.SuKienRequest r) {
        return new SuKienForm(r.eventType(), r.effectiveOn(), r.decisionNo(), r.decisionDate(), r.title(), r.detail());
    }
}
