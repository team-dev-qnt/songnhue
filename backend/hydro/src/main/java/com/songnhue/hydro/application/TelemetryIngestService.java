package com.songnhue.hydro.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.DiemDoDich;
import com.songnhue.hydro.domain.RawFetch;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.ReadingRow;
import com.songnhue.hydro.domain.ReadingSource;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncOutcome;
import com.songnhue.hydro.domain.SyncStatus;
import com.songnhue.hydro.domain.TelemetryAdapter;
import com.songnhue.hydro.domain.TelemetryBatch;
import com.songnhue.hydro.domain.TelemetryCall;
import com.songnhue.hydro.domain.TelemetryFetch;
import com.songnhue.hydro.domain.TelemetryReading;
import com.songnhue.hydro.domain.UnmappedRow;
import com.songnhue.hydro.infra.HydroRawLogWriter;
import com.songnhue.hydro.infra.HydroTimeSeriesWriter;
import com.songnhue.hydro.infra.PollerRepository;
import com.songnhue.hydro.infra.SyncLogWriter;
import com.songnhue.hydro.infra.TelemetryAdapters;

/**
 * ⭐⭐ Một lượt đồng bộ trọn vẹn — <b>đường ingest DUY NHẤT</b> của MOD-03 (WS-31).
 *
 * <pre>
 *   rate-limit ──► maSoDeGoi ──► adapter.goi() ──► GHI hydro_raw_logs ──► adapter.boc()
 *                                                   ▲                        │
 *                     quy tắc parse 1: ⛔ KHÔNG bước nào chen vào đây        ▼
 *                                                          ánh xạ mã → điểm đo (quy tắc 5)
 *                                                                            │
 *              sync_logs ◄── hydro_latest ◄── hydro_readings + hydro_unmapped_readings
 * </pre>
 *
 * <h2>Hai người gọi, một đường đi</h2>
 *
 * <ul>
 *   <li>{@link #chayTheoLich} — poller, 2 phút/lần, <b>có</b> rate-limit;
 *   <li>{@link #goiThu} — nút <i>Gọi thử</i>, ⛔ <b>không</b> rate-limit: một con người vừa bấm nút
 *       và đang chờ câu trả lời "mã số này có đúng không". Trả về {@link SyncStatus#SKIPPED_UP_TO_DATE}
 *       cho một cú bấm tay là trả lời một câu hỏi khác câu được hỏi.
 * </ul>
 *
 * <p>Hai lối vào ấy khác nhau đúng <b>một tham số boolean</b>. Cho poller một đường riêng là dựng
 * hai bộ đếm cho cùng một việc, rồi một hôm chúng nói khác nhau (luật 14).
 *
 * <h2>⭐⭐ Bốn quyết định chịu lực</h2>
 *
 * <ol>
 *   <li><b>⛔ Không mở giao dịch quanh lượt gọi HTTP.</b> Một nguồn treo 30 giây × vài lượt gọi là
 *       đủ khoá cạn hồ kết nối CSDL, và triệu chứng khi ấy là <i>toàn hệ thống</i> chậm — cách rất
 *       xa nguyên nhân. Ghi CSDL nằm ở những lời gọi ngắn <b>trước và sau</b> lượt gọi mạng.
 *   <li><b>⭐ Ghi {@code hydro_raw_logs} bằng giao dịch RIÊNG ({@code REQUIRES_NEW}).</b> Quy tắc
 *       18: nguồn không có API lịch sử, một response chứa 28 số đo thật là <b>không lấy lại
 *       được</b>. Lượt ghi ấy phải sống sót kể cả khi mọi bước sau nó đổ vỡ.
 *   <li><b>⛔ Ghi {@code sync_logs} ở MỌI nhánh thoát</b> — kể cả nhánh bị nguồn từ chối và nhánh cố
 *       ý bỏ qua (T31.12). Worker chỉ biết "job này hỏng"; nó không biết <i>vì sao</i> và không hiện
 *       ở màn hình của người vận hành thuỷ văn. §10.68-C là bài học nguyên bản: cơ chế bảo vệ và cơ
 *       chế tự động hoá đứng cạnh nhau mà không ai đối chiếu thì chúng ăn thịt nhau, và không có
 *       dòng nào giải thích.
 *   <li><b>⛔ Không ném ngoại lệ ở lớp này.</b> Quyết định "hỏng này có đáng làm job đỏ không" thuộc
 *       {@code HydroPollJobHandler} — vì nó phụ thuộc <i>ai đang gọi</i>: nút Gọi thử phải trả 200
 *       kèm chẩn đoán, còn poller phải để lại một job FAILED. Lớp này chỉ kể lại đã thấy gì.
 * </ol>
 *
 * <h2>⬜ Chất lượng số đo tạm thời luôn {@code HOP_LE} — nợ có tên, đóng ở WS-32</h2>
 *
 * <p>{@link ReadingQuality#NGHI_NGO} cần bộ quy tắc {@code hydro.quality.suspect-rule} (khoá đã seed,
 * chưa ai đọc) và một màn hình duyệt. Cho tới lúc ấy mọi dòng ghi {@code HOP_LE} — trùng khít giá trị
 * mặc định của lược đồ, nên ⛔ <b>không</b> có chỗ nào nói hai điều khác nhau. ⚠ Hệ quả phải nói ra:
 * bộ lọc {@code quality = 'HOP_LE'} của quy tắc 14 <b>hôm nay chưa loại được gì</b> — nó đúng nhưng
 * chưa được thử. Bài kiểm chứng minh nó thật sự lọc thuộc WS-32/T32.4.
 */
@Service
public class TelemetryIngestService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestService.class);

    /** Loại chỉ số mà adapter {@code BHH40} giao — {@code getmn.aspx} chỉ có mực nước (G3-a). */
    static final String MA_LOAI_CHI_SO = "MUC_NUOC";

    /** Số mã lạ tối đa liệt kê ra màn hình — phần còn lại đếm được ở {@code hydro_unmapped_readings}. */
    private static final int TRAN_MA_LA_LIET_KE = 50;

    private final ApiSourceService sources;
    private final TelemetryAdapters adapters;
    private final PollerRepository poller;
    private final HydroRawLogWriter rawLogs;
    private final HydroTimeSeriesWriter timeSeries;
    private final SyncLogWriter syncLogs;
    private final ApiSourceHealthService health;
    private final TransactionTemplate giaoDichRieng;
    private final TransactionTemplate giaoDichGhi;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public TelemetryIngestService(
            ApiSourceService sources,
            TelemetryAdapters adapters,
            PollerRepository poller,
            HydroRawLogWriter rawLogs,
            HydroTimeSeriesWriter timeSeries,
            SyncLogWriter syncLogs,
            ApiSourceHealthService health,
            PlatformTransactionManager txManager) {
        this.sources = sources;
        this.adapters = adapters;
        this.poller = poller;
        this.rawLogs = rawLogs;
        this.timeSeries = timeSeries;
        this.syncLogs = syncLogs;
        this.health = health;
        // ⛔⛔ TransactionTemplate, ⛔ KHÔNG phải @Transactional(REQUIRES_NEW) trên một phương thức
        //    của chính lớp này: Spring chặn lời gọi ở PROXY, nên một lời gọi nội bộ đi thẳng vào
        //    phương thức và chú thích ấy KHÔNG có tác dụng nào — mà mã vẫn biên dịch, bài kiểm vẫn
        //    xanh, và giao dịch "riêng" thật ra là giao dịch chung. §10.20: dự án này đã sập 2 lần
        //    vì đúng chỗ đó.
        this.giaoDichRieng = new TransactionTemplate(txManager);
        this.giaoDichRieng.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.giaoDichGhi = new TransactionTemplate(txManager);
    }

    /** Nút <i>Gọi thử</i> — ⛔ bỏ qua rate-limit, xem javadoc lớp. */
    public KetQuaDongBo goiThu(UUID publicId) {
        ApiSource nguon = sources.get(publicId);
        return chay(nguon, sources.thamSoHieuLuc(nguon), false);
    }

    /** Lượt polling theo lịch — ⭐ có rate-limit (quy tắc 17). */
    public KetQuaDongBo chayTheoLich(ApiSource nguon) {
        return chay(nguon, sources.thamSoHieuLuc(nguon), true);
    }

    private KetQuaDongBo chay(ApiSource nguon, ThamSoNguon thamSo, boolean apRateLimit) {
        Instant batDau = Instant.now();
        Instant khung = dauKhung(batDau, thamSo.khungNguon());
        BoiCanh bc = new BoiCanh(nguon, batDau, khung, poller.demDiemDoDangHoatDong(nguon.getId()));

        if (apRateLimit && daDuDuLieu(bc)) {
            SyncOutcome bo = SyncOutcome.boQuaVoiDuDuLieu(nguon.getId(), batDau, khung);
            return ketQua(bo, ghiSyncLog(bo), DoLuotGoi.CHUA_GOI, bc, BoDemChanDoan.RONG, List.of(), null);
        }

        String maSo = sources.maSoDeGoi(nguon);
        if (maSo == null) {
            // ⚠ Dừng TRƯỚC khi mở HTTP. Gọi bằng chuỗi rỗng cho ra `not.working` — trạng thái "chưa
            //   cấu hình mã số" khi ấy biến thành "mã số sai", và người vận hành đi tìm nhầm chỗ.
            //   ⛔ Cũng KHÔNG ghi hydro_raw_logs: không có lượt gọi nào thì không có response nào,
            //   và ràng buộc CHECK của bảng ấy cố ý không nhận THIEU_MA_SO.
            return hong(
                    bc,
                    null,
                    SyncFailureKind.THIEU_MA_SO,
                    "Nguồn chưa cấu hình mã số — đặt mã số rồi gọi lại",
                    DoLuotGoi.CHUA_GOI);
        }

        TelemetryAdapter adapter = adapters.cho(nguon.getAdapterType());
        TelemetryFetch fetch = adapter.goi(new TelemetryCall(nguon.getBaseUrl(), maSo, thamSo.timeout()));
        Long rawLogId = ghiRawLog(nguon, batDau, khung, fetch);

        if (!fetch.thanhCong()) {
            return hong(bc, rawLogId, fetch.failureKind(), fetch.failureDetail(), DoLuotGoi.cua(fetch));
        }

        TelemetryBatch me = adapter.boc(fetch.body());
        if (me.nguonBaoHong()) {
            // ⚠ Nhánh phòng thân, không phải nhánh chết: `Bhh40Adapter.goi()` đã bắt `not.working`
            //   và trả NOT_WORKING. Nhưng hợp đồng của `TelemetryAdapter` cho phép hai bước ấy là
            //   hai cài đặt độc lập, và một adapter tương lai có thể chỉ nhận ra ở bước bóc.
            return hong(
                    bc,
                    rawLogId,
                    SyncFailureKind.NOT_WORKING,
                    "Nguồn trả not.working — kiểm tra mã số, ⚠ kể cả dấu ';' ở cuối",
                    DoLuotGoi.cua(fetch));
        }
        if (me.soDo().isEmpty()) {
            // Quy tắc parse: HTTP 200 mà không bóc được dòng số đo nào ⇒ EMPTY_BODY — kiểu hỏng
            // nguy hiểm nhất, vì mọi chỉ số kỹ thuật đều bình thường. `soDongRac` đi kèm trong lý do
            // để người đọc phân biệt "nguồn im" với "nguồn đổi định dạng".
            return hong(
                    bc,
                    rawLogId,
                    SyncFailureKind.EMPTY_BODY,
                    "HTTP 200 nhưng không bóc được số đo nào (%d dòng rác) — nhiều khả năng nguồn đổi định dạng"
                            .formatted(me.soDongRac()),
                    DoLuotGoi.cua(fetch));
        }

        return ghiSoDo(bc, thamSo, rawLogId, fetch, me);
    }

    /**
     * ⭐⭐ Rate-limit — <b>quy tắc 17</b>, và điều kiện dừng là <b>ĐỦ TOÀN BỘ trạm</b>.
     *
     * <p>Ba nhánh phân biệt được, và nhánh giữa là chỗ dễ mất dữ liệu nhất:
     *
     * <ol>
     *   <li>đủ cả {@code n/n} → bỏ lượt gọi ({@link SyncStatus#SKIPPED_UP_TO_DATE});
     *   <li>⚠ <b>mới có bản ghi đầu tiên</b> {@code 1/n} → <b>VẪN GỌI</b>. Nguồn đẩy rải rác trong
     *       cửa sổ {@code x1:30 → x8:30}; dừng ở đây là bỏ lỡ mọi trạm lên muộn, <b>vĩnh viễn</b>;
     *   <li>chưa có gì {@code 0/n} → gọi.
     * </ol>
     *
     * <p>⚠ {@code dangHoatDong == 0} → <b>vẫn gọi</b>. "Không có trạm nào" làm điều kiện "đủ toàn
     * bộ" đúng một cách rỗng tuếch (luật 7), và hệ quả là poller im lặng vĩnh viễn — kể cả với 9 mã
     * chưa khai vẫn đang có số đo thật cần giữ lại.
     */
    private boolean daDuDuLieu(BoiCanh bc) {
        if (bc.dangHoatDong() == 0) {
            return false;
        }
        return poller.demDiemDoDaCoTrongKhung(bc.nguon().getId(), bc.khung()) >= bc.dangHoatDong();
    }

    private KetQuaDongBo ghiSoDo(
            BoiCanh bc, ThamSoNguon thamSo, Long rawLogId, TelemetryFetch fetch, TelemetryBatch me) {
        ApiSource nguon = bc.nguon();

        long loaiChiSo = poller.idLoaiChiSo(MA_LOAI_CHI_SO)
                // ⚠ Lỗi của TA, không phải của nguồn — nên nó ném, ⛔ không thành một SyncFailureKind.
                //   Ghi mực nước vào một id đoán bừa là sai số liệu câm; dừng lớn tiếng là đúng. Bản
                //   nguyên văn đã nằm an toàn trong hydro_raw_logs nên không mất gì.
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy loại chỉ số '" + MA_LOAI_CHI_SO
                        + "' — ai đó đã xoá mềm nó. ⛔ Lượt ingest dừng: đoán một loại chỉ số khác là "
                        + "ghi mực nước vào cột lượng mưa."));
        Map<String, DiemDoDich> anhXa = poller.dichTheoMaApi(loaiChiSo);

        List<ReadingRow> soDo = new ArrayList<>(me.soDo().size());
        List<UnmappedRow> maLa = new ArrayList<>();
        List<String> thieuLoaiChiSo = new ArrayList<>();
        List<String> khacNguon = new ArrayList<>();

        for (TelemetryReading r : me.soDo()) {
            DiemDoDich dich = anhXa.get(r.apiCode());
            if (dich == null) {
                // ⛔⛔ Quy tắc parse 5: KHÔNG tự tạo điểm đo từ mã lạ. Ta không biết mã ấy là trạm
                //    nào, ở đâu, thuộc công trình gì — đó là G8, thuộc Công ty. ⭐ Nhưng cũng không
                //    vứt số đo đi: nguồn không có API lịch sử, nên bỏ hai tháng là mất hai tháng của
                //    9 trạm ấy NGAY CẢ SAU KHI Công ty khai báo.
                maLa.add(new UnmappedRow(
                        r.apiCode(), nguon.getId(), r.measuredAt(), r.giaTriTho(), r.donViTho(), rawLogId));
                continue;
            }
            if (!dich.daKhaiLoaiChiSo()) {
                thieuLoaiChiSo.add(dich.code());
            }
            if (!Objects.equals(dich.apiSourceId(), nguon.getId())) {
                khacNguon.add(dich.code());
            }
            // ⚠ Ghi cho CẢ điểm đo đang ngừng (`active = false`). `active` là quyết định trưng bày,
            //   ⛔ không phải cái van của đường ingest: số đo vừa về là một sự thật đã xảy ra, và
            //   `StationDisplayStatus.suyRa` vẫn trả NGUNG nên không màn hình nào hiện nhầm.
            soDo.add(new ReadingRow(
                    dich.stationId(),
                    loaiChiSo,
                    r.measuredAt(),
                    r.giaTri(),
                    ReadingQuality.HOP_LE,
                    ReadingSource.API,
                    rawLogId));
        }

        canhBaoDanhMuc(nguon, thieuLoaiChiSo, khacNguon);

        int[] dem = giaoDichGhi.execute(tx -> {
            int ghiMoi = timeSeries.writeReadings(soDo);
            // ⚠ upsert cho MỌI dòng nhận được, kể cả dòng vừa bị ON CONFLICT bỏ qua: `last_seen_at`
            //   trả lời "trạm còn phát tín hiệu không", và một trạm gửi lại đúng giá trị cũ VẪN
            //   đang phát. Chỉ upsert cho dòng ghi mới là tự dựng ra một trạm mất tín hiệu giả.
            timeSeries.upsertLatest(soDo);
            return new int[] {ghiMoi, timeSeries.writeUnmapped(maLa)};
        });
        int ghiMoi = dem == null ? 0 : dem[0];
        int maLaGhi = dem == null ? 0 : dem[1];

        boolean thieu = me.thieuDuLieu(bc.dangHoatDong());
        SyncOutcome ket = new SyncOutcome(
                nguon.getId(),
                bc.batDau(),
                Instant.now(),
                bc.khung(),
                thieu ? SyncStatus.PARTIAL : SyncStatus.SUCCESS,
                null,
                null,
                me.soDo().size(),
                ghiMoi,
                soDo.size() - ghiMoi,
                maLaGhi,
                rawLogId);
        health.ghiNhanThanhCong(nguon, bc.batDau());

        if (thieu) {
            log.warn(
                    "⚠ Nguồn {} chỉ trả {} số đo cho {} điểm đo đang hoạt động (dưới 50% — quy tắc parse 9). "
                            + "Khung {}, thường là nguồn đang đẩy dở dữ liệu; kéo dài nhiều khung mới là sự cố.",
                    nguon.getCode(), me.soDo().size(), bc.dangHoatDong(), bc.khung());
        }
        log.info(
                "Đồng bộ {} khung {}: nhận {} · ghi mới {} · trùng {} · mã lạ {} · rác {} · {} ms (tham số {})",
                nguon.getCode(),
                bc.khung(),
                me.soDo().size(),
                ghiMoi,
                soDo.size() - ghiMoi,
                maLaGhi,
                me.soDongRac(),
                fetch.durationMs(),
                thamSo.coTuyChinh() ? "riêng" : "chung");

        return ketQua(
                ket,
                ghiSyncLog(ket),
                DoLuotGoi.cua(fetch),
                bc,
                new BoDemChanDoan(me.soDongRac(), me.soDongTrung(), thieuLoaiChiSo.size(), khacNguon.size()),
                maChuaKhai(maLa),
                mocDoGanNhat(me));
    }

    /**
     * Ba số đo <b>của lượt truyền</b>, gom lại để chữ ký hàm còn đọc được.
     *
     * @param soByte số byte thân <b>đã lưu</b> (sau khi che mã số), ⛔ không phải số byte trên dây
     */
    private record DoLuotGoi(Integer httpStatus, int durationMs, int soByte) {

        static final DoLuotGoi CHUA_GOI = new DoLuotGoi(null, 0, 0);

        static DoLuotGoi cua(TelemetryFetch fetch) {
            return new DoLuotGoi(fetch.httpStatus(), fetch.durationMs(), TelemetryIngestService.soByte(fetch));
        }
    }

    /**
     * Bối cảnh chung của một lượt đồng bộ — bốn giá trị mọi nhánh thoát đều cần.
     *
     * <p>⚠ {@code dangHoatDong} chốt <b>một lần</b> ở đầu lượt: nó là mẫu số của cả rate-limit lẫn
     * quy tắc parse 9, và hai phép tính ấy phải dùng đúng một con số — đọc lại giữa chừng là mở cửa
     * cho hai kết luận khác nhau trong cùng một lượt (§10.13, cột dẫn xuất trộn hai nguồn).
     */
    private record BoiCanh(ApiSource nguon, Instant batDau, Instant khung, int dangHoatDong) {}

    /**
     * Bốn con số chỉ có nghĩa khi <b>đứng riêng</b> — gộp lại là xoá mất chính thông tin cần
     * (§10.68-B). Gom thành một bản ghi thay vì bốn tham số để {@link #ketQua} đọc được ở chỗ gọi.
     *
     * @param soDongRac nguồn đổi định dạng → việc của lập trình viên
     * @param soDongTrung nguồn tự lặp trong một response → không ai phải làm gì
     * @param soThieuLoaiChiSo danh mục thiếu một dòng → việc của quản trị viên danh mục
     * @param soKhacNguon hồ sơ điểm đo khai nhầm nguồn → việc của người cấu hình nguồn
     */
    private record BoDemChanDoan(int soDongRac, int soDongTrung, int soThieuLoaiChiSo, int soKhacNguon) {

        static final BoDemChanDoan RONG = new BoDemChanDoan(0, 0, 0, 0);
    }

    /**
     * ⚠ Hai bất thường <b>danh mục</b>, cả hai đều KHÔNG chặn lượt ghi.
     *
     * <p>Bảng {@code station_measurement_types} nuôi biểu mẫu và báo cáo; nó ⛔ không phải cái van
     * của đường ingest. Bỏ một số đo có thật vì bảng nối thiếu một dòng là mất dữ liệu vĩnh viễn
     * (quy tắc 18) để bảo vệ một danh mục con người sửa được trong mười giây. ⇒ ghi WARN <b>kèm mã
     * điểm đo</b>, để người đọc log biết chính xác phải tích ô nào ở màn hình nào.
     */
    private static void canhBaoDanhMuc(ApiSource nguon, List<String> thieuLoaiChiSo, List<String> khacNguon) {
        if (!thieuLoaiChiSo.isEmpty()) {
            log.warn(
                    "⚠ {} điểm đo nhận số đo '{}' nhưng CHƯA tích loại chỉ số ấy trong hồ sơ: {}. "
                            + "Số đo vẫn được ghi; vào Thuỷ văn › Điểm đo tích ô 'Loại chỉ số' để báo cáo khớp.",
                    thieuLoaiChiSo.size(),
                    MA_LOAI_CHI_SO,
                    thieuLoaiChiSo);
        }
        if (!khacNguon.isEmpty()) {
            log.warn(
                    "⚠ Nguồn {} trả mã của {} điểm đo mà hồ sơ khai thuộc NGUỒN KHÁC: {}. Số đo vẫn được "
                            + "ghi vì ux_stations_api_code là duy nhất toàn hệ — nhưng hãy đối chiếu lại cấu hình.",
                    nguon.getCode(),
                    khacNguon.size(),
                    khacNguon);
        }
    }

    private KetQuaDongBo hong(BoiCanh bc, Long rawLogId, SyncFailureKind kieu, String lyDo, DoLuotGoi luotGoi) {
        health.ghiNhanThatBai(bc.nguon(), bc.batDau(), kieu, lyDo);
        SyncOutcome ket = new SyncOutcome(
                bc.nguon().getId(),
                bc.batDau(),
                Instant.now(),
                bc.khung(),
                SyncStatus.FAILED,
                kieu,
                lyDo,
                0,
                0,
                0,
                0,
                rawLogId);
        return ketQua(ket, ghiSyncLog(ket), luotGoi, bc, BoDemChanDoan.RONG, List.of(), null);
    }

    /**
     * Ghi dòng {@code sync_logs} — ⛔ ở <b>mọi</b> nhánh thoát, kể cả nhánh bỏ qua và nhánh hỏng.
     *
     * <p>⚠ Nuốt ngoại lệ ở đây là cố ý và có giới hạn: một lượt polling <b>đã lấy được dữ liệu</b>
     * không được coi là hỏng chỉ vì không ghi nổi dòng nhật ký của chính nó. Nhưng ⛔ không nuốt im
     * lặng — {@code syncLogId} trả về {@code null} và log ghi ERROR.
     */
    private Long ghiSyncLog(SyncOutcome ket) {
        try {
            return giaoDichRieng.execute(tx -> syncLogs.write(ket));
        } catch (RuntimeException e) {
            log.error("⛔ Không ghi được sync_logs (nguồn id={}, trạng thái {})", ket.apiSourceId(), ket.status(), e);
            return null;
        }
    }

    /**
     * Ghi nguyên văn xuống {@code hydro_raw_logs} — <b>quy tắc parse 1</b>.
     *
     * <p>{@code REQUIRES_NEW}: lượt ghi này phải sống sót độc lập với mọi thứ diễn ra sau nó. Đó là
     * bản sao <b>duy nhất</b> của response.
     *
     * <p>⚠ Nuốt ngoại lệ có giới hạn, cùng lý lẽ với {@link #ghiSyncLog}: {@code rawLogId} trả về
     * {@code null} và log ghi ERROR, vì "đã gọi mà không lưu được" là một sự cố CSDL đáng biết ngay.
     */
    private Long ghiRawLog(ApiSource nguon, Instant mocGoi, Instant khung, TelemetryFetch fetch) {
        RawFetch ban = new RawFetch(
                nguon.getId(),
                mocGoi,
                khung,
                fetch.httpStatus(),
                fetch.durationMs(),
                fetch.body(),
                fetch.failureKind(),
                fetch.failureDetail());
        try {
            return giaoDichRieng.execute(tx -> rawLogs.write(ban));
        } catch (RuntimeException e) {
            log.error("⛔ Không ghi được hydro_raw_logs cho nguồn {} — nguyên văn response đã MẤT", nguon.getCode(), e);
            return null;
        }
    }

    /**
     * Mốc đầu khung mà lượt gọi này nhắm tới — {@code floor(now / khung)}.
     *
     * <p>⚠ Chia trên <b>epoch giây</b>, ⛔ không trên giờ địa phương: khung 10 phút chia hết epoch nên
     * hai cách cho cùng kết quả hôm nay, nhưng một khung 90 phút thì không — và ta không muốn kết quả
     * phụ thuộc múi giờ của JVM (đúng cái bẫy hai-đồng-hồ đã cắn {@code HydroRetentionHandler}).
     */
    static Instant dauKhung(Instant moc, Duration khung) {
        long giay = khung.getSeconds();
        return giay <= 0 ? moc : Instant.ofEpochSecond(Math.floorDiv(moc.getEpochSecond(), giay) * giay);
    }

    /**
     * Mã nguồn trả về mà chưa ai khai — <b>quy tắc parse 5</b>, phần liệt kê ra màn hình.
     *
     * <p>⛔⛔ Chỉ <i>liệt kê</i>, tuyệt đối không tự tạo điểm đo. Bản suy đoán trước đó từ biểu tổng
     * hợp đã <b>sai 1/4 mã</b> ({@code F01705} đoán là Cống Phủ Lý, thực tế là Vân Đình hạ lưu).
     */
    private static List<String> maChuaKhai(List<UnmappedRow> maLa) {
        return maLa.stream()
                .map(UnmappedRow::apiCode)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new))
                .stream()
                .limit(TRAN_MA_LA_LIET_KE)
                .toList();
    }

    private static Instant mocDoGanNhat(TelemetryBatch me) {
        return me.soDo().stream()
                .map(TelemetryReading::measuredAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static int soByte(TelemetryFetch fetch) {
        return fetch.body() == null ? 0 : fetch.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static KetQuaDongBo ketQua(
            SyncOutcome ket,
            Long syncLogId,
            DoLuotGoi luotGoi,
            BoiCanh bc,
            BoDemChanDoan dem,
            List<String> maChuaKhai,
            Instant mocDoGanNhat) {
        return new KetQuaDongBo(
                ket.status(),
                luotGoi.httpStatus(),
                luotGoi.durationMs(),
                ket.failureKind(),
                ket.failureDetail(),
                luotGoi.soByte(),
                ket.frameStart(),
                ket.receivedCount(),
                ket.writtenCount(),
                ket.skippedCount(),
                ket.unmappedCount(),
                dem.soDongRac(),
                dem.soDongTrung(),
                maChuaKhai,
                bc.dangHoatDong(),
                dem.soThieuLoaiChiSo(),
                dem.soKhacNguon(),
                mocDoGanNhat,
                ket.rawLogId(),
                syncLogId);
    }
}
