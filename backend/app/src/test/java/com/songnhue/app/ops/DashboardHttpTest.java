package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.operations.application.ConstructionForm;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.application.DashboardService;
import com.songnhue.operations.application.MapConfigService;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;

/**
 * Dashboard điều hành <b>đi qua HTTP</b> — T23.11.
 *
 * <p>Ba nhóm khẳng định, và nhóm thứ hai mới là nhóm đáng giá:
 *
 * <ol>
 *   <li><b>Số liệu đúng</b> — KPI khớp với dữ liệu công trình thật.
 *   <li><b>Ô chưa có nguồn nói thẳng là chưa có</b> — {@code value} rỗng kèm lý do, <b>không phải
 *       số 0</b>. Đây là chỗ dễ đi sai nhất và cũng là chỗ sai đắt nhất: "Sự cố chưa xử lý: 0" là
 *       một câu khẳng định, và người trực ca sẽ tin nó.
 *   <li><b>Tham số cấu hình có tác dụng thật</b> — đổi giá trị trong {@code settings} thì phản hồi
 *       đổi theo, hỏi đúng câu hỏi mà WS-12 đã trả giá để học: <i>"đổi tham số thì hành vi có đổi
 *       theo không"</i>, chứ không phải "mã có đọc được một con số không".
 * </ol>
 */
// PER_CLASS để @BeforeAll không phải static — nó cần các bean được tiêm vào thực thể.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardHttpTest extends IntegrationTestBase {

    private static final String DUONG_DAN = "/api/v1/ops/dashboard";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private SettingService settings;

    @Autowired
    private ConstructionService constructions;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien duQuyen;
    private PhienHttp.Phien khongQuyen;
    private UUID donViGoc;

    /**
     * ⚠⚠ Đăng nhập <b>một lần cho cả lớp</b>, không phải mỗi bài kiểm một lần.
     *
     * <p>Bản đầu đăng nhập trong {@code @BeforeEach}: xanh khi chạy riêng, và <b>8/10 bài đỏ khi
     * chạy cả bộ</b> với {@code SYS-0002 · 429 Too Many Requests}. Nguyên nhân không nằm ở dashboard:
     * hạn mức đăng nhập là <b>30 lượt / 15 phút theo IP</b> (conventions.md §4.5), mọi bài kiểm HTTP
     * đều đi từ {@code 127.0.0.1}, và bộ đếm là Caffeine trong tiến trình nên nó <b>dùng chung cho
     * toàn bộ lượt chạy</b>. Riêng lớp này 10 bài × 2 tài khoản = 20 lượt, cộng với các lớp HTTP
     * chạy trước là vượt trần.
     *
     * <p>⛔ Cách chữa <i>sai</i> là nới hạn mức ở hồ sơ kiểm thử — làm thế thì một cơ chế bảo mật
     * thật không còn được chạy qua ở CI. Cách đúng là dùng ít vé hơn: phiên không bị thu hồi giữa
     * các bài, nên hai lượt đăng nhập cho cả lớp là đủ.
     *
     * <p>📌 Đây là một <b>ngân sách dùng chung và hữu hạn</b> giữa mọi lớp kiểm thử HTTP. Lớp nào
     * thêm sau cũng phải đăng nhập ở {@code @BeforeAll}, nếu không nó sẽ làm đỏ một lớp <i>khác</i>
     * — và người đọc log sẽ đi tìm lỗi ở đúng chỗ không có lỗi nào.
     */
    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);

        // TECHNICIAN có `ops:dashboard:view` theo ma trận §6 — dùng vai trò thật thay vì ADMIN, để
        // bài kiểm đồng thời chứng minh ma trận cấp đủ quyền cho đúng người.
        duQuyen = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "dash_full", "TECHNICIAN"));
        khongQuyen = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "dash_zero"));
    }

    @BeforeEach
    void setUp() {
        donDep();
    }

    @AfterEach
    void tearDown() {
        donDep();
        // Trả tham số về mặc định: một bài kiểm để lại giá trị lạ trong `settings` sẽ làm bài kiểm
        // khác đỏ ở một chỗ chẳng liên quan gì, và người đọc sẽ đi tìm nguyên nhân ở đó.
        settings.update(DashboardService.KEY_AUTO_REFRESH, "5");
        settings.update(MapConfigService.KEY_TILE_URL, MapConfigService.TILE_URL_MAC_DINH);
        jdbc.update(
                "UPDATE settings SET setting_value = ? WHERE setting_key = ?",
                "20.9800",
                MapConfigService.KEY_CENTER_LAT);
        settings.invalidate(MapConfigService.KEY_CENTER_LAT);
    }

    // === 1. Số liệu thật ======================================================

    @Test
    @DisplayName("⭐ KPI đếm đúng công trình thật, và mẫu số là tổng số hồ sơ")
    void kpiCountsRealConstructions() {
        constructions.create(hoSo("T23D-001", "Trạm bơm một"));
        constructions.create(hoSo("T23D-002", "Trạm bơm hai"));
        UUID thanhLy = constructions.create(hoSo("T23D-003", "Trạm bơm ba")).getPublicId();
        constructions.changeLifecycle(thanhLy, LifecycleState.DA_THANH_LY, "hư hỏng không sửa được");

        ResponseEntity<String> phanHoi = phienHttp.get(duQuyen, DUONG_DAN);
        assertThat(phanHoi.getStatusCode()).as("%s", phanHoi.getBody()).isEqualTo(HttpStatus.OK);

        String than = phanHoi.getBody();
        assertThat(than).isNotNull();
        assertThat(oKpi(than, "construction.active"))
                .as("hai hồ sơ đang hoạt động trên tổng ba — công trình đã thanh lý không được tính vào tử số")
                .contains("\"value\":2")
                .contains("\"total\":3");
    }

    @Test
    @DisplayName("Công trình chưa có toạ độ được đếm riêng — hồ sơ vắng mặt trên bản đồ thì không ai thấy")
    void withoutLocationIsCounted() {
        constructions.create(hoSo("T23D-010", "Chưa số hoá"));

        String than = phienHttp.get(duQuyen, DUONG_DAN).getBody();

        assertThat(oKpi(than, "construction.without-location")).contains("\"value\":1");
    }

    @Test
    @DisplayName("Marker mang tên đơn vị — popup M2.10 đòi Xí nghiệp, không để giao diện gọi thêm lượt nữa")
    void mapPointsCarryOrgUnitName() {
        UUID id = constructions.create(hoSoCoToaDo("T23D-020", "Có toạ độ")).getPublicId();
        assertThat(id).isNotNull();

        String than = phienHttp.get(duQuyen, DUONG_DAN + "/map-points").getBody();

        assertThat(than).contains("T23D-020").contains("\"orgUnitName\":");
        assertThat(than).doesNotContain("\"orgUnitName\":null");
    }

    // === 2. Hai ô thuỷ văn — T35.3, nay CÓ NGUỒN ==============================

    /**
     * ⚠⚠ <b>Khẳng định ở đây đã bị ĐẢO ở WS-35, ⛔ không phải nới ra.</b>
     *
     * <p>Từ WS-23 tới 04/09/2026 bài này khẳng định điều ngược lại — {@code "value":null} và
     * {@code doesNotContain("\"value\":0")} — vì MOD-03 chưa tồn tại và một ô "Cảnh báo thuỷ văn: 0"
     * là câu khẳng định sai. Nay hai ô có nguồn thật nên số 0 trở thành câu khẳng định <b>đúng</b>,
     * và khẳng định cũ phải chết chứ không được nới thành "null hoặc số".
     *
     * <p>⭐⭐ Vì sao bài này dựng dữ liệu thay vì chỉ đọc: khẳng định {@code "value":0} ⛔ <b>không
     * phân biệt được hai trạng thái</b> — "đã nối cổng và đếm được 0" trông y hệt "cổng chưa nối,
     * trả 0 ghi cứng". Đó đúng là luật 9 (<i>một khẳng định không phân biệt được hai trạng thái thì
     * không khẳng định gì</i>), và với hai ô vừa chuyển từ null sang số thì nó là rủi ro thật, không
     * phải rủi ro lý thuyết. ⇒ Bài này ép cả hai ô ra một số <b>khác 0</b>.
     */
    @Test
    @DisplayName("⭐ Hai ô thuỷ văn nay đếm dữ liệu THẬT — một số khác 0, không còn 'chưa có nguồn'")
    void hydroKpisNowCountRealData() {
        long idDiemDo = taoDiemDoImLang("T35D-001", "F99001", Duration.ofDays(10));
        taoCanhBaoDangMo(idDiemDo, true);

        String than = phienHttp.get(duQuyen, DUONG_DAN).getBody();

        String oCanhBao = oKpi(than, "hydro.active-alerts");
        assertThat(oCanhBao)
                .as("⭐ khác 0: một cảnh báo DANG_XAY_RA đã xác nhận vừa được dựng")
                .contains("\"value\":1")
                .doesNotContain("\"value\":null");

        String oMatTinHieu = oKpi(than, "hydro.stations-offline");
        assertThat(oMatTinHieu)
                .as("⭐ đúng MỘT điểm đo im lặng quá ngưỡng — 19 điểm seed chưa từng có bản ghi nào "
                        + "nên chúng là CHUA_CO_DU_LIEU, ⛔ không phải MAT_TIN_HIEU")
                .contains("\"value\":1")
                .doesNotContain("\"value\":null");

        // ⚠ Mẫu số phải là số điểm đo ĐANG DÙNG, và nó phải lớn hơn tử số. Khẳng định về QUAN HỆ
        //   giữa hai số, ⛔ không phải một hằng số 20 — con số ấy đổi mỗi lần ai đó thêm một bài
        //   kiểm dựng điểm đo, và một bài kiểm đỏ vì lý do sai còn tệ hơn không có bài kiểm.
        long tong = soTrongO(oMatTinHieu, "total");
        assertThat(tong).as("mẫu số gồm cả 19 điểm đo seed + điểm vừa dựng").isGreaterThan(1L);

        for (String khoa : new String[] {"hydro.active-alerts", "hydro.stations-offline"}) {
            assertThat(oKpi(than, khoa))
                    .as("ô %s đã có nguồn thì ⛔ không còn được hẹn 'sẽ có ở Phase 2' nữa", khoa)
                    .contains("\"unavailableReason\":null")
                    .contains("\"availableIn\":null");
        }
    }

    /**
     * ⭐ Vế {@code confirmed_at IS NOT NULL} là <b>vế chịu lực</b>, và nó có bài kiểm riêng.
     *
     * <p>Một dòng {@code DANG_XAY_RA} chưa xác nhận là điều kiện <i>đang được theo dõi</i> — chưa ai
     * nhận thông báo nào về nó. Đếm nó vào ô KPI là để một cú nhiễu cảm biến 2 phút hiện lên màn
     * hình trực ban như một cảnh báo thật.
     *
     * <p>⛔ Đây cũng là bài giữ cho ô KPI và <b>mắt xích 3</b> của {@code ConstructionStatusService}
     * dùng chung một định nghĩa: cả hai đi qua hằng số
     * {@code AlertEventQueryRepository#DIEU_KIEN_DANG_CANH_BAO}. Nếu ai đó chép vị từ ra làm hai
     * câu rồi nới một câu, bài này đỏ.
     */
    @Test
    @DisplayName("⛔ Cảnh báo CHƯA xác nhận không được đếm — nếu không, một cú nhiễu 2 phút thành cảnh báo")
    void unconfirmedAlertsAreNotCounted() {
        long idDiemDo = taoDiemDoImLang("T35D-002", "F99002", Duration.ofDays(10));
        taoCanhBaoDangMo(idDiemDo, false);

        String than = phienHttp.get(duQuyen, DUONG_DAN).getBody();

        assertThat(oKpi(than, "hydro.active-alerts"))
                .as("dòng DANG_XAY_RA nhưng confirmed_at NULL ⇒ chưa tính là cảnh báo")
                .contains("\"value\":0");
    }

    /**
     * Dựng một điểm đo đã im lặng quá ngưỡng.
     *
     * <p>⚠ Phải ghi {@code hydro_latest}: một điểm đo <b>chưa từng</b> có bản ghi nào là
     * {@code CHUA_CO_DU_LIEU}, ⛔ không phải {@code MAT_TIN_HIEU} — đó chính là ranh giới mà
     * {@code StationDisplayStatus} dựng nên để ngày triển khai đầu tiên không sinh 19 cảnh báo giả.
     */
    private long taoDiemDoImLang(String ma, String maApi, Duration imLangBaoLau) {
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        Long idLoai = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        assertThat(idNguon)
                .as("⚠ vế chống tập rỗng: không có nguồn seed thì điểm đo dưới đây không dựng được")
                .isNotNull();
        assertThat(idLoai)
                .as("⚠ vế chống tập rỗng: loại chỉ số MUC_NUOC phải có trong seed")
                .isNotNull();

        Long idDiemDo = jdbc.queryForObject(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, created_at)
                VALUES (?, 'Điểm đo kiểm thử KPI', ?, ?, 'MN_SONG', TRUE, now())
                RETURNING id
                """,
                Long.class,
                ma,
                // ⚠ `ck_stations_api_code_format` ép ĐÚNG `^F[0-9]{5}$` — mã truyền vào, ⛔ không suy
                //   từ `code`: lượt viết đầu ghép "F9" + "-001" và ràng buộc bắt được ngay. Dải F99xxx
                //   nằm ngoài mọi mã thật của nguồn bhh40 nên ⛔ không đụng 19 điểm đo seed.
                maApi,
                idNguon);

        java.time.Instant mocCu = java.time.Instant.now().minus(imLangBaoLau);
        jdbc.update(
                """
                INSERT INTO hydro_latest (
                    station_id, measurement_type_id, last_seen_at, last_quality, last_source,
                    valid_measured_at, valid_value)
                VALUES (?, ?, ?, 'HOP_LE', 'API', ?, 2.400)
                """,
                idDiemDo,
                idLoai,
                java.sql.Timestamp.from(mocCu),
                java.sql.Timestamp.from(mocCu));
        return idDiemDo;
    }

    /** Dựng một mức + một quy tắc + một sự kiện đang mở cho điểm đo đã cho. */
    private void taoCanhBaoDangMo(long idDiemDo, boolean daXacNhan) {
        Long idLoai = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        Long idMuc = jdbc.queryForObject(
                """
                INSERT INTO alert_levels (code, name, color_token, severity_rank, created_at)
                VALUES (?, 'Mức kiểm thử KPI', 'alert-level-1', ?, now())
                RETURNING id
                """,
                Long.class,
                "T35D-MUC-" + idDiemDo,
                // severity_rank là UNIQUE — lấy theo id điểm đo để hai bài kiểm không đụng nhau.
                (int) (900 + idDiemDo % 90));
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

        java.time.Instant batDau = java.time.Instant.now().minus(Duration.ofHours(1));
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
                daXacNhan ? java.sql.Timestamp.from(batDau) : null,
                java.sql.Timestamp.from(batDau));
    }

    /** Đọc một số nguyên trong ô KPI đã cắt ra — ⛔ không dựng cả cây JSON chỉ để lấy một con số. */
    private static long soTrongO(String oKpi, String truong) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"" + truong + "\":(\\d+)").matcher(oKpi);
        assertThat(m.find()).as("ô KPI phải có trường %s là một số", truong).isTrue();
        return Long.parseLong(m.group(1));
    }

    @Test
    @DisplayName("⭐ Hai ô của WS-18 nay có số thật, và không còn hẹn 'sẽ có ở WS-18' nữa")
    void theTwoMaintenanceKpisNowHaveARealSource() {
        String than = phienHttp.get(duQuyen, DUONG_DAN).getBody();

        for (String khoa : new String[] {"maintenance.in-progress", "incident.open"}) {
            assertThat(oKpi(than, khoa))
                    .as("ô %s: một lời hẹn không bao giờ tới hạn là một ô không ai còn nhìn", khoa)
                    .containsPattern("\"value\":\\d+")
                    .doesNotContain("\"availableIn\":\"WS-18");
        }
    }

    @Test
    @DisplayName("⛔ Không dựng được một KPI rỗng mà không nói lý do")
    void aBlankKpiMustExplainItself() {
        // Kiểm chứng ngược cho ràng buộc ở hàm dựng: nó chặn được ô KPI thứ mười một mà WS-18 hay
        // Phase 2 thêm vào sau, không chỉ chặn bốn ô đang có.
        assertThatThrownBy(() -> new DashboardService.Kpi(
                        "ban.quen", "Ô quên lý do", null, null, DashboardService.Tone.UNKNOWN, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ban.quen");

        assertThatThrownBy(() -> new DashboardService.Kpi(
                        "ban.trong", "Ô lý do rỗng", null, null, DashboardService.Tone.UNKNOWN, "   ", "WS-18"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // === 3. Tham số cấu hình có tác dụng thật =================================

    @Test
    @DisplayName("⭐ Đổi chu kỳ làm mới trong settings thì phản hồi đổi theo — không phải công tắc chết")
    void refreshCadenceFollowsTheSetting() {
        assertThat(phienHttp.get(duQuyen, DUONG_DAN).getBody())
                .as("mặc định seed là 5 phút")
                .contains("\"autoRefreshSeconds\":300");

        settings.update(DashboardService.KEY_AUTO_REFRESH, "2");

        assertThat(phienHttp.get(duQuyen, DUONG_DAN).getBody())
                .as("đọc lúc chạy chứ không chốt lúc dựng bean — nếu không thì tham số M2.15 bày ra "
                        + "màn hình cấu hình chỉ là một ô nhập không nối vào đâu")
                .contains("\"autoRefreshSeconds\":120");
    }

    @Test
    @DisplayName("Nguồn ảnh bản đồ đọc từ settings — đổi nguồn không phải dựng lại ảnh admin-app")
    void mapTileSourceComesFromSettings() {
        assertThat(phienHttp.get(duQuyen, DUONG_DAN).getBody()).contains(MapConfigService.TILE_URL_MAC_DINH);

        settings.update(MapConfigService.KEY_TILE_URL, "https://tile.noi-bo.example/{z}/{x}/{y}.png");

        assertThat(phienHttp.get(duQuyen, DUONG_DAN).getBody()).contains("tile.noi-bo.example");
    }

    @Test
    @DisplayName("Toạ độ tâm hỏng thì rơi về mặc định, không làm cả màn hình trả 500")
    void brokenCenterFallsBackInsteadOfFailing() {
        // ⭐ Bản đầu của bài kiểm này gọi `settings.update(...)` và nhận `ADM-2006` — tức là **đường
        // giao diện không tạo ra được giá trị hỏng**, cột `validation` (`min=-90;max=90`) chặn từ
        // trước. Đó là tin tốt, và nó cũng nói rằng lớp dự phòng ở MapConfigService bảo vệ một
        // đường khác: lượt UPDATE chạy tay trên CSDL, hoặc một migration ghi nhầm. Nên bài kiểm đi
        // đúng đường đó — ghi thẳng vào bảng rồi dọn bộ nhớ đệm.
        jdbc.update(
                "UPDATE settings SET setting_value = ? WHERE setting_key = ?",
                "hai mươi mốt",
                MapConfigService.KEY_CENTER_LAT);
        settings.invalidate(MapConfigService.KEY_CENTER_LAT);

        ResponseEntity<String> phanHoi = phienHttp.get(duQuyen, DUONG_DAN);

        assertThat(phanHoi.getStatusCode())
                .as("một ký tự thừa trong ô toạ độ tâm không đáng để cả màn hình điều hành trả 500")
                .isEqualTo(HttpStatus.OK);
        assertThat(phanHoi.getBody()).contains("\"centerLat\":20.98");
    }

    // === Quyền + envelope =====================================================

    @Test
    @DisplayName("Thiếu quyền ops:dashboard:view → AUTH-3001, và lỗi vẫn có traceId")
    void withoutPermissionItIsRefusedWithATraceId() {
        ResponseEntity<String> phanHoi = phienHttp.get(khongQuyen, DUONG_DAN);

        assertThat(phanHoi.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(phanHoi.getBody())
                .contains("AUTH-3001")
                .contains("\"success\":false")
                .contains("\"traceId\":\"");

        assertThat(phienHttp.get(khongQuyen, DUONG_DAN + "/map-points").getStatusCode())
                .as("bản đồ tổng quan cũng phải chặn — không thì danh sách công trình rò ra qua marker")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Phản hồi thành công đi trong envelope chuẩn kèm traceId")
    void successAlsoTravelsInTheEnvelope() {
        String than = phienHttp.get(duQuyen, DUONG_DAN).getBody();

        assertThat(than).contains("\"success\":true").contains("\"traceId\":\"").contains("\"generatedAt\":");
    }

    // -------------------------------------------------------------------------

    /** Cắt lấy đoạn JSON của một ô KPI theo khoá — để phép khẳng định không trượt sang ô bên cạnh. */
    private static String oKpi(String than, String khoa) {
        assertThat(than).isNotNull();
        int bat = than.indexOf("\"key\":\"" + khoa + "\"");
        assertThat(bat)
                .as("không thấy ô KPI '%s' trong phản hồi: %s", khoa, than)
                .isNotNegative();
        int het = than.indexOf('}', bat);
        return than.substring(bat, het < 0 ? than.length() : het);
    }

    /** Hồ sơ mới luôn bắt đầu ở {@link LifecycleState#DANG_HOAT_DONG} — đổi vòng đời đi bằng API riêng. */
    private ConstructionForm hoSo(String ma, String ten) {
        return form(ma, ten, null, null);
    }

    private ConstructionForm hoSoCoToaDo(String ma, String ten) {
        return form(ma, ten, new BigDecimal("20.980000"), new BigDecimal("105.780000"));
    }

    private ConstructionForm form(String ma, String ten, BigDecimal lat, BigDecimal lng) {
        return new ConstructionForm(
                ma,
                ten,
                ConstructionType.TRAM_BOM,
                null,
                donViGoc,
                ManagementLevel.XI_NGHIEP,
                null,
                null,
                lat,
                lng,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                // Hai cột tài liệu công bố (CR-28) — bài kiểm này không dùng tới.
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private void donDep() {
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T23D-%'");

        // ⚠ Dọn theo THỨ TỰ KHOÁ NGOẠI, từ lá về gốc: alert_events → alert_rules → alert_levels, và
        //   hydro_latest trước stations. Sai thứ tự thì lỗi ràng buộc nổ ở @AfterEach của bài kiểm
        //   này và làm đỏ một lớp KHÁC — người đọc log sẽ đi tìm lỗi ở đúng chỗ không có lỗi nào.
        jdbc.update("DELETE FROM alert_events WHERE station_id IN (SELECT id FROM stations WHERE code LIKE 'T35D-%')");
        jdbc.update("DELETE FROM alert_rules WHERE station_id IN (SELECT id FROM stations WHERE code LIKE 'T35D-%')");
        jdbc.update("DELETE FROM alert_levels WHERE code LIKE 'T35D-MUC-%'");
        jdbc.update("DELETE FROM hydro_latest WHERE station_id IN (SELECT id FROM stations WHERE code LIKE 'T35D-%')");
        jdbc.update("DELETE FROM stations WHERE code LIKE 'T35D-%'");
    }
}
