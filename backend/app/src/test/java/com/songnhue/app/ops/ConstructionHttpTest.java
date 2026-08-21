package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Danh mục công trình <b>đi qua HTTP</b> — T17.12.
 *
 * <h2>Vì sao không gọi thẳng service</h2>
 *
 * Bài học đắt nhất của lượt rà soát 21/8: {@code GET /api/v1/cms/articles} trả <b>500 cho mọi lượt
 * gọi suốt từ WS-13</b> trong khi 391 bài kiểm vẫn xanh — vì chúng gọi service, tức là khẳng định
 * <i>bên trong</i> giao dịch, nơi nạp lười còn hoạt động. Công trình dùng {@code @SecondaryTable},
 * một cơ chế Hibernate khác nữa mà đường ánh xạ sang DTO nằm ngoài giao dịch; nên nếu có chỗ nào vỡ
 * theo kiểu đó thì chỉ đường HTTP mới thấy.
 *
 * <p>Ba nhóm khẳng định: <b>quyền</b> (tầng 2 chặn đúng), <b>envelope + traceId</b>, và <b>trạng thái
 * dẫn xuất</b> ({@code OPS-3001}).
 */
class ConstructionHttpTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien duQuyen;
    private PhienHttp.Phien khongQuyen;
    private UUID donViGoc;

    @BeforeEach
    void setUp() {
        phienHttp = new PhienHttp(http);
        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);

        // ⚠ Cố ý dùng TECHNICIAN chứ không dùng ADMIN. Hai lý do, cả hai đều nói lên điều gì đó:
        //   · ADMIN nằm trong nhóm bắt buộc 2FA nên đăng nhập không ra thẳng AUTHENTICATED;
        //   · quan trọng hơn — TECHNICIAN là vai trò THẬT sẽ dùng chức năng này theo ma trận §6, nên
        //     bài kiểm cũng đồng thời chứng minh ma trận cấp đủ quyền cho đúng người. Cấp ADMIN cho
        //     mọi bài kiểm là cách chắc chắn nhất để một ô thiếu quyền không bao giờ lộ ra.
        String coQuyen = PhienHttp.taoNguoiDung(users, passwords, jdbc, "ops_full", "TECHNICIAN");
        String traiTay = PhienHttp.taoNguoiDung(users, passwords, jdbc, "ops_zero");

        duQuyen = phienHttp.dangNhap(coQuyen);
        khongQuyen = phienHttp.dangNhap(traiTay);
        donDep();
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    @Test
    @DisplayName("⭐ Tạo → danh sách → chi tiết đều 200, và thông số kỹ thuật về đủ")
    void createListDetailAllWork() {
        ResponseEntity<String> tao =
                phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thanTramBom());
        assertThat(tao.getStatusCode()).as("tạo hồ sơ: %s", tao.getBody()).isEqualTo(HttpStatus.CREATED);

        String publicId = PhienHttp.giaTriJson(tao.getBody(), "publicId");

        ResponseEntity<String> danhSach = phienHttp.get(duQuyen, "/api/v1/ops/constructions?q=T17H");
        assertThat(danhSach.getStatusCode())
                .as("danh sách: %s", danhSach.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(danhSach.getBody()).contains("T17H-001");

        ResponseEntity<String> chiTiet = phienHttp.get(duQuyen, "/api/v1/ops/constructions/" + publicId);
        assertThat(chiTiet.getStatusCode())
                .as("⛔ 500 ở đây nghĩa là ánh xạ entity→DTO chạy ngoài giao dịch: %s", chiTiet.getBody())
                .isEqualTo(HttpStatus.OK);

        // ⭐ Khẳng định NỘI DUNG chứ không chỉ mã trạng thái: 200 với thân rỗng vẫn là 200.
        assertThat(chiTiet.getBody())
                .as("thông số trạm bơm nằm ở bảng phụ — 200 mà thiếu chúng nghĩa là bảng phụ không được nạp")
                .contains("\"pumpCount\":3")
                .contains("\"totalFlowM3s\":");
    }

    @Test
    @DisplayName("⭐ Tổng lưu lượng do CSDL tính — client không gửi, mà kết quả vẫn đúng 3 × 1.5")
    void totalFlowIsComputedByDatabase() {
        ResponseEntity<String> tao =
                phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thanTramBom());
        String publicId = PhienHttp.giaTriJson(tao.getBody(), "publicId");

        String chiTiet =
                phienHttp.get(duQuyen, "/api/v1/ops/constructions/" + publicId).getBody();
        assertThat(chiTiet)
                .as("quy tắc 3: giá trị tính toán tính ở BE. Ở đây còn chặt hơn — cột sinh của CSDL, "
                        + "không có đường nào để FE tính ra số khác")
                .contains("\"totalFlowM3s\":4.500");
    }

    @Test
    @DisplayName("⛔ Client gửi trạng thái vận hành → OPS-3001, không nuốt lặng lẽ")
    void clientSuppliedStatusIsRejected() {
        String than = thanTramBom().replace("\"description\":null", "\"operationalStatus\":\"SU_CO\"");

        ResponseEntity<String> tao = phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", than);

        assertThat(tao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(tao.getBody()).contains("OPS-3001");
    }

    @Test
    @DisplayName("Thiếu quyền → 403 AUTH-3001, đúng tầng 2")
    void withoutPermissionIsForbidden() {
        ResponseEntity<String> danhSach = phienHttp.get(khongQuyen, "/api/v1/ops/constructions");

        assertThat(danhSach.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(danhSach.getBody()).contains("AUTH-3001");
    }

    @Test
    @DisplayName("Envelope + traceId có ở cả lượt thành công lẫn lượt lỗi")
    void envelopeAlwaysCarriesTraceId() {
        ResponseEntity<String> ok = phienHttp.get(duQuyen, "/api/v1/ops/constructions");
        assertThat(ok.getBody()).contains("\"success\":true").contains("\"traceId\"");

        ResponseEntity<String> loi = phienHttp.get(duQuyen, "/api/v1/ops/constructions/" + UUID.randomUUID());
        assertThat(loi.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(loi.getBody()).contains("\"success\":false").contains("\"traceId\"");
    }

    @Test
    @DisplayName("⚠ created_by được điền — AuditContext do filter đặt, không phải do test mô phỏng")
    void createdByIsFilledOnTheHttpPath() {
        ResponseEntity<String> tao =
                phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thanTramBom());
        String publicId = PhienHttp.giaTriJson(tao.getBody(), "publicId");

        Long nguoiTao = jdbc.queryForObject(
                "SELECT created_by FROM constructions WHERE public_id = ?::uuid", Long.class, publicId);
        Long mongDoi = jdbc.queryForObject("SELECT id FROM users WHERE username = 'kiemtra_ops_full'", Long.class);

        assertThat(nguoiTao)
                .as("AuditorAwareImpl đọc AuditContext do filter đặt, không đọc AuthContext — "
                        + "bài kiểm gọi thẳng service sẽ để cột này NULL mà vẫn xanh (nợ #66)")
                .isEqualTo(mongDoi);
    }

    @Test
    @DisplayName("⛔ Lý trình sai định dạng → OPS-2011; toạ độ nửa vời → OPS-2010")
    void locationRulesAreEnforced() {
        String lyTrinhSai = thanTramBom().replace("\"chainage\":\"K0+390\"", "\"chainage\":\"km 390\"");
        ResponseEntity<String> a = phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", lyTrinhSai);
        assertThat(a.getBody()).contains("OPS-2011");

        String thieuKinhDo = thanTramBom().replace("\"longitude\":105.780000,", "\"longitude\":null,");
        ResponseEntity<String> b = phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thieuKinhDo);
        assertThat(b.getBody())
                .as("một nửa toạ độ là một điểm sai trên bản đồ — tệ hơn hẳn chưa số hoá")
                .contains("OPS-2010");
    }

    @Test
    @DisplayName("⛔ Thông số sai loại công trình → OPS-2009 (cống không có số máy bơm)")
    void specsMustMatchTheType() {
        String congCoBom = thanTramBom()
                .replace("\"constructionType\":\"TRAM_BOM\"", "\"constructionType\":\"CONG\"")
                .replace("T17H-001", "T17H-002");

        ResponseEntity<String> tao = phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", congCoBom);

        assertThat(tao.getBody()).contains("OPS-2009");
    }

    @Test
    @DisplayName("Nhật ký thay đổi hồ sơ đọc được từ audit_logs — không có bảng lịch sử riêng")
    void changeLogReadsFromAuditLog() {
        ResponseEntity<String> tao =
                phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/ops/constructions", thanTramBom());
        String publicId = PhienHttp.giaTriJson(tao.getBody(), "publicId");

        ResponseEntity<String> nhatKy = phienHttp.get(duQuyen, "/api/v1/ops/constructions/" + publicId + "/change-log");

        assertThat(nhatKy.getStatusCode()).as("%s", nhatKy.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(nhatKy.getBody())
                .as("bộ ghi nhật ký của Core bắt lượt tạo ở tầng Hibernate — không service nào phải gọi tay")
                .contains("CREATE");
    }

    // -------------------------------------------------------------------------

    private String thanTramBom() {
        return """
            {"code":"T17H-001","name":"Trạm bơm kiểm thử HTTP","constructionType":"TRAM_BOM",
             "orgUnitId":"%s","managementLevel":"XI_NGHIEP",
             "latitude":20.980000,"longitude":105.780000,
             "riverName":"Nhuệ","chainage":"K0+390","basinNote":"Lưu vực sông Nhuệ",
             "pump":{"pumpCount":3,"flowPerPumpM3s":1.5,"totalPowerKw":250},
             "description":null}"""
                .formatted(donViGoc);
    }

    private void donDep() {
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T17H-%'");
    }
}
