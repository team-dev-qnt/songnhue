package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Biên bản nghiệm thu và ảnh trước/sau của một bản ghi sửa chữa — qua HTTP.</b> CN-02.2 · T61.19.
 *
 * <p>Năm endpoint dựng từ T18.6, có phân quyền, có service — và cho tới 14/09/2026 <b>0 bài kiểm HTTP,
 * 0 nơi gọi từ giao diện</b> ({@code EndpointCoNoiGoiTest}). Bài này đi đúng đường màn hình mới đi:
 * tải lên multipart kèm {@code docType} ở query · danh sách · đường tải có hạn · xoá.
 *
 * <p>⚠ Bộ kiểm chạy {@code WORKER_ENABLED=false} ⇒ tệp vừa tải giữ {@code UPLOADING} (chưa quét). Đó là
 * trạng thái THẬT của vài giây đầu trên máy chủ, nên bài khẳng định cả hai vế: trước READY ⇒ {@code
 * SYS-0009}; sau READY ⇒ có URL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MaintenanceAttachmentHttpTest extends IntegrationTestBase {

    private static final String VAI_TRO_XOA_TEP = "KIEMTRA_TEP_SUA_CHUA";

    /** Ba byte đầu `%PDF` — `FileValidator.detect` đi bằng magic bytes, ⛔ bằng đuôi tệp. */
    private static final byte[] PDF = "%PDF-1.4 bien ban nghiem thu T61.19".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien kyThuat;
    private PhienHttp.Phien nguoiXoaTep;
    private UUID donViGoc;
    private UUID congTrinh;

    @BeforeAll
    void dangNhap() {
        phienHttp = new PhienHttp(http);
        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_XOA_TEP);
        jdbc.update(
                "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_XOA_TEP);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO_XOA_TEP);
        jdbc.update(
                "INSERT INTO roles (code, name, is_system) VALUES (?, 'Kiểm thử xoá tệp sửa chữa', FALSE)",
                VAI_TRO_XOA_TEP);
        int soQuyen = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN ('ops:maintenance:view', 'ops:document:view', "
                        + "'ops:document:upload', 'ops:document:delete')",
                VAI_TRO_XOA_TEP);
        assertThat(soQuyen).as("chống tập rỗng: mã quyền đổi tên?").isEqualTo(4);

        // TECHNICIAN: tạo bản ghi + tải tệp lên, ⛔ có `ops:document:delete` (ma trận seed).
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6119_kythuat", "TECHNICIAN"));
        nguoiXoaTep =
                phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6119_xoatep", VAI_TRO_XOA_TEP));
    }

    @BeforeEach
    void setUp() {
        donDep();
        ResponseEntity<String> ct = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/ops/constructions",
                """
                {"code":"T6119-001","name":"Trạm bơm kiểm thử T61.19","constructionType":"TRAM_BOM",
                 "orgUnitId":"%s","managementLevel":"XI_NGHIEP"}"""
                        .formatted(donViGoc));
        assertThat(ct.getStatusCode()).as("%s", ct.getBody()).isEqualTo(HttpStatus.CREATED);
        congTrinh = UUID.fromString(PhienHttp.giaTriJson(ct.getBody(), "publicId"));
    }

    @AfterEach
    void tearDown() {
        donDep();
    }

    @AfterAll
    void boVaiTro() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_XOA_TEP);
        jdbc.update(
                "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_XOA_TEP);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO_XOA_TEP);
    }

    @Test
    @DisplayName("⭐⭐ Tải biên bản lên → hiện trong danh sách đúng loại → chưa quét thì SYS-0009 → READY thì có URL")
    void taiLenDanhSachTaiVe() {
        String banGhi = taoBanGhi();

        ResponseEntity<String> tai = taiTep(kyThuat, banGhi, "Biên bản nghiệm thu", "bien-ban.pdf");
        assertThat(tai.getStatusCode()).as("%s", tai.getBody()).isEqualTo(HttpStatus.CREATED);
        String tep = PhienHttp.giaTriJson(tai.getBody(), "id");

        String ds = phienHttp.get(kyThuat, duong(banGhi)).getBody();
        assertThat(ds)
                .as("loại tệp là NHÃN người dùng chọn — màn hình hiện lại đúng chữ ấy")
                .contains("\"originalName\":\"bien-ban.pdf\"", "\"purpose\":\"Biên bản nghiệm thu\"", tep);

        ResponseEntity<String> som = phienHttp.get(kyThuat, duong(banGhi) + "/" + tep + "/download-url");
        assertThat(som.getStatusCode())
                .as("tệp chưa quét virus ⛔ được tải — %s", som.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(som.getBody()).contains("SYS-0009");

        assertThat(jdbc.update("UPDATE attachments SET status = 'READY' WHERE public_id = ?", UUID.fromString(tep)))
                .isEqualTo(1);
        ResponseEntity<String> url = phienHttp.get(kyThuat, duong(banGhi) + "/" + tep + "/download-url");
        assertThat(url.getStatusCode()).as("%s", url.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(PhienHttp.giaTriJson(url.getBody(), "url")).startsWith("http");
    }

    @Test
    @DisplayName("⛔⛔ Tệp của bản ghi A ⛔ đọc/xoá được qua đường của bản ghi B — IDOR trên bảng attachments dùng chung")
    void tepKhacBanGhiLa404() {
        String a = taoBanGhi();
        String b = taoBanGhi();
        String tep = PhienHttp.giaTriJson(
                taiTep(kyThuat, a, "Ảnh trước", "truoc.pdf").getBody(), "id");

        assertThat(phienHttp
                        .get(kyThuat, duong(b) + "/" + tep + "/download-url")
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(phienHttp
                        .goi(nguoiXoaTep, HttpMethod.DELETE, duong(b) + "/" + tep, null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(phienHttp.get(kyThuat, duong(a)).getBody())
                .as("tệp ⛔ bị xoá qua đường sai")
                .contains(tep);
    }

    @Test
    @DisplayName("⛔ Xoá tệp cần `ops:document:delete` — Kỹ thuật tải lên được nhưng ⛔ xoá được")
    void xoaTepCanQuyenRieng() {
        String banGhi = taoBanGhi();
        // `URLSearchParams` của trình duyệt mã hoá dấu cách thành `+` — nhãn phải về lại đúng chữ.
        ResponseEntity<String> tai = taiTep(kyThuat, banGhi, "Ảnh+sau", "sau.pdf");
        assertThat(tai.getBody()).as("`+` trong query phải giải thành dấu cách").contains("\"purpose\":\"Ảnh sau\"");
        String tep = PhienHttp.giaTriJson(tai.getBody(), "id");

        assertThat(phienHttp
                        .goi(kyThuat, HttpMethod.DELETE, duong(banGhi) + "/" + tep, null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> xoa = phienHttp.goi(nguoiXoaTep, HttpMethod.DELETE, duong(banGhi) + "/" + tep, null);
        assertThat(xoa.getStatusCode()).as("%s", xoa.getBody()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(phienHttp.get(kyThuat, duong(banGhi)).getBody()).doesNotContain(tep);
    }

    // -------------------------------------------------------------------------

    private String taoBanGhi() {
        ResponseEntity<String> tao = phienHttp.goi(
                kyThuat,
                HttpMethod.POST,
                "/api/v1/ops/maintenance-logs",
                """
                {"constructionId":"%s","workType":"BAO_TRI_DINH_KY","severity":null,"initialState":null,
                 "startedOn":"2026-08-01","completedOn":null,"content":"Bảo trì kiểm thử T61.19",
                 "itemOrEquipment":"Tổ máy 1","performerOrgUnitId":null,"performerName":"Tổ kỹ thuật",
                 "cost":null,"assigneeUserId":null}"""
                        .formatted(congTrinh));
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return PhienHttp.giaTriJson(tao.getBody(), "id");
    }

    private ResponseEntity<String> taiTep(PhienHttp.Phien phien, String banGhi, String loai, String ten) {
        // ⚠ Truyền nhãn CHƯA mã hoá: bộ gọi của bài kiểm tự mã hoá URL. Mã hoá trước ở đây là mã hoá
        // HAI lần ⇒ máy chủ lưu `Bi%C3%AAn…` — đo được ở lượt chạy đầu (trình duyệt mã hoá đúng 1 lần).
        return phienHttp.dangTep(phien, duong(banGhi) + "?docType=" + loai, PDF, ten);
    }

    private static String duong(String banGhi) {
        return "/api/v1/ops/maintenance-logs/" + banGhi + "/attachments";
    }

    private void donDep() {
        jdbc.update(
                """
                DELETE FROM attachments WHERE owner_type = 'MAINTENANCE_LOG' AND owner_id IN
                    (SELECT m.id FROM maintenance_logs m JOIN constructions c ON c.id = m.construction_id
                     WHERE c.code LIKE 'T6119-%')""");
        jdbc.update(
                "DELETE FROM maintenance_logs WHERE construction_id IN (SELECT id FROM constructions WHERE code LIKE 'T6119-%')");
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T6119-%'");
    }
}
