package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Lớp GIS điểm đo <b>đi qua HTTP</b> — T35.1 · T35.2.
 *
 * <h2>⭐⭐ Vì sao bài này TỰ CHÈN TOẠ ĐỘ</h2>
 *
 * <p>Đo ngày 04/09/2026: <b>19/19</b> điểm đo seed có {@code latitude}/{@code longitude} NULL, vì
 * toạ độ thuộc <b>G8</b> và Công ty chưa cấp. Nghĩa là trên dữ liệu thật hôm nay, nhánh <i>"vẽ một
 * marker"</i> ⛔ <b>chưa từng chạy một lần nào</b> — và một bài kiểm chỉ đọc dữ liệu seed sẽ xanh
 * trọn vẹn mà ⛔ không chứng minh được gì (luật 7: <i>một cơ chế chưa ai đi qua thì chưa biết nó
 * đúng hay sai</i>).
 *
 * <p>⇒ Bài này chèn toạ độ cho một điểm đo <b>của riêng nó</b>, đi hết nhánh marker, rồi dọn sạch.
 * ⛔ Nó ⛔ không seed toạ độ cho 19 điểm đo thật — đó là dữ liệu bịa, và {@code CLAUDE.md} cấm.
 *
 * <h2>⚠ Ba trạng thái phải phân biệt được</h2>
 *
 * <p>{@code CHUA_CO_DU_LIEU} (chưa từng có bản ghi) · {@code MAT_TIN_HIEU} (im lặng quá ngưỡng) ·
 * {@code NGUNG} (người vận hành tắt). Gộp bất kỳ hai cái nào là biến ngày triển khai đầu tiên thành
 * 19 cảnh báo giả, hoặc giấu mất một trạm đã chết.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StationMapHttpTest extends IntegrationTestBase {

    private static final String DUONG_DAN = "/api/v1/hyd/stations/map-points";

    /** ⚠ Trong dải {@code F98xxx} — ⛔ không đụng mã thật nào của nguồn bhh40. */
    private static final String MA = "T35M-001";

    private static final String MA_API = "F98001";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien kyThuat;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        // TECHNICIAN có `hyd:station:view` — dùng vai trò thật, ⛔ không dùng ADMIN.
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t35_map", "TECHNICIAN"));
    }

    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM alert_events WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA);
        jdbc.update("DELETE FROM alert_rules WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA);
        jdbc.update("DELETE FROM alert_levels WHERE code = 'T35M-MUC'");
        jdbc.update("DELETE FROM hydro_latest WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA);
        jdbc.update("DELETE FROM stations WHERE code = ?", MA);
    }

    // === T35.2 — hiện trạng thật: 19 điểm đo, 0 chấm =========================

    /**
     * ⭐ Đây là <b>giá trị dùng được ngay</b> của lớp GIS hôm nay: nó nói ra đích xác Công ty còn nợ
     * bao nhiêu toạ độ (G8), thay vì hiện một bản đồ trống mà ⛔ không giải thích gì.
     */
    @Test
    @DisplayName("⭐ T35.2 — 19 điểm đo seed nằm ở 'chưa số hoá vị trí', bản đồ 0 chấm, và đó là ĐÚNG")
    void seededStationsAreAllAwaitingCoordinates() {
        String than = doc();

        assertThat(soLan(than, "\"publicId\""))
                .as("⚠ vế chống tập rỗng: phải có ĐỦ 19 điểm đo seed, nếu không mọi khẳng định dưới "
                        + "đây đều xanh trên một danh sách rỗng")
                .isGreaterThanOrEqualTo(19);

        String chuaSoHoa = phanChuaSoHoa(than);
        assertThat(soLan(chuaSoHoa, "\"publicId\""))
                .as("toạ độ là G8 — cả 19 điểm đo seed đều chưa có")
                .isGreaterThanOrEqualTo(19);

        assertThat(phanDiemDo(than))
                .as("⛔ 0 chấm là trạng thái ĐÚNG hôm nay — ⛔ không suy toạ độ từ công trình liên "
                        + "kết: thượng lưu và hạ lưu của cùng một cống là hai vị trí khác nhau")
                .doesNotContain("\"latitude\"");
    }

    // === T35.1 — nhánh marker, chạy thật ====================================

    @Test
    @DisplayName("⭐⭐ Có toạ độ ⇒ ra marker, toạ độ đi ra dây dưới dạng CHUỖI (§10.32)")
    void aStationWithCoordinatesBecomesAMarker() {
        taoDiemDo(true);

        String diemDo = phanDiemDo(doc());

        assertThat(diemDo).contains(MA);
        assertThat(diemDo)
                .as("⛔ BigDecimal ra dây dạng chuỗi — `21.023456` mà thành số JSON là mở đường cho "
                        + "đúng lỗi §10.32 (một lượt 'bỏ hết dấu chấm' biến vĩ độ thành 21023456)")
                .contains("\"latitude\":\"20.980000\"")
                .contains("\"longitude\":\"105.780000\"");
    }

    @Test
    @DisplayName("⭐ Bốn trạng thái phân biệt được: chưa có dữ liệu / mất tín hiệu / hoạt động / ngừng")
    void theFourDisplayStatesAreDistinguishable() {
        long id = taoDiemDo(true);

        assertThat(phanDiemDo(doc()))
                .as("chưa có bản ghi nào ⇒ CHUA_CO_DU_LIEU, ⛔ KHÔNG phải MAT_TIN_HIEU — một điểm đo "
                        + "vừa khai chưa tới lượt polling đầu tiên ⛔ không phải một trạm hỏng")
                .contains("\"trangThai\":\"CHUA_CO_DU_LIEU\"");

        ghiHydroLatest(id, Duration.ofDays(10), "HOP_LE");
        assertThat(phanDiemDo(doc())).as("im lặng 10 ngày ⇒ MAT_TIN_HIEU").contains("\"trangThai\":\"MAT_TIN_HIEU\"");

        ghiHydroLatest(id, Duration.ofMinutes(1), "HOP_LE");
        assertThat(phanDiemDo(doc())).as("vừa có số ⇒ HOAT_DONG").contains("\"trangThai\":\"HOAT_DONG\"");

        jdbc.update("UPDATE stations SET active = FALSE WHERE id = ?", id);
        assertThat(phanDiemDo(doc()))
                .as("⭐ NGUNG là quyết định của CON NGƯỜI và nó luôn thắng — kể cả khi trạm vẫn đang "
                        + "phát số bình thường")
                .contains("\"trangThai\":\"NGUNG\"");
    }

    @Test
    @DisplayName("⛔ NGHI_NGO đánh dấu bằng cờ RIÊNG, ⛔ không đổi màu chấm — một kênh, một thông tin")
    void suspectQualityIsItsOwnFlag() {
        long id = taoDiemDo(true);
        ghiHydroLatest(id, Duration.ofMinutes(1), "NGHI_NGO");

        String diemDo = phanDiemDo(doc());

        assertThat(diemDo).contains("\"nghiNgo\":true");
        assertThat(diemDo)
                .as("⭐ trạm gửi số nghi ngờ VẪN ĐANG PHÁT — báo nó mất tín hiệu là huy động sai người")
                .contains("\"trangThai\":\"HOAT_DONG\"");
    }

    /**
     * ⭐ Nối với T35.14: màu marker đến từ {@code color_token} của mức, ⛔ không từ một bảng ánh xạ
     * thứ hai ở FE.
     */
    @Test
    @DisplayName("⭐ Cảnh báo đang mở đưa khoá màu của mức NẶNG NHẤT ra marker")
    void anOpenAlertCarriesItsLevelColourToken() {
        long id = taoDiemDo(true);
        ghiHydroLatest(id, Duration.ofMinutes(1), "HOP_LE");

        assertThat(phanDiemDo(doc()))
                .as("chưa có cảnh báo ⇒ null, ⛔ khác hẳn 'có cảnh báo mức nhẹ'")
                .contains("\"khoaMauCanhBao\":null");

        taoCanhBaoDangMo(id);

        assertThat(phanDiemDo(doc()))
                .contains("\"khoaMauCanhBao\":\"alert-level-3\"")
                .contains("\"tenMucCanhBao\":\"Mức kiểm thử bản đồ\"");
    }

    // === Helper ==============================================================

    private String doc() {
        ResponseEntity<String> ra = phienHttp.get(kyThuat, DUONG_DAN);
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.OK);
        return ra.getBody();
    }

    /** Cắt phần {@code "diemDo":[…]} — ⛔ để hai danh sách không lẫn vào nhau lúc khẳng định. */
    private static String phanDiemDo(String than) {
        int tu = than.indexOf("\"diemDo\":[");
        int den = than.indexOf("\"chuaSoHoaViTri\":[");
        assertThat(tu).as("phản hồi phải có khoá `diemDo`").isGreaterThanOrEqualTo(0);
        assertThat(den).as("phản hồi phải có khoá `chuaSoHoaViTri`").isGreaterThan(tu);
        return than.substring(tu, den);
    }

    private static String phanChuaSoHoa(String than) {
        int tu = than.indexOf("\"chuaSoHoaViTri\":[");
        assertThat(tu).isGreaterThanOrEqualTo(0);
        return than.substring(tu);
    }

    private static int soLan(String trong, String mau) {
        int n = 0;
        int i = trong.indexOf(mau);
        while (i >= 0) {
            n++;
            i = trong.indexOf(mau, i + mau.length());
        }
        return n;
    }

    private long taoDiemDo(boolean coToaDo) {
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        assertThat(idNguon).as("⚠ vế chống tập rỗng: phải có nguồn seed").isNotNull();
        return jdbc.queryForObject(
                """
                INSERT INTO stations (
                    code, name, api_code, api_source_id, position_role, active, latitude, longitude, created_at)
                VALUES (?, 'Điểm đo kiểm thử bản đồ', ?, ?, 'MN_SONG', TRUE, ?, ?, now())
                RETURNING id
                """,
                Long.class,
                MA,
                MA_API,
                idNguon,
                coToaDo ? new java.math.BigDecimal("20.980000") : null,
                coToaDo ? new java.math.BigDecimal("105.780000") : null);
    }

    private void ghiHydroLatest(long idDiemDo, Duration truocDay, String chatLuong) {
        Long idLoai = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        Instant moc = Instant.now().minus(truocDay);
        jdbc.update("DELETE FROM hydro_latest WHERE station_id = ?", idDiemDo);
        jdbc.update(
                """
                INSERT INTO hydro_latest (
                    station_id, measurement_type_id, last_seen_at, last_quality, last_source,
                    valid_measured_at, valid_value)
                VALUES (?, ?, ?, ?, 'API', ?, 2.400)
                """,
                idDiemDo,
                idLoai,
                java.sql.Timestamp.from(moc),
                chatLuong,
                java.sql.Timestamp.from(moc));
    }

    private void taoCanhBaoDangMo(long idDiemDo) {
        Long idLoai = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        Long idMuc = jdbc.queryForObject(
                """
                INSERT INTO alert_levels (code, name, color_token, severity_rank, created_at)
                VALUES ('T35M-MUC', 'Mức kiểm thử bản đồ', 'alert-level-3', 888, now())
                RETURNING id
                """,
                Long.class);
        Long idQuyTac = jdbc.queryForObject(
                """
                INSERT INTO alert_rules (
                    station_id, measurement_type_id, alert_level_id, condition_type,
                    threshold_value, delay_minutes, created_at)
                VALUES (?, ?, ?, 'GT', 1.000, 0, now())
                RETURNING id
                """,
                Long.class,
                idDiemDo,
                idLoai,
                idMuc);
        Instant batDau = Instant.now().minus(Duration.ofHours(1));
        jdbc.update(
                """
                INSERT INTO alert_events (
                    rule_id, station_id, measurement_type_id, alert_level_id, started_at, confirmed_at,
                    status, trigger_value, peak_value, peak_at, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'DANG_XAY_RA', 2.400, 2.400, ?, '2.400 > 1.000', now())
                """,
                idQuyTac,
                idDiemDo,
                idLoai,
                idMuc,
                java.sql.Timestamp.from(batDau),
                java.sql.Timestamp.from(batDau),
                java.sql.Timestamp.from(batDau));
    }
}
