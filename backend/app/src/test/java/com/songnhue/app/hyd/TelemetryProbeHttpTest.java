package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.spi.JobContext;
import com.songnhue.hydro.application.ApiSourceService;
import com.songnhue.hydro.application.HydroJobTypes;
import com.songnhue.hydro.application.HydroPollJobHandler;

/**
 * ⭐⭐ <b>Gọi thử</b> nguồn đi qua HTTP — WS-30, lượt đi <b>trọn vòng</b> đầu tiên của adapter.
 *
 * <h2>Vì sao bài này tồn tại dù đã có {@code Bhh40AdapterHttpTest}</h2>
 *
 * <p>Bài kia chứng minh adapter nói đúng thứ trên dây. Bài này chứng minh <b>đường của người dùng
 * thật</b> khép kín: bấm nút → quyền → giải mã mã số → adapter → ghi {@code hydro_raw_logs} → cập
 * nhật bốn cột sức khoẻ → <b>tuần tự hoá ra JSON</b>. Luật 5: <i>bài kiểm gọi thẳng service không đi
 * cùng đường với production</i> — 391 bài đã xanh trong khi mọi màn hình quản trị nội dung trả 500.
 * Riêng khẳng định "phản hồi không lộ mã số" thì <b>chỉ</b> đo được ở đây: nó là chuyện Jackson in ra
 * những trường nào.
 *
 * <h2>⚠ Vai trò kiểm thử tạm — vì sao không dùng SUPER_ADMIN</h2>
 *
 * <p>{@code hyd:api-source:manage} chỉ SUPER_ADMIN và ADMIN có, mà cả hai nằm trong
 * {@code AuthenticatedUser.TWO_FACTOR_REQUIRED_ROLES} (chốt G12) nên lượt đăng nhập dừng ở
 * {@code TWO_FACTOR_ENROLL_REQUIRED}. ⛔ Không hạ 2FA của vai trò quản trị chỉ để bài kiểm chạy được —
 * đó là sửa hệ thống cho vừa phép đo. Thay vào đó lớp này dựng một vai trò mang <b>đúng một quyền</b>
 * và <b>xoá hẳn</b> ở {@code @AfterAll}, có khẳng định chứng minh đã xoá: {@code RbacMatrixTest} đếm
 * chính xác số vai trò, và một bản dọn dẹp hỏng trong im lặng sẽ làm nó đỏ ở một lớp khác — đúng loại
 * lỗi khó lần nhất.
 *
 * <p>⚠ Chạy trên {@code MockAdapter} (mã {@code Z9000x} ⛔ không khớp {@code CHECK ^F[0-9]{5}$} của
 * {@code stations.api_code}, nên về nguyên tắc không tra ra điểm đo thật nào). Vế "nói chuyện được với
 * IIS" thuộc {@code Bhh40AdapterHttpTest}; vế "gọi được nguồn THẬT" chỉ đo được trên stack có mạng ra
 * ngoài — ghi ở mục nghiệm thu, ⛔ không đọc cái xanh ở đây thành đã phủ (luật 28).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "app.hydro.api.mock=true")
class TelemetryProbeHttpTest extends IntegrationTestBase {

    private static final String MA_SO = "maso-kiem-thu-goi-thu;";

    /**
     * ⚠ Chuỗi payload viết TAY, ⛔ không gọi {@code HydroPollJobHandler.payloadCho} — hàm ấy
     * package-private và mở nó ra {@code public} chỉ để một bài kiểm gọi được là nới hợp đồng của mã
     * production cho vừa phép đo.
     *
     * <p>Bất biến "hai vế sinh–bóc khớp nhau" đã có bộ canh riêng ở đúng chỗ nó sống:
     * {@code HydroPollSchedulerTest.payloadChiMangMaNguon} khẳng định chuỗi này <b>từng ký tự</b>. Đổi
     * định dạng mà quên sửa ở đây thì {@code docMaNguon} ném — ⛔ không đi lọt trong im lặng.
     */
    private static final String PAYLOAD_POLL = "{\"maNguon\":\"%s\"}";

    private static final String VAI_TRO_TAM = "KIEMTRA_GOI_THU";

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

    @Autowired
    private ApiSourceService sources;

    @Autowired
    private HydroPollJobHandler pollHandler;

    private PhienHttp phienHttp;
    private PhienHttp.Phien quanTri;
    private PhienHttp.Phien kyThuat;

    @BeforeAll
    void dungVaiTroTamRoiDangNhap() {
        donSachVaiTroTam();
        jdbc.update(
                """
                INSERT INTO roles (code, name, description, is_system, created_at)
                VALUES (?, 'Vai trò kiểm thử Gọi thử', 'Tạm, xoá ở @AfterAll', FALSE, now())
                """,
                VAI_TRO_TAM);
        int soQuyen = jdbc.update(
                """
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT r.id, p.id FROM roles r, permissions p
                 WHERE r.code = ? AND p.code = 'hyd:api-source:manage'
                """,
                VAI_TRO_TAM);
        assertThat(soQuyen)
                .as("⚠ Vế chống tập rỗng: nếu mã quyền đổi tên thì lệnh trên gán 0 dòng trong im lặng, "
                        + "và mọi bài dưới đây đỏ với 403 — một triệu chứng chẳng liên quan gì tới thứ "
                        + "đang kiểm (luật 7)")
                .isEqualTo(1);

        phienHttp = new PhienHttp(http);
        quanTri = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tgt_nguon", VAI_TRO_TAM));
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tgt_kythuat", "TECHNICIAN"));
    }

    private void donSachVaiTroTam() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_TAM);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_TAM);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO_TAM);
    }

    @AfterAll
    void donVaiTroTam() {
        donSachVaiTroTam();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles WHERE code = ?", Integer.class, VAI_TRO_TAM))
                .as("⛔ Dọn dẹp hỏng trong im lặng ở đây làm RbacMatrixTest đỏ ở MỘT LỚP KHÁC — "
                        + "loại lỗi khó lần nhất. Khẳng định ngay tại chỗ dọn.")
                .isZero();
    }

    /** @return publicId của nguồn giả vừa tạo */
    private UUID taoNguonGia(String ma, boolean datMaSo) {
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO api_sources (code, name, adapter_type, base_url, status, created_at)
                VALUES (?, 'Nguồn giả kiểm thử', 'MOCK', 'http://nguon-gia.invalid/', 'HOAT_DONG', now())
                RETURNING public_id
                """,
                UUID.class,
                ma);
        if (datMaSo) {
            sources.datMaSo(id, MA_SO);
        }
        return id;
    }

    private JsonNode doc(ResponseEntity<String> phanHoi) {
        try {
            return json.readTree(phanHoi.getBody());
        } catch (Exception e) {
            throw new AssertionError("Không đọc được thân JSON: " + phanHoi.getBody(), e);
        }
    }

    /** Số dòng {@code hydro_unmapped_readings} do MockAdapter sinh ra — hai mã {@code Z9000x}. */
    private long demMaLaCuaMock() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM hydro_unmapped_readings WHERE api_code IN ('Z90001','Z90002')", Long.class);
    }

    private ResponseEntity<String> goiThu(PhienHttp.Phien phien, UUID nguon) {
        return phienHttp.goi(phien, HttpMethod.POST, "/api/v1/hyd/api-sources/" + nguon + "/goi-thu", null);
    }

    @Test
    @DisplayName("⭐⭐ Gọi thử khép trọn vòng: 200 · bóc được số đo · GHI hydro_raw_logs · CẬP NHẬT last_success_at")
    void goiThuKhepTronVong() {
        UUID nguon = taoNguonGia("GT-OK", true);
        long maLaTruoc = demMaLaCuaMock();

        ResponseEntity<String> kq = goiThu(quanTri, nguon);

        assertThat(kq.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode than = doc(kq).path("data");
        assertThat(than.get("trangThai").asText())
                .as("⭐ hai số đo giả cho 0 điểm đo đang hoạt động ⇒ quy tắc parse 9 KHÔNG bắn (mẫu số 0 "
                        + "trả false — một cảnh báo kêu vì tập rỗng là một cảnh báo sẽ bị tắt)")
                .isEqualTo("SUCCESS");
        assertThat(than.get("httpStatus").asInt()).isEqualTo(200);
        assertThat(than.get("soBanGhi").asInt()).isEqualTo(2);
        assertThat(than.get("soMaLa").asInt())
                .as("⭐ WS-31: lượt gọi thử nay GHI số đo thật — hai mã Z9000x chưa khai nên chúng xuống "
                        + "hydro_unmapped_readings, ⛔ không bị vứt đi (quy tắc 18). ⚠ Con số là số dòng "
                        + "GHI MỚI, và MockAdapter dùng mốc CỐ ĐỊNH 01/01/2000 (để dữ liệu giả không bao "
                        + "giờ trông tươi) — nên từ lượt thứ hai trở đi ux_hydro_unmapped_ma_khung bỏ qua "
                        + "và con số này về 0. Đó là hành vi ĐÚNG, nên khẳng định bắt vào ĐỘ CHÊNH.")
                .isEqualTo((int) (demMaLaCuaMock() - maLaTruoc));
        assertThat(demMaLaCuaMock())
                .as("⛔ và vế thật sự quan trọng: số đo của mã chưa khai CÓ MẶT trong bảng")
                .isEqualTo(2);
        assertThat(than.get("syncLogId").asLong()).isPositive();

        List<String> maLa = new ArrayList<>();
        than.get("maChuaKhai").forEach(n -> maLa.add(n.asText()));
        assertThat(maLa)
                .as("⛔ Quy tắc parse 5: mã lạ chỉ được LIỆT KÊ, tuyệt đối không tự tạo điểm đo — bản "
                        + "suy đoán trước đó từ biểu tổng hợp đã SAI 1/4 mã")
                .containsExactly("Z90001", "Z90002");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE api_code LIKE 'Z%'", Long.class))
                .as("⛔⛔ Và khẳng định vế THẬT SỰ quan trọng: không một điểm đo nào được sinh ra")
                .isZero();

        long rawLogId = than.get("rawLogId").asLong();
        assertThat(jdbc.queryForObject("SELECT body FROM hydro_raw_logs WHERE id = ?", String.class, rawLogId))
                .as("⭐ Quy tắc 18: nguồn KHÔNG có API lịch sử. Một lượt gọi thử vẫn mang về số đo "
                        + "thật, nên vứt nguyên văn đi vì 'chỉ là gọi thử' là vứt dữ liệu không lấy "
                        + "lại được.")
                .contains("Z90001");

        Map<String, Object> sucKhoe = jdbc.queryForMap(
                "SELECT last_success_at, consecutive_failures FROM api_sources WHERE public_id = ?", nguon);
        assertThat(sucKhoe.get("last_success_at"))
                .as("⚠⚠ Bốn cột sức khoẻ có người ĐỌC từ 31/08 (ApiSourceView) và tới 02/09 KHÔNG AI "
                        + "GHI — bốn ô rỗng vĩnh viễn mà người đọc hiểu thành 'chưa có sự cố nào'. "
                        + "Đây là khẳng định chứng minh vế ghi đã nối (luật 27).")
                .isNotNull();
        assertThat(sucKhoe.get("consecutive_failures")).isEqualTo(0);
    }

    @Test
    @DisplayName("⛔⛔ Phản hồi KHÔNG mang thân nguồn và KHÔNG mang mã số — thân thật chứa chính credential")
    void phanHoiKhongLoThanCungKhongLoMaSo() {
        UUID nguon = taoNguonGia("GT-KIN", true);

        ResponseEntity<String> kq = goiThu(quanTri, nguon);

        assertThat(kq.getBody())
                .as("⛔ conventions.md §4.7: mã số không bao giờ ra API, kể cả cho quản trị. Thân phản "
                        + "hồi của bhh40 mang credential trong <form action> (đo 01/09/2026), nên trả "
                        + "thân ra là trả mã số cho bất kỳ ai mở DevTools. KetQuaDongBo KHÔNG CÓ CHỖ "
                        + "để đặt thân vào — bảo đảm ở tầng cấu trúc, ⛔ không ở lời dặn.")
                .doesNotContain(MA_SO)
                .doesNotContain("maso-kiem-thu")
                .doesNotContain("value=100")
                .doesNotContain("__VIEWSTATE")
                .doesNotContain("<br>");
        assertThat(kq.getBody())
                .as("⚠ Vế PHÂN BIỆT: phải chắc là phản hồi CÓ nội dung, nếu không mọi doesNotContain "
                        + "ở trên xanh vì thân rỗng (luật 7)")
                .contains("soBanGhi")
                .contains("Z90001");
    }

    @Test
    @DisplayName("⭐ Nguồn CHƯA có mã số ⇒ THIEU_MA_SO, ⛔ KHÔNG mở HTTP, ⛔ KHÔNG ghi raw log")
    void chuaCoMaSoThiDungTruocKhiMoHttp() {
        UUID nguon = taoNguonGia("GT-NOKEY", false);
        Long rawTruoc = jdbc.queryForObject("SELECT count(*) FROM hydro_raw_logs", Long.class);

        ResponseEntity<String> kq = goiThu(quanTri, nguon);

        assertThat(kq.getStatusCode())
                .as("⚠ 200 kèm chi tiết, ⛔ không 502: lượt gọi này ĐÃ trả lời đúng câu hỏi người dùng "
                        + "đặt ra. Bóp năm tình trạng phân biệt được thành một câu 'Hệ thống bên ngoài "
                        + "không phản hồi' là xoá đúng thứ họ cần (§10.68-B).")
                .isEqualTo(HttpStatus.OK);
        JsonNode than = doc(kq).path("data");
        assertThat(than.get("trangThai").asText()).isEqualTo("FAILED");
        assertThat(than.get("loi").asText()).isEqualTo("THIEU_MA_SO");
        assertThat(than.get("syncLogId").asLong())
                .as("⭐ T31.12 — ghi sync_logs ở MỌI nhánh thoát, kể cả nhánh chưa hề mở kết nối. Worker "
                        + "chỉ biết 'job này hỏng'; nó không biết VÌ SAO và không hiện ở màn hình của "
                        + "người vận hành thuỷ văn")
                .isPositive();
        // ⚠ Envelope của dự án bỏ hẳn trường null khỏi JSON, nên đây là "vắng mặt" chứ không phải
        //   "null" — FE phải đọc `rawLogId == null` chứ ⛔ không `'rawLogId' in kq`.
        assertThat(than.path("rawLogId").isMissingNode()
                        || than.path("rawLogId").isNull())
                .as("⛔ Không có lượt gọi nào thì không có dòng raw log nào để trỏ tới")
                .isTrue();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM hydro_raw_logs", Long.class))
                .as("⛔ hydro_raw_logs.failure_kind có CHECK BỐN giá trị, cố ý thiếu THIEU_MA_SO: không "
                        + "có lượt gọi nào thì không có response nào để ghi")
                .isEqualTo(rawTruoc);

        assertThat(jdbc.queryForObject(
                        "SELECT consecutive_failures FROM api_sources WHERE public_id = ?", Integer.class, nguon))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⚠ TECHNICIAN mở được DANH SÁCH nguồn nhưng ⛔ KHÔNG gọi thử được — gọi thử mở kết nối ra ngoài")
    void kyThuatKhongGoiThuDuoc() {
        UUID nguon = taoNguonGia("GT-RBAC", true);

        assertThat(phienHttp.get(kyThuat, "/api/v1/hyd/api-sources").getStatusCode())
                .as("T28.25 đã nới đường ĐỌC danh sách cho TECHNICIAN — ô 'Nguồn dữ liệu' của biểu mẫu "
                        + "điểm đo là bắt buộc, không nới thì họ không tạo nổi điểm đo nào")
                .isEqualTo(HttpStatus.OK);

        assertThat(goiThu(kyThuat, nguon).getStatusCode())
                .as("⛔ Gọi thử mở một kết nối ra ngoài và cập nhật bộ đếm sức khoẻ — nó là việc quản "
                        + "trị, đứng sau hyd:api-source:manage như mọi việc ghi khác")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName(
            "⭐⭐ Hợp đồng JSON khớp KHÍT `KetQuaDongBo` của api-types.ts — thừa một trường là một bề mặt không ai canh")
    void hopDongJsonKhopVoiKieuFe() {
        UUID nguon = taoNguonGia("GT-JSON", true);

        JsonNode than = doc(goiThu(quanTri, nguon)).path("data");
        List<String> truong = new ArrayList<>();
        than.fieldNames().forEachRemaining(truong::add);

        // ⚠ Envelope bỏ hẳn trường null, nên đây là tập trường CÓ MẶT ở một lượt THÀNH CÔNG. Danh
        //   sách này phải trùng khít `KetQuaDongBo` trong `frontend/admin-app/src/shared/api-types.ts`
        //   — chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ (luật 14).
        assertThat(truong)
                .as("⛔ Một trường lọt ra JSON mà FE không khai là một bề mặt không ai canh (§7.7); một "
                        + "trường FE khai mà BE không gửi là một ô rỗng vĩnh viễn (luật 27)")
                .containsExactlyInAnyOrder(
                        "trangThai",
                        "httpStatus",
                        "durationMs",
                        "soByteThan",
                        "khungNhamToi",
                        "soBanGhi",
                        "soGhiMoi",
                        "soTrungBoQua",
                        "soMaLa",
                        "soDongRac",
                        "soDongTrung",
                        "maChuaKhai",
                        "soDiemDoDangHoatDong",
                        "soThieuLoaiChiSo",
                        "soKhacNguon",
                        "mocDoGanNhat",
                        "rawLogId",
                        "syncLogId");
        assertThat(String.join(",", truong))
                .as("⛔ thân phản hồi của nguồn KHÔNG có chỗ nào để đặt vào — bảo đảm ở tầng cấu trúc")
                .doesNotContain("body")
                .doesNotContain("than");
    }

    @Test
    @DisplayName(
            "⭐⭐ Lượt POLLING khép trọn vòng qua hàng đợi thật: ghi raw · ghi mã lạ · ghi sync_logs · ⛔ 0 điểm đo mới")
    void luotPollingKhepTronVong() {
        UUID nguon = taoNguonGia("PL-OK", true);
        Long soSyncTruoc = jdbc.queryForObject("SELECT count(*) FROM sync_logs", Long.class);
        long maLaTruoc = demMaLaCuaMock();

        pollHandler.handle(
                new JobContext(UUID.randomUUID(), HydroJobTypes.POLL, PAYLOAD_POLL.formatted("PL-OK"), null, p -> {}));

        Map<String, Object> log = jdbc.queryForMap(
                """
                SELECT s.status, s.received_count, s.written_count, s.unmapped_count, s.raw_log_id, s.frame_start
                  FROM sync_logs s JOIN api_sources a ON a.id = s.api_source_id
                 WHERE a.public_id = ? ORDER BY s.id DESC LIMIT 1
                """,
                nguon);
        assertThat(log.get("status")).isEqualTo("SUCCESS");
        assertThat(log.get("received_count")).isEqualTo(2);
        assertThat(log.get("written_count"))
                .as("hai mã Z9000x KHÔNG khớp CHECK ^F[0-9]{5}$ của stations.api_code nên về nguyên tắc "
                        + "không tra ra điểm đo thật nào ⇒ 0 dòng vào hydro_readings")
                .isEqualTo(0);
        assertThat(log.get("unmapped_count"))
                .as("⚠ Bộ đếm phải khớp SỐ DÒNG THẬT SỰ SINH RA, ⛔ không phải số mã nhận được — mốc cố "
                        + "định của MockAdapter làm lượt thứ hai trở đi ghi 0 dòng, và đó là đúng")
                .isEqualTo((int) (demMaLaCuaMock() - maLaTruoc));
        assertThat(log.get("raw_log_id")).isNotNull();
        assertThat(log.get("frame_start"))
                .as("mốc khung phải được ghi — nó là khoá đối soát của rate-limit")
                .isNotNull();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_logs", Long.class))
                .as("⭐ ĐÚNG MỘT dòng cho một lượt polling — quy tắc parse 10")
                .isEqualTo(soSyncTruoc + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE api_code LIKE 'Z%'", Long.class))
                .as("⛔⛔ Quy tắc parse 5: KHÔNG tự tạo điểm đo từ mã lạ, kể cả trên đường tự động")
                .isZero();
        assertThat(demMaLaCuaMock())
                .as("⭐ nhưng số đo VẪN được giữ: nguồn không có API lịch sử, bỏ đi là bỏ vĩnh viễn")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("⭐ Nguồn chưa có mã số ⇒ job polling KHÔNG ném — 720 job FAILED/ngày là một màn hình không ai đọc")
    void pollingThieuMaSoKhongLamJobDo() {
        taoNguonGia("PL-NOKEY", false);

        org.assertj.core.api.Assertions.assertThatCode(() -> pollHandler.handle(new JobContext(
                        UUID.randomUUID(), HydroJobTypes.POLL, PAYLOAD_POLL.formatted("PL-NOKEY"), null, p -> {})))
                .as("⭐⭐ Luật: ném khi lượt gọi ĐÃ XẢY RA và hỏng; ⛔ không ném khi chưa hề có lượt gọi "
                        + "nào. Đó chính là đường phân chia mà lược đồ đã vẽ giữa hai ràng buộc CHECK.")
                .doesNotThrowAnyException();

        assertThat(jdbc.queryForObject(
                        """
                        SELECT s.failure_kind FROM sync_logs s JOIN api_sources a ON a.id = s.api_source_id
                         WHERE a.code = 'PL-NOKEY' ORDER BY s.id DESC LIMIT 1
                        """,
                        String.class))
                .as("⛔ và nó vẫn phải để lại dấu vết ở đúng chỗ người vận hành thuỷ văn nhìn")
                .isEqualTo("THIEU_MA_SO");
    }
}
