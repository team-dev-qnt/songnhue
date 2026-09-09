package com.songnhue.hydro.application;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.common.export.BangCsv;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.core.spi.ReportFilePort;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoDongBoView;
import com.songnhue.hydro.api.HydroReportDtos.BaoCaoTongHopView;
import com.songnhue.hydro.api.HydroReportDtos.BieuTuyenSongView;
import com.songnhue.hydro.api.HydroReportDtos.ChatLuongNgayView;
import com.songnhue.hydro.api.HydroReportDtos.ChiTietSoDoView;

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
                    case YeuCauXuatBaoCao.BC11 -> xuatBc11(yc);
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

    /**
     * ⭐⭐ BC-13 — <b>Nhật ký đồng bộ &amp; chất lượng dữ liệu</b>, hai khối trong một tệp.
     *
     * <h2>⛔ Bản trước 09/09/2026 xuất NHẦM BẢNG</h2>
     *
     * <p>{@link BaoCaoDongBoView} mang <b>hai</b> danh sách — {@code chatLuong} (điểm đo × ngày) và
     * {@code dongBo} (nguồn × ngày). Bản kết xuất cũ chỉ in danh sách thứ nhất, nên
     * {@code BaoCaoDongBoView.dongBo()} có <b>0 nơi gọi</b> trong toàn kho: 13 trường được truy vấn,
     * ánh xạ, trả qua API — và ⛔ chưa bao giờ tới tay ai qua đường kết xuất.
     *
     * <p>Đối chiếu {@code report-templates-proposal.md} §2.2: 12 cột đặc tả của BC-13 là cột của
     * <b>nhật ký đồng bộ</b> (số lần gọi thật · số lần bỏ qua vì rate-limit · thành công · thất bại
     * · số bản ghi nhận…). Bản cũ trùng đúng <b>2</b> trong 12. Cái tên báo cáo đọc như đã đủ, và
     * người nhận tệp ⛔ không có cách nào biết mình đang cầm bảng khác.
     *
     * <h2>⚠ Vì sao HAI khối chứ ⛔ không phải hai báo cáo</h2>
     *
     * <p>Tên báo cáo có chữ "&amp;". Hai khối trả lời hai câu khác nhau về cùng một kỳ —
     * <i>"nguồn có chạy không"</i> và <i>"số về có dùng được không"</i> — và người trực ban đọc
     * chúng cạnh nhau. Tách thành hai mã báo cáo là bắt họ xuất hai lần rồi tự ghép.
     *
     * <h2>⛔ Hai cột đặc tả CỐ Ý vắng mặt</h2>
     *
     * <ul>
     *   <li><b>Thời gian nguồn OFFLINE</b> — hệ ⛔ không đo. {@code api_sources} chỉ giữ
     *       {@code last_failure_at} (một mốc), ⛔ không giữ khoảng. Suy ra từ {@code hongGanNhat} là
     *       phát minh một con số.
     *   <li><b>Số trạm mất tín hiệu</b> — {@code MAT_TIN_HIEU} là trạng thái <b>suy ra lúc đọc</b>
     *       ({@code TrangThaiDiemDo.suyRa}), ⛔ không phải một đại lượng theo ngày. Thứ đếm được từ
     *       dữ liệu kỳ là <i>số trạm ⛔ không có bản ghi hợp lệ nào trong ngày</i> — gần nghĩa nhưng
     *       ⛔ KHÔNG đồng nghĩa, nên cột mang đúng cái tên nó đo.
     * </ul>
     */
    private BangCsv xuatBc13(YeuCauXuatBaoCao yc) {
        BaoCaoDongBoView bc = baoCao.baoCaoDongBo(yc.tuNgay(), yc.denNgay(), yc.stationPublicId());
        BangCsv b = new BangCsv();

        // === Khối A — nhật ký đồng bộ (nguồn × ngày) =========================
        // ⛔⛔ `BangCsv` TỪ CHỐI dòng lệch số cột, và nó từ chối đúng: một CSV lệch cột vẫn mở
        //    được trong Excel, chỉ đẩy dữ liệu sang cột bên cạnh từ dòng ấy trở đi — im lặng.
        //    Nên hai khối ở đây nằm trên CÙNG một lưới SO_COT_BC13 cột; khối B ngắn hơn thì được
        //    đệm ô rỗng, ⛔ không được rút ngắn dòng.
        dongBc13(b, "A. NHẬT KÝ ĐỒNG BỘ — theo nguồn dữ liệu và ngày");
        dongBc13(
                b,
                "Ngày",
                "Mã nguồn",
                "Tên nguồn",
                "Số lượt gọi thật",
                "Thành công",
                "Thành công một phần",
                "Thất bại",
                "Bỏ qua (rate-limit)",
                "Số bản ghi nhận",
                "Ghi mới",
                "Trùng (bỏ)",
                "Mã lạ (không khớp điểm đo)",
                "Lần hỏng gần nhất",
                "Bản ghi nghi ngờ trong ngày",
                "Khung bỏ sót trong ngày",
                "Số trạm không có bản ghi hợp lệ");

        Map<LocalDate, TongHopNgay> theoNgay = tongHopChatLuongTheoNgay(bc.chatLuong());
        bc.dongBo().forEach(d -> {
            TongHopNgay t = theoNgay.get(d.ngay());
            dongBc13(
                    b,
                    d.ngay(),
                    d.sourceCode(),
                    d.sourceName(),
                    d.soLuot(),
                    d.soThanhCong(),
                    d.soMotPhan(),
                    d.soHong(),
                    d.soBoQua(),
                    d.soNhan(),
                    d.soGhiMoi(),
                    d.soTrung(),
                    d.soMaLa(),
                    d.hongGanNhat() == null ? "" : d.hongGanNhat(),
                    // ⛔ Ngày ⛔ không có hàng chất lượng nào ⇒ RỖNG, ⛔ không phải 0 (quy tắc 16):
                    //   "0 bản ghi nghi ngờ" và "chưa tính được" là hai câu khác hẳn nhau.
                    t == null ? "" : t.nghiNgo(),
                    t == null || !t.khungBoSotDoDuoc() ? "" : t.khungBoSot(),
                    t == null ? "" : t.tramKhongCoBanGhiHopLe());
        });

        // === Khối B — chất lượng dữ liệu (điểm đo × ngày) ====================
        dongBc13(b, "");
        dongBc13(b, "B. CHẤT LƯỢNG DỮ LIỆU — theo điểm đo và ngày");
        dongBc13(
                b,
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
                .forEach(h -> dongBc13(
                        b,
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

    /**
     * ⛔ Bề rộng lưới của BC-13 — <b>cả hai khối</b> dùng chung.
     *
     * <p>Khối A có 16 ô, khối B có 12. {@link BangCsv#dong} <b>ném</b> khi một dòng lệch số cột so
     * với dòng đầu, và nó đúng: một CSV lệch cột vẫn mở được trong Excel, chỉ <i>đẩy dữ liệu sang cột
     * bên cạnh</i> từ dòng ấy trở đi — im lặng, và người đọc thấy một bảng trông bình thường với mọi
     * giá trị lệch một ô.
     *
     * <p>⚠ Đây là lỗi đã <b>đo được</b> ở lượt chạy đầu của khối hai-tầng này (09/09/2026): việc nền
     * FAILED chứ ⛔ không cho ra một tệp xấu — tức là bộ canh làm đúng việc của nó. Cách sửa là
     * <b>đệm</b> cho đủ lưới, ⛔ không phải nới bộ canh.
     */
    private static final int SO_COT_BC13 = 16;

    /** Ghi một dòng BC-13, đệm ô rỗng cho đủ {@link #SO_COT_BC13}. */
    private static void dongBc13(BangCsv b, Object... o) {
        if (o.length > SO_COT_BC13) {
            throw new IllegalStateException(
                    "Dòng BC-13 có " + o.length + " ô, vượt lưới " + SO_COT_BC13 + " — nới lưới, đừng cắt dữ liệu");
        }
        Object[] day = new Object[SO_COT_BC13];
        System.arraycopy(o, 0, day, 0, o.length);
        for (int i = o.length; i < SO_COT_BC13; i++) {
            day[i] = "";
        }
        b.dong(day);
    }

    /**
     * Tổng hợp theo ngày của khối chất lượng, để khối A có ba cột nữa mà ⛔ không phải truy vấn lại.
     *
     * @param khungBoSotDoDuoc ⛔ {@code false} khi <b>bất kỳ</b> hàng nào trong ngày có
     *     {@code soKhungBoSot == null}. Cộng dồn bỏ qua {@code null} sẽ ra một tổng <i>nhỏ hơn sự
     *     thật</i> mà trông hoàn toàn bình thường — và đây đúng là cột chịu lực của NFR-03, nơi mỗi
     *     khung bỏ sót là dữ liệu mất vĩnh viễn. Rỗng-kèm-lý-do thắng một con số thiếu.
     */
    private record TongHopNgay(long nghiNgo, long khungBoSot, boolean khungBoSotDoDuoc, long tramKhongCoBanGhiHopLe) {}

    private static Map<LocalDate, TongHopNgay> tongHopChatLuongTheoNgay(List<ChatLuongNgayView> hang) {
        Map<LocalDate, List<ChatLuongNgayView>> nhom =
                hang.stream().collect(Collectors.groupingBy(ChatLuongNgayView::ngay));

        Map<LocalDate, TongHopNgay> ket = new LinkedHashMap<>();
        nhom.forEach((ngay, ds) -> {
            long nghiNgo = ds.stream().mapToLong(ChatLuongNgayView::soNghiNgo).sum();
            boolean doDuoc = ds.stream().allMatch(h -> h.soKhungBoSot() != null);
            long boSot = doDuoc
                    ? ds.stream().mapToLong(ChatLuongNgayView::soKhungBoSot).sum()
                    : 0L;
            // ⚠ Đếm theo MÃ ĐIỂM ĐO, ⛔ không đếm theo hàng: một điểm đo có nhiều loại chỉ số sẽ có
            //   nhiều hàng trong cùng một ngày, và đếm hàng là đếm nó nhiều lần.
            long imLang = ds.stream()
                    .filter(h -> h.soHopLe() == 0)
                    .map(ChatLuongNgayView::stationCode)
                    .distinct()
                    .count();
            ket.put(ngay, new TongHopNgay(nghiNgo, boSot, doDuoc, imLang));
        });
        return ket;
    }

    /**
     * ⭐ BC-05 — thuỷ văn tháng.
     *
     * <h2>Năm cột thêm ngày 09/09/2026, đối chiếu {@code report-templates-proposal.md} §2.2</h2>
     *
     * <p>Bản trước thiếu <b>STT · Lý trình · Lượng mưa · Số lần vượt ngưỡng · Ghi chú</b> — và ⛔
     * không có gì nói ra điều đó, vì 14 cột nó <i>có</i> đều đúng. Đây là báo cáo tháng mà Công ty
     * thực sự nhận, nên "thiếu 5 cột" ⛔ không phải một nợ kỹ thuật, nó là một bản báo cáo sai mẫu.
     *
     * <ul>
     *   <li><b>STT</b> — thuần trình bày, sinh lúc in. ⛔ Không lưu ở đâu cả: số thứ tự phụ thuộc bộ
     *       lọc và thứ tự sắp, nên một cột STT lưu sẵn sẽ sai ngay lượt lọc thứ hai.
     *   <li><b>Lý trình</b> — {@code stations.chainage}. Bản chụp G8 09/09 cấp 10/19 giá trị;
     *       9 điểm còn lại vẫn rỗng, và ô rỗng nói ra là chưa có chứ ⛔ không để trắng.
     *   <li><b>Lượng mưa</b> — ⛔ <b>chưa có nguồn</b> (G3-a: {@code bhh40} chỉ có {@code getmn.aspx}).
     *       Ô ghi thẳng lý do, ⛔ không ghi {@code 0}: một cột lượng mưa toàn số 0 trong báo cáo mùa
     *       mưa là một khẳng định sai và rất đáng tin (quy tắc 16).
     *   <li><b>Số lần vượt ngưỡng</b> — đếm từ {@code alert_events}, xem {@code SQL_TONG_HOP_KY}.
     *   <li><b>Ghi chú</b> — cột CỐ Ý rỗng: đặc tả dành nó cho người trực ban viết tay sau khi in.
     *       Đây là ngoại lệ có chủ đích của quy tắc 16 — ô rỗng vì ⛔ chưa ai điền, ⛔ không phải vì
     *       hệ ⛔ không đo được.
     * </ul>
     */
    private BangCsv xuatBc05(YeuCauXuatBaoCao yc) {
        BaoCaoTongHopView bc = baoCao.tongHopKy(yc.tuNgay(), yc.denNgay(), yc.stationPublicId());
        BangCsv b = new BangCsv();
        b.dong(
                "STT",
                "Mã điểm đo",
                "Tên điểm đo",
                "Tuyến sông",
                "Lý trình",
                "Loại chỉ số",
                "Đơn vị",
                "Nhỏ nhất",
                "Lúc đạt nhỏ nhất",
                "Lớn nhất",
                "Lúc đạt lớn nhất",
                "Trung bình (theo trọng số)",
                "Lượng mưa (mm)",
                "Số lần vượt ngưỡng",
                "Số bản ghi hợp lệ",
                "Số ngày có dữ liệu",
                "Số ngày trong kỳ",
                "Lý do ô trống",
                "Ghi chú");

        int[] stt = {0};
        bc.hang()
                .forEach(h -> b.dong(
                        ++stt[0],
                        h.stationCode(),
                        h.stationName(),
                        // ⛔ `riverName` NULL là trạng thái ĐÚNG hôm nay (G8 chưa chốt trọn) — nói ra,
                        //    ⛔ đừng để một ô trắng không lời trong tệp gửi cho Công ty.
                        h.riverName() == null ? "Chưa phân tuyến" : h.riverName(),
                        h.chainage() == null ? "Chưa có lý trình (G8)" : h.chainage(),
                        h.measurementTypeName(),
                        h.unit(),
                        BangCsv.so(h.giaTriMin() == null ? null : h.giaTriMin().toPlainString()),
                        h.mocMin() == null ? "" : h.mocMin(),
                        BangCsv.so(h.giaTriMax() == null ? null : h.giaTriMax().toPlainString()),
                        h.mocMax() == null ? "" : h.mocMax(),
                        BangCsv.so(h.giaTriTb() == null ? null : h.giaTriTb().toPlainString()),
                        LUONG_MUA_CHUA_CO_NGUON,
                        h.soLanVuotNguong(),
                        h.soBanGhi(),
                        h.soNgayCoDuLieu(),
                        bc.soNgayTrongKy(),
                        h.lyDoTrong() == null ? "" : h.lyDoTrong(),
                        ""));
        return b;
    }

    /**
     * ⛔ Ô lượng mưa của BC-05 — G3-a.
     *
     * <p>Nguồn {@code bhh40} ⛔ không có endpoint lượng mưa, nên cột này ⛔ chưa bao giờ có số. Một
     * hằng số nói ra lý do thắng một ô trắng: người đọc báo cáo tháng 8 mà thấy cột lượng mưa trắng
     * sẽ nghĩ tháng ấy ⛔ không mưa.
     */
    private static final String LUONG_MUA_CHUA_CO_NGUON = "Chưa có nguồn (G3-a)";

    /**
     * ⭐⭐ BC-11 — biểu tổng hợp mực nước theo tuyến sông, <b>một ngày</b>.
     *
     * <p>Tới 09/09/2026 đây là báo cáo <b>DUY NHẤT ⛔ không có đường xuất nào</b>:
     * {@code RiverBoardPage.tsx} có 97 dòng và 0 nút Xuất, trong khi đặc tả gọi nó là biểu <i>"ưu
     * tiên cao nhất"</i> và nó mô phỏng đúng biểu Công ty đang dùng trên giấy hằng ngày. Người trực
     * ban ⛔ không có cách nào lấy biểu ấy ra khỏi màn hình.
     *
     * <h2>⚠ Bảng PHẲNG, ⛔ không phải bố cục hai tầng của bản in</h2>
     *
     * <p>Bản in của Công ty xếp mỗi công trình một cột, tách TL–HL, lý trình đặt dưới tên. Bố cục ấy
     * cần một cỗ máy dựng bảng có gộp ô — mà kho ⛔ chưa có bộ kết xuất nào ngoài CSV
     * ({@code common/export/} có đúng một tệp). Ép nó vào CSV sẽ ra một tệp <i>trông giống</i> bản
     * in mà Excel ⛔ không lọc, ⛔ không sắp, ⛔ không cộng được.
     *
     * <p>⇒ Ở đây xuất <b>một dòng một điểm đo</b>, mang đủ mọi ô của biểu kèm tên tuyến sông.
     * Người nhận pivot lại trong Excel trong 30 giây, và bố cục in đúng mẫu chờ <b>G10</b> — lúc ấy
     * mới biết Công ty duyệt khổ giấy và bố cục nào, và mới đáng dựng bộ kết xuất có định dạng.
     */
    private BangCsv xuatBc11(YeuCauXuatBaoCao yc) {
        BieuTuyenSongView bieu = baoCao.bieuTuyenSong(yc.denNgay());
        BangCsv b = new BangCsv();
        b.dong(
                "Ngày",
                "Tuyến sông",
                "Mã điểm đo",
                "Tên điểm đo",
                "Vị trí",
                "Lý trình",
                "Chỉ số",
                "Đơn vị",
                "Hiện tại",
                "Mốc đo",
                "Nhỏ nhất trong ngày",
                "Lớn nhất trong ngày",
                "Số bản ghi trong ngày",
                "Trạng thái tín hiệu",
                "Lý do ô trống",
                "Lượng mưa (mm)",
                "Lý do ô lượng mưa trống",
                "Tình hình vận hành",
                "Lý do ô tình hình trống");

        bieu.tuyen().forEach(t -> t.diemDo()
                .forEach(d -> b.dong(
                        bieu.ngay(),
                        t.tenTuyen(),
                        d.stationCode(),
                        d.stationName(),
                        d.positionRole(),
                        // ⛔ Rỗng nói ra là chưa có, ⛔ không để ô trắng không lời — bản chụp
                        //    G8 (09/09) mới cấp lý trình cho 10/19 điểm đo.
                        d.chainage() == null ? "Chưa có lý trình (G8)" : d.chainage(),
                        d.measurementTypeName(),
                        d.unit(),
                        BangCsv.so(d.giaTri() == null ? null : d.giaTri().toPlainString()),
                        d.mocDo() == null ? "" : d.mocDo(),
                        BangCsv.so(d.minNgay() == null ? null : d.minNgay().toPlainString()),
                        BangCsv.so(d.maxNgay() == null ? null : d.maxNgay().toPlainString()),
                        d.soBanGhiNgay(),
                        d.trangThaiTinHieu(),
                        d.lyDoTrong() == null ? "" : d.lyDoTrong(),
                        BangCsv.so(d.luongMua() == null ? null : d.luongMua().toPlainString()),
                        d.lyDoLuongMua() == null ? "" : d.lyDoLuongMua(),
                        d.tinhHinhVanHanh() == null ? "" : d.tinhHinhVanHanh().ma(),
                        d.lyDoTinhHinh() == null ? "" : d.lyDoTinhHinh())));
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
