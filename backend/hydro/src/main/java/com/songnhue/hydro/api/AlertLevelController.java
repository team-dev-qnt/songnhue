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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hydro.application.AlertLevelService;
import com.songnhue.hydro.domain.AlertLevel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục mức cảnh báo — {@code /api/v1/hyd/alert-levels/**} (T33.1).
 *
 * <p>Dùng lại {@code hyd:threshold:view} / {@code hyd:threshold:manage} — hai quyền đã có trong ma
 * trận từ {@code V202608131007} và đã được gán cho vai trò. ⛔ Không thêm quyền mới: một quyền ngoài
 * ma trận là một ô không vai trò nào được gán, tức một chức năng ⛔ không ai dùng được (§9.10.5).
 *
 * <p>⚠ Danh sách trả về <b>rỗng</b> cho tới khi Công ty chốt G9-a. Màn hình phải nói lý do, ⛔ không
 * dựng một lưới dấu gạch và ⛔ không seed vài mức "cho đẹp".
 */
@RestController
@RequestMapping("/api/v1/hyd/alert-levels")
@Tag(name = "03-hyd · Mức cảnh báo", description = "Danh mục mức cảnh báo ngưỡng (G9-a)")
public class AlertLevelController {

    private final AlertLevelService levels;

    public AlertLevelController(AlertLevelService levels) {
        this.levels = levels;
    }

    @GetMapping
    @Operation(summary = "Danh sách mức cảnh báo — rỗng khi Công ty chưa chốt G9-a")
    @RequirePermission("hyd:threshold:view")
    public List<HydroAlertDtos.AlertLevelView> list() {
        return levels.list().stream().map(AlertLevelController::toView).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm mức cảnh báo")
    @RequirePermission("hyd:threshold:manage")
    public HydroAlertDtos.AlertLevelView create(@Valid @RequestBody HydroAlertDtos.AlertLevelRequest request) {
        return toView(levels.create(
                request.code(),
                request.name(),
                request.colorToken(),
                request.severityRank(),
                request.active(),
                request.description()));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa mức cảnh báo")
    @RequirePermission("hyd:threshold:manage")
    public HydroAlertDtos.AlertLevelView update(
            @PathVariable UUID publicId, @Valid @RequestBody HydroAlertDtos.AlertLevelRequest request) {
        return toView(levels.update(
                publicId,
                request.code(),
                request.name(),
                request.colorToken(),
                request.severityRank(),
                request.active(),
                request.description()));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mức cảnh báo — chặn khi còn ngưỡng trỏ vào (HYD-2010)")
    @RequirePermission("hyd:threshold:manage")
    public void delete(@PathVariable UUID publicId) {
        levels.delete(publicId);
    }

    static HydroAlertDtos.AlertLevelView toView(AlertLevel muc) {
        return new HydroAlertDtos.AlertLevelView(
                muc.getPublicId(),
                muc.getCode(),
                muc.getName(),
                muc.getColorToken(),
                muc.getSeverityRank(),
                muc.isActive(),
                muc.getDescription());
    }
}
