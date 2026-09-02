package com.songnhue.hydro.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.hydro.application.HydroReviewService;
import com.songnhue.hydro.application.SoDoNhapTayService;
import com.songnhue.hydro.domain.ReadingQuality;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Dữ liệu nghi ngờ + nhập tay — {@code /api/v1/hyd/so-do/**} (CN-03.2, WS-32).
 *
 * <h2>⭐⭐ Ba mức quyền khác nhau trên cùng một tuyến, và cả ba đều đo được</h2>
 *
 * <table border="1">
 *   <caption>Ai làm được gì</caption>
 *   <tr><th>Việc</th><th>Quyền</th><th>Vai trò có (ngoài SA/ADMIN)</th></tr>
 *   <tr><td>Xem hàng chờ</td><td>{@code hyd:measurement:view}</td>
 *       <td>TECHNICIAN · XN_MANAGER · XN_OPERATOR · DUTY_OFFICER · EXECUTIVE · VIEWER</td></tr>
 *   <tr><td>Duyệt / Loại bỏ</td><td>{@code hyd:measurement:review}</td><td><b>TECHNICIAN</b></td></tr>
 *   <tr><td>Nhập tay</td><td>{@code hyd:measurement:create}</td>
 *       <td>TECHNICIAN · XN_MANAGER · XN_OPERATOR · DUTY_OFFICER</td></tr>
 * </table>
 *
 * <p>⚠ Cột thứ ba là <b>số đo trên ma trận seed</b>, ⛔ không phải mong muốn. Nó là lý do
 * {@code hyd:measurement:create} phải ra đời ở {@code V202609021054}: gác ô nhập tay bằng
 * {@code :review} thì chỉ TECHNICIAN dùng được, trong khi chức năng này tồn tại cho người
 * <b>đang trực</b> lúc API chết. Đó là hình dạng T27.20 lặp lần thứ ba — một biểu mẫu mà vai trò sở
 * hữu công việc ấy không mở được (§10.70).
 *
 * <p>⛔ Xem hàng chờ chỉ cần {@code :view}: người không duyệt được vẫn phải <b>biết</b> số liệu nào
 * đang bị treo, vì chính họ là người đọc biểu đồ có lỗ hổng ấy.
 */
@RestController
@RequestMapping("/api/v1/hyd/so-do")
@Tag(name = "03-hyd · Dữ liệu nghi ngờ", description = "Hàng chờ duyệt chất lượng số đo và đường nhập tay")
public class SuspectReadingController {

    private final HydroReviewService review;
    private final SoDoNhapTayService nhapTay;

    public SuspectReadingController(HydroReviewService review, SoDoNhapTayService nhapTay) {
        this.review = review;
        this.nhapTay = nhapTay;
    }

    /**
     * Hàng chờ duyệt.
     *
     * <p>⛔ <b>Không có tham số {@code sort}</b> — cùng lý do với nhật ký đồng bộ: hàng chờ chỉ có
     * một thứ tự đọc được (mới nhất trước), và mở một tham số sort là dựng lại nguyên hình dạng A1
     * (mặc định giao diện nằm ngoài whitelist ⇒ 422 ngay lượt tải đầu, triệu chứng "bảng rỗng" trùng
     * khít trạng thái đúng nên không ai báo).
     *
     * @param trangThai {@code NGHI_NGO} (mặc định) hoặc {@code XOA} — ⛔ {@code HOP_LE} bị
     *     {@code SuspectReadingRepository} từ chối ở tầng dưới, ⚠ chốt chặn ở đó chứ không ở đây vì
     *     controller ⛔ không phải đường vào duy nhất (luật 12)
     * @param den ⚠ <b>nửa khoảng mở</b> ({@code measured_at < den}) — cùng quy ước với
     *     {@code AuditLogRepository} và nhật ký đồng bộ
     */
    @GetMapping("/nghi-ngo")
    @Operation(summary = "Hàng chờ duyệt — mới nhất trước, ⛔ không đổi được thứ tự")
    @RequirePermission({"hyd:measurement:view", "hyd:measurement:review"})
    public Page<HydroQualityDtos.SuspectRow> hangCho(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "NGHI_NGO") ReadingQuality trangThai,
            @RequestParam(required = false) UUID diemDoId,
            @RequestParam(required = false) Instant tu,
            @RequestParam(required = false) Instant den) {

        return review.hangCho(trangThai, diemDoId, tu, den, PageUtils.toPageable(page, size, null, Set.of()))
                .map(HydroQualityDtos.SuspectRow::cua);
    }

    /**
     * ⚠ Bộ phân loại có đang bật không — ⛔ đọc trước khi tin một bảng rỗng.
     *
     * <p>Quyền giống hàng chờ: ai đọc được bảng thì phải đọc được câu giải thích vì sao nó rỗng. Tách
     * hai quyền ở đây là dựng ra đúng tình huống người dùng thấy bảng trống mà ⛔ không được biết lý
     * do.
     */
    @GetMapping("/nghi-ngo/tinh-trang")
    @Operation(summary = "Bộ phân loại đang bật hay tắt — câu trả lời cho 'bảng rỗng nghĩa là gì'")
    @RequirePermission({"hyd:measurement:view", "hyd:measurement:review"})
    public HydroQualityDtos.QualityRuleStatus tinhTrang() {
        return HydroQualityDtos.QualityRuleStatus.cua(review.tinhTrangQuyTac());
    }

    /**
     * Các nút được phép hiện cho một bản ghi — ⛔ giao diện không tự suy (conventions.md §3).
     *
     * <p>Cờ {@code requiresReason} đi cùng đường này nên hộp thoại nhập lý do ⛔ không thể lệch với
     * chốt chặn của máy chủ. Đã trả giá đúng chỗ đó: kiểu {@code AllowedAction} phía giao diện từng
     * mang thêm ba trường mà không nơi nào điền, nên hộp thoại <b>không bao giờ mở</b>.
     */
    @GetMapping("/thao-tac")
    @Operation(summary = "Nút Duyệt / Loại bỏ — đã lọc theo quyền của người đang đăng nhập")
    @RequirePermission("hyd:measurement:review")
    public List<AllowedAction> thaoTac(
            @RequestParam UUID diemDoId, @RequestParam String maLoaiChiSo, @RequestParam Instant mocDo) {
        return review.nutChoPhep(new HydroReviewService.KhoaBanGhi(diemDoId, maLoaiChiSo, mocDo));
    }

    /**
     * Thực hiện một bước chuyển.
     *
     * <p>⚠ Gác bằng {@code hyd:measurement:review} <b>và</b> engine kiểm lại quyền của chính bước
     * chuyển ấy — hai lớp, cố ý. Lớp ở đây chặn sớm và cho thông báo đúng; lớp ở engine là lớp
     * <b>không bỏ sót đường vào nào</b> (quy tắc 4: đổi trạng thái chỉ qua Workflow engine).
     */
    @PostMapping("/thao-tac")
    @Operation(summary = "Duyệt là số liệu thật, hoặc Loại bỏ (bắt buộc nêu lý do)")
    @RequirePermission("hyd:measurement:review")
    public HydroQualityDtos.ReviewResult xuLy(@Valid @RequestBody HydroQualityDtos.ReviewRequest yeuCau) {
        return HydroQualityDtos.ReviewResult.cua(review.xuLy(
                new HydroReviewService.KhoaBanGhi(yeuCau.diemDoId(), yeuCau.maLoaiChiSo(), yeuCau.mocDo()),
                yeuCau.action(),
                yeuCau.reason()));
    }

    /**
     * Nhập tay một số đo — dùng khi API gián đoạn (CN-03.2).
     *
     * <p>⚠ Trả <b>201 Created</b> kèm {@code Location}: đây là lượt tạo một bản ghi mới, khác hẳn
     * hai endpoint trên (chúng đổi trạng thái một bản ghi đã có).
     */
    @PostMapping("/nhap-tay")
    @Operation(summary = "Nhập tay số đo khi API gián đoạn — ⛔ từ chối giá trị ngoài khoảng vật lý")
    @RequirePermission("hyd:measurement:create")
    public ResponseEntity<Void> nhapTay(@Valid @RequestBody HydroQualityDtos.ManualEntryRequest yeuCau) {
        nhapTay.ghi(yeuCau.diemDoId(), yeuCau.maLoaiChiSo(), yeuCau.mocDo(), yeuCau.giaTri(), yeuCau.ghiChu());
        // ⚠ 201 KHÔNG kèm `Location`: đường dẫn tới một số đo đơn lẻ ⛔ không tồn tại (và cố ý không
        //   tồn tại — xem `HydroReviewService.KhoaBanGhi`). Trả một `Location` trỏ vào khoá tự tăng
        //   là mời người sau dựng đúng endpoint mà `ApiSurfaceRuleTest` vừa cấm.
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .build();
    }
}
