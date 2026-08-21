package com.songnhue.operations.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.application.DashboardService;
import com.songnhue.operations.domain.ConstructionType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Dashboard điều hành — {@code /api/v1/ops/dashboard} (CN-02.5, CN-02.6).
 *
 * <h2>Hai endpoint, không phải một — và cũng không phải bảy</h2>
 *
 * Toàn bộ KPI, biểu đồ thống kê, chu kỳ làm mới và cấu hình bản đồ đi trong <b>một</b> lượt gọi:
 * mỗi con số phải do BE tính (quy tắc 3), và để giao diện gọi từng mảnh rồi tự cộng là chuyển phép
 * tính sang chỗ không kiểm chứng được.
 *
 * <p>Điểm bản đồ tách riêng vì <b>nhịp sống khác hẳn</b>: KPI làm mới mỗi vài phút (M2.15), còn toạ
 * độ công trình chỉ đổi khi có người sửa hồ sơ. Gộp vào thì mỗi lượt làm mới kéo lại nguyên danh
 * sách marker mà không có gì thay đổi; ở chế độ màn hình lớn chạy suốt ngày, đó là lưu lượng thừa
 * nhân với số lần làm mới trong tám tiếng.
 */
@RestController
@RequestMapping("/api/v1/ops/dashboard")
@Tag(name = "02-ops · Dashboard điều hành", description = "KPI, thống kê công trình, bản đồ tổng quan")
public class DashboardController {

    private final DashboardService dashboard;
    private final ConstructionService constructions;
    private final OrgUnitPort orgUnits;

    public DashboardController(DashboardService dashboard, ConstructionService constructions, OrgUnitPort orgUnits) {
        this.dashboard = dashboard;
        this.constructions = constructions;
        this.orgUnits = orgUnits;
    }

    @GetMapping
    @Operation(summary = "Số liệu tổng hợp cho dashboard — KPI, thống kê, chu kỳ làm mới, cấu hình bản đồ")
    @RequirePermission("ops:dashboard:view")
    public DashboardService.Dashboard summary() {
        return dashboard.summary();
    }

    /**
     * Marker cho bản đồ tổng quan.
     *
     * <p>Dùng lại đúng nguồn của bản đồ ở màn hình danh mục công trình — hai bản đồ vẽ từ hai truy
     * vấn khác nhau là hai cơ hội để chúng hiện hai tập công trình khác nhau.
     *
     * <p>⚠ Có endpoint riêng thay vì để giao diện gọi {@code /ops/constructions/map-points} là vì
     * <b>quyền</b>: hôm nay mọi vai trò có {@code ops:dashboard:view} đều có kèm
     * {@code ops:construction:view}, nhưng đó là sự trùng hợp của ma trận §6 hiện tại, không phải
     * một bảo đảm. Một vai trò "chỉ xem dashboard" cấp sau này sẽ thấy bản đồ trống không lời giải
     * thích, và người đi tìm nguyên nhân sẽ soi bản đồ chứ không soi bảng phân quyền.
     */
    @GetMapping("/map-points")
    @Operation(summary = "Điểm công trình trên bản đồ tổng quan — chỉ hồ sơ đã số hoá toạ độ")
    @RequirePermission("ops:dashboard:view")
    public List<ConstructionDtos.MapPoint> mapPoints(@RequestParam(required = false) ConstructionType type) {
        return constructions.mapPoints(type).stream()
                .map(c -> ConstructionDtos.MapPoint.of(
                        c,
                        orgUnits.findRefById(c.getOrgUnitId())
                                .map(ref -> ref.name())
                                .orElse(null)))
                .toList();
    }
}
