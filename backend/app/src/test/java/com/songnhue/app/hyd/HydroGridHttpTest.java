package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.hydro.application.HydroGridService;

/**
 * Bảng lưới mực nước — WS-43, đi qua <b>HTTP</b> bằng trình duyệt <b>vô danh</b>.
 *
 * <h2>⛔ Vì sao qua HTTP chứ ⛔ không gọi thẳng service (luật 5)</h2>
 *
 * <p>Ba cam kết của bảng này ⛔ không nằm trong service: nó <b>công khai</b> (đi qua
 * {@code PermissionInterceptor} và {@code @PublicEndpoint}), nó đi qua <b>envelope</b> (§11.20 vừa
 * cho thấy envelope có thể lặng lẽ biến mất mà mã vẫn biên dịch), và nó phải chịu được lượt gọi
 * mang {@code Origin} của trình duyệt thật (luật 6 — CORS từng chặn toàn bộ giao diện suốt
 * WS-8→WS-20 mà {@code curl} ⛔ không thấy).
 *
 * <h2>⚠ Vế chống tập rỗng ở khắp nơi trong bài này</h2>
 *
 * <p>§11.19 đã trả giá đúng hình dạng này: một bộ canh thiếu vế <i>"mốc phải ĐANG CÓ"</i> sẽ xanh
 * trong <b>đúng</b> tình huống nó sinh ra để bắt. Mỗi khẳng định phủ định ở dưới đều đi kèm một
 * khẳng định khẳng định-dương trước nó.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HydroGridHttpTest extends IntegrationTestBase {

    private static final String DUONG_DAN = "/api/v1/public/hydro/luoi-muc-nuoc";

    /** ⚠ Trình duyệt thật LUÔN gửi `Origin`. `curl` thì không — và nó đi lọt qua đúng bức tường CORS. */
    private static final String NGUON_GOC = "http://localhost:3000";

    private static final String MA_TL = "T43-CAP-TL";
    private static final String MA_HL = "T43-CAP-HL";
    private static final String MA_LE = "T43-LE-TL";
    private static final String MA_CT = "T43CT";

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    private long idTl;
    private long idHl;
    private long idLe;
    private long idLoaiChiSo;

    @BeforeAll
    void dungDuLieu() {
        long idNguon = jdbc.queryForObject("SELECT id FROM api_sources ORDER BY id LIMIT 1", Long.class);
        idLoaiChiSo = jdbc.queryForObject("SELECT id FROM measurement_types WHERE code = 'MUC_NUOC'", Long.class);

        // Một công trình ĐỦ CẶP thượng lưu + hạ lưu → phải sinh ra dòng "Chênh lệch".
        taoDiemDo(MA_TL, "F93001", idNguon, "THUONG_LUU", MA_CT, "Cống kiểm thử T43");
        taoDiemDo(MA_HL, "F93002", idNguon, "HA_LUU", MA_CT, "Cống kiểm thử T43");
        // Một công trình chỉ có MỘT vế → ⛔ KHÔNG được có dòng "Chênh lệch".
        taoDiemDo(MA_LE, "F93003", idNguon, "THUONG_LUU", MA_CT + "LE", "Cống lẻ T43");

        idTl = id(MA_TL);
        idHl = id(MA_HL);
        idLe = id(MA_LE);

        Instant mocMoi = mocChan(Instant.now());
        Instant mocTruoc = mocMoi.minus(Duration.ofMinutes(10));

        // Mốc mới nhất: cả hai vế có số → chênh lệch tính được.
        ghi(idTl, mocMoi, "2.320", "HOP_LE");
        ghi(idHl, mocMoi, "1.570", "HOP_LE");
        // Mốc trước đó: CHỈ có thượng lưu → chênh lệch phải TRỐNG, ⛔ không phải 0.
        ghi(idTl, mocTruoc, "2.310", "HOP_LE");
        // Điểm lẻ mang một số NGHI_NGO → phải ra dây KÈM SỐ và KÈM NHÃN (ngoại lệ có tên).
        ghi(idLe, mocMoi, "9.990", "NGHI_NGO");
    }

    @AfterAll
    void donDep() {
        for (long id : new long[] {idTl, idHl, idLe}) {
            jdbc.update("DELETE FROM hydro_readings WHERE station_id = ?", id);
            jdbc.update("DELETE FROM hydro_latest WHERE station_id = ?", id);
            jdbc.update("DELETE FROM station_measurement_types WHERE station_id = ?", id);
            jdbc.update("DELETE FROM stations WHERE id = ?", id);
        }
    }

    // =========================================================================

    @Test
    @DisplayName("⭐⭐ DOD3.1 — khách vô danh nhận 200, và lưới có công trình ĐỦ CẶP kèm dòng Chênh lệch")
    void anonymousBrowserGetsGridWithComputedDifferenceRow() {
        String than = goi("?cheDo=PHUT&soCot=3");

        assertThat(than)
                .as("⚠ Vế chống tập rỗng: lưới rỗng thì mọi khẳng định dưới đây xanh mà ⛔ không đo gì")
                .contains("\"tuyenSong\"")
                .contains("Cống kiểm thử T43");

        assertThat(than)
                .as("§6.1.2 — công trình có đủ thượng lưu + hạ lưu PHẢI có dòng Chênh lệch")
                .contains("\"Thượng lưu\"")
                .contains("\"Hạ lưu\"")
                .contains("\"Chênh lệch\"");

        assertThat(than)
                .as("Quy tắc 3 — chênh lệch tính ở BE: 2.320 − 1.570 = 0.750, ⛔ không phải việc của FE")
                .contains("0.750");

        assertThat(than)
                .as("Dòng Chênh lệch phải mang nhãn TINH để §6.1.2 in nghiêng và ⛔ không tô màu ngưỡng")
                .contains("\"TINH\"");
    }

    @Test
    @DisplayName("⭐⭐ DOD3.2 — thiếu MỘT vế thì chênh lệch TRỐNG kèm lý do, ⛔ không phải 0")
    void differenceIsAbsentNotZeroWhenOneSideMissing() {
        String than = goi("?cheDo=PHUT&soCot=3");

        assertThat(than)
                .as("⚠ Vế chống tập rỗng: mốc chỉ-có-thượng-lưu phải thật sự nằm trong cửa sổ")
                .contains("2.310");

        assertThat(than)
                .as("spec §3.2 — ⛔ không suy diễn, ⛔ không gán 0 khi thiếu một vế")
                .contains("Thiếu số đo ở một trong hai phía");

        assertThat(than)
                .as("⛔ Một ô chênh lệch bằng 0.000 ở mốc thiếu vế là lời khẳng định 'hai bên bằng nhau'")
                .doesNotContain("\"0.000\"");
    }

    @Test
    @DisplayName("⭐⭐ DOD3.3 — lưới CHỮ NHẬT: số ô mỗi dòng đúng bằng số cột, kể cả mốc ⛔ không có số")
    void gridIsRectangularSoMissingSlotsBecomeEmptyCellsNotSwallowedColumns() {
        String than = goi("?cheDo=PHUT&soCot=6");

        assertThat(than).as("⚠ Vế chống tập rỗng").contains("Cống kiểm thử T43");

        int soMoc = demChuoi(than, "\"moc\"");
        assertThat(soMoc).as("Khối `moc` phải có mặt để FE dựng tiêu đề cột").isEqualTo(1);

        assertThat(than)
                .as("⛔⛔ T43.13 — mốc ⛔ không có số phải thành Ô TRỐNG có lý do, ⛔ không bị NUỐT khỏi trục")
                .contains("Không có dữ liệu tại mốc này");

        // 6 cột được yêu cầu ⇒ chính xác 6 mốc ra dây. Lưới dựng TRƯỚC dữ liệu, nên con số này
        // ⛔ không phụ thuộc vào việc có bao nhiêu số đo trong khung.
        assertThat(demChuoi(than.substring(than.indexOf("\"moc\""), than.indexOf("\"tuyenSong\"")), "Z\""))
                .as("6 cột yêu cầu ⇒ 6 mốc; lệch nghĩa là trục thời gian đang dựng TỪ dữ liệu")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("⭐ Ô NGHI_NGO ra dây KÈM SỐ và KÈM NHÃN — lọc nó đi là biến ô nghi ngờ thành ô trống")
    void suspectCellsAreLabelledNotFiltered() {
        String than = goi("?cheDo=PHUT&soCot=3");

        assertThat(than)
                .as("⚠ Vế chống tập rỗng: điểm đo mang số NGHI_NGO phải có trong lưới")
                .contains("Cống lẻ T43");

        assertThat(than)
                .as("spec §6.1.2 — ô SUSPECT tô vàng kèm ⚠, nên nó phải ra dây CÙNG giá trị lẫn nhãn")
                .contains("9.990")
                .contains("NGHI_NGO");
    }

    @Test
    @DisplayName("⭐ Công trình chỉ có MỘT chỉ tiêu thì ⛔ KHÔNG có dòng Chênh lệch (spec §6.1.2)")
    void singleIndicatorStructureHasNoDifferenceRow() {
        String than = goi("?cheDo=PHUT&soCot=3");
        int batDau = than.indexOf("Cống lẻ T43");
        assertThat(batDau).as("⚠ Vế chống tập rỗng").isGreaterThan(0);

        int ketThuc = than.indexOf("\"maCongTrinh\"", batDau + 1);
        String khoi = ketThuc > 0 ? than.substring(batDau, ketThuc) : than.substring(batDau);
        assertThat(khoi)
                .as("Công trình một vế ⛔ không được có dòng tự tính — nó ⛔ không có gì để trừ")
                .doesNotContain("Chênh lệch");
    }

    @Test
    @DisplayName("⭐ Khối `meta` của §10 luôn ra dây — kể cả để FE chọn giữa ba trạng thái của §8")
    void metaBlockIsAlwaysPresent() {
        String than = goi("?cheDo=PHUT&soCot=3");
        assertThat(than).contains("\"meta\"").contains("\"trangThaiNguon\"").contains("\"lanLayCuoi\"");
        assertThat(than)
                .as("Đơn vị phải ra dây — spec §10 đòi `unit` trong meta")
                .contains("\"donVi\":\"m\"");
    }

    // =========================================================================
    // §5.3 — tô màu ngưỡng. DOD3.8 đòi ĐÚNG CẢ HAI phía: tập rỗng và tập có ngưỡng.
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ DOD3.8/a — `alert_levels` RỖNG thì ⛔ KHÔNG ô nào bị tô, và bảng vẫn dựng bình thường")
    void withNoThresholdsNoCellIsColoured() {
        // ⚠ Đây là trạng thái THẬT hôm nay: `alert_levels` cố ý 0 hàng cho tới khi Công ty đưa bộ
        //   mức (G9-a). Nhánh này là nhánh mà MỌI ô đang đi qua — nó phải đúng trước đã (quy tắc 7).
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_levels WHERE deleted_at IS NULL", Integer.class))
                .as("⚠ Vế chống tập rỗng ĐẢO: bài này chỉ có nghĩa khi bảng ngưỡng thật sự rỗng")
                .isZero();

        String than = goi("?cheDo=PHUT&soCot=3");
        assertThat(than).as("⚠ Vế chống tập rỗng").contains("Cống kiểm thử T43");
        assertThat(than)
                .as("§5.3 — điểm đo chưa khai ngưỡng thì ⛔ không tô màu, ⛔ không mượn ngưỡng điểm khác")
                .contains("\"khoaMauCanhBao\":null");
        assertThat(than).doesNotContain("alert-level-");
    }

    @Test
    @DisplayName("⭐⭐ DOD3.8/b — có ngưỡng thì ô vượt bậc nào mang màu bậc ấy; dòng Chênh lệch ⛔ KHÔNG tô")
    void cellsCarryTheHighestThresholdBandTheyExceed() {
        long idMuc1 = taoMucCanhBao("T43-BD1", "Báo động I", "alert-level-1", 901);
        long idMuc2 = taoMucCanhBao("T43-BD2", "Báo động II", "alert-level-2", 902);
        // Thượng lưu đo 2.320 ⇒ vượt 2.000 (BĐ1) nhưng CHƯA tới 3.000 (BĐ2).
        taoNguong(idTl, idMuc1, "2.000");
        taoNguong(idTl, idMuc2, "3.000");
        try {
            String than = goi("?cheDo=PHUT&soCot=3");

            assertThat(than).as("⚠ Vế chống tập rỗng").contains("2.320");
            assertThat(than)
                    .as("2.320 vượt 2.000 ⇒ mang màu BĐ1")
                    .contains("alert-level-1")
                    .contains("Báo động I");
            assertThat(than)
                    .as("⛔ 2.320 CHƯA tới 3.000 ⇒ ⛔ không được leo lên BĐ2 — bậc CAO NHẤT ĐÃ VƯỢT, "
                            + "⛔ không phải bậc gần nhất")
                    .doesNotContain("alert-level-2");

            // Chênh lệch = 2.320 − 1.570 = 0.750, dưới mọi ngưỡng — nhưng điều cần khẳng định
            // ⛔ không phải "nó dưới ngưỡng" mà là "nó KHÔNG BAO GIỜ được xét ngưỡng" (§6.1.2).
            int batDau = than.indexOf("Chênh lệch");
            assertThat(batDau)
                    .as("⚠ Vế chống tập rỗng: dòng Chênh lệch phải có mặt")
                    .isGreaterThan(0);
            String khoiChenh = than.substring(batDau, Math.min(batDau + 400, than.length()));
            assertThat(khoiChenh)
                    .as("§6.1.2 — ngưỡng đo ĐỘ CAO mực nước; một hiệu số ⛔ không nằm trên thang ấy")
                    .doesNotContain("alert-level-");
        } finally {
            jdbc.update("DELETE FROM alert_rules WHERE station_id = ?", idTl);
            jdbc.update("DELETE FROM alert_levels WHERE id IN (?, ?)", idMuc1, idMuc2);
        }
    }

    private long taoMucCanhBao(String ma, String ten, String khoaMau, int mucDo) {
        jdbc.update(
                """
                INSERT INTO alert_levels (code, name, color_token, severity_rank, active, created_at)
                VALUES (?, ?, ?, ?, TRUE, now())
                """,
                ma,
                ten,
                khoaMau,
                mucDo);
        return jdbc.queryForObject("SELECT id FROM alert_levels WHERE code = ?", Long.class, ma);
    }

    private void taoNguong(long idDiemDo, long idMuc, String nguong) {
        jdbc.update(
                """
                INSERT INTO alert_rules (station_id, measurement_type_id, alert_level_id,
                                         condition_type, threshold_value, active, created_at)
                VALUES (?, ?, ?, 'GT', CAST(? AS NUMERIC), TRUE, now())
                """,
                idDiemDo,
                idLoaiChiSo,
                idMuc,
                nguong);
    }

    // =========================================================================

    /**
     * Ba trạng thái nguồn — kiểm bằng <b>hàm thuần</b>, ⛔ không chạm CSDL.
     *
     * <p>Dựng nhánh {@code DOWN} qua CSDL đòi xoá sạch {@code hydro_latest}, một bảng dùng chung
     * của nhiều lớp kiểm — và triệu chứng sẽ là <i>"bài kiểm đỏ theo thứ tự chạy"</i>, đúng thứ
     * {@code make ci-order} sinh ra để bắt (§11.19).
     */
    @Nested
    @DisplayName("DOD3.4 — source_status phân biệt được CẢ BA trạng thái")
    class TrangThaiNguon {

        private final Instant bayGio = Instant.parse("2026-09-09T10:00:00Z");
        private final Duration han = Duration.ofMinutes(30);

        @Test
        @DisplayName("Chưa từng có số đo nào ⇒ DOWN (§8.1 'chưa đấu nối')")
        void neverAnyReadingIsDown() {
            assertThat(HydroGridService.MetaLuoi.trangThai(null, bayGio, han)).isEqualTo("DOWN");
        }

        @Test
        @DisplayName("Mốc đo còn trong hạn ⇒ OK")
        void freshReadingIsOk() {
            assertThat(HydroGridService.MetaLuoi.trangThai(bayGio.minus(Duration.ofMinutes(5)), bayGio, han))
                    .isEqualTo("OK");
        }

        @Test
        @DisplayName("⭐ Mốc đo quá hạn ⇒ DEGRADED — §8.2 hiện dải 'đang hiển thị số liệu lúc …'")
        void staleReadingIsDegraded() {
            assertThat(HydroGridService.MetaLuoi.trangThai(bayGio.minus(Duration.ofMinutes(31)), bayGio, han))
                    .isEqualTo("DEGRADED");
        }

        @Test
        @DisplayName("⛔ DOWN mà vẫn có mốc đo là HAI khẳng định trái nhau — hàm dựng phải ném")
        void downWithAMeasurementInstantIsContradictory() {
            assertThatThrownBy(() -> new HydroGridService.MetaLuoi(bayGio, bayGio, "DOWN", "m", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DOWN");
        }

        @Test
        @DisplayName("⛔ Trạng thái ngoài ba giá trị đã biết phải ném, ⛔ không lặng lẽ ra dây")
        void unknownStatusIsRejected() {
            assertThatThrownBy(() -> new HydroGridService.MetaLuoi(bayGio, bayGio, "MAYBE", "m", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** Lưới phải chữ nhật — ép ở hàm dựng vì đó là nơi DUY NHẤT biết cả số mốc lẫn từng dòng. */
    @Nested
    @DisplayName("⛔ Lưới lệch cột phải ném ở HÀM DỰNG")
    class LuoiChuNhat {

        @Test
        @DisplayName("⛔ Một dòng thiếu ô ⇒ ném — bảng lệch cột là số đúng nằm dưới nhãn giờ SAI")
        void raggedRowIsRejected() {
            var oCoSo = new HydroGridService.OLuoi(new java.math.BigDecimal("1.00"), "HOP_LE", null, null, null);
            var dongThieu = new HydroGridService.DongChiSo("Thượng lưu", HydroGridService.LoaiDong.DO, List.of(oCoSo));
            var ct = new HydroGridService.CongTrinh("X", "Công trình X", null, false, List.of(dongThieu));

            assertThatThrownBy(() -> new HydroGridService.LuoiMucNuoc(
                            null,
                            List.of(Instant.parse("2026-09-09T10:00:00Z"), Instant.parse("2026-09-09T09:50:00Z")),
                            List.of(new HydroGridService.NhomTuyenSong("Sông Nhuệ", List.of(ct))),
                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lệch cột");
        }

        @Test
        @DisplayName("⛔ Ô vừa có số vừa có lý do trống ⇒ ném (quy tắc 16)")
        void cellCannotHaveBothValueAndReason() {
            assertThatThrownBy(() -> new HydroGridService.OLuoi(
                            new java.math.BigDecimal("1.00"), "HOP_LE", "vừa có vừa không", null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("⛔ Ô TRỐNG mà mang màu cảnh báo ⇒ ném — tô đỏ một ô ⛔ không đo được gì")
        void emptyCellCannotCarryAnAlertColour() {
            assertThatThrownBy(
                            () -> new HydroGridService.OLuoi(null, null, "chưa có số", "alert-level-3", "Báo động III"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("màu cảnh báo");
        }

        @Test
        @DisplayName("⛔ Có màu mà ⛔ không có tên mức ⇒ ném — hai nửa của một nhãn (luật 27)")
        void colourAndLevelNameGoTogether() {
            assertThatThrownBy(() -> new HydroGridService.OLuoi(
                            new java.math.BigDecimal("9.00"), "HOP_LE", null, "alert-level-3", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cặp");
        }

        @Test
        @DisplayName("⛔ Ô ⛔ không số và ⛔ không lý do ⇒ ném — nó sẽ bị đọc thành 'bằng không'")
        void emptyCellMustCarryAReason() {
            assertThatThrownBy(() -> new HydroGridService.OLuoi(null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================

    private String goi(String truyVan) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.ORIGIN, NGUON_GOC);
        ResponseEntity<String> ra =
                http.exchange(DUONG_DAN + truyVan, HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(ra.getStatusCode())
                .as("⛔ Bảng lưới CÔNG KHAI — khách vô danh phải nhận 200 (quyết định Q4, huỷ CR-08)")
                .isEqualTo(HttpStatus.OK);
        return ra.getBody();
    }

    private void taoDiemDo(String ma, String maApi, long idNguon, String vaiTro, String maCt, String tenCt) {
        jdbc.update(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role,
                                      structure_code, structure_name, river_name, active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'Sông kiểm thử T43', TRUE, now())
                """,
                ma,
                tenCt + " — " + vaiTro,
                maApi,
                idNguon,
                vaiTro,
                maCt,
                tenCt);
        jdbc.update(
                """
                INSERT INTO station_measurement_types (station_id, measurement_type_id)
                SELECT s.id, m.id FROM stations s, measurement_types m
                 WHERE s.code = ? AND m.code = 'MUC_NUOC'
                """,
                ma);
    }

    /**
     * ⚠ {@code quality_reason} là <b>bắt buộc</b> với bản ghi {@code NGHI_NGO} —
     * {@code ck_hydro_readings_nghi_ngo_co_ly_do} chặn ở tầng CSDL, và bản đầu của bài này đã bị
     * chính nó bác. Quy tắc 16 ép ở lược đồ, ⛔ không chỉ ở lời dặn: một ô nghi ngờ ⛔ không nói
     * được vì sao nó nghi ngờ thì tooltip của §6.1.2 ⛔ không có gì để hiện.
     */
    private void ghi(long idDiemDo, Instant moc, String giaTri, String chatLuong) {
        jdbc.update(
                """
                INSERT INTO hydro_readings (
                    measured_at, station_id, measurement_type_id, reading_value,
                    quality, quality_reason, source)
                VALUES (?, ?, ?, CAST(? AS NUMERIC), ?, ?, 'API')
                """,
                Timestamp.from(moc),
                idDiemDo,
                idLoaiChiSo,
                giaTri,
                chatLuong,
                "NGHI_NGO".equals(chatLuong) ? "Dữ liệu dựng cho bài kiểm WS-43" : null);
    }

    private long id(String ma) {
        return jdbc.queryForObject("SELECT id FROM stations WHERE code = ?", Long.class, ma);
    }

    /** Cắt xuống bội của 10 phút — cùng phép cắt mà lưới dùng, nếu không số đo rơi ngoài mọi ô. */
    private static Instant mocChan(Instant t) {
        return Instant.ofEpochSecond(Math.floorDiv(t.getEpochSecond(), 600L) * 600L);
    }

    private static int demChuoi(String trong, String tim) {
        int n = 0;
        for (int i = trong.indexOf(tim); i >= 0; i = trong.indexOf(tim, i + tim.length())) {
            n++;
        }
        return n;
    }
}
