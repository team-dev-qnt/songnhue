package com.songnhue.hydro.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.PublicEndpoint;
import com.songnhue.hydro.application.HydroGridService;
import com.songnhue.hydro.domain.CheDoXemLuoi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Bảng lưới mực nước cho cổng công khai — WS-43, spec-description.md §5.2 và §6.1.2.
 *
 * <h2>⛔ Vì sao công khai — và vì sao ⛔ KHÔNG có nhánh "sau đăng nhập"</h2>
 *
 * <p>Spec §9 dựng một bảng phân quyền 8 hàng, trong đó bảng theo tuần/tháng, biểu đồ và chức năng
 * xuất tệp đều <i>"yêu cầu đăng nhập"</i>. <b>Yêu cầu ấy đã bị huỷ</b> — quyết định Q4 ngày
 * 09/09/2026 ({@code phase3-plan.md} §1): dữ liệu thuỷ văn công khai toàn bộ, và việc ẩn/hiện đi
 * qua công tắc quản trị đã có ({@code khoiVanHanh} + {@code hydro.portal.station-codes} +
 * {@code stations.is_main_axis}), ⛔ không qua một tầng xác thực trên cổng.
 *
 * <p>⇒ ⛔ Đừng thêm {@code @RequirePermission} vào đây "cho chắc", và ⛔ đừng dựng lại
 * {@code KhoaDangNhap}. Nếu quyết định ấy đảo lại thì nó đảo ở <b>một chỗ</b> — chú thích
 * {@link PublicEndpoint} dưới đây — chứ ⛔ không rải ra thành ẩn/hiện ở giao diện.
 *
 * <h2>⚠ Hệ quả bắt buộc của việc công khai: trần gọi</h2>
 *
 * <p>Một bề mặt công khai ⛔ không giới hạn là một cửa quét dữ liệu. Trần nằm ở
 * {@code RateLimitFilter} (chuỗi filter của {@code core}), và chi phí mỗi lượt gọi bị chặn thêm
 * một lần nữa ở SQL bằng {@code HydroGridRepository.TRAN_HANG}. Hai lớp ấy độc lập với nhau, cố ý.
 */
@RestController
@RequestMapping("/api/v1/public/hydro")
@Tag(name = "06-public · Thuỷ văn", description = "Số liệu mực nước công bố trên cổng")
public class HydroGridController {

    private final HydroGridService service;

    public HydroGridController(HydroGridService service) {
        this.service = service;
    }

    /**
     * Bảng lưới theo tuyến sông → công trình → chỉ tiêu.
     *
     * <p>⚠ Trả về <b>lưới rỗng kèm lý do</b> khi chưa có điểm đo nào — ⛔ không phải 404, và ⛔ không
     * phải một bộ dữ liệu mẫu. Khối {@code meta} vẫn ra dây đầy đủ trong trường hợp ấy, vì §8.1 đòi
     * dòng "Thời điểm cập nhật gần nhất" hiện <b>kể cả khi chưa có dữ liệu nào</b>.
     *
     * @param cheDo {@code PHUT} (mặc định, mốc 10 phút) hoặc {@code GIO}
     * @param soCot số cột thời gian; mặc định 12 theo §6.1.1
     * @param trucChinh {@code true} cho khối trang chủ §5.2; mặc định {@code false} (trang chi tiết)
     */
    /**
     * Dữ liệu biểu đồ diễn biến một công trình — spec §7.1, <b>WS-45</b>.
     *
     * <p>⚠ <b>Công khai</b>, cùng lý do với bảng lưới: spec §9 xếp biểu đồ vào nhóm "yêu cầu đăng
     * nhập", nhưng yêu cầu ấy đã bị huỷ (quyết định Q4, 09/09/2026).
     *
     * <p>⚠ Trả biểu đồ <b>rỗng kèm lý do</b> khi mã công trình ⛔ không khớp — ⛔ không phải 404.
     * Một mã gõ sai trên thanh địa chỉ ⛔ không phải một sự cố hệ thống, và §7.3 đòi hiện câu chữ
     * chứ ⛔ không vẽ một khung trục rỗng.
     *
     * @param soCot số mốc; mặc định 144 = trọn một ngày ở nhịp 10 phút
     */
    @GetMapping("/bieu-do/{maCongTrinh}")
    @Operation(summary = "Biểu đồ diễn biến mực nước một công trình — 2 đường thượng/hạ lưu + chênh lệch")
    @PublicEndpoint(
            reason = "Biểu đồ diễn biến §7.1 — dữ liệu thuỷ văn công khai toàn bộ theo quyết định "
                    + "Q4 ngày 09/09/2026 (huỷ CR-08)")
    public HydroGridService.BieuDoCongTrinh bieuDo(
            @PathVariable String maCongTrinh,
            @RequestParam(defaultValue = "PHUT") CheDoXemLuoi cheDo,
            @RequestParam(defaultValue = "0") int soCot) {
        return service.bieuDo(maCongTrinh, cheDo, soCot);
    }

    @GetMapping("/luoi-muc-nuoc")
    @Operation(summary = "Bảng lưới mực nước — nhóm theo tuyến sông, mỗi công trình một cặp thượng/hạ lưu")
    @PublicEndpoint(
            reason = "Bảng 'Mực nước, lượng mưa' §5.2 và trang chi tiết §6.1.2 — dữ liệu thuỷ văn "
                    + "công khai toàn bộ theo quyết định Q4 ngày 09/09/2026 (huỷ CR-08)")
    public HydroGridService.LuoiMucNuoc luoiMucNuoc(
            @RequestParam(defaultValue = "PHUT") CheDoXemLuoi cheDo,
            @RequestParam(defaultValue = "0") int soCot,
            @RequestParam(defaultValue = "false") boolean trucChinh) {
        return service.luoi(cheDo, soCot, trucChinh);
    }
}
