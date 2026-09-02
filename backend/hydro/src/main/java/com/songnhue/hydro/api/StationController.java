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
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.hydro.application.StationForm;
import com.songnhue.hydro.application.StationService;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.domain.StationConstruction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục điểm đo — {@code /api/v1/hyd/stations/**} (T28.3, T28.8, T28.9).
 *
 * <h2>⚠ Hai màn hình, không phải một</h2>
 *
 * <p>{@code GET /} là danh mục. {@code GET /chua-gan-don-vi} là <b>danh sách việc còn thiếu</b>, hệ
 * quả trực tiếp của OI-05: cho tới khi nó rỗng, resolver người nhận cảnh báo (G11 tập 2) không tìm
 * được ai để gửi. Một cảnh báo không có người nhận là một cảnh báo không tồn tại, và nó không báo
 * lỗi ở đâu cả — nên phần còn thiếu phải hiện thành một con số trên màn hình, không phải một dòng
 * trong tài liệu.
 */
@RestController
@RequestMapping("/api/v1/hyd/stations")
@Tag(name = "03-hyd · Điểm đo", description = "Danh mục điểm đo và ánh xạ mã API bên thứ 3")
public class StationController {

    private final StationService stations;
    private final OrgUnitPort orgUnits;

    public StationController(StationService stations, OrgUnitPort orgUnits) {
        this.stations = stations;
        this.orgUnits = orgUnits;
    }

    @GetMapping
    @Operation(summary = "Danh sách điểm đo")
    @RequirePermission("hyd:station:view")
    public List<HydroCatalogDtos.StationView> list() {
        return stations.list().stream().map(this::toView).toList();
    }

    @GetMapping("/chua-gan-don-vi")
    @Operation(summary = "Điểm đo chưa gán đơn vị phụ trách — chặn resolver người nhận cảnh báo")
    @RequirePermission("hyd:station:view")
    public List<HydroCatalogDtos.StationView> chuaGanDonVi() {
        return stations.chuaGanDonVi().stream().map(this::toView).toList();
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Chi tiết điểm đo")
    @RequirePermission("hyd:station:view")
    public HydroCatalogDtos.StationView get(@PathVariable UUID publicId) {
        return toView(stations.get(publicId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm điểm đo")
    @RequirePermission("hyd:station:manage")
    public HydroCatalogDtos.StationView create(@Valid @RequestBody HydroCatalogDtos.StationRequest request) {
        return toView(stations.create(hoSo(request)));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa điểm đo — ⛔ đổi apiCode bị từ chối (HYD-2006)")
    @RequirePermission("hyd:station:manage")
    public HydroCatalogDtos.StationView update(
            @PathVariable UUID publicId, @Valid @RequestBody HydroCatalogDtos.StationRequest request) {
        return toView(stations.update(publicId, hoSo(request)));
    }

    /**
     * ⭐ Một hàm đổi DTO → form, dùng cho <b>cả hai</b> đường ghi — T28.33.
     *
     * <p>Trước 02/09 chỉ {@code update} dựng {@link StationForm}, còn {@code create} tự chuyển tay
     * <b>7 trong 14</b> trường. Hai cách viết cạnh nhau cho cùng một DTO là chỗ chênh lệch sinh ra
     * và không ai thấy: {@code @Valid} chạy trên đủ 14 trường ở cả hai đường, nên lượt {@code POST}
     * kèm toạ độ vẫn <b>201 Created</b> trong khi toạ độ không tới được tầng dưới.
     *
     * <p>⚠ Hai phép giải mặc định nằm ở đây chứ không ở tầng application, có chủ ý: {@code Boolean}
     * của DTO mang <b>ba</b> trạng thái (true / false / không gửi) còn nghiệp vụ chỉ có hai. Giải
     * ngay tại biên là chỗ duy nhất còn phân biệt được "không gửi" — sâu hơn thì thông tin ấy mất.
     */
    private static StationForm hoSo(HydroCatalogDtos.StationRequest request) {
        return new StationForm(
                request.code(),
                request.name(),
                request.apiCode(),
                request.apiSourceId(),
                request.positionRole(),
                request.orgUnitId(),
                request.riverName(),
                request.chainage(),
                request.latitude(),
                request.longitude(),
                request.interpolated() != null && request.interpolated(),
                request.active() == null || request.active(),
                request.description(),
                request.measurementTypeIds());
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm điểm đo")
    @RequirePermission("hyd:station:manage")
    public void delete(@PathVariable UUID publicId) {
        stations.delete(publicId);
    }

    private HydroCatalogDtos.StationView toView(Station diemDo) {
        List<StationConstruction> lienKet = stations.lienKetCua(diemDo);
        OrgUnitRef donVi = diemDo.getOrgUnitId() == null
                ? null
                : orgUnits.findRefById(diemDo.getOrgUnitId()).orElse(null);
        ApiSource nguon = stations.nguonCua(diemDo);
        return new HydroCatalogDtos.StationView(
                diemDo.getPublicId(),
                diemDo.getCode(),
                diemDo.getName(),
                diemDo.getApiCode(),
                nguon == null ? null : nguon.getPublicId(),
                nguon == null ? null : nguon.getCode(),
                diemDo.getPositionRole(),
                donVi == null ? null : donVi.publicId(),
                donVi == null ? null : donVi.name(),
                diemDo.getRiverName(),
                diemDo.getChainage(),
                diemDo.getChainageM(),
                diemDo.getLatitude(),
                diemDo.getLongitude(),
                diemDo.isInterpolated(),
                diemDo.isActive(),
                diemDo.getDescription(),
                diemDo.getMeasurementTypes().stream()
                        .map(MeasurementTypeController::toView)
                        .toList(),
                lienKet.stream()
                        .map(l -> new HydroCatalogDtos.StationConstructionView(
                                l.getPublicId(), l.getConstructionPublicId(), l.getRole(), l.isPrimary()))
                        .toList(),
                lienKet.isEmpty() && !diemDo.duocPhepKhongGanCongTrinh(),
                diemDo.chuaGanDonVi());
    }
}
