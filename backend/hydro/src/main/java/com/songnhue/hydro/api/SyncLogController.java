package com.songnhue.hydro.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.hydro.application.HydroDiagnosticsService;
import com.songnhue.hydro.domain.BoLocNhatKy;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Nhật ký đồng bộ — {@code /api/v1/hyd/sync-logs/**} (M3.16, T31.13).
 *
 * <h2>⭐ Quyền: {@code hyd:measurement:view}, ⛔ không phải {@code hyd:api-source:manage}</h2>
 *
 * <p>Câu hỏi màn hình này trả lời — <i>"số liệu quan trắc có về không"</i> — thuộc về người
 * <b>dùng</b> số liệu, không thuộc về người cấu hình nguồn. Đo trên ma trận seed:
 * {@code hyd:api-source:manage} chỉ SUPER_ADMIN và ADMIN có, nên gác bằng nó thì
 * {@code TECHNICIAN} — vai trò duy nhất ngoài quản trị được {@code hyd:station:manage}, tức đúng
 * người sẽ đi khai một mã lạ — <b>không đọc nổi lý do vì sao số liệu không về</b>. Đó là hình dạng
 * T27.20 lặp lại lần thứ ba trong hai tuần (§10.70).
 *
 * <p>⚠ Vì thế {@code hyd:measurement:view} có <b>người đọc đầu tiên</b> từ đây, và phải được gỡ khỏi
 * {@code RbacMatrixTest.QUYEN_PHASE_SAU} — bài kiểm ấy canh đúng chiều đó.
 *
 * <h2>⛔ Không endpoint nào ở đây trả nguyên văn thân phản hồi</h2>
 *
 * <p>{@code sync_logs.failure_detail} do adapter sinh và <b>đã đi qua bộ che mã số</b>
 * ({@code Bhh40Adapter.cheMaSo}); {@code raw_log_id} chỉ là con trỏ. Muốn đọc nguyên văn thì tra
 * {@code hydro_raw_logs} — nơi có phân quyền riêng, có hạn lưu và bị {@code REVOKE UPDATE/DELETE}.
 */
@RestController
@RequestMapping("/api/v1/hyd/sync-logs")
@Tag(name = "03-hyd · Nhật ký đồng bộ", description = "Lượt polling: kết cục, lý do hỏng, bốn bộ đếm")
public class SyncLogController {

    private final HydroDiagnosticsService chanDoan;

    public SyncLogController(HydroDiagnosticsService chanDoan) {
        this.chanDoan = chanDoan;
    }

    /**
     * ⚠ <b>Không có tham số {@code sort}</b>, và đó là chủ ý — xem {@link BoLocNhatKy}.
     *
     * <p>⭐ Hai mốc {@code tu}/{@code den} nhận <b>chuỗi ISO UTC</b>, đúng hợp đồng mà
     * {@code AuditController.SearchRequest} đã đặt ra và {@code DateRangeFilter} của giao diện đã
     * nói cùng ngôn ngữ: người dùng chọn "02/09" là chọn ngày 02/09 <b>giờ Việt Nam</b>, và phép
     * đổi múi giờ nằm ở đúng một chỗ trong giao diện ({@code APP_TIMEZONE}). ⛔ Không mở thêm một
     * quy ước thứ hai nhận {@code LocalDate}: hai quy ước lọc thời gian trong một ứng dụng là đúng
     * chỗ mọi lỗi lệch 7 tiếng ra đời.
     *
     * @param den ⚠ <b>nửa khoảng mở</b> ({@code started_at < den}) — cùng quy ước với
     *     {@code AuditLogRepository}. Giao diện gửi {@code endOf('day')} nên biên trên là cuối ngày
     *     người dùng chọn
     */
    @GetMapping
    @Operation(summary = "Nhật ký đồng bộ — mới nhất trước, ⛔ không đổi được thứ tự")
    @RequirePermission({"hyd:measurement:view", "hyd:api-source:manage"})
    public Page<HydroDiagnosticDtos.SyncLogView> danhSach(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) UUID nguonId,
            @RequestParam(required = false) SyncStatus trangThai,
            @RequestParam(required = false) SyncFailureKind loi,
            @RequestParam(required = false) Instant tu,
            @RequestParam(required = false) Instant den,
            @RequestParam(required = false, defaultValue = "false") boolean chiHong) {

        BoLocNhatKy loc = new BoLocNhatKy(nguonId, trangThai, loi, tu, den, chiHong);

        // ⛔ Whitelist sort RỖNG: endpoint không nhận tham số sort nên không có trường nào để cho
        //    phép. Vẫn đi qua PageUtils để dùng đúng một luật kẹp `size` của cả hệ (MAX_SIZE = 100).
        return chanDoan.nhatKy(loc, PageUtils.toPageable(page, size, null, Set.of()))
                .map(HydroDiagnosticDtos.SyncLogView::cua);
    }

    @GetMapping("/tong-hop")
    @Operation(summary = "Dải tóm tắt sức khoẻ — TOÀN BỘ nguồn trong N giờ qua")
    @RequirePermission({"hyd:measurement:view", "hyd:api-source:manage"})
    public HydroDiagnosticDtos.SyncSummaryView tongHop(@RequestParam(required = false) Integer soGio) {
        return HydroDiagnosticDtos.SyncSummaryView.cua(
                chanDoan.tongHop(soGio), HydroDiagnosticsService.kepSoGio(soGio));
    }

    /**
     * Danh sách giá trị của hai ô lọc — ⛔ để giao diện <b>không chép lại</b> hai bộ từ vựng.
     *
     * <p>Luật 14 ở dạng cụ thể nhất: bốn kết cục và năm lý do hỏng hiện đã sống ở ba nơi (enum Java,
     * hai ràng buộc {@code CHECK}, và {@code api-types.ts}). Thêm một danh sách cứng trong một tệp
     * {@code .tsx} là nơi thứ tư, và là nơi duy nhất không bài kiểm nào canh.
     *
     * @return {@code [ "SUCCESS", "PARTIAL", … ]} và {@code [ "THIEU_MA_SO", … ]}
     */
    @GetMapping("/tu-vung")
    @Operation(summary = "Bốn kết cục và năm lý do hỏng — nguồn của hai ô lọc")
    @RequirePermission({"hyd:measurement:view", "hyd:api-source:manage"})
    public TuVung tuVung() {
        return new TuVung(
                List.of(SyncStatus.values()),
                List.of(SyncFailureKind.values()),
                // ⛔ Không viết `List.of(THIEU_MA_SO)`: đó là chép lại kết quả của vị ngữ, và chép
                //    lại là mở nơi thứ tư phải nhớ. Hỏi chính vị ngữ thì thêm một giá trị "chưa
                //    gọi" về sau sẽ tự đi tới giao diện.
                Arrays.stream(SyncFailureKind.values())
                        .filter(k -> !k.duocGhiVaoRawLog())
                        .toList());
    }

    /**
     * @param loiChuaGoi lý do hỏng <b>xảy ra trước khi mở kết nối</b> — giao diện dùng nó để nói
     *     đúng câu "chưa hề gọi lần nào" thay vì "gọi hỏng". ⛔ Không để giao diện tự liệt kê: vị ngữ
     *     {@code SyncFailureKind.duocGhiVaoRawLog()} là nơi duy nhất giữ luật ấy
     */
    public record TuVung(
            List<SyncStatus> trangThai, List<SyncFailureKind> lyDoHong, List<SyncFailureKind> loiChuaGoi) {}
}
