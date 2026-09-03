package com.songnhue.hydro.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoDongBoView;
import com.songnhue.hydro.application.HydroReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Báo cáo thuỷ văn — {@code /api/v1/hyd/bao-cao/**} (WS-34).
 *
 * <h2>⭐ Quyền {@code hyd:report:view} có người đọc ĐẦU TIÊN từ đây</h2>
 *
 * <p>Nó được seed từ 13/8 và nằm trong {@code RbacMatrixTest.QUYEN_PHASE_SAU} suốt từ đó — tức một
 * quyền <b>chưa cổng nào dùng</b>. Đợt này nối vế đọc, nên nó phải rời khỏi danh sách ấy; bài kiểm
 * canh đúng chiều đó, và để nguyên là giữ một mã quyền không điều khiển gì (luật 15).
 *
 * <p>⚠ Gác bằng {@code hyd:report:view}, ⛔ <b>không</b> bằng {@code hyd:report:export}: xem báo cáo
 * và xuất báo cáo là hai việc, và đo trên ma trận seed thì {@code XN_OPERATOR} · {@code DUTY_OFFICER}
 * chỉ có vế đầu. Gác cả trang bằng quyền hẹp hơn là chôn trang sau nút của nó — hình dạng T27.20 đã
 * tái phát ba lần trong hai tuần (§10.70).
 *
 * <h2>⛔ Ngày ở đây là NGÀY GIỜ VIỆT NAM</h2>
 *
 * <p>Tham số {@code tuNgay}/{@code denNgay} nhận {@code yyyy-MM-dd} và được hiểu theo lịch làm việc
 * của Công ty, ⛔ không phải theo UTC. Phép đổi sống ở cặp hàm CSDL {@code hyd_ngay_vn} /
 * {@code hyd_dau_ngay_vn}, có khối tự kiểm chứng chạy ngay lúc migrate.
 *
 * <p>⚠ Bộ lọc điểm đo nhận {@code publicId}, ⛔ không nhận khoá nội bộ — cùng luật với mọi endpoint
 * khác của hệ ({@code dtoKhongLoKhoaNoiBo}). Khoá bigint rò ra dây là mở đường cho một client đoán
 * số, và số ấy thì đoán được.
 */
@RestController
@RequestMapping("/api/v1/hyd/bao-cao")
@Tag(name = "03-hyd · Báo cáo thuỷ văn", description = "BC-13 nhật ký đồng bộ & chất lượng dữ liệu")
public class HydroReportController {

    private final HydroReportService baoCao;

    public HydroReportController(HydroReportService baoCao) {
        this.baoCao = baoCao;
    }

    /**
     * BC-13 — T34.3.
     *
     * <p>⛔ Cố ý <b>không phân trang</b>: báo cáo là một khối để đọc và để xuất, ⛔ không phải một
     * danh sách để lướt, và một bảng phân trang thì cột tổng ở chân trang nói dối. Trần khoảng ngày
     * ({@code HYD-2012}) là thứ giữ cho khối ấy có kích thước đọc được.
     */
    @GetMapping("/dong-bo")
    @RequirePermission("hyd:report:view")
    @Operation(
            summary = "BC-13 — nhật ký đồng bộ & chất lượng dữ liệu",
            description = "Số bản ghi theo mức chất lượng và SỐ KHUNG 10' BỊ BỎ SÓT theo từng ngày "
                    + "(phép đo của NFR-03), kèm tổng hợp lượt polling theo nguồn")
    public BaoCaoDongBoView dongBo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tuNgay,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate denNgay,
            @RequestParam(required = false) UUID stationPublicId) {
        return baoCao.baoCaoDongBo(tuNgay, denNgay, stationPublicId);
    }
}
