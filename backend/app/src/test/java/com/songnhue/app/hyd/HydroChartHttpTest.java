package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Biểu đồ mực nước 24 giờ — <b>T35.4</b>, qua HTTP.
 *
 * <h2>⛔⛔ Bài chịu lực: bản ghi {@code NGHI_NGO} ⛔ KHÔNG được lên đường cong</h2>
 *
 * <p>Quy tắc 14 gọi đây là <i>"bẫy sai số liệu dễ mắc nhất"</i>: bản ghi nghi ngờ nằm <b>chung
 * bảng</b> với dữ liệu tốt. Trên một bảng số nó còn có cột Chất lượng đứng cạnh; trên một
 * <b>đường cong</b> thì ⛔ không — một điểm nghi ngờ trông y hệt một điểm tốt, và nó kéo cả đoạn
 * đường đi theo.
 *
 * <p>⇒ Bài này dựng <b>cả hai</b> loại bản ghi trong cùng một cửa sổ rồi đếm: một khẳng định chỉ
 * dựng dữ liệu hợp lệ ⛔ không phân biệt được <i>"có lọc"</i> với <i>"chưa từng có gì để lọc"</i>
 * (luật 9).
 */
class HydroChartHttpTest extends IntegrationTestBase {

    private static final String MA = "T354-001";
    private static final String MA_API = "F95001";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    private PhienHttp phienHttp;

    /**
     * ⭐⭐ Đăng nhập bằng <b>DUTY_OFFICER</b>, ⛔ không phải ADMIN.
     *
     * <p>Trực ban là người ngồi nhìn đường cong này, và §10.61 đã trả giá đúng chỗ đó: 906 bài kiểm
     * hai phía đều xanh mà trang vẫn hỏng ở lượt tải đầu <i>bằng một vai trò thật</i>. Một màn hình
     * chỉ được kiểm bằng ADMIN là một màn hình chưa biết nó mở được cho ai.
     *
     * <p>⚠ Nó cũng là phép đo cho lựa chọn quyền của controller: DUTY_OFFICER có
     * {@code hyd:report:view} nhưng ⛔ <b>không</b> có {@code hyd:report:export} — nếu ai đó "dọn"
     * quyền của endpoint sang một mã khác, bài này đỏ ngay.
     */
    private PhienHttp.Phien trucBan;

    private UUID publicId;
    private long idDiemDo;
    private long idLoai;

    @BeforeEach
    void setUp() {
        donDep();
        if (phienHttp == null) {
            phienHttp = new PhienHttp(http);
            trucBan =
                    phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t354_trucban", "DUTY_OFFICER"));
        }
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        assertThat(idNguon).as("⚠ vế chống tập rỗng: phải có nguồn seed").isNotNull();
        idLoai = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);

        idDiemDo = jdbc.queryForObject(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, created_at)
                VALUES (?, 'Điểm đo kiểm thử biểu đồ', ?, ?, 'THUONG_LUU', TRUE, now())
                RETURNING id
                """,
                Long.class,
                MA,
                MA_API,
                idNguon);
        publicId = jdbc.queryForObject("SELECT public_id FROM stations WHERE id = ?", UUID.class, idDiemDo);
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    // =========================================================================

    @Test
    @DisplayName("⭐ Trả về các điểm hợp lệ trong 24 giờ, xếp theo mốc TĂNG dần")
    void itReturnsValidPointsInChronologicalOrder() {
        ghi(Duration.ofHours(3), new BigDecimal("1.100"), "HOP_LE");
        ghi(Duration.ofHours(1), new BigDecimal("1.500"), "HOP_LE");

        ResponseEntity<String> ra = doc();

        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.OK);
        String than = ra.getBody();
        assertThat(than).contains("\"1.100\"").contains("\"1.500\"");
        assertThat(than.indexOf("\"1.100\""))
                .as("⛔ Đường cong phải đi theo thời gian TĂNG dần — vẽ ngược là một đồ thị nói ngược")
                .isLessThan(than.indexOf("\"1.500\""));
        assertThat(than)
                .as("có điểm ⇒ `lyDoTrong` phải null; backend ép ràng buộc ấy ở hàm dựng (quy tắc 16)")
                .contains("\"lyDoTrong\":null");
    }

    /**
     * ⛔⛔ Vế chịu lực của cả bài — quy tắc 14.
     *
     * <p>Dựng <b>hai</b> bản ghi cùng cửa sổ: một HỢP LỆ, một NGHI_NGO. Bản NGHI_NGO mang một giá
     * trị ⛔ không thể nhầm lẫn ({@code 9.999}) để khẳng định đọc được bằng mắt khi nó đỏ.
     */
    @Test
    @DisplayName("⛔⛔ Bản ghi NGHI_NGO ⛔ KHÔNG lên đường cong — quy tắc 14, và đây là chỗ nó vô hình nhất")
    void suspectReadingsNeverReachTheCurve() {
        ghi(Duration.ofHours(2), new BigDecimal("1.200"), "HOP_LE");
        ghi(Duration.ofHours(1), new BigDecimal("9.999"), "NGHI_NGO");

        String than = doc().getBody();

        assertThat(than)
                .as("tiền đề: bản hợp lệ CÓ mặt — nếu không thì khẳng định dưới đây xanh vì bảng rỗng")
                .contains("\"1.200\"");
        assertThat(than)
                .as(
                        """
                        ⛔ Một số đo NGHI_NGO trên BẢNG còn có cột Chất lượng đứng cạnh; trên một ĐƯỜNG CONG \
                        thì ⛔ không — nó trông y hệt một điểm tốt và kéo cả đoạn đường đi theo. Đây đúng là \
                        "bẫy sai số liệu dễ mắc nhất" mà quy tắc 14 gọi tên.""")
                .doesNotContain("9.999");
    }

    @Test
    @DisplayName("⛔ Số đo NGOÀI cửa sổ 24 giờ ⛔ không lên đường cong — cửa sổ là ràng buộc, không phải mặc định")
    void readingsOlderThan24HoursAreExcluded() {
        ghi(Duration.ofHours(2), new BigDecimal("1.300"), "HOP_LE");
        ghi(Duration.ofHours(30), new BigDecimal("8.888"), "HOP_LE");

        String than = doc().getBody();

        assertThat(than).contains("\"1.300\"");
        assertThat(than)
                .as("⛔ Nới cửa sổ là cách một ngoại lệ hợp lệ của quy tắc 8 trở thành lượt quét 82 nghìn dòng")
                .doesNotContain("8.888");
    }

    /**
     * ⛔ Biểu đồ rỗng phải nói VÌ SAO — quy tắc 16 ở tầng API.
     *
     * <p>Một khung trục rỗng trông <b>y hệt</b> một biểu đồ mà mọi giá trị bằng 0, và cũng y hệt
     * trường hợp quên đăng ký component ECharts. Ba tình huống khác hẳn nhau, một hình ảnh.
     */
    @Test
    @DisplayName("⛔ Chưa có số đo nào ⇒ danh sách RỖNG KÈM LÝ DO — ⛔ không 404, ⛔ không 0")
    void anEmptyCurveCarriesItsReason() {
        ResponseEntity<String> ra = doc();

        assertThat(ra.getStatusCode())
                .as("trạm chưa có số là chuyện BÌNH THƯỜNG, ⛔ không phải 404")
                .isEqualTo(HttpStatus.OK);
        assertThat(ra.getBody()).contains("\"diem\":[]").doesNotContain("\"lyDoTrong\":null");
    }

    @Test
    @DisplayName("⛔ Mọi bản ghi đều NGHI_NGO ⇒ vẫn RỖNG KÈM LÝ DO, ⛔ không phải một đường cong sạch")
    void allSuspectStillYieldsAReason() {
        ghi(Duration.ofHours(1), new BigDecimal("9.999"), "NGHI_NGO");

        String than = doc().getBody();

        assertThat(than)
                .as(
                        """
                        ⛔ Đây là tình huống nguy hiểm nhất của màn hình: trạm ĐANG gửi số, số ĐANG bị treo, \
                        và biểu đồ trống. ⛔ Không được im lặng — người trực phải đọc được rằng có dữ liệu \
                        đang chờ duyệt ở màn hình Dữ liệu nghi ngờ.""")
                .contains("\"diem\":[]")
                .doesNotContain("\"lyDoTrong\":null");
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<String> doc() {
        return phienHttp.get(trucBan, "/api/v1/hyd/bieu-do/muc-nuoc-24h?stationPublicId=" + publicId);
    }

    /**
     * ⚠ {@code quality_reason} bắt buộc khi {@code NGHI_NGO} — ràng buộc
     * {@code ck_hydro_readings_nghi_ngo_co_ly_do} ép ở <b>tầng CSDL</b>.
     *
     * <p>⭐ Đó là quy tắc 16 đặt đúng chỗ: một bản ghi bị treo mà ⛔ không nói được vì sao thì màn
     * hình Dữ liệu nghi ngờ hiện một dòng ⛔ không ai duyệt nổi. Bài này mắc ngay lượt chạy đầu, và
     * ⛔ đó là ràng buộc làm đúng việc của nó chứ ⛔ không phải một trở ngại để lách.
     */
    private void ghi(Duration truocDay, BigDecimal giaTri, String chatLuong) {
        Instant moc = Instant.now().minus(truocDay);
        jdbc.update(
                """
                INSERT INTO hydro_readings (measured_at, station_id, measurement_type_id, reading_value,
                                            quality, quality_reason, source, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?, 'API', now())
                """,
                java.sql.Timestamp.from(moc),
                idDiemDo,
                idLoai,
                giaTri,
                chatLuong,
                "NGHI_NGO".equals(chatLuong) ? "kiểm thử T35.4" : null);
    }

    private void donDep() {
        jdbc.update("DELETE FROM hydro_readings WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA);
        jdbc.update("DELETE FROM stations WHERE code = ?", MA);
    }
}
