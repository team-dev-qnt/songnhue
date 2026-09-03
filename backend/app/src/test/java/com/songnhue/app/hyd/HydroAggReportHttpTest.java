package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
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
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.hydro.application.HydroAggService;

/**
 * ⭐⭐ Bảng tổng hợp ngày + BC-13 <b>đi qua HTTP</b> — WS-34 (T34.1 · T34.2 · T34.3).
 *
 * <h2>Bài này đo VÒNG KHÉP KÍN, ⛔ không đo từng mảnh</h2>
 *
 * <p>Vòng: <i>ghi số đo (đúng endpoint người dùng bấm) → trigger cắm cờ bẩn → việc nền tính lại →
 * BC-13 hiện đúng số khung bỏ sót</i>. Bốn mảnh ấy chạy riêng lẻ hoàn hảo vẫn có thể cho ra số
 * không — WS-33 vừa gặp đúng thế: {@code alert_events} ghi đúng trong khi
 * {@code constructions.operational_status} đứng im, và ⛔ không phép kiểm theo lớp nào thấy được.
 *
 * <h2>⭐ Ba khẳng định chịu lực, mỗi cái bắt một lớp lỗi KHÁC nhau</h2>
 *
 * <ol>
 *   <li>{@link #aggDateIsTheVietnameseCalendarDay} — ranh giới ngày. Sai ở đây đẩy <b>42/144
 *       khung</b> mỗi ngày sang ngày hôm trước, và báo cáo vẫn ra đủ hàng với max/min hợp lý.
 *   <li>{@link #runningTheJobTwiceDoesNotDoubleTheNumbers} — idempotent, yêu cầu nguyên văn của
 *       T34.1.
 *   <li>{@link #approvingASuspectReadingRemovesTheStaleSuspectBucket} — <b>UPSERT thuần sẽ ⛔ KHÔNG
 *       qua bài này</b>. Nó là lý do {@code HydroAggRepository} xoá trọn kỳ rồi dựng lại.
 * </ol>
 *
 * <p>⚠ {@code @TestMethodOrder} là cố ý: các bài dưới đây dựng dần trạng thái của <b>một</b> điểm đo
 * và đọc lại chính nó, đúng như vòng đời thật.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HydroAggReportHttpTest extends IntegrationTestBase {

    private static final String MA_DIEM_DO = "T34-DIEMDO";
    private static final String MA_API = "F97341";

    /** Điểm đo thứ hai — dùng để chứng minh nhánh "ô rỗng kèm lý do" (quy tắc 16). */
    private static final String MA_DIEM_DO_IM = "T34-DIEMDO-IM";

    private static final String MA_API_IM = "F97342";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HydroAggService tongHop;

    private PhienHttp phienHttp;

    /** TECHNICIAN có {@code hyd:report:view} và {@code hyd:measurement:*} theo ma trận seed. */
    private PhienHttp.Phien kyThuat;

    private long idDiemDo;
    private long idDiemDoIm;
    private long idLoaiChiSo;
    private String publicIdDiemDo;

    /**
     * Ngày làm mốc cho mọi phép đo — <b>hôm kia giờ VN</b>.
     *
     * <p>⛔ Cố ý ⛔ không dùng hôm nay: số khung mong đợi của hôm nay trôi theo đồng hồ, nên một
     * khẳng định về nó sẽ đỏ ngẫu nhiên tuỳ phút chạy CI. Một ngày đã trọn vẹn thì mong đợi là 144,
     * cố định.
     */
    private LocalDate ngayDo;

    @BeforeAll
    void dungDuLieu() {
        idLoaiChiSo = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        assertThat(idNguon)
                .as("⚠ Vế chống tập rỗng: không có nguồn seed thì điểm đo dưới đây ⛔ không tạo được")
                .isNotNull();

        taoDiemDo(MA_DIEM_DO, MA_API, idNguon);
        taoDiemDo(MA_DIEM_DO_IM, MA_API_IM, idNguon);
        Map<String, Object> d = jdbc.queryForMap("SELECT id, public_id FROM stations WHERE code = ?", MA_DIEM_DO);
        idDiemDo = ((Number) d.get("id")).longValue();
        publicIdDiemDo = d.get("public_id").toString();
        idDiemDoIm = jdbc.queryForObject("SELECT id FROM stations WHERE code = ?", Long.class, MA_DIEM_DO_IM);

        ngayDo = LocalDate.now(DateTimeUtils.ZONE_VN).minusDays(2);

        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t34_kythuat", "TECHNICIAN"));
    }

    private void taoDiemDo(String ma, String maApi, long idNguon) {
        jdbc.update(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, created_at)
                VALUES (?, 'Điểm đo kiểm thử tổng hợp', ?, ?, 'MN_SONG', TRUE, now())
                """,
                ma,
                maApi,
                idNguon);
        jdbc.update(
                """
                INSERT INTO station_measurement_types (station_id, measurement_type_id)
                SELECT s.id, m.id FROM stations s, measurement_types m
                 WHERE s.code = ? AND m.code = 'MUC_NUOC'
                """,
                ma);
    }

    @AfterAll
    void donSachSauCung() {
        for (long id : new long[] {idDiemDo, idDiemDoIm}) {
            jdbc.update("DELETE FROM hydro_agg_daily WHERE station_id = ?", id);
            jdbc.update("DELETE FROM hydro_agg_dirty WHERE station_id = ?", id);
            jdbc.update("DELETE FROM hydro_readings WHERE station_id = ?", id);
            jdbc.update("DELETE FROM hydro_latest WHERE station_id = ?", id);
            jdbc.update("DELETE FROM station_measurement_types WHERE station_id = ?", id);
            jdbc.update("DELETE FROM stations WHERE id = ?", id);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE deleted_at IS NULL", Long.class))
                .as("⛔ Khẳng định NGAY TẠI CHỖ DỌN — danh mục thật có đúng 19 điểm đo")
                .isEqualTo(19L);
    }

    // =========================================================================
    // ⭐ T34.1 — trigger, ranh giới ngày, idempotent
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("⭐⭐ Ghi số đo qua ĐÚNG endpoint người dùng bấm → trigger tự cắm cờ bẩn (luật 12)")
    void writingAReadingMarksTheAggregationPeriodDirty() {
        donCoBan();

        ResponseEntity<String> ra = nhapTay(mocTrongNgay(ngayDo, 8, 0), "3.100");
        assertThat(ra.getStatusCode())
                .as("⚠ Vế chống tập rỗng: nhập tay hỏng thì mọi bài dưới xanh mà ⛔ không đo gì. Thân: %s", ra.getBody())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(demCoBan(idDiemDo))
                .as(
                        """
                        ⛔ Trigger `trg_hydro_readings_danh_dau_agg` ⛔ KHÔNG cắm cờ. Bảo đảm này CỐ Ý \
                        nằm ở CSDL chứ ⛔ không ở tầng ứng dụng (luật 12): hôm nay có ba đường ghi \
                        `hydro_readings`, và T27.7 đã chứng minh đường ghi THỨ TƯ ra đời cùng đợt với \
                        bản vá cho ba đường đầu.""")
                .isEqualTo(1L);
    }

    @Test
    @Order(2)
    @DisplayName("⭐⭐ `agg_date` là NGÀY GIỜ VIỆT NAM — 00:30 giờ VN ⛔ không rơi vào ngày hôm trước")
    void aggDateIsTheVietnameseCalendarDay() {
        // 00:30 giờ VN = 17:30 UTC ngày hôm trước. `measured_at::date` sẽ trả ngày hôm trước.
        Instant nuaDem = mocTrongNgay(ngayDo, 0, 30);
        assertThat(nuaDem.atZone(java.time.ZoneOffset.UTC).toLocalDate())
                .as("⚠ Vế phân biệt: mốc này PHẢI khác ngày theo UTC, nếu không bài dưới ⛔ không đo gì")
                .isEqualTo(ngayDo.minusDays(1));

        assertThat(nhapTay(nuaDem, "3.200").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        tongHop.chayMotLuot();

        List<LocalDate> ngayCoSoLieu = jdbc.queryForList(
                "SELECT agg_date FROM hydro_agg_daily WHERE station_id = ? ORDER BY agg_date",
                LocalDate.class,
                idDiemDo);

        assertThat(ngayCoSoLieu)
                .as(
                        """
                        ⛔ Bản ghi 00:30 giờ VN bị xếp sang ngày hôm trước — tức đang cắt ngày theo UTC. \
                        Đó là 42/144 khung MỖI NGÀY xếp sai, và triệu chứng gần như vô hình: báo cáo vẫn \
                        ra đủ hàng, "mực nước cao nhất ngày 12" chỉ là chuyện xảy ra rạng sáng ngày 13.""")
                .containsExactly(ngayDo)
                .doesNotContain(ngayDo.minusDays(1));
    }

    @Test
    @Order(3)
    @DisplayName("⭐ Chạy việc nền HAI lượt liên tiếp → số liệu ⛔ không nhân đôi (T34.1)")
    void runningTheJobTwiceDoesNotDoubleTheNumbers() {
        assertThat(nhapTay(mocTrongNgay(ngayDo, 12, 0), "3.900").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        tongHop.chayMotLuot();
        Map<String, Object> lan1 = hangAgg(idDiemDo, ngayDo, "HOP_LE");

        // Lượt hai: hàng đợi đã rỗng ⇒ ⛔ không tính lại gì. Ép tính lại bằng cách cắm cờ tay —
        // ⛔ đây mới là phép thử idempotent thật, chứ "chạy lại khi hàng đợi rỗng" thì hiển nhiên.
        camCoBanTay(ngayDo);
        tongHop.chayMotLuot();
        Map<String, Object> lan2 = hangAgg(idDiemDo, ngayDo, "HOP_LE");

        assertThat(lan2.get("reading_count"))
                .as("⛔ Tính lại làm số bản ghi nhân đôi — bảng tổng hợp ⛔ không idempotent (T34.1)")
                .isEqualTo(lan1.get("reading_count"));
        assertThat(lan2.get("sum_value")).isEqualTo(lan1.get("sum_value"));
        assertThat(lan2.get("max_value")).isEqualTo(lan1.get("max_value"));
        assertThat(lan2.get("max_at"))
                .as("⛔ Mốc đạt max phải TẤT ĐỊNH: mực nước đứng yên nhiều giờ là chuyện thường, và "
                        + "nếu ⛔ không chốt thứ tự thì hai lượt tính cho ra hai mốc khác nhau")
                .isEqualTo(lan1.get("max_at"));

        assertThat(((Number) lan2.get("reading_count")).intValue())
                .as("⚠ Vế chống tập rỗng cho chính bài này")
                .isEqualTo(3);
    }

    @Test
    @Order(4)
    @DisplayName("⭐ `sum_value` cho trung bình đúng — TB của các TB ngày là một con số khác")
    void sumIsStoredSoThatMonthlyAveragesAreWeighted() {
        Map<String, Object> hang = hangAgg(idDiemDo, ngayDo, "HOP_LE");
        java.math.BigDecimal tong = (java.math.BigDecimal) hang.get("sum_value");
        java.math.BigDecimal tb = (java.math.BigDecimal) hang.get("avg_value");
        int dem = ((Number) hang.get("reading_count")).intValue();

        assertThat(tong).as("3.100 + 3.200 + 3.900").isEqualByComparingTo(new java.math.BigDecimal("10.200"));
        assertThat(tong.divide(java.math.BigDecimal.valueOf(dem), 5, java.math.RoundingMode.HALF_UP))
                .as("⭐ SUM/COUNT phải khớp AVG đã lưu — nếu lệch thì phép gộp nhiều ngày sẽ lệch theo")
                .isEqualByComparingTo(tb);
    }

    @Test
    @Order(5)
    @DisplayName("⭐⭐ Duyệt một bản ghi NGHI_NGO làm hàng agg NGHI_NGO BIẾN MẤT — UPSERT thuần ⛔ không qua nổi")
    void approvingASuspectReadingRemovesTheStaleSuspectBucket() {
        Instant moc = mocTrongNgay(ngayDo, 20, 0);
        // ⛔ Ghi thẳng bằng SQL là CỐ Ý ở đúng bài này: nó chứng minh cờ bẩn đến từ TRIGGER chứ
        //   ⛔ không từ một lời gọi ở tầng ứng dụng. Không đường ghi nào lách được.
        jdbc.update(
                """
                INSERT INTO hydro_readings (
                    measured_at, station_id, measurement_type_id, reading_value,
                    quality, quality_reason, source)
                VALUES (?, ?, ?, '9.999', 'NGHI_NGO', 'kiểm thử T34', 'API')
                """,
                Timestamp.from(moc),
                idDiemDo,
                idLoaiChiSo);
        tongHop.chayMotLuot();

        assertThat(hangAgg(idDiemDo, ngayDo, "NGHI_NGO"))
                .as("⚠ Vế chống tập rỗng: chưa có hàng NGHI_NGO thì bài dưới xanh mà ⛔ không đo gì")
                .isNotEmpty();

        ResponseEntity<String> ra = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/hyd/so-do/thao-tac",
                """
                {"diemDoId":"%s","maLoaiChiSo":"MUC_NUOC","mocDo":"%s","action":"DUYET","reason":"kiểm thử T34"}
                """
                        .formatted(publicIdDiemDo, moc.toString()));
        assertThat(ra.getStatusCode()).as("Thân: %s", ra.getBody()).isEqualTo(HttpStatus.OK);

        tongHop.chayMotLuot();

        assertThat(hangAgg(idDiemDo, ngayDo, "NGHI_NGO"))
                .as(
                        """
                        ⛔⛔ Hàng tổng hợp NGHI_NGO còn nguyên sau khi bản ghi cuối cùng của nhóm ấy đã \
                        được duyệt lên HOP_LE. Đây chính là điều một UPSERT thuần ⛔ KHÔNG sửa được: nó \
                        chỉ ghi đè những nhóm CÒN sinh ra hàng. Hệ quả người dùng thấy là BC-13 báo có \
                        dữ liệu nghi ngờ VĨNH VIỄN dù ⛔ không còn cái nào — luật 27 ở tầng bảng tổng hợp.""")
                .isEmpty();
        assertThat(((Number) hangAgg(idDiemDo, ngayDo, "HOP_LE").get("reading_count")).intValue())
                .as("bản ghi vừa duyệt phải chuyển sang nhóm HOP_LE, ⛔ không biến mất")
                .isEqualTo(4);
    }

    // =========================================================================
    // ⭐⭐ T34.3 — BC-13 qua HTTP
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("⭐⭐ BC-13 — cột SỐ KHUNG BỎ SÓT, phép đo duy nhất của NFR-03")
    void theReportCountsMissedTenMinuteFrames() {
        ResponseEntity<String> ra = phienHttp.get(
                kyThuat,
                "/api/v1/hyd/bao-cao/dong-bo?tuNgay=%s&denNgay=%s&stationPublicId=%s"
                        .formatted(ngayDo, ngayDo, publicIdDiemDo));

        assertThat(ra.getStatusCode()).as("Thân: %s", ra.getBody()).isEqualTo(HttpStatus.OK);
        String than = ra.getBody();

        assertThat(than).contains("\"khungPhut\":10");
        assertThat(than)
                .as("4 bản ghi trong một ngày trọn vẹn ⇒ 144 − 4 = 140 khung bỏ sót")
                .contains("\"soKhungMongDoi\":144")
                .contains("\"soKhungBoSot\":140")
                .contains("\"soHopLe\":4");

        assertThat(than)
                .as("⚠ T28.27/V2 — số thập phân đi ra dây phải là CHUỖI, nếu không 2.30 về thành 2.3")
                .contains("\"tyLeDayDu\":\"2.8\"");

        assertThat(than)
                .as("⛔ Ô có số liệu ⛔ không được kèm lý do rỗng — hai câu trái nhau trên một ô")
                .contains("\"lyDoTrong\":null");
    }

    @Test
    @Order(7)
    @DisplayName("⭐ Quy tắc 16 — ngày chưa theo dõi trả ô RỖNG KÈM LÝ DO, ⛔ không trả 0")
    void anUnmonitoredDayReturnsAnEmptyCellWithAReason() {
        LocalDate truocKhiCo = ngayDo.minusDays(1);

        ResponseEntity<String> ra = phienHttp.get(
                kyThuat,
                "/api/v1/hyd/bao-cao/dong-bo?tuNgay=%s&denNgay=%s&stationPublicId=%s"
                        .formatted(truocKhiCo, truocKhiCo, publicIdDiemDo));

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ra.getBody())
                .as(
                        """
                        ⛔ Ngày TRƯỚC ngày có số đo đầu tiên bị đếm thành "bỏ sót 144 khung" — tức bịa ra \
                        một sự cố ⛔ chưa từng xảy ra, và bịa đúng vào con số nghiệm thu NFR-03. Quy tắc \
                        16: ô chưa có nguồn trả RỖNG KÈM LÝ DO, và ràng buộc ấy ép ở hàm dựng \
                        (`DoDayDuKhung`), ⛔ không ở lời dặn.""")
                .contains("\"soKhungBoSot\":null")
                .contains("Trước ngày có số đo đầu tiên");
    }

    @Test
    @Order(8)
    @DisplayName("⭐ Điểm đo chưa từng có số đo ⇒ ô rỗng với lý do KHÁC — hai trạng thái phân biệt được")
    void aStationThatNeverReportedSaysSoDistinctly() {
        ResponseEntity<String> ra =
                phienHttp.get(kyThuat, "/api/v1/hyd/bao-cao/dong-bo?tuNgay=%s&denNgay=%s".formatted(ngayDo, ngayDo));

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ra.getBody())
                .as("luật 9 — 'chưa theo dõi' và 'theo dõi mà không có dữ liệu' là hai câu trái ngược")
                .contains("Chưa có số đo nào");
    }

    @Test
    @Order(9)
    @DisplayName("⛔ HYD-2013 · HYD-2012 — hai lỗi khoảng ngày PHÂN BIỆT ĐƯỢC, ⛔ không gộp một câu")
    void theTwoDateRangeErrorsAreDistinct() {
        ResponseEntity<String> nguoc =
                phienHttp.get(kyThuat, "/api/v1/hyd/bao-cao/dong-bo?tuNgay=2026-09-10&denNgay=2026-09-01");
        assertThat(nguoc.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(nguoc.getBody()).contains("HYD-2013");

        ResponseEntity<String> qua =
                phienHttp.get(kyThuat, "/api/v1/hyd/bao-cao/dong-bo?tuNgay=2020-01-01&denNgay=2026-09-01");
        assertThat(qua.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(qua.getBody())
                .as("⛔ 19 điểm đo × 2 chỉ số × 5 năm = 69 nghìn hàng đổ vào một bảng ⛔ không phân trang")
                .contains("HYD-2012")
                .contains("366");
    }

    @Test
    @Order(10)
    @DisplayName("⛔ Điểm đo không tồn tại trả 404, ⛔ không phải một báo cáo RỖNG")
    void anUnknownStationIsFourOhFourNotAnEmptyReport() {
        ResponseEntity<String> ra = phienHttp.get(
                kyThuat,
                "/api/v1/hyd/bao-cao/dong-bo?tuNgay=%s&denNgay=%s&stationPublicId=%s"
                        .formatted(ngayDo, ngayDo, "00000000-0000-0000-0000-0000000034ff"));

        assertThat(ra.getStatusCode())
                .as("bỏ qua bộ lọc không giải được là cho ra bảng trống — trông y hệt 'trạm chưa có số đo'")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // ⭐ T34.5 — BC-05 tổng hợp kỳ
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("⭐⭐ BC-05 — max/min kèm THỜI ĐIỂM đạt, và trung bình tính THEO TRỌNG SỐ")
    void thePeriodReportCarriesTheMomentEachExtremeWasReached() {
        ResponseEntity<String> ra = phienHttp.get(
                kyThuat,
                "/api/v1/hyd/bao-cao/tong-hop?tuNgay=%s&denNgay=%s&stationPublicId=%s"
                        .formatted(ngayDo, ngayDo, publicIdDiemDo));

        assertThat(ra.getStatusCode()).as("Thân: %s", ra.getBody()).isEqualTo(HttpStatus.OK);
        String than = ra.getBody();

        // Bốn bản ghi HỢP LỆ trong ngày: 3.100 (08:00) · 3.200 (00:30) · 3.900 (12:00) · 9.999 (20:00)
        assertThat(than).contains("\"soBanGhi\":4").contains("\"soNgayCoDuLieu\":1");
        assertThat(than)
                .as("⚠ Số thập phân ra dây phải là CHUỖI — quy tắc 2 + bài học T28.27")
                .contains("\"giaTriMin\":\"3.100\"")
                .contains("\"giaTriMax\":\"9.999\"");

        // (3.100 + 3.200 + 3.900 + 9.999) / 4 = 20.199 / 4 = 5.04975 → làm tròn 3 chữ số
        assertThat(than)
                .as("⭐ Trung bình THEO TRỌNG SỐ = SUM(sum_value)/SUM(reading_count), ⛔ không phải "
                        + "trung bình của các trung bình ngày")
                .contains("\"giaTriTb\":\"5.050\"");

        assertThat(than)
                .as("⭐ BC-05 đòi 'kèm thời điểm đạt max/min' — mốc phải lấy từ bảng tổng hợp, "
                        + "⛔ không phải quét lại số đo thô")
                .contains("\"mocMax\":\"" + mocTrongNgay(ngayDo, 20, 0) + "\"")
                .contains("\"mocMin\":\"" + mocTrongNgay(ngayDo, 8, 0) + "\"");
    }

    @Test
    @Order(12)
    @DisplayName("⭐⭐ Điểm đo KHÔNG có số liệu hợp lệ vẫn PHẢI có hàng — ô rỗng kèm lý do, ⛔ không biến mất")
    void aStationWithNoValidDataStillGetsARowInThePeriodReport() {
        ResponseEntity<String> ra =
                phienHttp.get(kyThuat, "/api/v1/hyd/bao-cao/tong-hop?tuNgay=%s&denNgay=%s".formatted(ngayDo, ngayDo));

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ra.getBody())
                .as(
                        """
                        ⛔⛔ Điểm đo ⛔ KHÔNG có số liệu hợp lệ đã biến mất khỏi báo cáo. Nguyên nhân gần \
                        như luôn là một chỗ: vị từ `quality = 'HOP_LE'` bị đẩy từ mệnh đề ON xuống WHERE, \
                        biến LEFT JOIN thành INNER JOIN. Khi ấy đúng những điểm đo ĐANG CÓ VẤN ĐỀ bị giấu \
                        đi, và bảng trông sạch sẽ, đủ hàng, ⛔ không nói ra rằng nó vừa giấu gì.""")
                .contains(MA_DIEM_DO_IM);

        assertThat(ra.getBody())
                .as("quy tắc 16 — ô rỗng BẮT BUỘC kèm lý do, ⛔ không được thay bằng 0")
                .contains("\"giaTriTb\":null")
                .contains("không có bản ghi hợp lệ nào");
    }

    // =========================================================================
    // ⭐ T34.6 — BC-12 chi tiết theo yêu cầu
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("⭐⭐ BC-12 — nơi DUY NHẤT hiện bản ghi NGHI_NGO, kèm cột Chất lượng và cột Nguồn")
    void theDetailReportIsTheOnlyPlaceSuspectRowsAreShown() {
        // Một bản ghi nghi ngờ mới, để BC-12 có cái để hiện.
        jdbc.update(
                """
                INSERT INTO hydro_readings (
                    measured_at, station_id, measurement_type_id, reading_value,
                    quality, quality_reason, source)
                VALUES (?, ?, ?, '8.888', 'NGHI_NGO', 'vượt khoảng vật lý (kiểm thử T34)', 'API')
                """,
                Timestamp.from(mocTrongNgay(ngayDo, 22, 0)),
                idDiemDo,
                idLoaiChiSo);

        ResponseEntity<String> ra = phienHttp.get(
                kyThuat,
                "/api/v1/hyd/bao-cao/chi-tiet?stationPublicId=%s&maLoaiChiSo=MUC_NUOC&tuNgay=%s&denNgay=%s"
                        .formatted(publicIdDiemDo, ngayDo, ngayDo));

        assertThat(ra.getStatusCode()).as("Thân: %s", ra.getBody()).isEqualTo(HttpStatus.OK);
        String than = ra.getBody();

        assertThat(than)
                .as("⭐ Bản ghi nghi ngờ PHẢI hiện ra ở đây — đó là lý do báo cáo này được miễn quy tắc 14")
                .contains("\"giaTri\":\"8.888\"")
                .contains("\"quality\":\"NGHI_NGO\"");

        assertThat(than)
                .as(
                        """
                        ⛔⛔ Hai cột `quality` và `source` là thứ được ĐÁNH ĐỔI lấy quyền không lọc chất \
                        lượng. Bỏ chúng đi thì ngoại lệ của quy tắc 14 mất chỗ dựa và biến thành đúng cái \
                        lỗi nó được miễn: một con số nghi ngờ đứng lẫn giữa số liệu chính thức.""")
                .contains("\"source\":\"API\"")
                .contains("vượt khoảng vật lý");

        assertThat(than)
                .as("⛔ Cố ý ⛔ KHÔNG join `users` — danh tính người nhập là dữ liệu cá nhân, và `users` "
                        + "là bảng của Core (quy tắc 6)")
                .doesNotContain("fullName")
                .doesNotContain("createdBy");
    }

    @Test
    @Order(14)
    @DisplayName("⭐ Trần khoảng ngày của BC-12 HẸP HƠN hẳn (31) — hai trần khác nhau và cùng đúng")
    void theDetailReportHasItsOwnTighterDateCap() {
        // 40 ngày: BC-13 và BC-05 nhận (trần 366), BC-12 từ chối (trần 31).
        LocalDate tu = ngayDo.minusDays(39);

        assertThat(phienHttp
                        .get(kyThuat, "/api/v1/hyd/bao-cao/tong-hop?tuNgay=%s&denNgay=%s".formatted(tu, ngayDo))
                        .getStatusCode())
                .as("⚠ Vế phân biệt: cùng khoảng ngày ấy BC-05 PHẢI nhận, nếu không bài này ⛔ không "
                        + "chứng minh được hai trần là hai con số khác nhau")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> qua = phienHttp.get(
                kyThuat,
                "/api/v1/hyd/bao-cao/chi-tiet?stationPublicId=%s&maLoaiChiSo=MUC_NUOC&tuNgay=%s&denNgay=%s"
                        .formatted(publicIdDiemDo, tu, ngayDo));

        assertThat(qua.getStatusCode())
                .as("⛔ BC-12 là báo cáo DUY NHẤT quét bảng gốc: 144 bản ghi/ngày × 40 ngày là đúng lượt "
                        + "quét mà bảng tổng hợp sinh ra để tránh")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(qua.getBody()).contains("HYD-2012").contains("31");
    }

    // =========================================================================

    private ResponseEntity<String> nhapTay(Instant moc, String giaTri) {
        return phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/hyd/so-do/nhap-tay",
                """
                {"diemDoId":"%s","maLoaiChiSo":"MUC_NUOC","mocDo":"%s","giaTri":"%s"}
                """
                        .formatted(publicIdDiemDo, moc.toString(), giaTri));
    }

    /** Mốc UTC ứng với {@code hh:mm} <b>giờ VN</b> của ngày ấy. */
    private static Instant mocTrongNgay(LocalDate ngay, int gio, int phut) {
        return ZonedDateTime.of(ngay, java.time.LocalTime.of(gio, phut), DateTimeUtils.ZONE_VN)
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS);
    }

    private long demCoBan(long stationId) {
        return jdbc.queryForObject("SELECT count(*) FROM hydro_agg_dirty WHERE station_id = ?", Long.class, stationId);
    }

    private void donCoBan() {
        jdbc.update("DELETE FROM hydro_agg_dirty WHERE station_id IN (?, ?)", idDiemDo, idDiemDoIm);
    }

    private void camCoBanTay(LocalDate ngay) {
        jdbc.update(
                """
                INSERT INTO hydro_agg_dirty (station_id, measurement_type_id, agg_date)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """,
                idDiemDo,
                idLoaiChiSo,
                java.sql.Date.valueOf(ngay));
    }

    /** ⛔ Trả {@code Map} rỗng thay vì ném khi ⛔ không có hàng — sự VẮNG MẶT là thứ đang được đo. */
    private Map<String, Object> hangAgg(long stationId, LocalDate ngay, String quality) {
        List<Map<String, Object>> ra = jdbc.queryForList(
                """
                SELECT reading_count, min_value, max_value, max_at, avg_value, sum_value, computed_at
                  FROM hydro_agg_daily
                 WHERE station_id = ? AND agg_date = ? AND quality = ?
                """,
                stationId,
                java.sql.Date.valueOf(ngay),
                quality);
        return ra.isEmpty() ? Map.of() : ra.get(0);
    }
}
