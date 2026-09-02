package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.ReadingRow;
import com.songnhue.hydro.domain.ReadingSource;
import com.songnhue.hydro.domain.SyncOutcome;
import com.songnhue.hydro.domain.SyncStatus;
import com.songnhue.hydro.domain.UnmappedRow;
import com.songnhue.hydro.infra.HydroTimeSeriesWriter;
import com.songnhue.hydro.infra.PollerRepository;
import com.songnhue.hydro.infra.SyncLogWriter;

/**
 * ⭐⭐ Ba câu SQL ghi của MOD-03 chạy trên <b>CSDL THẬT</b> — WS-29 viết chúng, WS-31 mới là lượt đầu
 * tiên chúng được chạy.
 *
 * <h2>Vì sao bài này là bài đắt nhất của WS-31</h2>
 *
 * <p>Đo 02/09/2026: {@code HydroTimeSeriesWriter} và {@code SyncLogWriter} ra đời ngày 01/09 và tới
 * hôm nay <b>không một lời gọi production nào</b> — chúng chỉ được nhắc tên trong javadoc. Luật 7:
 * <i>một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai</i>. Và ba thứ dưới đây <b>về nguyên
 * tắc</b> không đo được bằng bài đơn vị:
 *
 * <ul>
 *   <li>{@code ON CONFLICT DO NOTHING} có thật sự trả về <b>số dòng ghi mới</b> không — cả bộ đếm
 *       {@code written}/{@code skipped} của {@code sync_logs} treo trên con số ấy;
 *   <li>hai luật "không lùi" của {@code hydro_latest} có đúng như câu {@code CASE WHEN} viết không;
 *   <li>bảy ràng buộc {@code CHECK} và ba chỉ mục duy nhất có nhận đúng thứ ta ghi không.
 * </ul>
 *
 * <p>Một mock ở đúng chỗ mã chạm CSDL là <b>chưa kiểm gì cả</b> (luật 4 — {@code BackupServiceTest}
 * mock {@code PostgresToolRunner}, và {@code pg_dump} chưa từng chạy suốt 4 ngày).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HydroIngestWriteTest extends IntegrationTestBase {

    private static final Instant KHUNG = Instant.parse("2026-09-02T03:20:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HydroTimeSeriesWriter timeSeries;

    @Autowired
    private SyncLogWriter syncLogs;

    @Autowired
    private PollerRepository poller;

    private long idNguon;
    private long idLoaiChiSo;
    private long idTram;
    private String maApi;

    @BeforeEach
    void dungDuLieu() {
        String maNguon = "IW-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(java.util.Locale.ROOT);
        idNguon = jdbc.queryForObject(
                """
                INSERT INTO api_sources (code, name, adapter_type, base_url, status, created_at)
                VALUES (?, 'Nguồn kiểm thử ghi', 'MOCK', 'http://nguon-gia.invalid/', 'HOAT_DONG', now())
                RETURNING id
                """,
                Long.class,
                maNguon);
        idLoaiChiSo = poller.idLoaiChiSo("MUC_NUOC").orElseThrow();
        // ⚠ `ck_stations_api_code_format` đòi đúng `^F[0-9]{5}$`, và `ux_stations_api_code` là duy
        //   nhất TOÀN HỆ. Sinh mã bằng đồng hồ là mời một lượt đỏ ngẫu nhiên trong CI — hỏi CSDL mã
        //   kế tiếp còn rẻ hơn, và nó tất định. Dải `F9xxxx` nằm ngoài 19 mã seed (đều là `F0`/`F1`/`F2`).
        maApi = jdbc.queryForObject(
                """
                SELECT 'F' || (COALESCE(MAX(CAST(SUBSTRING(api_code FROM 2) AS INTEGER)), 90000) + 1)::text
                  FROM stations WHERE api_code ~ '^F9[0-9]{4}$'
                """,
                String.class);
        idTram = jdbc.queryForObject(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, created_at)
                VALUES (?, 'Trạm kiểm thử ghi', ?, ?, 'THUONG_LUU', now())
                RETURNING id
                """,
                Long.class,
                "IW-TRAM-" + maApi,
                maApi,
                idNguon);
    }

    /**
     * ⚠⚠ <b>Dọn sạch mọi hàng bài này tạo ra — bắt buộc, không phải lịch sự.</b>
     *
     * <p>Bộ kiểm tích hợp dùng <b>chung một CSDL</b>, và {@code HydroCatalogueSeedTest} khẳng định
     * bảng G8b có <b>đúng 19 điểm đo</b>. Bản đầu của lớp này để lại 10 trạm và làm <b>5 bài kiểm ở
     * MỘT LỚP KHÁC đỏ</b> — triệu chứng ("phải seed đúng 19 điểm đo, đang có 29") không hề chỉ về
     * phía nguyên nhân. Đây đúng loại lỗi khó lần nhất, và nó tự lộ ra ngay lượt {@code make ci-local}
     * đầu tiên.
     *
     * <p>⛔ {@code hydro_raw_logs} ⛔ không dọn được và cũng không cần: vai trò {@code songnhue_app}
     * không có {@code DELETE} trên bảng ấy (T29.2, append-only), và bài này không ghi vào đó.
     */
    @AfterEach
    void donSach() {
        jdbc.update("DELETE FROM hydro_readings WHERE station_id = ?", idTram);
        jdbc.update("DELETE FROM hydro_latest WHERE station_id = ?", idTram);
        jdbc.update("DELETE FROM hydro_unmapped_readings WHERE api_source_id = ?", idNguon);
        jdbc.update("DELETE FROM sync_logs WHERE api_source_id = ?", idNguon);
        jdbc.update("DELETE FROM station_measurement_types WHERE station_id = ?", idTram);
        jdbc.update("DELETE FROM stations WHERE id = ?", idTram);
        jdbc.update("DELETE FROM api_sources WHERE id = ?", idNguon);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE deleted_at IS NULL", Long.class))
                .as("⛔ Khẳng định NGAY TẠI CHỖ DỌN: một bản dọn dẹp hỏng trong im lặng làm "
                        + "HydroCatalogueSeedTest đỏ ở một lớp khác, với một thông điệp chẳng liên quan "
                        + "gì tới thứ đang kiểm")
                .isEqualTo(19L);
    }

    private ReadingRow dong(Instant moc, String giaTri, ReadingQuality chatLuong) {
        return new ReadingRow(idTram, idLoaiChiSo, moc, new BigDecimal(giaTri), chatLuong, ReadingSource.API, null);
    }

    private Map<String, Object> latest() {
        return jdbc.queryForMap(
                "SELECT * FROM hydro_latest WHERE station_id = ? AND measurement_type_id = ?", idTram, idLoaiChiSo);
    }

    @Test
    @DisplayName("⭐⭐ ON CONFLICT DO NOTHING trả về ĐÚNG số dòng ghi mới — cả hai bộ đếm sync_logs treo trên con số này")
    void onConflictTraVeSoDongGhiMoi() {
        List<ReadingRow> me = List.of(dong(KHUNG, "4.930", ReadingQuality.HOP_LE));

        assertThat(timeSeries.writeReadings(me)).isEqualTo(1);
        assertThat(timeSeries.writeReadings(me))
                .as("poll 2' trên nguồn 10' ⇒ 4/5 lượt trả dữ liệu TRÙNG. Nếu con số này không phân biệt "
                        + "được 'ghi mới' với 'đã có' thì một poller ghi 0 dòng suốt ba ngày trông y hệt "
                        + "một poller khoẻ mạnh — và ba ngày ấy mất vĩnh viễn")
                .isZero();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM hydro_readings WHERE station_id = ?", Long.class, idTram))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Một câu INSERT nhiều dòng có cả trùng lẫn mới ⇒ đếm đúng phần mới")
    void meVuaCoTrungVuaCoMoi() {
        timeSeries.writeReadings(List.of(dong(KHUNG, "4.930", ReadingQuality.HOP_LE)));

        int ghiMoi = timeSeries.writeReadings(List.of(
                dong(KHUNG, "4.930", ReadingQuality.HOP_LE),
                dong(KHUNG.plus(Duration.ofMinutes(10)), "4.940", ReadingQuality.HOP_LE)));

        assertThat(ghiMoi)
                .as("⛔ jdbc.update() trên một câu INSERT nhiều dòng trả TỔNG số dòng ghi được — đây là "
                        + "lý do lớp ấy cố ý không dùng batchUpdate (driver được phép trả −2)")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐⭐ hydro_latest: last_seen_at tiến theo MỌI bản ghi, valid_* chỉ theo bản HỢP LỆ")
    void haiVeCuaLatestDocLapNhau() {
        Instant sau = KHUNG.plus(Duration.ofMinutes(10));

        timeSeries.upsertLatest(List.of(dong(KHUNG, "4.930", ReadingQuality.HOP_LE)));
        assertThat(latest().get("valid_value")).isEqualTo(new BigDecimal("4.930"));

        // Bản ghi MỚI HƠN nhưng NGHI NGỜ: mốc nhìn thấy phải tiến, còn giá trị hợp lệ phải ĐỨNG YÊN.
        timeSeries.upsertLatest(List.of(dong(sau, "99.000", ReadingQuality.NGHI_NGO)));

        Map<String, Object> l = latest();
        assertThat(((Timestamp) l.get("last_seen_at")).toInstant())
                .as("một trạm chỉ trả số nghi ngờ VẪN đang phát tín hiệu — cột này trả lời 'trạm còn "
                        + "sống không', ⛔ không trả lời 'mực nước bao nhiêu'")
                .isEqualTo(sau);
        assertThat(l.get("last_quality")).isEqualTo("NGHI_NGO");
        assertThat(l.get("valid_value"))
                .as("⭐⭐ Đây là chỗ quy tắc 14 được ép ở TẦNG DỮ LIỆU: widget cổng và lớp GIS đọc "
                        + "valid_value nên KHÔNG CÓ CÁCH NÀO hiện nhầm một số đang bị nghi ngờ (luật 12)")
                .isEqualTo(new BigDecimal("4.930"));
        assertThat(((Timestamp) l.get("valid_measured_at")).toInstant()).isEqualTo(KHUNG);
    }

    @Test
    @DisplayName("⛔ Cả hai vế của hydro_latest KHÔNG LÙI — một lượt ingest muộn không kéo mốc hiện tại lùi lại")
    void latestKhongLui() {
        Instant sau = KHUNG.plus(Duration.ofMinutes(10));
        timeSeries.upsertLatest(List.of(dong(sau, "5.000", ReadingQuality.HOP_LE)));

        timeSeries.upsertLatest(List.of(dong(KHUNG, "4.930", ReadingQuality.HOP_LE)));

        Map<String, Object> l = latest();
        assertThat(((Timestamp) l.get("last_seen_at")).toInstant()).isEqualTo(sau);
        assertThat(l.get("valid_value"))
                .as("thử lại sau lỗi mạng, hoặc nhập tay bù dữ liệu quá khứ, ⛔ không được kéo mực nước "
                        + "hiện tại lùi về giá trị cũ")
                .isEqualTo(new BigDecimal("5.000"));
    }

    @Test
    @DisplayName("Mã chưa khai ghi được, và trùng (mã, mốc) thì bỏ qua — giữ NGUYÊN VĂN, ⛔ không quy đổi")
    void maChuaKhaiGhiVaChongTrung() {
        // ⚠ Dùng lại mã của trạm vừa tạo: nó đã tất định-duy nhất, và `hydro_unmapped_readings`
        //   không có FK sang `stations` nên đây vẫn là "một mã chưa khai" theo đúng nghĩa của bảng.
        String maLa = maApi;
        List<UnmappedRow> me = List.of(new UnmappedRow(maLa, idNguon, KHUNG, new BigDecimal("198"), "cm", null));

        assertThat(timeSeries.writeUnmapped(me)).isEqualTo(1);
        assertThat(timeSeries.writeUnmapped(me)).isZero();

        Map<String, Object> dong =
                jdbc.queryForMap("SELECT raw_value, raw_unit FROM hydro_unmapped_readings WHERE api_code = ?", maLa);
        assertThat(dong.get("raw_value"))
                .as("chưa biết mã ấy là loại chỉ số gì thì cũng chưa biết quy đổi về đâu — quy đổi bây "
                        + "giờ là ĐOÁN")
                .isEqualTo(new BigDecimal("198.000"));
        assertThat(dong.get("raw_unit")).isEqualTo("cm");
    }

    @Test
    @DisplayName("⭐ sync_logs ghi được ở CẢ BỐN trạng thái — bốn kết cục phải phân biệt được (§10.68-B)")
    void syncLogGhiDuocCaBonTrangThai() {
        Instant batDau = Instant.now();
        List<SyncStatus> daGhi = new java.util.ArrayList<>();

        daGhi.add(luuVaDoc(
                new SyncOutcome(idNguon, batDau, batDau, KHUNG, SyncStatus.SUCCESS, null, null, 28, 28, 0, 9, null)));
        daGhi.add(luuVaDoc(
                new SyncOutcome(idNguon, batDau, batDau, KHUNG, SyncStatus.PARTIAL, null, null, 3, 3, 0, 0, null)));
        daGhi.add(luuVaDoc(new SyncOutcome(
                idNguon,
                batDau,
                batDau,
                KHUNG,
                SyncStatus.FAILED,
                com.songnhue.hydro.domain.SyncFailureKind.THIEU_MA_SO,
                "chưa cấu hình",
                0,
                0,
                0,
                0,
                null)));
        daGhi.add(luuVaDoc(SyncOutcome.boQuaVoiDuDuLieu(idNguon, batDau, KHUNG)));

        // ⭐ Khẳng định về SỐ LƯỢNG: enum có 4 giá trị và cả 4 phải đi qua ràng buộc CHECK được. Thêm
        //   một trạng thái mà quên nới CHECK là một lượt INSERT vỡ giữa lúc ingest (luật 29).
        assertThat(daGhi).hasSize(SyncStatus.values().length).containsExactlyInAnyOrder(SyncStatus.values());
    }

    private SyncStatus luuVaDoc(SyncOutcome ket) {
        long id = syncLogs.write(ket);
        return SyncStatus.valueOf(jdbc.queryForObject("SELECT status FROM sync_logs WHERE id = ?", String.class, id));
    }

    @Test
    @DisplayName("⭐⭐ THIEU_MA_SO ghi được vào sync_logs — đây là giá trị thứ NĂM mà hydro_raw_logs cố ý không nhận")
    void thieuMaSoChiSongOSyncLogs() {
        Instant batDau = Instant.now();
        long id = syncLogs.write(new SyncOutcome(
                idNguon,
                batDau,
                batDau,
                KHUNG,
                SyncStatus.FAILED,
                com.songnhue.hydro.domain.SyncFailureKind.THIEU_MA_SO,
                "Nguồn chưa cấu hình mã số",
                0,
                0,
                0,
                0,
                null));

        assertThat(jdbc.queryForObject("SELECT failure_kind FROM sync_logs WHERE id = ?", String.class, id))
                .as("ck_sync_logs_failure_kind nhận 5 giá trị, ck_hydro_raw_logs_failure_kind nhận 4 — "
                        + "chênh lệch ấy là một QUYẾT ĐỊNH, ⛔ không phải một chỗ quên")
                .isEqualTo("THIEU_MA_SO");
    }

    @Test
    @DisplayName("⭐⭐ Rate-limit đọc CSDL thật: một hàng hydro_latest trong khung ⇒ điểm đo được tính là đã đủ")
    void rateLimitDocDuocTuCsdl() {
        assertThat(poller.demDiemDoDangHoatDong(idNguon)).isEqualTo(1);
        assertThat(poller.demDiemDoDaCoTrongKhung(idNguon, KHUNG)).isZero();

        timeSeries.upsertLatest(List.of(dong(KHUNG, "4.930", ReadingQuality.HOP_LE)));

        assertThat(poller.demDiemDoDaCoTrongKhung(idNguon, KHUNG)).isEqualTo(1);
        assertThat(poller.demDiemDoDaCoTrongKhung(idNguon, KHUNG.plus(Duration.ofMinutes(10))))
                .as("khung SAU thì bản ghi cũ không còn tính — nếu không thì poller im lặng vĩnh viễn "
                        + "sau lượt gọi thành công đầu tiên")
                .isZero();
    }

    @Test
    @DisplayName("⭐ Ánh xạ mã → điểm đo đọc đúng cờ 'đã khai loại chỉ số', ⛔ không chịu bộ lọc phạm vi đơn vị")
    void anhXaMaApiDocDuocCoDaKhai() {
        assertThat(poller.dichTheoMaApi(idLoaiChiSo).get(maApi))
                .as("điểm đo vừa tạo CHƯA có dòng station_measurement_types nào")
                .isNotNull()
                .satisfies(d -> {
                    assertThat(d.stationId()).isEqualTo(idTram);
                    assertThat(d.daKhaiLoaiChiSo()).isFalse();
                    assertThat(d.apiSourceId()).isEqualTo(idNguon);
                });

        jdbc.update(
                "INSERT INTO station_measurement_types (station_id, measurement_type_id) VALUES (?, ?)",
                idTram,
                idLoaiChiSo);

        assertThat(poller.dichTheoMaApi(idLoaiChiSo).get(maApi).daKhaiLoaiChiSo())
                .as("⚠ vế PHÂN BIỆT — không có nó thì cờ này có thể luôn false và bài trên vẫn xanh (luật 9)")
                .isTrue();
    }

    @Test
    @DisplayName("⭐ Ảnh chụp tín hiệu: điểm đo CHƯA có bản ghi nào vẫn đi ra khỏi câu LEFT JOIN")
    void anhChupTinHieuGiuCaTramChuaCoDuLieu() {
        assertThat(poller.tinHieuDiemDo())
                .as("⛔ đổi LEFT JOIN thành JOIN là làm cả nhóm CHUA_CO_DU_LIEU biến mất khỏi màn hình "
                        + "— và đó chính là nhóm cần nhìn nhất trong tuần đầu vận hành")
                .anySatisfy(t -> {
                    assertThat(t.stationId()).isEqualTo(idTram);
                    assertThat(t.lastSeenAt()).isNull();
                });
    }
}
