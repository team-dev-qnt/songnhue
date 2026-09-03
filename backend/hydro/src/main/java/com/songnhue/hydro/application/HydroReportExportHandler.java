package com.songnhue.hydro.application;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.core.spi.ReportFilePort;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoDongBoView;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoTongHopView;
import com.songnhue.hydro.api.HydroReportDtos.ChiTietSoDoView;
import com.songnhue.hydro.domain.BangCsv;

/**
 * Kết xuất báo cáo thuỷ văn ra CSV — T34.7 · T34.8.
 *
 * <h2>⭐ Vì sao qua hàng đợi chứ ⛔ không trả thẳng trong request</h2>
 *
 * <p>Pattern P5. BC-12 một tháng của một điểm đo là ~4.500 dòng; BC-13 một năm của 19 điểm đo là
 * ~14 nghìn dòng. Giữ request mở suốt lượt dựng ấy là chiếm một luồng và một connection, và proxy
 * cắt ở 60 giây — người dùng nhận một trang trắng rồi bấm lại, sinh thêm một lượt dựng nữa.
 *
 * <h2>⚠⚠ Hai nửa cặp đọc–ghi được nối ở đây, cả hai hở từ Phase 0</h2>
 *
 * <ol>
 *   <li>{@code MINIO_BUCKET_REPORT} — khai ở 4 tệp env, {@code minio-init} tạo, {@code push-offsite.sh}
 *       sao lưu, {@code @NotBlank} chặn khởi động nếu thiếu, và ⛔ <b>không dòng mã nào đọc</b>. Xem
 *       {@link ReportFilePort}.
 *   <li>{@code jobs.result} — ba nơi đọc, ⛔ <b>không nơi ghi</b>. Xem {@link JobContext#result}.
 * </ol>
 *
 * <p>⭐ Cả hai đều là luật 15 ở dạng khó thấy nhất: <i>có</i> fail-fast, <i>có</i> sao lưu, <i>có</i>
 * javadoc mô tả đích danh tính năng — nên mọi lượt rà đọc chúng là <b>đã xong</b>. Thứ vắng mặt là
 * đúng một đầu dây.
 *
 * <h2>⛔ Số lần thử = 1</h2>
 *
 * <p>Khai ở {@link HydroReportController} qua {@code JobRequest}, ⛔ không ở đây
 * ({@link JobHandler#maxAttempts()} ⛔ không có người đọc trong toàn kho — xem
 * {@code HydroRetentionHandler}). Lý do: một lượt kết xuất hỏng gần như luôn là hỏng <i>tất định</i>
 * (khoảng ngày quá rộng, điểm đo vừa bị xoá), nên thử lại ba lần chỉ dựng lại cùng một lỗi ba lần —
 * và mỗi lượt là một lần quét bảng. Hỏng thì hiện FAILED cho người dùng bấm Xuất lại.
 */
@Component
public class HydroReportExportHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(HydroReportExportHandler.class);

    /**
     * ⭐ Hạn tải bản kết xuất — <b>một</b> con số, hai nơi dùng (luật 14).
     *
     * <p>{@link HydroReportController} kiểm nó ở đường tải; {@link HydroRetentionHandler} dùng chính
     * nó để dọn đối tượng. Tách làm hai hằng là mở đường cho một bản kết xuất <i>còn hạn</i> mà tệp
     * <i>đã bị xoá</i> — hoặc ngược lại, một tệp nằm mãi trong bucket được sao lưu ra ngoài mỗi đêm.
     */
    public static final Duration HAN_TAI = Duration.ofHours(24);

    /** Tiền tố khoá đối tượng của MOD-03 — {@link HydroRetentionHandler} quét đúng tiền tố này. */
    public static final String TIEN_TO_KHOA = "hyd/";

    /** ⛔ Trần dòng cho một lượt kết xuất BC-12 — xem {@link #xuatBc12}. */
    static final int TRAN_DONG_CHI_TIET = 50_000;

    private static final int CO_TRANG = 1000;

    private final HydroReportService baoCao;
    private final ReportFilePort khoTep;
    private final ObjectMapper json;

    public HydroReportExportHandler(HydroReportService baoCao, ReportFilePort khoTep, ObjectMapper json) {
        this.baoCao = baoCao;
        this.khoTep = khoTep;
        this.json = json;
    }

    @Override
    public String jobType() {
        return HydroJobTypes.REPORT_EXPORT;
    }

    @Override
    public void handle(JobContext context) throws Exception {
        YeuCauXuatBaoCao yc = json.readValue(context.payload(), YeuCauXuatBaoCao.class);
        context.progress(10);

        BangCsv bang =
                switch (yc.loai()) {
                    case YeuCauXuatBaoCao.BC13 -> xuatBc13(yc);
                    case YeuCauXuatBaoCao.BC05 -> xuatBc05(yc);
                    case YeuCauXuatBaoCao.BC12 -> xuatBc12(yc, context);
                    // ⛔ Nhánh này ⛔ KHÔNG được im lặng: một mã báo cáo lạ nghĩa là API và
                    //   handler đã lệch nhau, và một tệp CSV RỖNG trông y hệt "kỳ này không có
                    //   dữ liệu" — đúng thứ quy tắc 16 cấm.
                    default -> throw new IllegalStateException("Mã báo cáo không nhận ra: " + yc.loai());
                };
        context.progress(80);

        String tenTep = tenTep(yc);
        String khoa = TIEN_TO_KHOA + context.jobPublicId() + "/" + tenTep;
        byte[] noiDung = bang.byteUtf8Bom();
        khoTep.luu(khoa, noiDung, "text/csv; charset=utf-8");

        // ⚠⚠ `jobs.result` là JSONB — một chuỗi khoá TRẦN làm PostgreSQL từ chối với "invalid input
        //    syntax for type json", và lượt việc hỏng SAU KHI đã dựng xong toàn bộ tệp.
        // ⚠ Ghi con trỏ TRƯỚC khi trả về: `JobWorker.succeed()` đọc lại giá trị từ CSDL ngay sau đó,
        //   nên một lời gọi muộn hơn sẽ ⛔ không kịp và trường ấy im lặng ở lại NULL.
        context.resultJson(json.writeValueAsString(new KetQuaXuat(khoa, tenTep, bang.soDong(), noiDung.length)));
        log.info("Đã kết xuất {} — {} dòng, {} byte, khoá {}", yc.loai(), bang.soDong(), noiDung.length, khoa);
    }

    // =========================================================================

    private BangCsv xuatBc13(YeuCauXuatBaoCao yc) {
        BaoCaoDongBoView bc = baoCao.baoCaoDongBo(yc.tuNgay(), yc.denNgay(), yc.stationPublicId());
        BangCsv b = new BangCsv();
        b.dong(
                "Ngày",
                "Mã điểm đo",
                "Tên điểm đo",
                "Đang dùng",
                "Loại chỉ số",
                "Hợp lệ",
                "Nghi ngờ",
                "Đã loại bỏ",
                "Khung mong đợi",
                "Khung bỏ sót",
                "Tỷ lệ đầy đủ (%)",
                "Lý do ô trống");
        bc.chatLuong()
                .forEach(h -> b.dong(
                        h.ngay(),
                        h.stationCode(),
                        h.stationName(),
                        h.stationActive() ? "Có" : "Không",
                        h.measurementTypeName(),
                        h.soHopLe(),
                        h.soNghiNgo(),
                        h.soDaXoa(),
                        // ⛔ Ô rỗng ra CSV là ô RỖNG, ⛔ không phải 0 — quy tắc 16 áp cho cả bản
                        //   kết xuất, và một số 0 trong Excel là thứ người ta cộng vào tổng.
                        h.soKhungMongDoi() == null ? "" : h.soKhungMongDoi(),
                        h.soKhungBoSot() == null ? "" : h.soKhungBoSot(),
                        BangCsv.so(h.tyLeDayDu() == null ? null : h.tyLeDayDu().toPlainString()),
                        h.lyDoTrong() == null ? "" : h.lyDoTrong()));
        return b;
    }

    private BangCsv xuatBc05(YeuCauXuatBaoCao yc) {
        BaoCaoTongHopView bc = baoCao.tongHopKy(yc.tuNgay(), yc.denNgay(), yc.stationPublicId());
        BangCsv b = new BangCsv();
        b.dong(
                "Mã điểm đo",
                "Tên điểm đo",
                "Tuyến sông",
                "Loại chỉ số",
                "Đơn vị",
                "Nhỏ nhất",
                "Lúc đạt nhỏ nhất",
                "Lớn nhất",
                "Lúc đạt lớn nhất",
                "Trung bình (theo trọng số)",
                "Số bản ghi hợp lệ",
                "Số ngày có dữ liệu",
                "Số ngày trong kỳ",
                "Lý do ô trống");
        bc.hang()
                .forEach(h -> b.dong(
                        h.stationCode(),
                        h.stationName(),
                        // ⛔ `riverName` NULL là trạng thái ĐÚNG hôm nay (G8 chưa chốt) — nói ra,
                        //    ⛔ đừng để một ô trắng không lời trong tệp gửi cho Công ty.
                        h.riverName() == null ? "Chưa phân tuyến" : h.riverName(),
                        h.measurementTypeName(),
                        h.unit(),
                        BangCsv.so(h.giaTriMin() == null ? null : h.giaTriMin().toPlainString()),
                        h.mocMin() == null ? "" : h.mocMin(),
                        BangCsv.so(h.giaTriMax() == null ? null : h.giaTriMax().toPlainString()),
                        h.mocMax() == null ? "" : h.mocMax(),
                        BangCsv.so(h.giaTriTb() == null ? null : h.giaTriTb().toPlainString()),
                        h.soBanGhi(),
                        h.soNgayCoDuLieu(),
                        bc.soNgayTrongKy(),
                        h.lyDoTrong() == null ? "" : h.lyDoTrong()));
        return b;
    }

    /**
     * ⭐ BC-12 kết xuất <b>toàn bộ</b> khoảng ngày, đi theo trang.
     *
     * <p>Màn hình phân trang vì người ta đọc từng trang; bản kết xuất thì ⛔ không — một tệp CSV chỉ
     * có 100 dòng đầu là một tệp <b>sai</b> mà ⛔ không có gì nói ra điều đó. Nên ở đây lặp qua hết,
     * ⛔ không dùng lại tham số phân trang của API.
     *
     * <p>⛔ Nhưng phải có trần: {@link #TRAN_DONG_CHI_TIET}. Nó ⛔ không bao giờ chạm tới với trần 31
     * ngày của BC-12 (31 × 144 = 4.464), và đó là chủ ý — nó là lưới chặn cho ngày ai đó nới trần
     * ngày mà quên rằng báo cáo này quét bảng gốc. ⚠ Chạm trần thì <b>ném</b>, ⛔ không cắt cụt: một
     * tệp bị cắt cụt trông y hệt một tệp đầy đủ.
     */
    private BangCsv xuatBc12(YeuCauXuatBaoCao yc, JobContext context) {
        BangCsv b = new BangCsv();
        b.dong("Mốc đo", "Giá trị", "Chất lượng", "Nguồn", "Máy chẩn đoán", "Người duyệt ghi", "Người nhập ghi");

        int trang = 0;
        long tong;
        do {
            var p = baoCao.chiTiet(
                    yc.stationPublicId(), yc.maLoaiChiSo(), yc.tuNgay(), yc.denNgay(), PageRequest.of(trang, CO_TRANG));
            tong = p.getTotalElements();
            if (tong > TRAN_DONG_CHI_TIET) {
                throw new IllegalStateException(
                        "Bản kết xuất chi tiết vượt trần " + TRAN_DONG_CHI_TIET + " dòng (" + tong + ")");
            }
            List<ChiTietSoDoView> ds = p.getContent();
            ds.forEach(r -> b.dong(
                    r.mocDo(),
                    BangCsv.so(r.giaTri() == null ? null : r.giaTri().toPlainString()),
                    r.quality(),
                    r.source(),
                    r.qualityReason() == null ? "" : r.qualityReason(),
                    r.reviewNote() == null ? "" : r.reviewNote(),
                    r.note() == null ? "" : r.note()));
            trang++;
            context.progress(Math.min(75, 10 + (int) (65L * b.soDong() / Math.max(1, tong))));
        } while ((long) trang * CO_TRANG < tong);

        return b;
    }

    /**
     * Con trỏ kết quả ghi vào {@code jobs.result}.
     *
     * <p>⛔ Chỉ <b>siêu dữ liệu</b>: khoá, tên tệp, số dòng, số byte. ⛔ Không một ô số liệu nào —
     * cột này nằm nguyên văn trong mọi bản sao lưu CSDL, và một bản sao của báo cáo trong bảng hàng
     * đợi là một bản sao ⛔ không ai biết là mình đang giữ.
     *
     * <p>⭐ {@code soDong} và {@code soByte} có mặt để màn hình theo dõi việc nền trả lời được
     * <i>"bản kết xuất ấy có gì trong đó không"</i> mà ⛔ không phải tải tệp về. Một tệp 3 byte (chỉ
     * BOM) và một tệp 400 KB trông y hệt nhau trên một dòng job.
     */
    private record KetQuaXuat(String khoa, String tenTep, int soDong, int soByte) {}

    private static String tenTep(YeuCauXuatBaoCao yc) {
        return "%s_%s_%s.csv".formatted(yc.loai(), yc.tuNgay(), yc.denNgay());
    }
}
