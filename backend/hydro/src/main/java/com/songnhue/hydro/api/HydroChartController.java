package com.songnhue.hydro.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hydro.application.HydroChartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Biểu đồ thuỷ văn — <b>T35.4</b>.
 *
 * <h2>⚠ Gác bằng {@code hyd:report:view}, ⛔ không phải {@code hyd:station:view}</h2>
 *
 * <p>Hai quyền ấy trả lời hai câu khác nhau: {@code station:view} là <i>"được xem HỒ SƠ điểm đo"</i>
 * (mã, tên, toạ độ, nguồn), còn {@code report:view} là <i>"được xem SỐ ĐO"</i>. Một đường cong 24
 * giờ là số đo. Chọn nhầm quyền ở đây mở số liệu vận hành cho mọi vai trò được phép mở danh mục —
 * và nó ⛔ không có triệu chứng, vì màn hình vẫn hiện đúng.
 *
 * <p>⚠ Đo trên ma trận seed 04/09: {@code DUTY_OFFICER} — người trực ban, đúng người ngồi nhìn
 * đường cong này — <b>có</b> {@code hyd:report:view}. Đó là điều kiện để màn hình mới ⛔ không chết
 * ngay ở vai trò dùng nó nhiều nhất (§10.61: 906 bài kiểm hai phía đều xanh mà trang vẫn hỏng ở
 * lượt tải đầu bằng một vai trò thật).
 *
 * <h2>⛔ Controller riêng, ⛔ không nhét vào {@code HydroReportController}</h2>
 *
 * <p>Kho truy vấn khác ({@link com.songnhue.hydro.infra.HydroChartRepository}), và kho ấy mang một
 * <b>ngoại lệ có tên</b> của quy tắc 8. Trộn chung là làm ngoại lệ ấy trôi vào một tệp mà bộ canh
 * đang soi với một luật khác.
 */
@RestController
@RequestMapping("/api/v1/hyd/bieu-do")
@Tag(name = "03-hyd · Biểu đồ thuỷ văn", description = "Chuỗi thời gian 24 giờ cho màn hình vận hành")
public class HydroChartController {

    private final HydroChartService bieuDo;

    public HydroChartController(HydroChartService bieuDo) {
        this.bieuDo = bieuDo;
    }

    /**
     * Đường cong 24 giờ của một điểm đo.
     *
     * <p>⚠ ⛔ Không nhận khoảng ngày — cửa sổ chốt 24 giờ. Xem javadoc {@link HydroChartService}: đó
     * là thứ giữ cho ngoại lệ quy tắc 8 còn nhỏ, ⛔ không phải một thiếu sót.
     */
    @GetMapping("/muc-nuoc-24h")
    @RequirePermission("hyd:report:view")
    @Operation(
            summary = "T35.4 — chuỗi mực nước 24 giờ của một điểm đo",
            description = "CHỈ số đo HỢP LỆ (quy tắc 14). Khoảng trống dữ liệu được GIỮ NGUYÊN — biểu đồ "
                    + "đặt connectNulls=false để chỗ mất tín hiệu nhìn thấy được, ⛔ không nội suy. "
                    + "⛔ Không có điểm nào thì trả danh sách rỗng KÈM LÝ DO, ⛔ không phải 404 và ⛔ không phải 0")
    public HydroChartService.BieuDoMucNuoc mucNuoc24h(
            @RequestParam UUID stationPublicId, @RequestParam(defaultValue = "MUC_NUOC") String maLoaiChiSo) {
        return bieuDo.mucNuoc24h(stationPublicId, maLoaiChiSo);
    }
}
