package com.songnhue.hydro.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRef;
import com.songnhue.core.spi.JobRequest;
import com.songnhue.core.spi.ReportFilePort;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoDongBoView;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoTongHopView;
import com.songnhue.hydro.api.HydroReportDtos.ChiTietSoDoView;
import com.songnhue.hydro.application.HydroJobTypes;
import com.songnhue.hydro.application.HydroReportExportHandler;
import com.songnhue.hydro.application.HydroReportService;
import com.songnhue.hydro.application.YeuCauXuatBaoCao;

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
    private final JobPort jobs;
    private final ReportFilePort khoTep;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public HydroReportController(
            HydroReportService baoCao,
            JobPort jobs,
            ReportFilePort khoTep,
            com.fasterxml.jackson.databind.ObjectMapper json) {
        this.baoCao = baoCao;
        this.jobs = jobs;
        this.khoTep = khoTep;
        this.json = json;
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

    /**
     * BC-05 — T34.5.
     *
     * <p>⚠ Nhận một <b>khoảng ngày</b> chứ ⛔ không nhận "tháng": báo cáo tháng là <i>trường hợp
     * dùng mặc định</i> của giao diện, ⛔ không phải giới hạn của số liệu. Một kỳ 10 ngày (đợt xả)
     * hay một kỳ mùa lũ đều là câu hỏi có thật, và ép chúng vào ranh giới tháng là bắt người dùng
     * cộng tay hai bản báo cáo — đúng lúc con số phải chính xác nhất.
     */
    @GetMapping("/tong-hop")
    @RequirePermission("hyd:report:view")
    @Operation(
            summary = "BC-05 — tổng hợp kỳ: max/min/TB CHỈ trên số liệu hợp lệ, kèm thời điểm đạt max/min",
            description = "Trung bình tính THEO TRỌNG SỐ (tổng giá trị / tổng số bản ghi), ⛔ không phải "
                    + "trung bình của các trung bình ngày. Kỳ không có bản ghi hợp lệ trả ô RỖNG KÈM LÝ DO, "
                    + "⛔ không trả 0 và ⛔ không biến mất khỏi danh sách")
    public BaoCaoTongHopView tongHop(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tuNgay,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate denNgay,
            @RequestParam(required = false) UUID stationPublicId) {
        return baoCao.tongHopKy(tuNgay, denNgay, stationPublicId);
    }

    /**
     * BC-12 — T34.6.
     *
     * <p>⭐⭐ Báo cáo <b>duy nhất</b> hiện bản ghi {@code NGHI_NGO} và {@code XOA}, và ⛔ là ngoại lệ
     * hợp lệ duy nhất của quy tắc 8 (đọc bảng gốc thay vì bảng tổng hợp). Hai điều kiện giữ cho
     * ngoại lệ ấy an toàn: khoảng ngày <b>tối đa 31 ngày</b> ({@code HYD-2012}) và phân trang.
     *
     * <p>⛔ Whitelist sort RỖNG: endpoint ⛔ không nhận {@code sort} — số đo chỉ có một thứ tự có
     * nghĩa là theo mốc đo, mới nhất trước. Vẫn đi qua {@code PageUtils} để dùng đúng một luật kẹp
     * {@code size} của cả hệ.
     */
    @GetMapping("/chi-tiet")
    @RequirePermission("hyd:report:view")
    @Operation(
            summary = "BC-12 — chi tiết từng bản ghi, KÈM cột Chất lượng và cột Nguồn",
            description = "Nơi DUY NHẤT hiện cả bản ghi nghi ngờ và đã xoá. Hai cột ấy là thứ được đánh "
                    + "đổi lấy quyền không lọc chất lượng — ⛔ đừng bỏ chúng đi")
    public Page<ChiTietSoDoView> chiTiet(
            @RequestParam UUID stationPublicId,
            @RequestParam String maLoaiChiSo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tuNgay,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate denNgay,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return baoCao.chiTiet(
                stationPublicId, maLoaiChiSo, tuNgay, denNgay, PageUtils.toPageable(page, size, null, Set.of()));
    }

    // =========================================================================
    // ⭐ T34.7 — kết xuất qua hàng đợi việc nền
    // =========================================================================

    /**
     * Đặt một lượt kết xuất — trả <b>202 Accepted</b> kèm mã việc.
     *
     * <p>⭐ {@code 202}, ⛔ không {@code 200}: mã trạng thái phải nói ra rằng <i>chưa có gì để tải</i>.
     * Trả 200 kèm một mã việc là để giao diện tự đoán, và nửa số client sẽ đoán rằng thân phản hồi
     * chính là tệp.
     *
     * <p>⚠ Gác bằng {@code hyd:report:export}, ⛔ <b>không</b> bằng {@code hyd:report:view}: xuất là
     * một việc khác xem. Đo trên ma trận seed, {@code XN_OPERATOR} và {@code DUTY_OFFICER} chỉ có
     * quyền xem — họ đọc được báo cáo trên màn hình nhưng ⛔ không mang được nó ra ngoài, và đó là
     * một quyết định của Công ty chứ ⛔ không phải một chi tiết kỹ thuật.
     *
     * <p>⛔ {@code maxAttempts = 1}: một lượt kết xuất hỏng gần như luôn hỏng <b>tất định</b>
     * (khoảng ngày quá rộng, điểm đo vừa bị xoá), nên thử lại chỉ dựng lại cùng một lỗi — và mỗi lượt
     * là một lần quét bảng.
     */
    @PostMapping("/xuat")
    @RequirePermission("hyd:report:export")
    @Operation(
            summary = "Đặt lượt kết xuất CSV — 202 kèm mã việc, tải về ở /bao-cao/tai/{jobId}",
            description = "Tệp CSV mã hoá UTF-8 có BOM, phân tách bằng dấu chấm phẩy, số dùng dấu phẩy "
                    + "thập phân — để Excel bản tiếng Việt đọc đúng ngay khi mở. Bản kết xuất có hạn tải 24 giờ")
    public ResponseEntity<JobRef> xuat(@RequestBody YeuCauXuatBaoCao yeuCau) throws Exception {
        kiemYeuCau(yeuCau);

        // ⚠ Khoá chống trùng mang TOÀN BỘ tham số: hai khoảng ngày khác nhau là hai việc khác nhau,
        //   và gộp chúng lại thì người thứ hai nhận bản kết xuất của người thứ nhất — đúng số liệu,
        //   sai kỳ, ⛔ không có gì báo sai.
        String payload = json.writeValueAsString(yeuCau);
        String dedupKey = HydroJobTypes.REPORT_EXPORT + ":" + payload.hashCode() + ":" + payload.length();

        JobRef viec = jobs.enqueue(new JobRequest(HydroJobTypes.REPORT_EXPORT, payload, dedupKey, (short) 1));
        return ResponseEntity.accepted().body(viec);
    }

    /**
     * Tải bản kết xuất.
     *
     * <h2>⛔⛔ Kiểm {@code jobType} là chốt chặn, ⛔ không phải phép kiểm cho đẹp</h2>
     *
     * <p>Endpoint này nhận một mã việc và trả nội dung mà việc ấy sinh ra. Bỏ phép kiểm loại việc
     * thì bất kỳ ai có {@code hyd:report:export} cũng đọc được kết quả của <b>mọi</b> loại việc nền
     * — gồm {@code DB_BACKUP}. Mã việc là UUID nên khó đoán, nhưng "khó đoán" ⛔ không phải một tầng
     * phân quyền (§4.2).
     *
     * <h2>⚠ Ba trạng thái, ba câu trả lời PHÂN BIỆT ĐƯỢC (luật 9)</h2>
     *
     * <ul>
     *   <li>⛔ không có việc ấy / sai loại → <b>404</b>
     *   <li>việc chưa xong hoặc đã hỏng → <b>409</b> {@code HYD-2015} kèm trạng thái
     *   <li>quá hạn tải → <b>410</b> {@code HYD-2014} — ⛔ <b>không</b> 404: "chưa từng có" và "có,
     *       và đã hết hạn" dẫn tới hai việc phải làm khác hẳn nhau
     * </ul>
     *
     * <p>⚠ Thân phản hồi là {@code byte[]} trần — §10.52: envelope bọc {@code byte[]} làm ảnh cổng
     * ⛔ chưa từng ra được một byte nào suốt bốn ngày. Cùng khuôn {@code PublicPortalController.file}.
     */
    @GetMapping("/tai/{jobPublicId}")
    @RequirePermission("hyd:report:export")
    @Operation(summary = "Tải bản kết xuất CSV đã sinh xong — hạn 24 giờ kể từ lúc đặt lượt")
    public ResponseEntity<byte[]> tai(@PathVariable UUID jobPublicId) {
        JobRef viec = jobs.findJob(jobPublicId)
                .filter(j -> HydroJobTypes.REPORT_EXPORT.equals(j.jobType()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        if (!"SUCCEEDED".equals(viec.status()) || viec.resultRef() == null) {
            throw new ConflictException(ErrorCode.HYD_2015, viec.status());
        }
        if (viec.createdAt().plus(HydroReportExportHandler.HAN_TAI).isBefore(Instant.now())) {
            throw new ConflictException(ErrorCode.HYD_2014, HydroReportExportHandler.HAN_TAI.toHours());
        }

        String khoa;
        try {
            // ⚠ `jobs.result` là JSONB — con trỏ là một tài liệu, ⛔ không phải một chuỗi khoá trần.
            khoa = json.readTree(viec.resultRef()).path("khoa").asText(null);
        } catch (Exception e) {
            throw new ConflictException(ErrorCode.HYD_2015, viec.status());
        }
        if (khoa == null || khoa.isBlank()) {
            throw new ConflictException(ErrorCode.HYD_2015, viec.status());
        }

        byte[] noiDung = khoTep.doc(khoa)
                // ⚠ Tệp đã bị lượt dọn hằng ngày xoá trong khi mốc tạo job vẫn còn trong hạn — cửa
                //   sổ ấy có thật vì hai việc chạy theo hai nhịp. Trả HYD-2014 chứ ⛔ không 404: với
                //   người dùng thì đây đúng là "đã hết hạn", và việc phải làm là bấm Xuất lại.
                .orElseThrow(
                        () -> new ConflictException(ErrorCode.HYD_2014, HydroReportExportHandler.HAN_TAI.toHours()));

        String tenTep = khoa.substring(khoa.lastIndexOf('/') + 1);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + tenTep + "\"")
                .body(noiDung);
    }

    /**
     * ⚠ Đối chiếu mã báo cáo <b>ở tầng API</b>, ⛔ không để handler gặp một chuỗi lạ lúc 3 giờ sáng.
     *
     * <p>Handler vẫn có nhánh {@code default} ném ngoại lệ (luật 9 — "chưa xong" phải là nhánh
     * riêng), nhưng một lỗi phát hiện được lúc bấm nút thì phải báo lúc bấm nút: để nó rơi xuống
     * việc nền là biến một lỗi nhập liệu thành một dòng FAILED mà ⛔ không ai đọc.
     */
    private void kiemYeuCau(YeuCauXuatBaoCao yc) {
        if (yc.loai() == null
                || !Set.of(YeuCauXuatBaoCao.BC13, YeuCauXuatBaoCao.BC05, YeuCauXuatBaoCao.BC12)
                        .contains(yc.loai())) {
            throw new com.songnhue.core.common.exception.ValidationException(ErrorCode.SYS_0003);
        }
        // ⭐ Đi qua ĐÚNG phép kiểm khoảng ngày mà đường xem dùng — kể cả hai trần khác nhau. Một
        //   đường xuất lỏng hơn đường xem là một cách đi vòng qua chính cái trần vừa đặt.
        boolean chiTiet = YeuCauXuatBaoCao.BC12.equals(yc.loai());
        baoCao.kiemKhoang(
                yc.tuNgay(),
                yc.denNgay(),
                chiTiet ? HydroReportService.TRAN_NGAY_CHI_TIET : HydroReportService.TRAN_SO_NGAY);
        if (chiTiet && (yc.stationPublicId() == null || yc.maLoaiChiSo() == null)) {
            throw new com.songnhue.core.common.exception.ValidationException(ErrorCode.SYS_0003);
        }
    }
}
