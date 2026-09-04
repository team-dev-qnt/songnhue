package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.hydro.application.PublicHydroService;

/**
 * Mực nước trên <b>cổng công khai</b> — T35.5 · T35.7, đi qua HTTP bằng trình duyệt <b>vô danh</b>.
 *
 * <h2>⛔⛔ Bài này canh đúng chỗ §10.54 đã trả giá</h2>
 *
 * <p>Bản cũ của khối "Mực nước, lượng mưa" có 5 trạm viết cứng kèm mực nước và một mức "Cảnh báo BĐ
 * I" gắn tên cống có thật — tất cả đều bịa, và đã lên staging. Nên ở đây khẳng định <b>hai chiều</b>:
 * số phải đến từ CSDL (đổi CSDL thì phản hồi đổi theo), và ⛔ không có số nào xuất hiện khi CSDL
 * chưa có gì.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicHydroHttpTest extends IntegrationTestBase {

    private static final String DUONG_DAN = "/api/v1/public/hydro/muc-nuoc";
    private static final String MA = "T35P-001";
    private static final String MA_API = "F97001";

    /** ⚠ Trình duyệt thật LUÔN gửi `Origin`. `curl` thì không — và nó đi lọt qua đúng bức tường CORS. */
    private static final String NGUON_GOC = "http://localhost:3000";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.songnhue.core.application.settings.SettingService settings;

    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM hydro_latest WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA);
        jdbc.update("DELETE FROM stations WHERE code = ?", MA);
        // ⚠ Trả khoá T35.8 về rỗng: một bài để lại danh sách lọc thì mọi bài SAU nó đo trên một cổng
        //   đã bị thu hẹp, và triệu chứng là "bài kiểm đỏ theo thứ tự chạy".
        datDanhSachCong("");
    }

    /**
     * ⚠ Ghi qua {@code SettingService}, ⛔ không {@code UPDATE} thẳng bảng.
     *
     * <p>{@code SettingPort} có bộ đệm Caffeine phía sau, và nó chỉ được dọn bởi lượt ghi đi qua
     * service. {@code UPDATE} thẳng thì hàng CSDL đổi mà {@code HydroSettings} vẫn đọc giá trị cũ —
     * bài kiểm sẽ đỏ vì <b>cách đo</b>, và ta sẽ đi sửa mã đúng.
     */
    private void datDanhSachCong(String giaTri) {
        settings.update("hydro.portal.station-codes", giaTri);
    }

    // === 1. Đường công khai đi được, và đi bằng đường TRÌNH DUYỆT ============

    @Test
    @DisplayName("⭐ Trình duyệt vô danh đọc được — ⛔ không cần đăng nhập, ⛔ không cần token CSRF")
    void anAnonymousBrowserCanReadIt() {
        ResponseEntity<String> ra = doc();

        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(ra.getBody()).contains("\"success\":true");
    }

    /**
     * ⭐ Vế thứ hai của cặp CSRF (§10.19): miễn CSRF cho {@code /public/**} là đúng, nhưng nó ⛔
     * KHÔNG được biến thành "mọi phương thức đều mở". Một {@code POST} vào tiền tố công khai phải
     * bị từ chối vì <b>không có endpoint nào như thế</b>, ⛔ không phải vì được bỏ qua trong im lặng.
     */
    @Test
    @DisplayName("⛔ POST vào đường công khai ⛔ không mở ra một đường ghi nào")
    void thePublicPrefixDoesNotOpenAWritePath() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.ORIGIN, NGUON_GOC);
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<String> ra = http.exchange(DUONG_DAN, HttpMethod.POST, new HttpEntity<>("{}", h), String.class);

        assertThat(ra.getStatusCode())
                .as("⛔ không được là 2xx — đường công khai chỉ ĐỌC")
                .isNotEqualTo(HttpStatus.OK)
                .isNotEqualTo(HttpStatus.CREATED);
    }

    // === 2. ⛔ Không một byte dữ liệu bịa nào =================================

    @Test
    @DisplayName("⭐⭐ Số đến từ CSDL — ghi một số vào CSDL thì cổng trả đúng số ấy")
    void everyNumberComesFromTheDatabase() {
        long id = taoDiemDo("THUONG_LUU");

        assertThat(doc().getBody())
                .as("⛔ §10.54: điểm đo chưa có số ⇒ ⛔ KHÔNG được có con số nào, và phải nói VÌ SAO")
                .contains("\"maDiemDo\":\"" + MA + "\"")
                .contains("\"mucNuocThuongLuu\":null")
                .contains("Điểm đo chưa gửi về số liệu nào");

        ghiSoDo(id, new java.math.BigDecimal("3.750"));

        assertThat(doc().getBody())
                .as("⭐ ghi 3.750 vào CSDL ⇒ cổng trả đúng 3.750, dạng CHUỖI (quy tắc 2)")
                .contains("\"mucNuocThuongLuu\":\"3.750\"")
                .contains("\"lyDoTrong\":null");
    }

    @Test
    @DisplayName("⭐ Giá trị rơi đúng cột theo vai trò vị trí; cột kia để trống")
    void theValueLandsInTheColumnMatchingThePositionRole() {
        long id = taoDiemDo("HA_LUU");
        ghiSoDo(id, new java.math.BigDecimal("1.200"));

        String than = doc().getBody();
        String dong = dongCua(than, MA);

        assertThat(dong).contains("\"mucNuocHaLuu\":\"1.200\"").contains("\"mucNuocThuongLuu\":null");
    }

    /**
     * ⛔⛔ Cột lượng mưa <b>luôn rỗng</b> và luôn kèm lý do — mục <b>G3-a</b>.
     *
     * <p>⛔ Đây ⛔ không phải một thiếu sót để "sửa cho đủ": loại chỉ số lượng mưa đã khai nhưng
     * chưa gắn cho điểm đo nào, và {@code 0 mm} là một <b>câu khẳng định về thời tiết</b>.
     */
    @Test
    @DisplayName("⛔ Lượng mưa LUÔN rỗng kèm lý do — ⛔ không bao giờ là 0 (G3-a)")
    void rainfallIsAlwaysEmptyWithAReason() {
        taoDiemDo("THUONG_LUU");

        String than = doc().getBody();

        assertThat(than).contains("\"luongMua\":null").contains("mục G3-a");
        assertThat(than)
                .as("⛔ `0 mm` là một khẳng định về thời tiết, và nó SAI")
                .doesNotContain("\"luongMua\":0")
                .doesNotContain("\"luongMua\":\"0");
    }

    @Test
    @DisplayName("⚠ Điểm đo mất tín hiệu VẪN có mặt, ô trống kèm lý do — ⛔ không bị lọc đi")
    void aSilentStationStaysOnTheTableWithAReason() {
        long id = taoDiemDo("THUONG_LUU");
        ghiSoDoCu(id, Duration.ofDays(10));

        String dong = dongCua(doc().getBody(), MA);

        assertThat(dong)
                .as("⛔ ẩn trạm im lặng là để lại một bảng sạch sẽ đúng lúc nó phải kêu")
                .contains("mất tín hiệu");

        // ⭐⭐ Vế chịu lực, và là vế mà bản đầu của service ĐÃ SAI: trạm im lặng 10 ngày vẫn còn
        //    `valid_value`, nên cổng hiện mực nước của mười ngày trước như một số HIỆN TẠI. ⛔ Không
        //    phải số bịa — là số THẬT đặt sai thì hiện tại, và trên một trang phòng chống thiên tai
        //    thì đó là thứ người ta ra quyết định dựa vào.
        assertThat(dong)
                .as("⛔ số cuối của một trạm đã chết ⛔ KHÔNG được công bố như mực nước hiện tại")
                .contains("\"mucNuocThuongLuu\":null")
                .doesNotContain("2.000");
    }

    @Test
    @DisplayName("⛔ Điểm đo đã NGỪNG ⛔ không ra cổng — quyết định của người vận hành, khác 'trạm hỏng'")
    void aRetiredStationIsNotPublished() {
        long id = taoDiemDo("THUONG_LUU");
        assertThat(doc().getBody()).contains(MA);

        jdbc.update("UPDATE stations SET active = FALSE WHERE id = ?", id);

        assertThat(doc().getBody()).doesNotContain(MA);
    }

    // === 3. ⛔ Rò rỉ trường nội bộ ===========================================

    /**
     * ⭐⭐ Bài canh rò rỉ, chép khuôn {@code PublicOperationStatusServiceTest} — và <b>khẳng định về
     * SỐ LƯỢNG</b> mới là vế chịu lực.
     *
     * <p>{@code getRecordComponents()} trả mảng rỗng nếu ai đó đổi record thành class:
     * {@code doesNotContain(...)} khi ấy xanh trọn vẹn trong khi ⛔ không kiểm gì cả (§10.62). Một
     * phép đếm ⛔ không chia sẻ giả định nào với danh sách tên (luật 29).
     */
    @Test
    @DisplayName("⛔ Mã API và đơn vị phụ trách ⛔ KHÔNG ra tới dây — đếm chính xác số trường")
    void internalKeysNeverReachTheWire() {
        List<String> truong = Arrays.stream(PublicHydroService.MucNuocRow.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(truong)
                .as("⚠ vế chống tập rỗng: record phải CÓ trường, nếu không mọi khẳng định dưới đây vô nghĩa")
                .hasSize(12);
        assertThat(truong)
                .as("⛔ `apiCode` là khoá đối soát với nguồn bên thứ 3 — công bố nó là công bố cách "
                        + "gọi thẳng nguồn của Công ty")
                .doesNotContain("apiCode", "apiSourceId", "orgUnitId", "orgUnitName", "publicId", "id");

        // ⭐ Và vế đi HTTP thật: record đúng mà controller trả DTO khác thì bài phản chiếu vẫn xanh
        //    (§10.68-D — khai một thứ và dùng một thứ chưa phải là nối nó).
        taoDiemDo("THUONG_LUU");
        assertThat(doc().getBody())
                .doesNotContain(MA_API)
                .doesNotContain("apiCode")
                .doesNotContain("orgUnit");
    }

    // === 4. ⭐ T35.8 — danh sách công bố cấu hình được từ admin ===============

    /**
     * ⛔⛔ Nhánh <b>RỖNG = TẤT CẢ</b>, và nó phải có bài kiểm riêng.
     *
     * <p>Đây là nhánh mà một người đọc mã sáu tháng nữa sẽ đọc ngược — "khoá rỗng thì lọc ra rỗng"
     * là cách đọc tự nhiên hơn. Nếu ai đó "sửa" theo cách đọc ấy thì cổng mất trắng bảng mực nước
     * và ⛔ không lỗi nào bắn: một danh sách rỗng trả về {@code []} hợp lệ, và giao diện hiện đúng
     * khối "chưa có dữ liệu" mà nó được dựng để hiện.
     */
    @Test
    @DisplayName("⭐ T35.8 — khoá RỖNG nghĩa là công bố TẤT CẢ, ⛔ không phải 'không công bố gì'")
    void anEmptyPortalListPublishesEveryActiveStation() {
        taoDiemDo("THUONG_LUU");
        datDanhSachCong("");

        assertThat(doc().getBody())
                .as(
                        """
                        ⛔ Rỗng = TẤT CẢ. Đọc ngược thành "không công bố gì" là tự quyết định thay Công ty \
                        rằng OI-03 chưa chốt nghĩa là giấu đi — và nó xoá 19 dòng SỐ THẬT đang chạy.""")
                .contains(MA);
    }

    @Test
    @DisplayName("⭐ T35.8 — khoá có mã điểm đo thì điểm đo ấy lên cổng")
    void aListedStationIsPublished() {
        taoDiemDo("THUONG_LUU");
        datDanhSachCong(MA);

        assertThat(doc().getBody()).contains(MA);
    }

    /**
     * ⭐⭐ Vế chịu lực: bộ lọc phải <b>thật sự lọc</b>.
     *
     * <p>Không có bài này thì hai bài trên xanh trọn vẹn kể cả khi {@code maDiemDoLenCong()} bị bỏ
     * qua hoàn toàn — cả hai chỉ khẳng định "có mặt", và một bộ lọc <b>chưa từng chạy</b> cũng cho
     * ra đúng kết quả ấy (luật 9: một khẳng định không phân biệt được hai trạng thái thì ⛔ không
     * khẳng định gì).
     */
    @Test
    @DisplayName("⭐⭐ T35.8 — điểm đo NGOÀI danh sách ⛔ KHÔNG lên cổng (bộ lọc thật sự chạy)")
    void anUnlistedStationIsFilteredOut() {
        taoDiemDo("THUONG_LUU");
        assertThat(doc().getBody()).as("tiền đề: rỗng thì nó có mặt").contains(MA);

        datDanhSachCong("MOT-MA-KHAC");

        assertThat(doc().getBody())
                .as("⛔ danh sách khác rỗng và ⛔ không chứa mã này ⇒ điểm đo phải biến mất khỏi cổng")
                .doesNotContain(MA);
    }

    @Test
    @DisplayName("⚠ T35.8 — mã gõ thừa dấu cách và viết thường vẫn khớp; ⛔ chỉ có thế mới dùng được")
    void codesAreTrimmedAndCaseInsensitive() {
        taoDiemDo("THUONG_LUU");
        datDanhSachCong("  " + MA.toLowerCase(java.util.Locale.ROOT) + " , MOT-MA-KHAC ");

        assertThat(doc().getBody())
                .as("người vận hành dán danh sách từ Excel — dấu cách và chữ thường là chuyện thường ngày")
                .contains(MA);
    }

    // === Helper ==============================================================

    /** ⚠ LUÔN mang `Origin` — xem {@link #NGUON_GOC}. */
    private ResponseEntity<String> doc() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.ORIGIN, NGUON_GOC);
        return http.exchange(DUONG_DAN, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private static String dongCua(String than, String ma) {
        int i = than.indexOf("\"maDiemDo\":\"" + ma + "\"");
        assertThat(i).as("phản hồi phải chứa điểm đo %s", ma).isGreaterThanOrEqualTo(0);
        int tu = than.lastIndexOf('{', i);
        int den = than.indexOf('}', i);
        return than.substring(tu, den + 1);
    }

    private long taoDiemDo(String vaiTro) {
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        assertThat(idNguon).as("⚠ vế chống tập rỗng: phải có nguồn seed").isNotNull();
        return jdbc.queryForObject(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, created_at)
                VALUES (?, 'Điểm đo kiểm thử cổng', ?, ?, ?, TRUE, now())
                RETURNING id
                """,
                Long.class,
                MA,
                MA_API,
                idNguon,
                vaiTro);
    }

    private void ghiSoDo(long idDiemDo, java.math.BigDecimal giaTri) {
        ghi(idDiemDo, giaTri, Duration.ofMinutes(1));
    }

    private void ghiSoDoCu(long idDiemDo, Duration truocDay) {
        ghi(idDiemDo, new java.math.BigDecimal("2.000"), truocDay);
    }

    private void ghi(long idDiemDo, java.math.BigDecimal giaTri, Duration truocDay) {
        Long idLoai = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        Instant moc = Instant.now().minus(truocDay);
        jdbc.update("DELETE FROM hydro_latest WHERE station_id = ?", idDiemDo);
        jdbc.update(
                """
                INSERT INTO hydro_latest (
                    station_id, measurement_type_id, last_seen_at, last_quality, last_source,
                    valid_measured_at, valid_value)
                VALUES (?, ?, ?, 'HOP_LE', 'API', ?, ?)
                """,
                idDiemDo,
                idLoai,
                java.sql.Timestamp.from(moc),
                java.sql.Timestamp.from(moc),
                giaTri);
    }
}
