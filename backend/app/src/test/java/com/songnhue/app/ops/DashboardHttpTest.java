package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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

    // === 2. Ô chưa có nguồn ===================================================

    @Test
    @DisplayName("⛔ Hai ô thuỷ văn chưa có nguồn trả rỗng kèm lý do — KHÔNG phải số 0")
    void unavailableKpisSayWhyInsteadOfShowingZero() {
        String than = phienHttp.get(duQuyen, DUONG_DAN).getBody();

        // ⚠ Danh sách này rút từ bốn xuống hai ở WS-18: hai ô sửa chữa / sự cố nay có nguồn thật, và
        //   từ đó số 0 của CHÚNG là một câu khẳng định đúng ("đã đếm, không có bản ghi nào đang mở").
        //   Hai ô còn lại thuộc MOD-03, chưa có gì để đếm.
        for (String khoa : new String[] {"hydro.active-alerts", "hydro.stations-offline"}) {
            String o = oKpi(than, khoa);
            assertThat(o)
                    .as("ô %s: số 0 nghĩa là 'đã đo và bằng không', khác hẳn 'chưa đo'", khoa)
                    .contains("\"value\":null")
                    .doesNotContain("\"value\":0");
            assertThat(o)
                    .as("ô %s phải nói vì sao trống và bao giờ có số", khoa)
                    .contains("\"unavailableReason\":\"")
                    .contains("\"availableIn\":\"");
        }
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
                null,
                null,
                null,
                null);
    }

    private void donDep() {
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T23D-%'");
    }
}
