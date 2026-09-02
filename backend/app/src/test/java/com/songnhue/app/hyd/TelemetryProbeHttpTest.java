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
import com.songnhue.hydro.application.ApiSourceService;

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

    private ResponseEntity<String> goiThu(PhienHttp.Phien phien, UUID nguon) {
        return phienHttp.goi(phien, HttpMethod.POST, "/api/v1/hyd/api-sources/" + nguon + "/goi-thu", null);
    }

    @Test
    @DisplayName("⭐⭐ Gọi thử khép trọn vòng: 200 · bóc được số đo · GHI hydro_raw_logs · CẬP NHẬT last_success_at")
    void goiThuKhepTronVong() {
        UUID nguon = taoNguonGia("GT-OK", true);

        ResponseEntity<String> kq = goiThu(quanTri, nguon);

        assertThat(kq.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode than = doc(kq).path("data");
        assertThat(than.get("thanhCong").asBoolean()).isTrue();
        assertThat(than.get("httpStatus").asInt()).isEqualTo(200);
        assertThat(than.get("soBanGhi").asInt()).isEqualTo(2);

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
                        + "thân ra là trả mã số cho bất kỳ ai mở DevTools. KetQuaGoiThu KHÔNG CÓ CHỖ "
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
        assertThat(than.get("thanhCong").asBoolean()).isFalse();
        assertThat(than.get("loi").asText()).isEqualTo("THIEU_MA_SO");
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
}
