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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * ⭐⭐ Vòng khép kín của WS-32 đi qua <b>HTTP thật</b> — T32.5 · T32.6 · T32.7 · T32.8.
 *
 * <h2>Vì sao phải là HTTP, ⛔ không phải gọi thẳng service</h2>
 *
 * <p>Luật 5: <i>bài kiểm gọi thẳng service không đi cùng đường với production</i>. Ba thứ chỉ tồn
 * tại trên đường HTTP và cả ba đều đã từng hỏng riêng lẻ trong dự án này: {@code @RequirePermission}
 * (T27.20 — biểu mẫu đứng sau một quyền vai trò sở hữu nó không có), tuần tự hoá {@code BigDecimal}
 * (T28.27 rồi V2 — {@code 2.30} thành {@code 2.3}), và ánh xạ mã lỗi nghiệp vụ sang HTTP status.
 *
 * <h2>⚠ Ba vai trò THẬT, ⛔ không dựng vai trò tạm mang sẵn quyền cần thiết</h2>
 *
 * <ul>
 *   <li>{@code TECHNICIAN} — vai trò <b>duy nhất</b> ngoài quản trị có {@code hyd:measurement:review};
 *   <li>{@code DUTY_OFFICER} — người <b>đang trực</b>: có {@code :create} (mới ở
 *       {@code V202609041061}) mà ⛔ <b>không</b> có {@code :review}. Đây là khẳng định chịu lực của
 *       cả lớp: nếu ô nhập tay bị gác bằng {@code :review} thì đúng người cần nó nhất bị 403;
 *   <li>một tài khoản <b>không vai trò</b> — vế phân biệt, ⛔ không có gì cả.
 * </ul>
 *
 * <p>Dựng một vai trò tạm mang sẵn quyền cần thiết là kiểm cái mình vừa cấp cho mình, ⛔ không kiểm
 * ma trận thật.
 *
 * <h2>⚠ ⛔ Không giả định CSDL rỗng</h2>
 *
 * <p>Bộ kiểm dùng chung một CSDL. Mọi khẳng định danh sách lọc theo <b>điểm đo riêng của lớp này</b>,
 * và {@code @AfterAll} khẳng định ngay tại chỗ dọn — một bản dọn hỏng trong im lặng làm
 * {@code HydroCatalogueSeedTest} đỏ ở một lớp khác với thông điệp chẳng liên quan.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HydroQualityHttpTest extends IntegrationTestBase {

    /** ⚠ Dải {@code F9xxxx} để ⛔ không đụng 19 mã seed thật. */
    private static final String MA_API = "F97032";

    private static final String MA_DIEM_DO = "T32-DIEMDO";

    /**
     * ⭐ Điểm đo THỨ HAI, dùng riêng cho hai bài khẳng định trên {@code hydro_latest}.
     *
     * <p>⚠ Bảng ấy giữ <b>một dòng cho mỗi (điểm đo × loại chỉ số)</b> và luôn phản ánh bản ghi mới
     * nhất — nên hai bài cùng ghi lên một điểm đo sẽ đọc thấy kết quả của nhau, và bài nào chạy
     * trước thì tuỳ JUnit. Đó là một bài kiểm chỉ đúng khi nó chạy một mình, tức là một bài kiểm sẽ
     * đỏ vào ngày xấu nhất. Tách điểm đo là cách rẻ nhất để loại hẳn phụ thuộc thứ tự.
     */
    private static final String MA_DIEM_DO_LATEST = "T32-LATEST";

    private static final String MA_API_LATEST = "F97033";

    /** Khớp vỏ bọc seed ở {@code V202609041061}: {@code {"MUC_NUOC":{"min":-10,"max":30}}}. */
    private static final String NGOAI_KHOANG = "493.000";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private PhienHttp phienHttp;
    private PhienHttp.Phien kyThuat;
    private PhienHttp.Phien trucBan;
    private PhienHttp.Phien khongQuyen;

    private long idDiemDo;
    private long idDiemDoLatest;
    private long idLoaiChiSo;
    private String publicIdDiemDo;
    private String publicIdDiemDoLatest;
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
        taoDiemDo(MA_DIEM_DO_LATEST, MA_API_LATEST, idNguon);

        Map<String, Object> diemDo = jdbc.queryForMap("SELECT id, public_id FROM stations WHERE code = ?", MA_DIEM_DO);
        idDiemDo = ((Number) diemDo.get("id")).longValue();
        publicIdDiemDo = diemDo.get("public_id").toString();

        Map<String, Object> diemDo2 =
                jdbc.queryForMap("SELECT id, public_id FROM stations WHERE code = ?", MA_DIEM_DO_LATEST);
        idDiemDoLatest = ((Number) diemDo2.get("id")).longValue();
        publicIdDiemDoLatest = diemDo2.get("public_id").toString();

        mocGoc = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(6));

        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "q32_kythuat", "TECHNICIAN"));
        trucBan = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "q32_trucban", "DUTY_OFFICER"));
        khongQuyen = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "q32_trongtron"));
    }

    /**
     * ⛔ Dọn <b>và khẳng định ngay tại chỗ dọn</b>.
     *
     * <p>{@code HydroCatalogueSeedTest} khẳng định danh mục có <b>đúng 19 điểm đo</b>. Một bản dọn
     * hỏng trong im lặng làm bài ấy đỏ ở một lớp khác, với một thông điệp không hề chỉ về phía
     * nguyên nhân — đúng loại lỗi khó lần nhất.
     */
    private void taoDiemDo(String ma, String maApi, long idNguon) {
        jdbc.update(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, created_at)
                VALUES (?, 'Điểm đo kiểm thử chất lượng', ?, ?, 'MN_SONG', TRUE, now())
                """,
                ma,
                maApi,
                idNguon);
    }

    @AfterAll
    void donSachSauCung() {
        for (long id : new long[] {idDiemDo, idDiemDoLatest}) {
            jdbc.update("DELETE FROM hydro_readings WHERE station_id = ?", id);
            jdbc.update("DELETE FROM hydro_latest WHERE station_id = ?", id);
            jdbc.update("DELETE FROM stations WHERE id = ?", id);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE deleted_at IS NULL", Long.class))
                .as("⛔ Khẳng định NGAY TẠI CHỖ DỌN — xem javadoc")
                .isEqualTo(19L);
    }

    // =========================================================================
    // Dựng dữ liệu
    // =========================================================================

    /** Chèn thẳng một dòng số đo — ⚠ ⛔ không đi qua bộ phân loại, vì bài này đo <b>đường duyệt</b>. */
    private long chen(Instant moc, String giaTri, String chatLuong, String lyDoMay) {
        return chenVao(idDiemDo, moc, giaTri, chatLuong, lyDoMay);
    }

    private long chenVao(long stationId, Instant moc, String giaTri, String chatLuong, String lyDoMay) {
        return jdbc.queryForObject(
                """
                INSERT INTO hydro_readings (
                    measured_at, station_id, measurement_type_id, reading_value, quality, quality_reason, source)
                VALUES (?, ?, ?, ?::numeric, ?, ?, 'API')
                RETURNING id
                """,
                Long.class,
                Timestamp.from(moc),
                stationId,
                idLoaiChiSo,
                giaTri,
                chatLuong,
                lyDoMay);
    }

    /** @return mốc đo — <b>địa chỉ</b> của dòng vừa chèn, ⛔ không phải khoá tự tăng */
    private Instant chenNghiNgo(int phutTruoc, String giaTri) {
        Instant moc = mocGoc.plus(Duration.ofMinutes(phutTruoc));
        chen(moc, giaTri, "NGHI_NGO", "Giá trị " + giaTri + " ngoài khoảng vật lý [-10 … 30]");
        return moc;
    }

    private JsonNode than(ResponseEntity<String> ra) {
        try {
            return json.readTree(ra.getBody()).path("data");
        } catch (Exception e) {
            throw new IllegalStateException("Thân phản hồi không phải JSON: " + ra.getBody(), e);
        }
    }

    /**
     * ⭐ Địa chỉ hoá bằng <b>khoá tự nhiên</b>, ⛔ không phải khoá tự tăng trên URL —
     * {@code ApiSurfaceRuleTest} cấm mọi {@code @PathVariable} kiểu số.
     */
    private ResponseEntity<String> thaoTac(PhienHttp.Phien phien, Instant moc, String action, String lyDo) {
        return thaoTacTai(phien, publicIdDiemDo, moc, action, lyDo);
    }

    private ResponseEntity<String> thaoTacTai(
            PhienHttp.Phien phien, String publicId, Instant moc, String action, String lyDo) {
        String khoa = "\"diemDoId\":\"%s\",\"maLoaiChiSo\":\"MUC_NUOC\",\"mocDo\":\"%s\"".formatted(publicId, moc);
        String than = lyDo == null
                ? "{%s,\"action\":\"%s\"}".formatted(khoa, action)
                : "{%s,\"action\":\"%s\",\"reason\":\"%s\"}".formatted(khoa, action, lyDo);
        return phienHttp.goi(phien, HttpMethod.POST, "/api/v1/hyd/so-do/thao-tac", than);
    }

    /** Đọc thẳng CSDL theo <b>khoá tự nhiên</b> — cùng bộ khoá mà API dùng. */
    private Map<String, Object> dongTai(long stationId, Instant moc, String cot) {
        return jdbc.queryForMap(
                "SELECT " + cot + " FROM hydro_readings WHERE station_id = ? AND measured_at = ?",
                stationId,
                Timestamp.from(moc));
    }

    private ResponseEntity<String> nutCua(PhienHttp.Phien phien, String publicId, Instant moc) {
        return phienHttp.get(
                phien, "/api/v1/hyd/so-do/thao-tac?diemDoId=%s&maLoaiChiSo=MUC_NUOC&mocDo=%s".formatted(publicId, moc));
    }

    // =========================================================================
    // Quyền — ba mức, đo trên ma trận THẬT
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ DUTY_OFFICER nhập tay ĐƯỢC nhưng duyệt thì KHÔNG — lý do `hyd:measurement:create` ra đời")
    void trucBanNhapDuocNhungKhongDuyetDuoc() {
        // Vế phân biệt phải đứng trước: nếu DUTY_OFFICER cũng có :review thì bài này chẳng chứng minh gì.
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM roles r
                          JOIN role_permissions rp ON rp.role_id = r.id
                          JOIN permissions p ON p.id = rp.permission_id
                         WHERE r.code = 'DUTY_OFFICER' AND p.code = 'hyd:measurement:review'
                        """,
                        Integer.class))
                .as("⛔ DUTY_OFFICER phải KHÔNG có :review — nếu có thì cả lớp này mất vế đối chiếu")
                .isZero();

        Instant moc = chenNghiNgo(1, "31.000");
        assertThat(thaoTac(trucBan, moc, "DUYET", null).getStatusCode())
                .as("người trực ⛔ không phán xét chất lượng cảm biến")
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> nhap = phienHttp.goi(
                trucBan,
                HttpMethod.POST,
                "/api/v1/hyd/so-do/nhap-tay",
                """
                {"diemDoId":"%s","maLoaiChiSo":"MUC_NUOC","mocDo":"%s","giaTri":"2.345","ghiChu":"API chết, đọc thước"}
                """
                        .formatted(publicIdDiemDo, mocGoc.plus(Duration.ofMinutes(2))));
        assertThat(nhap.getStatusCode())
                .as("⭐ ĐÂY là lý do `hyd:measurement:create` phải ra đời: gác ô nhập tay bằng "
                        + ":review thì đúng người cần nó nhất — người đang trực lúc API chết — bị 403")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("⛔ Tài khoản không vai trò: 403 ở cả ba endpoint")
    void khongQuyenThiKhongVaoDuoc() {
        assertThat(phienHttp.get(khongQuyen, "/api/v1/hyd/so-do/nghi-ngo").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(thaoTac(khongQuyen, mocGoc, "DUYET", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(phienHttp
                        .goi(
                                khongQuyen,
                                HttpMethod.POST,
                                "/api/v1/hyd/so-do/nhap-tay",
                                "{\"diemDoId\":\"%s\",\"maLoaiChiSo\":\"MUC_NUOC\",\"mocDo\":\"%s\",\"giaTri\":\"1\"}"
                                        .formatted(publicIdDiemDo, mocGoc))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // Hàng chờ duyệt
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ `giaTri` ra dây dưới dạng CHUỖI — `2.300` ⛔ không được thành `2.3`")
    void giaTriRaDayLaChuoi() {
        chen(mocGoc.plus(Duration.ofMinutes(3)), "2.300", "NGHI_NGO", "kiểm thang số");

        JsonNode dong = timDong(mocGoc.plus(Duration.ofMinutes(3)));
        assertThat(dong.path("giaTri").isTextual())
                .as("⛔ Số JSON mất chữ số 0 cuối. Với mực nước thì chữ số thập phân thứ ba là "
                        + "MILIMÉT — thứ mà toàn bộ ngưỡng cảnh báo treo lên. Đã trả giá hai lần: "
                        + "T28.27 ở cổng công khai, rồi V2 ở đường quản trị.")
                .isTrue();
        assertThat(dong.path("giaTri").asText()).isEqualTo("2.300");
        assertThat(dong.path("donVi").asText()).isEqualTo("m");
    }

    @Test
    @DisplayName("⭐ Lý do MÁY đi ra tới màn hình — một cờ đỏ không nói được vì sao là cờ không hành động được")
    void lyDoMayRaToiManHinh() {
        Instant moc = mocGoc.plus(Duration.ofMinutes(4));
        chen(moc, NGOAI_KHOANG, "NGHI_NGO", "Giá trị 493.000 ngoài khoảng vật lý [-10 … 30]");

        JsonNode dong = timDong(moc);
        assertThat(dong.path("lyDoMay").asText()).contains("ngoài khoảng vật lý");
        assertThat(dong.path("lyDoNguoi").asText(null))
                .as("chưa ai xử lý thì lời NGƯỜI phải rỗng — ⛔ không mượn lời máy điền vào")
                .isNull();
        assertThat(dong.path("diemDoName").asText()).isNotBlank();
    }

    @Test
    @DisplayName("⛔ Màn hình này ⛔ KHÔNG phục vụ HOP_LE — chặn ở repository, không ở controller")
    void khongPhucVuHopLe() {
        ResponseEntity<String> ra = phienHttp.get(kyThuat, "/api/v1/hyd/so-do/nghi-ngo?trangThai=HOP_LE");
        assertThat(ra.getStatusCode())
                .as("⛔ Mở cửa hậu ở đây là biến hàng chờ duyệt thành một trình duyệt dữ liệu thứ hai, "
                        + "không phân trang theo cùng luật và không ai canh")
                .isNotEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("⛔ Tham số `sort` bị BỎ QUA, ⛔ không phải 422 — hình dạng A1 ⛔ không được dựng lại")
    void thamSoSortBiBoQua() {
        assertThat(phienHttp
                        .get(kyThuat, "/api/v1/hyd/so-do/nghi-ngo?sort=updatedAt,desc")
                        .getStatusCode())
                .as("A1: sort mặc định của giao diện nằm ngoài whitelist ⇒ 422 ngay lượt tải đầu, và "
                        + "triệu chứng 'bảng rỗng' trùng khít trạng thái đúng nên ⛔ không ai báo")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("⚠ `tinh-trang` nói ra bộ phân loại có đang bật không — quy tắc 16")
    void tinhTrangNoiRaBoPhanLoaiCoBatKhong() {
        JsonNode t = than(phienHttp.get(kyThuat, "/api/v1/hyd/so-do/nghi-ngo/tinh-trang"));

        assertThat(t.path("dangKiem").asBoolean())
                .as("⭐ `V202609041061` seed vỏ bọc MUC_NUOC [-10 … 30]; nếu cờ này FALSE thì hoặc "
                        + "migration không chạy, hoặc bộ đọc JSON hỏng — và hàng chờ sẽ RỖNG VĨNH VIỄN "
                        + "mà trông y hệt 'không có gì đáng ngờ'")
                .isTrue();
        // ⚠ `ResponseEnvelopeAdvice` BỎ HẲN trường null khỏi JSON, nên `path(...)` trả MissingNode —
        //   mà `MissingNode.isNull()` là FALSE. Một khẳng định `isNull()` ở đây đỏ vì lý do sai.
        assertThat(t.path("loiCauHinh").asText(null))
                .as("⛔ Ba trạng thái phải phân biệt được: đang chạy · chưa cấu hình · cấu hình HỎNG")
                .isNull();
    }

    // =========================================================================
    // Hai bước chuyển — T32.5 · T32.6
    // =========================================================================

    @Test
    @DisplayName("⭐ Hai nút, và cờ `requiresReason` khác nhau — giao diện ⛔ không tự suy")
    void haiNutVaCoLyDo() {
        Instant moc = chenNghiNgo(5, "31.000");
        JsonNode nut = than(nutCua(kyThuat, publicIdDiemDo, moc));

        assertThat(nut).hasSize(2);
        Map<String, Boolean> doiLyDo = Map.of(
                nut.get(0).path("action").asText(),
                        nut.get(0).path("requiresReason").asBoolean(),
                nut.get(1).path("action").asText(),
                        nut.get(1).path("requiresReason").asBoolean());

        assertThat(doiLyDo)
                .as("⛔ `DUYET` cố ý KHÔNG đòi lý do (bắt gõ lý do cho việc thường xuyên nhất là dạy "
                        + "người dùng gõ bừa); `XOA` thì BẮT BUỘC — đây là bước duy nhất làm một số đo "
                        + "có thật biến mất khỏi mọi báo cáo")
                .containsExactlyInAnyOrderEntriesOf(Map.of("DUYET", false, "XOA", true));
    }

    @Test
    @DisplayName("⭐⭐ DUYỆT: NGHI_NGO → HOP_LE và `hydro_latest.valid_value` NHẢY TỚI nó")
    void duyetLamValidValueNhayToi() {
        Instant moc = mocGoc.plus(Duration.ofMinutes(6));
        chenVao(idDiemDoLatest, moc, "3.210", "NGHI_NGO", "kiểm duyệt");
        jdbc.update(
                """
                INSERT INTO hydro_latest (station_id, measurement_type_id, last_seen_at, last_quality, last_source)
                VALUES (?, ?, ?, 'NGHI_NGO', 'API')
                ON CONFLICT (station_id, measurement_type_id) DO UPDATE
                    SET last_seen_at = EXCLUDED.last_seen_at, last_quality = 'NGHI_NGO',
                        valid_measured_at = NULL, valid_value = NULL
                """,
                idDiemDoLatest,
                idLoaiChiSo,
                Timestamp.from(moc));

        assertThat(latest(idDiemDoLatest).get("valid_value"))
                .as("⛔ Trạng thái nền phải là 'chưa có giá trị hợp lệ nào', nếu không bài dưới không "
                        + "phân biệt được 'đã cập nhật' với 'vốn đã đúng sẵn'")
                .isNull();

        ResponseEntity<String> ra = thaoTacTai(kyThuat, publicIdDiemDoLatest, moc, "DUYET", null);
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(than(ra).path("trangThai").asText()).isEqualTo("HOP_LE");

        Map<String, Object> l = latest(idDiemDoLatest);
        assertThat(l.get("valid_value"))
                .as("⭐ Lượt UPSERT của poller CHỈ BIẾT TIẾN; một lượt duyệt đi ngược chiều ấy. Bỏ bước "
                        + "dựng lại thì widget cổng và GIS tiếp tục hiện một con số cũ trông rất bình thường")
                .hasToString("3.210");
        assertThat(l.get("last_quality")).isEqualTo("HOP_LE");
    }

    @Test
    @DisplayName("⭐⭐ XOÁ không kèm lý do → SYS-0003 gắn ĐÚNG ô `reason`")
    void xoaThieuLyDoBiChan() {
        Instant moc = chenNghiNgo(7, "31.000");
        ResponseEntity<String> ra = thaoTac(kyThuat, moc, "XOA", null);

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ra.getBody()).contains("SYS-0003").contains("reason");
        assertThat(dongTai(idDiemDo, moc, "quality").get("quality"))
                .as("⛔ Bước chuyển bị từ chối ⛔ không được để lại dấu vết nào")
                .isEqualTo("NGHI_NGO");
    }

    /**
     * ⭐⭐ Bài chịu lực nhất của lớp này — nó chứng minh một lỗ hổng của <b>engine dùng chung</b> đã
     * được bịt.
     *
     * <p>Trước 02/09/2026, tham số {@code reason} của {@code WorkflowPort.execute} được
     * <b>validate rồi vứt đi</b>: không cột nào, không bảng nào giữ nó ({@code audit_logs} ⛔ không
     * có cột lý do). Nghĩa là {@code requires_reason = TRUE} chỉ bắt người dùng gõ một câu vào hư
     * không — một nửa cặp đọc–ghi <i>trông y hệt</i> cả cặp, vì màn hình có ô nhập và engine có chốt
     * chặn. Chốt F2 thì đòi <i>"soft delete + audit ai xoá, lý do"</i>.
     */
    @Test
    @DisplayName("⭐⭐ XOÁ kèm lý do: lý do ĐƯỢC GIỮ LẠI và đi vào nhật ký kiểm toán")
    void xoaGiuLaiLyDoVaVaoNhatKy() {
        Instant moc = chenNghiNgo(8, "35.000");
        String lyDo = "Cảm biến trạm này vừa được thay, số liệu 35 m là của lần hiệu chuẩn";

        ResponseEntity<String> ra = thaoTac(kyThuat, moc, "XOA", lyDo);
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> dong = dongTai(idDiemDo, moc, "id, quality, quality_reason, review_note");
        assertThat(dong.get("quality")).isEqualTo("XOA");
        assertThat(dong.get("review_note"))
                .as("⭐ Lý do phải NẰM LẠI. Trước bản này engine kiểm nó rồi ném đi, nên câu hỏi 'vì sao "
                        + "bản ghi này bị loại bỏ' ⛔ không có chỗ nào trả lời")
                .isEqualTo(lyDo);
        assertThat(dong.get("quality_reason"))
                .as("⛔ Và lời MÁY ⛔ không bị lượt duyệt ghi đè — hai câu trả lời hai câu hỏi khác nhau")
                .asString()
                .contains("ngoài khoảng vật lý");

        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM audit_logs
                         WHERE entity_type = 'Số đo thuỷ văn' AND entity_id = ? AND action = 'UPDATE'
                        """,
                        Integer.class,
                        ((Number) dong.get("id")).longValue()))
                .as("⭐ Ghi vào một CỘT của entity là cách lý do đi vào chuỗi băm MIỄN PHÍ, cùng lô với "
                        + "ai bấm và lúc nào — ⛔ không phải đổi lược đồ bảng có hash chain")
                .isPositive();
    }

    @Test
    @DisplayName("⛔ XOA là trạng thái CUỐI — ⛔ không có đường quay lại")
    void xoaLaTrangThaiCuoi() {
        Instant moc = chenNghiNgo(9, "40.000");
        assertThat(thaoTac(kyThuat, moc, "XOA", "loại bỏ để kiểm trạng thái cuối")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(than(nutCua(kyThuat, publicIdDiemDo, moc)))
                .as("⛔ Một dòng xoá nhầm thì NHẬP LẠI bằng đường MANUAL — có người ký tên — chứ không "
                        + "'hoàn tác' một bước đã ghi vào chuỗi băm (quy tắc 18)")
                .isEmpty();
        assertThat(thaoTac(kyThuat, moc, "DUYET", null).getStatusCode())
                .as("và chốt chặn thật vẫn là engine, ⛔ không phải danh sách nút")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * ⭐⭐ T32.6 — lượt kiểm quy tắc phải chạy <b>TRƯỚC</b> {@code workflow.execute}.
     *
     * <p>§10.34: kiểm <i>sau</i> thì lượt kiểm ấy không bao giờ chạy tới — engine đổi trạng thái trên
     * entity đang được quản lý, Hibernate flush nó, và ràng buộc {@code CHECK} bắn trước. Người dùng
     * nhận một lỗi CSDL thô thay vì mã lỗi nghiệp vụ.
     */
    @Test
    @DisplayName("⭐⭐ Duyệt một giá trị VẪN ngoài khoảng vật lý → HYD-2001, ⛔ không phải 500")
    void duyetGiaTriVanNgoaiKhoangBiChan() {
        Instant moc = chenNghiNgo(10, NGOAI_KHOANG);

        ResponseEntity<String> ra = thaoTac(kyThuat, moc, "DUYET", null);
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ra.getBody())
                .as("⭐ HYD-2001 khai từ Phase 0 và tới 02/09/2026 CHƯA lượt chạy nào ném nó — đây là "
                        + "đường chạy thật đầu tiên")
                .contains("HYD-2001");
        assertThat(dongTai(idDiemDo, moc, "quality").get("quality")).isEqualTo("NGHI_NGO");
    }

    @Test
    @DisplayName("⛔ Nhảy quá nhanh thì VẪN DUYỆT ĐƯỢC — mở cống là chuyện thật, và đó là lý do người duyệt tồn tại")
    void nhayQuaNhanhVanDuyetDuoc() {
        Instant moc = mocGoc.plus(Duration.ofMinutes(11));
        chen(moc, "12.000", "NGHI_NGO", "Chênh 8.000 so với bản trước");

        assertThat(thaoTac(kyThuat, moc, "DUYET", null).getStatusCode())
                .as("⛔ Chặn ở đây là biến nút Duyệt thành nút không bao giờ bấm được đúng lúc cần nhất. "
                        + "Máy không phân biệt được 'cảm biến nhiễu' với 'vừa mở cống'; người trực thì biết")
                .isEqualTo(HttpStatus.OK);
    }

    // =========================================================================
    // Nhập tay — T32.7 · T32.8
    // =========================================================================

    @Test
    @DisplayName("⭐ Nhập tay ghi dòng MANUAL có người chịu trách nhiệm — ⛔ máy không mượn tên ai")
    void nhapTayGhiDongCoNguoiKy() {
        Instant moc = mocGoc.plus(Duration.ofMinutes(20));
        ResponseEntity<String> ra = nhapTay(kyThuat, moc, "2.500", "API chết từ 3h sáng, đọc thước tại trạm");

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // ⛔ CỐ Ý không có `Location`: đường dẫn tới MỘT số đo không tồn tại, vì địa chỉ của một
        //   bản ghi là bộ ba (điểm đo, loại chỉ số, mốc đo) — ⛔ không phải khoá tự tăng
        //   (`ApiSurfaceRuleTest`). Trả một `Location` trỏ vào `/{id}` là mời người sau dựng đúng
        //   endpoint vừa bị cấm.
        assertThat(ra.getHeaders().getLocation()).isNull();

        Map<String, Object> dong = jdbc.queryForMap(
                """
                SELECT source, quality, created_by, note, raw_log_id
                  FROM hydro_readings WHERE station_id = ? AND measured_at = ?
                """,
                idDiemDo,
                Timestamp.from(moc));
        assertThat(dong.get("source")).isEqualTo("MANUAL");
        assertThat(dong.get("quality")).isEqualTo("HOP_LE");
        assertThat(dong.get("created_by")).isNotNull();
        assertThat(dong.get("note")).asString().contains("đọc thước");
        assertThat(dong.get("raw_log_id"))
                .as("⛔ Không có lượt gọi nào thì không có nguyên văn response nào — cột rỗng chính là "
                        + "cách phân biệt dòng máy ghi với dòng người ghi khi truy ngược sau này")
                .isNull();
    }

    @Test
    @DisplayName("⭐ Nhập tay ngoài khoảng vật lý → HYD-2001 — ⛔ KHÔNG lặng lẽ gắn cờ NGHI_NGO")
    void nhapTayNgoaiKhoangBiTuChoi() {
        ResponseEntity<String> ra = nhapTay(kyThuat, mocGoc.plus(Duration.ofMinutes(21)), NGOAI_KHOANG, null);

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ra.getBody()).contains("HYD-2001");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM hydro_readings WHERE station_id = ? AND measured_at = ?",
                        Integer.class,
                        idDiemDo,
                        Timestamp.from(mocGoc.plus(Duration.ofMinutes(21)))))
                .as("⛔ Số đo của MÁY thì quý và không lấy lại được nên vẫn ghi kèm cờ; số đo của NGƯỜI "
                        + "ngoài khoảng vật lý gần như chắc chắn là lỗi gõ, và người gõ đang ngồi ngay đó")
                .isZero();
    }

    @Test
    @DisplayName("⭐⭐ Ô đã có bản ghi NGHI_NGO → HYD-2002, và nó CHỈ ĐƯỜNG phải làm gì tiếp")
    void nhapTayVaoODangTreoBaoHyd2002() {
        Instant moc = mocGoc.plus(Duration.ofMinutes(22));
        chenNghiNgo(22, "31.000");

        ResponseEntity<String> ra = nhapTay(kyThuat, moc, "2.500", null);
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ra.getBody())
                .as("⭐ HYD-2002 khai từ Phase 0 và chưa lượt chạy nào ném nó. Một thông báo 'trùng dữ "
                        + "liệu' chung chung để người trực đứng đó không biết làm gì; mã này chỉ thẳng "
                        + "sang màn hình Dữ liệu nghi ngờ")
                .contains("HYD-2002");
    }

    @Test
    @DisplayName("⛔ Ô đã có bản ghi HỢP LỆ → HYD-2007, ⛔ tuyệt đối không ghi đè")
    void nhapTayVaoODaCoBaoHyd2007() {
        Instant moc = mocGoc.plus(Duration.ofMinutes(23));
        chen(moc, "2.100", "HOP_LE", null);

        ResponseEntity<String> ra = nhapTay(kyThuat, moc, "9.900", null);
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ra.getBody()).contains("HYD-2007");
        assertThat(jdbc.queryForObject(
                        "SELECT reading_value FROM hydro_readings WHERE station_id = ? AND measured_at = ?",
                        String.class,
                        idDiemDo,
                        Timestamp.from(moc)))
                .as("⛔ Bản ghi đang nằm đó là bằng chứng nguyên trạng của thứ nguồn đã trả về")
                .isEqualTo("2.100");
    }

    @Test
    @DisplayName("⛔ Mốc đo ở TƯƠNG LAI bị từ chối — nếu không, widget cổng đứng im ở một con số gõ nhầm")
    void mocTuongLaiBiTuChoi() {
        ResponseEntity<String> ra = nhapTay(kyThuat, Instant.now().plus(Duration.ofDays(1)), "2.500", null);

        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ra.getBody()).contains("KHONG_DUOC_O_TUONG_LAI");
    }

    @Test
    @DisplayName("⭐ Nhập tay mốc QUÁ KHỨ ⛔ không kéo `hydro_latest` lùi lại")
    void nhapTayQuaKhuKhongKeoLatestLui() {
        Instant moiHon = mocGoc.plus(Duration.ofMinutes(40));
        // Dựng `hydro_latest` trỏ vào bản mới nhất.
        assertThat(nhapTay(kyThuat, publicIdDiemDoLatest, moiHon, "4.100", null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        Object mocTruoc = latest(idDiemDoLatest).get("valid_measured_at");

        assertThat(nhapTay(
                                kyThuat,
                                publicIdDiemDoLatest,
                                mocGoc.minus(Duration.ofHours(2)),
                                "1.000",
                                "bù dữ liệu quãng API chết")
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(latest(idDiemDoLatest).get("valid_measured_at"))
                .as("⭐ `HydroLatestRecomputer` dựng lại từ TOÀN BỘ lịch sử của cặp ấy, nên một dòng bù "
                        + "quá khứ ⛔ không kéo mốc hiện tại lùi lại")
                .isEqualTo(mocTruoc);
        assertThat(latest(idDiemDoLatest).get("valid_value")).hasToString("4.100");
    }

    // =========================================================================

    private ResponseEntity<String> nhapTay(PhienHttp.Phien phien, Instant moc, String giaTri, String ghiChu) {
        return nhapTay(phien, publicIdDiemDo, moc, giaTri, ghiChu);
    }

    private ResponseEntity<String> nhapTay(
            PhienHttp.Phien phien, String publicId, Instant moc, String giaTri, String ghiChu) {
        String than = ghiChu == null
                ? "{\"diemDoId\":\"%s\",\"maLoaiChiSo\":\"MUC_NUOC\",\"mocDo\":\"%s\",\"giaTri\":\"%s\"}"
                        .formatted(publicId, moc, giaTri)
                : "{\"diemDoId\":\"%s\",\"maLoaiChiSo\":\"MUC_NUOC\",\"mocDo\":\"%s\",\"giaTri\":\"%s\",\"ghiChu\":\"%s\"}"
                        .formatted(publicId, moc, giaTri, ghiChu);
        return phienHttp.goi(phien, HttpMethod.POST, "/api/v1/hyd/so-do/nhap-tay", than);
    }

    private Map<String, Object> latest(long stationId) {
        return jdbc.queryForMap(
                "SELECT * FROM hydro_latest WHERE station_id = ? AND measurement_type_id = ?", stationId, idLoaiChiSo);
    }

    /** Tìm một dòng của hàng chờ theo mốc đo — ⚠ lọc theo điểm đo RIÊNG của lớp này. */
    private JsonNode timDong(Instant moc) {
        JsonNode trang = than(phienHttp.get(kyThuat, "/api/v1/hyd/so-do/nghi-ngo?size=100&diemDoId=" + publicIdDiemDo));
        // ⚠ `ResponseEnvelopeAdvice.ofPage` đặt CONTENT thẳng vào `data` và phân trang vào `meta` —
        //   ⛔ không có nút `content` lồng bên trong. Đọc nhầm chỗ là chạy qua tập rỗng và xanh.
        List<JsonNode> khop = new java.util.ArrayList<>();
        trang.forEach(n -> {
            if (Instant.parse(n.path("mocDo").asText()).equals(moc)) {
                khop.add(n);
            }
        });
        assertThat(khop)
                .as("⛔ Không tìm thấy dòng mốc %s trong hàng chờ — mọi khẳng định dưới sẽ chạy qua tập rỗng", moc)
                .hasSize(1);
        return khop.get(0);
    }
}
