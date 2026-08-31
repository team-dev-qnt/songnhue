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
import com.songnhue.hydro.application.MeasurementTypeService;
import com.songnhue.hydro.domain.MeasurementType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục loại chỉ số quan trắc — {@code /api/v1/hyd/measurement-types/**} (T28.1).
 *
 * <p>Dùng lại quyền {@code hyd:station:view} / {@code hyd:station:manage} theo đúng câu chữ đã duyệt
 * của ma trận: <i>"Quản lý điểm đo <b>và loại chỉ số</b>"</i> ({@code V202608131007}). Thêm quyền
 * mới ngoài ma trận sẽ tạo một ô không vai trò nào được gán — tức một chức năng không ai dùng được.
 */
@RestController
@RequestMapping("/api/v1/hyd/measurement-types")
@Tag(name = "03-hyd · Loại chỉ số", description = "Danh mục loại chỉ số quan trắc và đơn vị chuẩn hoá")
public class MeasurementTypeController {

    private final MeasurementTypeService types;

    public MeasurementTypeController(MeasurementTypeService types) {
        this.types = types;
    }

    @GetMapping
    @Operation(summary = "Danh sách loại chỉ số")
    @RequirePermission("hyd:station:view")
    public List<HydroCatalogDtos.MeasurementTypeView> list() {
        return types.list().stream().map(MeasurementTypeController::toView).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm loại chỉ số")
    @RequirePermission("hyd:station:manage")
    public HydroCatalogDtos.MeasurementTypeView create(
            @Valid @RequestBody HydroCatalogDtos.MeasurementTypeRequest request) {
        return toView(types.create(
                request.code(),
                request.name(),
                request.unit(),
                request.valueScale(),
                request.sortOrder(),
                request.active(),
                request.description()));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa loại chỉ số")
    @RequirePermission("hyd:station:manage")
    public HydroCatalogDtos.MeasurementTypeView update(
            @PathVariable UUID publicId, @Valid @RequestBody HydroCatalogDtos.MeasurementTypeRequest request) {
        return toView(types.update(
                publicId,
                request.code(),
                request.name(),
                request.unit(),
                request.valueScale(),
                request.sortOrder(),
                request.active() == null || request.active(),
                request.description()));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá loại chỉ số — chặn khi còn điểm đo đang gắn (HYD-1002)")
    @RequirePermission("hyd:station:manage")
    public void delete(@PathVariable UUID publicId) {
        types.delete(publicId);
    }

    static HydroCatalogDtos.MeasurementTypeView toView(MeasurementType loai) {
        return new HydroCatalogDtos.MeasurementTypeView(
                loai.getPublicId(),
                loai.getCode(),
                loai.getName(),
                loai.getUnit(),
                loai.getValueScale(),
                loai.getSortOrder(),
                loai.isActive(),
                loai.getDescription());
    }
}
