package com.songnhue.operations.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.core.spi.AuditEntryView;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.operations.application.ConstructionChangeLogService;
import com.songnhue.operations.application.ConstructionClusterService;
import com.songnhue.operations.application.ConstructionFilter;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.application.ConstructionStatisticsService;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionCluster;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục công trình — {@code /api/v1/ops/constructions/**} (CN-02.1, CN-02.6, CN-02.7).
 *
 * <p>⚠ Không endpoint nào ở đây nhận tham số "đơn vị của tôi". Phạm vi dữ liệu do tầng 3 áp tự động
 * cho mọi truy vấn; một tham số như vậy chỉ tạo ảo giác rằng người gọi điều khiển được phạm vi.
 */
@RestController
@RequestMapping("/api/v1/ops/constructions")
@Tag(name = "02-ops · Danh mục công trình", description = "Hồ sơ công trình thuỷ lợi, toạ độ GIS, tài liệu")
public class ConstructionController {

    private final ConstructionService constructions;
    private final ConstructionClusterService clusters;
    private final ConstructionStatisticsService statistics;
    private final ConstructionChangeLogService changeLog;
    private final OrgUnitPort orgUnits;

    public ConstructionController(
            ConstructionService constructions,
            ConstructionClusterService clusters,
            ConstructionStatisticsService statistics,
            ConstructionChangeLogService changeLog,
            OrgUnitPort orgUnits) {
        this.constructions = constructions;
        this.clusters = clusters;
        this.statistics = statistics;
        this.changeLog = changeLog;
        this.orgUnits = orgUnits;
    }

    // CHECKSTYLE.OFF: ParameterNumber - mỗi tham số là một ô lọc trên màn hình danh sách; gói lại
    // thành object binding thì Swagger mất mô tả từng ô và FE mất gợi ý kiểu.
    @GetMapping
    @Operation(summary = "Danh sách công trình — lọc, tìm kiếm bỏ dấu, phân trang")
    @RequirePermission("ops:construction:view")
    public Page<ConstructionDtos.ConstructionRow> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ConstructionType type,
            @RequestParam(required = false) OperationalStatus status,
            @RequestParam(required = false) LifecycleState lifecycle,
            @RequestParam(required = false) ManagementLevel level,
            @RequestParam(required = false) UUID clusterId,
            @RequestParam(required = false) String river,
            @RequestParam(required = false, defaultValue = "false") boolean withoutLocation,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {

        Pageable pageable = PageUtils.toPageable(page, size, sort, ConstructionService.allowedSortFields());
        ConstructionFilter filter =
                new ConstructionFilter(q, type, status, lifecycle, level, null, clusterId, river, withoutLocation);
        // Nạp danh mục cụm MỘT LẦN cho cả trang. Tra từng dòng thì một trang 100 công trình thành
        // 100 lượt truy vấn — kiểu N+1 không gây lỗi nào, chỉ làm màn hình chậm dần theo dữ liệu.
        Map<Long, ConstructionCluster> danhMucCum = cumTheoId();
        Page<Construction> pageResult = constructions.search(filter, pageable);

        List<Long> orgUnitIds = pageResult.stream()
                .map(Construction::getOrgUnitId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, com.songnhue.core.spi.OrgUnitRef> orgUnitRefs = orgUnits.findRefsByIds(orgUnitIds);

        return pageResult.map(c -> {
            ConstructionCluster cum = c.getClusterId() == null ? null : danhMucCum.get(c.getClusterId());
            String orgUnitName = c.getOrgUnitId() == null
                    ? null
                    : orgUnitRefs.getOrDefault(c.getOrgUnitId(), null) == null
                            ? null
                            : orgUnitRefs.get(c.getOrgUnitId()).name();
            return ConstructionDtos.ConstructionRow.of(c, orgUnitName, cum == null ? null : cum.getName());
        });
    }
    // CHECKSTYLE.ON: ParameterNumber

    @GetMapping("/{publicId}")
    @Operation(summary = "Hồ sơ đầy đủ của một công trình")
    @RequirePermission("ops:construction:view")
    public ConstructionDtos.ConstructionDetail detail(@PathVariable UUID publicId) {
        return toDetail(constructions.get(publicId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm hồ sơ công trình")
    @RequirePermission("ops:construction:create")
    public ConstructionDtos.ConstructionDetail create(@Valid @RequestBody ConstructionDtos.SaveRequest request) {
        chanTrangThaiTuClient(request);
        return toDetail(constructions.create(request.toForm()));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa hồ sơ công trình")
    @RequirePermission("ops:construction:update")
    public ConstructionDtos.ConstructionDetail update(
            @PathVariable UUID publicId, @Valid @RequestBody ConstructionDtos.SaveRequest request) {
        chanTrangThaiTuClient(request);
        return toDetail(constructions.update(publicId, request.toForm()));
    }

    @PutMapping("/{publicId}/lifecycle")
    @Operation(summary = "Đổi vòng đời hồ sơ — đang hoạt động / ngừng mùa vụ / đã thanh lý")
    @RequirePermission("ops:construction:update")
    public ConstructionDtos.ConstructionDetail changeLifecycle(
            @PathVariable UUID publicId, @Valid @RequestBody ConstructionDtos.LifecycleRequest request) {
        return toDetail(constructions.changeLifecycle(publicId, request.state(), request.reason()));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm hồ sơ công trình")
    @RequirePermission("ops:construction:delete")
    public void delete(@PathVariable UUID publicId) {
        constructions.delete(publicId);
    }

    @GetMapping("/code-suggestion")
    @Operation(summary = "Mã gợi ý cho hồ sơ mới — gợi ý, người dùng sửa được")
    @RequirePermission("ops:construction:create")
    public CodeSuggestion suggestCode(@RequestParam ConstructionType type, @RequestParam UUID orgUnitId) {
        return new CodeSuggestion(constructions.suggestCode(type, orgUnitId));
    }

    public record CodeSuggestion(String code) {}

    @GetMapping("/map-points")
    @Operation(summary = "Điểm vẽ lên bản đồ GIS — chỉ công trình đã số hoá toạ độ")
    @RequirePermission("ops:construction:view")
    public List<ConstructionDtos.MapPoint> mapPoints(@RequestParam(required = false) ConstructionType type) {
        return constructions.mapPoints(type).stream()
                .map(c -> ConstructionDtos.MapPoint.of(c, tenDonVi(c.getOrgUnitId())))
                .toList();
    }

    @GetMapping("/rivers")
    @Operation(summary = "Tuyến sông đang có công trình — nguồn cho ô lọc")
    @RequirePermission("ops:construction:view")
    public List<String> rivers() {
        return constructions.rivers();
    }

    @GetMapping("/statistics")
    @Operation(summary = "Thống kê theo loại / trạng thái / đơn vị / cấp quản lý (CN-02.6)")
    @RequirePermission("ops:construction:view")
    public ConstructionStatisticsService.Statistics statistics() {
        return statistics.summary();
    }

    @GetMapping("/{publicId}/change-log")
    @Operation(summary = "Nhật ký thay đổi hồ sơ (CN-02.7) — đọc từ audit_logs, không có bảng riêng")
    @RequirePermission("ops:change-log:view")
    public List<AuditEntryView> changeLog(@PathVariable UUID publicId, @RequestParam(required = false) Integer limit) {
        return changeLog.historyOf(publicId, limit);
    }

    /**
     * Chặn lượt ghi mang theo trạng thái vận hành — {@code OPS-3001}, quy tắc 4.
     *
     * <p>Trả lỗi thay vì bỏ qua lặng lẽ: người tích hợp gửi {@code operationalStatus} là đang tin
     * rằng mình đặt được nó, và sự im lặng sẽ được hiểu là "đã đặt xong". Một mã lỗi rõ ràng ở đây
     * rẻ hơn nhiều so với việc đi tìm xem vì sao trạng thái không đổi.
     */
    private static void chanTrangThaiTuClient(ConstructionDtos.SaveRequest request) {
        if (request.operationalStatus() != null) {
            throw new PermissionDeniedException(ErrorCode.OPS_3001);
        }
    }

    private Map<Long, ConstructionCluster> cumTheoId() {
        return clusters.list().stream().collect(Collectors.toMap(ConstructionCluster::getId, cum -> cum));
    }

    private ConstructionDtos.ConstructionRow toRow(Construction c, Map<Long, ConstructionCluster> danhMucCum) {
        ConstructionCluster cum = c.getClusterId() == null ? null : danhMucCum.get(c.getClusterId());
        return ConstructionDtos.ConstructionRow.of(c, tenDonVi(c.getOrgUnitId()), cum == null ? null : cum.getName());
    }

    private ConstructionDtos.ConstructionDetail toDetail(Construction c) {
        Map<Long, ConstructionCluster> danhMucCum = cumTheoId();
        ConstructionCluster cum = c.getClusterId() == null ? null : danhMucCum.get(c.getClusterId());
        return new ConstructionDtos.ConstructionDetail(
                toRow(c, danhMucCum),
                orgUnits.findRefById(c.getOrgUnitId())
                        .map(ref -> ref.publicId())
                        .orElse(null),
                cum == null ? null : cum.getPublicId(),
                c.getPurpose(),
                c.getAddress(),
                c.getChainageM(),
                c.getBasinNote(),
                c.getBuiltYear(),
                c.getCommissionedYear(),
                c.getDesigner(),
                c.getContractor(),
                c.getTotalInvestment(),
                c.getOperatingProcedureAttachmentPublicId(),
                c.getProtectionPlanAttachmentPublicId(),
                c.getDescription(),
                c.getConstructionType() == ConstructionType.TRAM_BOM ? pump(c) : null,
                c.getConstructionType() == ConstructionType.CONG ? sluice(c) : null,
                c.getConstructionType().laCongTrinhTuyen() ? linear(c) : null);
    }

    private static ConstructionDtos.PumpSpecView pump(Construction c) {
        return new ConstructionDtos.PumpSpecView(
                c.getTotalPowerKw(),
                c.getPumpCount(),
                c.getStandbyPumpCount(),
                c.getFlowPerPumpM3s(),
                c.getTotalFlowM3s(),
                c.getHeadM(),
                c.getPowerSource(),
                c.getVoltageKv(),
                c.getOperatingLevelMinM(),
                c.getOperatingLevelMaxM());
    }

    private static ConstructionDtos.SluiceSpecView sluice(Construction c) {
        return new ConstructionDtos.SluiceSpecView(
                c.getSluiceType(),
                c.getBayCount(),
                c.getBayWidthM(),
                c.getSillElevationM(),
                c.getSluiceCrestElevationM(),
                c.getSluiceDesignFlowM3s(),
                c.getGateOperation(),
                c.getUpstreamWarningLevelM(),
                c.getUpstreamDangerLevelM());
    }

    private static ConstructionDtos.LinearSpecView linear(Construction c) {
        return new ConstructionDtos.LinearSpecView(
                c.getLengthKm(),
                c.getStartChainage(),
                c.getEndChainage(),
                c.getLinearDesignFlowM3s(),
                c.getLinearCrestElevationM(),
                c.getTechnicalGrade(),
                c.getCrossSection(),
                c.getSpecNote());
    }

    private String tenDonVi(Long id) {
        return id == null
                ? null
                : orgUnits.findRefById(id).map(ref -> ref.name()).orElse(null);
    }
}
