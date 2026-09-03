package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * ⭐⭐ Máy cảnh báo ngưỡng <b>đi qua HTTP</b> — WS-33, và là lượt chạy <b>đầu tiên trong lịch sử dự
 * án</b> mà mắt xích 3 của {@code ConstructionStatusService.tinh()} có thể trả {@code true}.
 *
 * <h2>Vì sao bài này phải đi bằng HTTP chứ không gọi service</h2>
 *
 * <p>Luật 5, đã trả giá bốn lần. Gần nhất: {@code GET /hyd/stations} trả <b>500 suốt bốn ngày</b> từ
 * WS-28 và không bài kiểm nào thấy, vì tất cả đều gọi thẳng service — tức chạy <i>trong</i> giao
 * dịch của bài kiểm, nơi proxy lười vẫn đọc được. Ở đây rủi ro y hệt: {@code AlertRule} có <b>ba</b>
 * {@code @ManyToOne(LAZY)} và {@code spring.jpa.open-in-view = false}.
 *
 * <h2>⭐ Bài này đo <b>vòng khép kín</b>, ⛔ không đo từng mảnh</h2>
 *
 * <p>Đơn vị đếm đúng không phải "đã dựng bao nhiêu lớp" mà là <i>"nhập → lưu → nổ ra cái gì"</i>
 * (luật 27). Vòng ở đây: khai mức cảnh báo → khai ngưỡng → <b>nhập một số đo vượt ngưỡng qua đúng
 * endpoint người dùng bấm</b> → {@code alert_events} có dòng → công trình liên kết chuyển sang
 * {@code CANH_BAO}.
 *
 * <p>⛔ Số đo đi vào bằng {@code POST /hyd/so-do/nhap-tay}, ⛔ không bằng một câu {@code INSERT}
 * thẳng: câu INSERT bỏ qua đúng thứ đang được kiểm — cái móc gọi máy cảnh báo ở tầng application.
 *
 * <h2>⚠ Thứ tự bài kiểm ở đây là CỐ Ý — {@code @TestMethodOrder}</h2>
 *
 * <p>Bảy bài giữa lớp này đi theo <b>vòng đời của MỘT cảnh báo</b>: mở → xác nhận → đóng → đóng
 * lần hai. Đó ⛔ không phải bảy bài độc lập tình cờ dùng chung dữ liệu — nó là <i>một</i> khẳng
 * định dài, và hysteresis (thứ đang được kiểm) chỉ có nghĩa khi các lượt đánh giá nối tiếp nhau.
 *
 * <p>⛔ Thứ tự mặc định của JUnit 5 là <i>tất định nhưng cố ý khó đoán</i>. Dựa vào nó là dựa vào
 * một chi tiết cài đặt, và ngày nó đổi thì bộ này đỏ bằng một thông điệp ⛔ không chỉ về phía
 * nguyên nhân. Khai {@code @Order} là nói ra sự phụ thuộc thay vì để nó ngầm.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlertEngineHttpTest extends IntegrationTestBase {

    private static final String MA_DIEM_DO = "T33-DIEMDO";
    private static final String MA_API = "F97331";
    private static final String MA_DIEM_DO_TRE = "T33-DIEMDO-TRE";
    private static final String MA_API_TRE = "F97332";
    private static final String MA_CONG_TRINH = "T33-CT-01";

    /** ⚠ Mã mức cảnh báo của bài kiểm — ⛔ KHÔNG phải "BĐ I": danh mục thật là G9-a, chưa chốt. */
    private static final String MA_MUC = "T33-MUC-CAO";

    private static final String MA_MUC_TRE = "T33-MUC-TRE";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;

    /** TECHNICIAN mang đủ cả bốn quyền cần: threshold view/manage · alert view/handle. */
    private PhienHttp.Phien kyThuat;

    private long idDiemDo;
    private long idDiemDoTre;
    private long idCongTrinh;
    private long idLoaiChiSo;
    private String publicIdDiemDo;
    private String publicIdDiemDoTre;
    private String publicIdMuc;
    private String publicIdMucTre;
    private Instant mocGoc;

    @BeforeAll
    void dungDuLieu() {
        idLoaiChiSo = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        assertThat(idNguon)
                .as("⚠ Vế chống tập rỗng: không có nguồn seed nào thì điểm đo dưới đây không tạo được")
                .isNotNull();

        taoDiemDo(MA_DIEM_DO, MA_API, idNguon);
        taoDiemDo(MA_DIEM_DO_TRE, MA_API_TRE, idNguon);
        Map<String, Object> d1 = jdbc.queryForMap("SELECT id, public_id FROM stations WHERE code = ?", MA_DIEM_DO);
        idDiemDo = ((Number) d1.get("id")).longValue();
        publicIdDiemDo = d1.get("public_id").toString();
        Map<String, Object> d2 = jdbc.queryForMap("SELECT id, public_id FROM stations WHERE code = ?", MA_DIEM_DO_TRE);
        idDiemDoTre = ((Number) d2.get("id")).longValue();
        publicIdDiemDoTre = d2.get("public_id").toString();

        idCongTrinh = taoCongTrinh();
        // Liên kết điểm đo ↔ công trình (T28.19) — đây là đường mắt xích 3 đi qua.
        // ⚠ `construction_public_id` là NOT NULL và CỐ Ý dư thừa: nó là bản sao "đọc được" của khoá
        //   nội bộ, vì ⛔ không có khoá ngoại xuyên module để join lấy nó lúc hiển thị.
        jdbc.update(
                """
                INSERT INTO station_constructions (
                    station_id, construction_id, construction_public_id, role, is_primary, created_at)
                SELECT ?, c.id, c.public_id, 'MN_SONG', TRUE, now() FROM constructions c WHERE c.id = ?
                """,
                idDiemDo,
                idCongTrinh);

        mocGoc = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofHours(3));

        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t33_kythuat", "TECHNICIAN"));

        publicIdMuc = taoMuc(MA_MUC, "Mức kiểm thử — cao", "alert-level-1", 10);
        publicIdMucTre = taoMuc(MA_MUC_TRE, "Mức kiểm thử — có độ trễ", "alert-level-2", 20);
    }

    private void taoDiemDo(String ma, String maApi, long idNguon) {
        jdbc.update(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, created_at)
                VALUES (?, 'Điểm đo kiểm thử cảnh báo', ?, ?, 'MN_SONG', TRUE, now())
                """,
                ma,
                maApi,
                idNguon);
    }

    private long taoCongTrinh() {
        Long idDonVi = jdbc.queryForObject(
                "SELECT id FROM org_units WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        return jdbc.queryForObject(
                """
                INSERT INTO constructions (
                    code, name, construction_type, management_level, org_unit_id,
                    lifecycle_state, operational_status, created_at)
                VALUES (?, 'Công trình kiểm thử cảnh báo', 'CONG', 'CONG_TY', ?,
                        'DANG_HOAT_DONG', 'BINH_THUONG', now())
                RETURNING id
                """,
                Long.class,
                MA_CONG_TRINH,
                idDonVi);
    }

    /**
     * ⛔ Dọn <b>và khẳng định ngay tại chỗ dọn</b> — {@code HydroCatalogueSeedTest} khẳng định danh
     * mục có đúng 19 điểm đo, và một bản dọn hỏng trong im lặng làm bài ấy đỏ ở một lớp khác với
     * một thông điệp không hề chỉ về phía nguyên nhân.
     */
    @AfterAll
    void donSachSauCung() {
        jdbc.update("DELETE FROM alert_events WHERE station_id IN (?, ?)", idDiemDo, idDiemDoTre);
        jdbc.update("DELETE FROM alert_rules WHERE station_id IN (?, ?)", idDiemDo, idDiemDoTre);
        jdbc.update("DELETE FROM alert_levels WHERE code LIKE 'T33-%'");
        jdbc.update("DELETE FROM station_constructions WHERE station_id IN (?, ?)", idDiemDo, idDiemDoTre);
        jdbc.update("DELETE FROM constructions WHERE code = ?", MA_CONG_TRINH);
        for (long id : new long[] {idDiemDo, idDiemDoTre}) {
            jdbc.update("DELETE FROM hydro_readings WHERE station_id = ?", id);
            jdbc.update("DELETE FROM hydro_latest WHERE station_id = ?", id);
            jdbc.update("DELETE FROM stations WHERE id = ?", id);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE deleted_at IS NULL", Long.class))
                .as("⛔ Khẳng định NGAY TẠI CHỖ DỌN — xem javadoc")
                .isEqualTo(19L);
    }

    // =========================================================================
    // ⭐ Danh mục mức cảnh báo (T33.1)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("⛔⛔ Danh mục mức cảnh báo KHÔNG được seed sẵn — G9-a chưa chốt")
    void theAlertLevelCatalogueShipsEmpty() {
        Long seed = jdbc.queryForObject(
                "SELECT count(*) FROM alert_levels WHERE code NOT LIKE 'T33-%' AND deleted_at IS NULL", Long.class);

        assertThat(seed)
                .as(
                        """
                        ⛔ Migration đã seed mức cảnh báo. Bộ mức là G9-a — Công ty CHƯA chốt, nên mọi
                        con số đi kèm là số bịa, và số bịa sẽ đứng im trong CSDL sau khi số thật về
                        mà ⛔ KHÔNG lệnh nào báo sai. `CLAUDE.md`: cấm seed dữ liệu "cho đẹp demo".""")
                .isZero();
    }

    @Test
    @Order(2)
    @DisplayName("⛔ Mã màu hex bị TỪ CHỐI — colorToken phải là khoá design-tokens (nợ T25.23)")
    void aHexColourIsRejectedAsAColourToken() {
        ResponseEntity<String> ra = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/hyd/alert-levels",
                """
                {"code":"T33-HEX","name":"Sai màu","colorToken":"#d4380d","severityRank":90}
                """);

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ra.getBody())
                .as("⭐ Lỗi phải gắn TÊN TRƯỜNG, nếu không FE chỉ hiện một toast chung chung (F1)")
                .contains("colorToken");
    }

    @Test
    @Order(3)
    @DisplayName("⛔ Hai mức cùng severity_rank bị từ chối — 'mức nào nặng hơn' phải có câu trả lời")
    void twoLevelsCannotShareASeverityRank() {
        ResponseEntity<String> ra = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/hyd/alert-levels",
                """
                {"code":"T33-TRUNG","name":"Trùng hạng","colorToken":"alert-level-9","severityRank":10}
                """);

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // =========================================================================
    // ⭐⭐ Vòng khép kín: khai ngưỡng → nhập số đo → cảnh báo → trạng thái công trình
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("⭐⭐ VÒNG KHÉP KÍN — số đo vượt ngưỡng sinh cảnh báo VÀ lật công trình sang CANH_BAO")
    void aBreachingReadingRaisesAnAlertAndFlipsTheConstructionStatus() {
        khaiNguong(publicIdDiemDo, publicIdMuc, "GT", "5.000", null, 0);

        // ⛔ Trạng thái TRƯỚC — nếu không đo, một công trình vốn đã CANH_BAO sẵn sẽ làm bài này xanh
        //    mà không chứng minh gì (luật 9: khẳng định không phân biệt được hai trạng thái).
        assertThat(trangThaiCongTrinh()).isEqualTo("BINH_THUONG");

        Instant moc = mocGoc.plus(Duration.ofMinutes(10));
        assertThat(nhapTay(publicIdDiemDo, moc, "6.200").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> canhBao = jdbc.queryForMap(
                "SELECT status, confirmed_at, trigger_value, peak_value, reason FROM alert_events "
                        + "WHERE station_id = ? ORDER BY id DESC LIMIT 1",
                idDiemDo);

        assertThat(canhBao.get("status")).isEqualTo("DANG_XAY_RA");
        assertThat(canhBao.get("confirmed_at"))
                .as("⭐ delay_minutes = 0 ⇒ xác nhận NGAY, và thông báo đi trong cùng lượt")
                .isNotNull();
        assertThat(canhBao.get("reason").toString())
                .as("Lý do phải đọc được, ⛔ không phải ba cột số bắt người đọc tự dựng lại phép so")
                .contains("6.200")
                .contains("5.000");

        assertThat(trangThaiCongTrinh())
                .as(
                        """
                        ⭐⭐ Mắt xích 3 vừa chạy THẬT lần đầu trong lịch sử dự án.
                        Trước WS-33 nó là `return false` ghi cứng ở `DummyHydroAlertService:16`, và
                        `CANH_BAO` là một trạng thái ⛔ KHÔNG công trình nào chạm tới được — trong khi
                        chuỗi 6 mắt xích trông như đã phủ vì bài kiểm của nó MOCK cổng ấy (luật 7).""")
                .isEqualTo("CANH_BAO");
    }

    @Test
    @Order(5)
    @DisplayName("⭐ Giá trị về dưới ngưỡng ĐÓNG cảnh báo và trả công trình về BINH_THUONG")
    void aReadingBackInRangeClosesTheAlert() {
        Instant moc = mocGoc.plus(Duration.ofMinutes(20));
        assertThat(nhapTay(publicIdDiemDo, moc, "4.100").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> canhBao = jdbc.queryForMap(
                "SELECT status, ended_at, resolved_by, peak_value FROM alert_events "
                        + "WHERE station_id = ? ORDER BY id DESC LIMIT 1",
                idDiemDo);

        assertThat(canhBao.get("status")).isEqualTo("DA_XU_LY");
        assertThat(canhBao.get("ended_at")).isNotNull();
        assertThat(canhBao.get("resolved_by"))
                .as("⭐ NULL = MÁY tự đóng vì giá trị về dưới ngưỡng. Có id = người trực bấm. "
                        + "Hai chuyện khác nhau, và màn hình phải phân biệt được")
                .isNull();
        assertThat(((java.math.BigDecimal) canhBao.get("peak_value")).stripTrailingZeros())
                .as("Đỉnh giữ giá trị NẶNG NHẤT quan sát được, ⛔ không phải giá trị cuối cùng")
                .isEqualByComparingTo("6.2");

        assertThat(trangThaiCongTrinh()).isEqualTo("BINH_THUONG");
    }

    @Test
    @Order(6)
    @DisplayName("⛔⛔ Số đo NGHI_NGO ⛔ KHÔNG sinh cảnh báo — quy tắc 14, bẫy sai số liệu số một")
    void aSuspectReadingNeverRaisesAnAlert() {
        long truoc = demCanhBao(idDiemDo);

        // Chèn thẳng một dòng NGHI_NGO vượt ngưỡng rất xa rồi gọi lại đường đánh giá qua bước DUYỆT
        // — ⛔ không duyệt, chỉ để nó nằm đó. Máy cảnh báo ⛔ không được thấy nó.
        Instant moc = mocGoc.plus(Duration.ofMinutes(30));
        jdbc.update(
                """
                INSERT INTO hydro_readings (
                    measured_at, station_id, measurement_type_id, reading_value, quality, quality_reason, source)
                VALUES (?, ?, ?, 99.000, 'NGHI_NGO', 'Ngoài khoảng vật lý', 'API')
                """,
                Timestamp.from(moc),
                idDiemDo,
                idLoaiChiSo);

        assertThat(demCanhBao(idDiemDo))
                .as(
                        """
                        ⛔ Một cảm biến hỏng báo 99 m ⛔ KHÔNG được đánh thức Ban điều hành lúc 2 giờ
                        sáng. Quy tắc 14: dòng NGHI_NGO nằm trong bảng chính, nên mọi truy vấn
                        báo cáo/alert/tổng hợp phải lọc `quality = 'HOP_LE'`.""")
                .isEqualTo(truoc);
    }

    @Test
    @Order(7)
    @DisplayName("⭐⭐ delay_minutes: lượt vượt ĐẦU TIÊN chưa báo động — chưa ai nhận thông báo nào")
    void aBreachIsNotConfirmedUntilItHasHeldForDelayMinutes() {
        khaiNguong(publicIdDiemDoTre, publicIdMucTre, "GT", "5.000", null, 30);

        Instant dau = mocGoc.plus(Duration.ofMinutes(40));
        assertThat(nhapTay(publicIdDiemDoTre, dau, "7.000").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> sauLuotDau = canhBaoMoiNhat(idDiemDoTre);
        assertThat(sauLuotDau.get("status")).isEqualTo("DANG_XAY_RA");
        assertThat(sauLuotDau.get("confirmed_at"))
                .as("⚠ `DANG_XAY_RA` mà `confirmed_at` NULL nghĩa là ĐANG THEO DÕI — ⛔ chưa phải báo động")
                .isNull();

        // ⭐ Và mắt xích 3 ⛔ KHÔNG được đếm nó: một cú nhiễu 10 phút ⛔ không được lật trạng thái
        //   một công trình. Điểm đo này cố ý ⛔ không liên kết công trình nào, nên phép đo trực tiếp
        //   là câu truy vấn dưới đây.
        assertThat(demCanhBaoDaXacNhan(idDiemDoTre)).isZero();

        // Lượt thứ hai, đủ 30 phút sau, vẫn vượt ⇒ xác nhận.
        Instant sau = dau.plus(Duration.ofMinutes(35));
        assertThat(nhapTay(publicIdDiemDoTre, sau, "7.500").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> sauLuotHai = canhBaoMoiNhat(idDiemDoTre);
        assertThat(sauLuotHai.get("confirmed_at"))
                .as("⭐ Xác nhận bằng MỘT QUAN SÁT KHÁC vẫn còn vượt, ⛔ không bằng đồng hồ treo tường")
                .isNotNull();
        assertThat(demCanhBao(idDiemDoTre))
                .as("⛔ Vẫn đúng MỘT dòng — chỉ mục ux_alert_events_mot_cai_dang_mo là hysteresis")
                .isEqualTo(1L);
    }

    @Test
    @Order(8)
    @DisplayName("⭐ Hết vượt TRƯỚC khi xác nhận ⇒ FALSE_ALARM, ⛔ không phải 'đã xử lý'")
    void aBreachThatClearsBeforeConfirmationIsAFalseAlarm() {
        // Đóng dòng đang mở của bài trên bằng một giá trị trong ngưỡng.
        assertThat(nhapTay(publicIdDiemDoTre, mocGoc.plus(Duration.ofMinutes(90)), "1.000")
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(canhBaoMoiNhat(idDiemDoTre).get("status")).isEqualTo("DA_XU_LY");

        // Một cú nhiễu: vượt rồi hết ngay, chưa kịp 30 phút.
        assertThat(nhapTay(publicIdDiemDoTre, mocGoc.plus(Duration.ofMinutes(100)), "8.000")
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(nhapTay(publicIdDiemDoTre, mocGoc.plus(Duration.ofMinutes(110)), "1.200")
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> nhieu = canhBaoMoiNhat(idDiemDoTre);
        assertThat(nhieu.get("status"))
                .as(
                        """
                        ⭐ Ba trạng thái, ba nghĩa khác nhau — và đây là chỗ chúng phân biệt được:
                        cảnh báo này ⛔ CHƯA TỪNG được gửi cho ai, nên đóng nó thành `DA_XU_LY` là
                        nói dối trên màn hình lịch sử (ai đó đã xử lý gì?).""")
                .isEqualTo("FALSE_ALARM");
        assertThat(nhieu.get("confirmed_at")).isNull();
    }

    // =========================================================================
    // Ràng buộc cấu hình
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("⛔ Ngưỡng trùng (điểm đo × loại chỉ số × mức) → HYD-2009")
    void aDuplicateRuleIsRejected() {
        ResponseEntity<String> ra = goiKhaiNguong(publicIdDiemDo, publicIdMuc, "GT", "9.000", null, 0);

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ra.getBody()).contains("HYD-2009");
    }

    @Test
    @Order(10)
    @DisplayName("⛔ OUT_OF_RANGE thiếu cận trên bị từ chối — ba tầng cùng canh một luật")
    void anOutOfRangeRuleWithoutAnUpperBoundIsRejected() {
        ResponseEntity<String> ra = goiKhaiNguong(publicIdDiemDoTre, publicIdMuc, "OUT_OF_RANGE", "1.000", null, 0);

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ra.getBody())
                .as("⭐ Bất biến ép ở HÀM DỰNG của DieuKienNguong, và lỗi mang tên trường")
                .contains("thresholdValueHigh");
    }

    @Test
    @Order(11)
    @DisplayName("⛔ Xoá mức cảnh báo đang có ngưỡng trỏ vào → HYD-2010, ⛔ không xoá lan")
    void deletingALevelStillInUseIsRefused() {
        ResponseEntity<String> ra =
                phienHttp.goi(kyThuat, HttpMethod.DELETE, "/api/v1/hyd/alert-levels/" + publicIdMuc, null);

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ra.getBody()).contains("HYD-2010");
    }

    @Test
    @Order(12)
    @DisplayName("⭐ Danh sách ngưỡng tải được QUA HTTP — ba @ManyToOne lười, open-in-view = false")
    void theRuleListActuallyLoadsOverHttp() {
        ResponseEntity<String> ra = phienHttp.get(kyThuat, "/api/v1/hyd/alert-rules?stationId=" + publicIdDiemDo);

        assertThat(ra.getStatusCode())
                .as(
                        """
                        ⭐ Đây đúng hình dạng đã làm `GET /hyd/stations` trả 500 SUỐT BỐN NGÀY từ WS-28:
                        `open-in-view = false` + một quan hệ lười đọc trong hàm dựng DTO, tức SAU khi
                        giao dịch đóng. Bài kiểm gọi thẳng service ⛔ không thể thấy (luật 5).""")
                .isEqualTo(HttpStatus.OK);
        assertThat(ra.getBody())
                .as("⛔ Số đo ra dây phải là CHUỖI — `5.000`, ⛔ không phải `5` (T28.27 / V2)")
                .contains("\"thresholdValue\":\"5.000\"")
                .contains(MA_DIEM_DO);
    }

    @Test
    @Order(13)
    @DisplayName("⭐ Nửa ĐỌC của HYD-2003 — danh sách 'điểm đo chưa cấu hình ngưỡng' có thật")
    void theUnconfiguredStationListIsReadable() {
        ResponseEntity<String> ra = phienHttp.get(kyThuat, "/api/v1/hyd/alert-rules/chua-cau-hinh");

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ra.getBody())
                .as(
                        """
                        ⚠ 19 điểm đo seed ⛔ CHƯA điểm nào có ngưỡng (G9-a). Danh sách này rỗng nghĩa
                        là nửa đọc lại thiếu — và lúc ấy "chưa cấu hình ngưỡng" là một trạng thái đúng
                        mà ⛔ không ai nhìn thấy, cho tới khi một trận lũ đi qua trong im lặng.""")
                .contains("DO-");
        assertThat(ra.getBody())
                .as("⛔ Điểm đo ĐÃ có ngưỡng phải biến khỏi danh sách nhắc việc")
                .doesNotContain(MA_DIEM_DO + "\"");
    }

    @Test
    @Order(14)
    @DisplayName("⭐ Đóng cảnh báo lần hai → HYD-2011, ⛔ không phải một thông báo thành công giả")
    void closingAnAlreadyClosedAlertIsRefused() {
        String publicId = jdbc.queryForObject(
                "SELECT public_id::text FROM alert_events WHERE station_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                idDiemDo);

        ResponseEntity<String> ra = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/hyd/alerts/" + publicId + "/dong",
                "{\"falseAlarm\":false,\"note\":\"Đóng lần hai\"}");

        assertThat(ra.getStatusCode())
                .as("Dòng ấy MÁY đã đóng ở bài `aReadingBackInRangeClosesTheAlert`. "
                        + "Hai người trực cùng bấm là chuyện bình thường — người bấm sau phải được nói rõ")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(ra.getBody()).contains("HYD-2011");
    }

    @Test
    @Order(15)
    @DisplayName("⭐ maintenance_logs.alert_event_public_id nay ĐƯỢC ĐỐI CHIẾU — T33.4, OPS-2021")
    void aMaintenanceLogCannotReferenceAnAlertThatDoesNotExist() {
        // ⚠ ⛔ KHÔNG dùng ADMIN: 2FA là BẮT BUỘC cho Admin từ Phase 0, nên `dangNhap` dừng ở
        //   `TWO_FACTOR_ENROLL_REQUIRED`. TECHNICIAN mang `ops:maintenance:create` theo đúng ma
        //   trận §6, và đó cũng là vai trò thật sẽ ghi bản ghi khắc phục từ một cảnh báo.
        String congTrinhPublicId = jdbc.queryForObject(
                "SELECT public_id::text FROM constructions WHERE code = ?", String.class, MA_CONG_TRINH);

        ResponseEntity<String> ra = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/ops/maintenance-logs",
                """
                {"constructionId":"%s","workType":"BAO_TRI_DINH_KY","startedOn":"2026-09-01",
                 "content":"Kiểm thử tham chiếu cảnh báo","performerName":"Đội kiểm thử WS-33",
                 "alertEventId":"00000000-0000-4000-8000-000000000000"}
                """
                        .formatted(congTrinhPublicId));

        assertThat(ra.getStatusCode())
                .as(
                        """
                        ⭐ Cột này có từ 21/08 — có cột, có setter, có trường trong form — và ⛔ CHƯA
                        BAO GIỜ được đối chiếu với bất cứ thứ gì. Một UUID bất kỳ lưu thành công.
                        Luật 27 ở chiều ngược: nửa GHI hoàn chỉnh, ⛔ không có ai kiểm.""")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ra.getBody()).contains("OPS-2021");
        // TAM
    }

    // =========================================================================
    // Trợ giúp
    // =========================================================================

    private String taoMuc(String ma, String ten, String khoaMau, int hang) {
        ResponseEntity<String> ra = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/hyd/alert-levels",
                """
                {"code":"%s","name":"%s","colorToken":"%s","severityRank":%d}
                """
                        .formatted(ma, ten, khoaMau, hang));
        assertThat(ra.getStatusCode())
                .as("⚠ Vế chống tập rỗng: không tạo được mức thì mọi bài dưới đây vô nghĩa. Thân: %s", ra.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return jdbc.queryForObject("SELECT public_id::text FROM alert_levels WHERE code = ?", String.class, ma);
    }

    private ResponseEntity<String> goiKhaiNguong(
            String diemDo, String muc, String loai, String nguong, String nguongCao, int tre) {
        String than =
                """
                {"stationId":"%s","measurementTypeCode":"MUC_NUOC","alertLevelId":"%s",
                 "conditionType":"%s","thresholdValue":"%s",%s"delayMinutes":%d}
                """
                        .formatted(
                                diemDo,
                                muc,
                                loai,
                                nguong,
                                nguongCao == null ? "" : "\"thresholdValueHigh\":\"" + nguongCao + "\",",
                                tre);
        return phienHttp.goi(kyThuat, HttpMethod.POST, "/api/v1/hyd/alert-rules", than);
    }

    private void khaiNguong(String diemDo, String muc, String loai, String nguong, String nguongCao, int tre) {
        ResponseEntity<String> ra = goiKhaiNguong(diemDo, muc, loai, nguong, nguongCao, tre);
        assertThat(ra.getStatusCode())
                .as("⚠ Vế chống tập rỗng: khai ngưỡng hỏng thì bài dưới xanh mà ⛔ không đo gì. Thân: %s", ra.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    /** ⛔ Đi qua đúng endpoint người dùng bấm — một câu INSERT thẳng bỏ qua chính cái móc đang kiểm. */
    private ResponseEntity<String> nhapTay(String diemDoPublicId, Instant moc, String giaTri) {
        return phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/hyd/so-do/nhap-tay",
                """
                {"diemDoId":"%s","maLoaiChiSo":"MUC_NUOC","mocDo":"%s","giaTri":%s}
                """
                        .formatted(diemDoPublicId, moc.toString(), giaTri));
    }

    private String trangThaiCongTrinh() {
        return jdbc.queryForObject(
                "SELECT operational_status FROM constructions WHERE id = ?", String.class, idCongTrinh);
    }

    private long demCanhBao(long stationId) {
        return jdbc.queryForObject("SELECT count(*) FROM alert_events WHERE station_id = ?", Long.class, stationId);
    }

    private long demCanhBaoDaXacNhan(long stationId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM alert_events WHERE station_id = ? AND status = 'DANG_XAY_RA' "
                        + "AND confirmed_at IS NOT NULL",
                Long.class,
                stationId);
    }

    private Map<String, Object> canhBaoMoiNhat(long stationId) {
        List<Map<String, Object>> ds = jdbc.queryForList(
                "SELECT status, confirmed_at, ended_at, peak_value FROM alert_events "
                        + "WHERE station_id = ? ORDER BY id DESC LIMIT 1",
                stationId);
        assertThat(ds).as("⚠ Chưa có cảnh báo nào cho điểm đo #%d", stationId).isNotEmpty();
        return ds.get(0);
    }
}
