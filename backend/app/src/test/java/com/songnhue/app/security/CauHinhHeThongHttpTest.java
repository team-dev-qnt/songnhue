package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.spi.BiMatTichHopPort;
import com.songnhue.core.spi.LoaiBiMat;

/**
 * <b>Cấu hình hệ thống từ giao diện</b> — T61.41 · T61.42 · T61.44 · T54.4 (QuanTran chốt 15/09/2026).
 *
 * <p>Đi trọn bằng HTTP thật, TOTP thật trên secret máy chủ trả về (luật 4, 5). Bốn bất biến:
 *
 * <ol>
 *   <li>⛔ Endpoint nào trả GIÁ TRỊ bí mật — kể cả ngay sau khi đặt.
 *   <li>Ghi bí mật / tham số nhóm nhạy cảm đòi mã 2FA NGAY LÚC NÀY; mã sai là <b>403 ADM-2024</b> (⛔ 401 — giao diện đọc
 *       401 là mất phiên) và bị đếm vào khoá tài khoản.
 *   <li>Quyền ghi bí mật mà ⛔ vai trò SUPER_ADMIN ⇒ vẫn bị chặn.
 *   <li>⛔ Cấp được quyền/vai trò mình ⛔ có (T54.4).
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CauHinhHeThongHttpTest extends IntegrationTestBase {

    private static final String VAI_TRO_PHAN_QUYEN = "KIEMTRA_T544_PHAN_QUYEN";
    private static final String VAI_TRO_CO_QUYEN_BI_MAT = "KIEMTRA_T6144_QUYEN_KHONG_VAI_TRO";
    private static final String GIA_TRI_BI_MAT = "khoa-bi-mat-recaptcha-T6144-khong-duoc-lo";
    private static final String DUONG_BI_MAT = "/api/v1/system/cau-hinh/bi-mat/RECAPTCHA_SECRET_KEY";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BiMatTichHopPort biMat;

    private PhienHttp phienHttp;
    private String tenSieuQuanTri;
    private PhienHttp.PhienHaiBuoc sieuQuanTri;

    @BeforeAll
    void dung() {
        donDep();
        jdbc.update(
                "INSERT INTO roles (code, name, is_system) VALUES (?, 'Kiểm thử phân quyền T54.4', FALSE)",
                VAI_TRO_PHAN_QUYEN);
        assertThat(jdbc.update(
                        "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                                + "WHERE r.code = ? AND p.code IN ('adm:role:view', 'adm:role:manage', 'adm:user:view', "
                                + "'adm:user:assign-role')",
                        VAI_TRO_PHAN_QUYEN))
                .as("chống tập rỗng: mã quyền đổi tên?")
                .isEqualTo(4);
        jdbc.update(
                "INSERT INTO roles (code, name, is_system) VALUES (?, 'Kiểm thử quyền bí mật không vai trò', FALSE)",
                VAI_TRO_CO_QUYEN_BI_MAT);
        assertThat(jdbc.update(
                        "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                                + "WHERE r.code = ? AND p.code IN ('adm:system-config:view', 'adm:system-config:secret')",
                        VAI_TRO_CO_QUYEN_BI_MAT))
                .isEqualTo(2);

        phienHttp = new PhienHttp(http);
        tenSieuQuanTri = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6141_sa", "SUPER_ADMIN");
        sieuQuanTri = phienHttp.dangNhapHaiBuoc(tenSieuQuanTri);
    }

    @AfterAll
    void donDep() {
        jdbc.update("DELETE FROM integration_secrets");
        for (String vaiTro : new String[] {VAI_TRO_PHAN_QUYEN, VAI_TRO_CO_QUYEN_BI_MAT}) {
            jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vaiTro);
            jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vaiTro);
            jdbc.update("DELETE FROM roles WHERE code = ?", vaiTro);
        }
    }

    /**
     * Máy chủ chống dùng lại mã theo bước thời gian — kịch bản cần nhiều lượt xác thực lại trong cùng 30 giây, nên đặt
     * lại mốc (⛔ đụng cơ chế kiểm mã: mã SAI vẫn sai) và bộ đếm lượt sai giữa các bài.
     */
    @BeforeEach
    void datLaiMocMa() {
        // ⚠ Dọn TRƯỚC mỗi bài: một bài đỏ giữa chừng để lại hàng bí mật làm bài bên cạnh đỏ lây (đo 15/09).
        jdbc.update("DELETE FROM integration_secrets");
        datLaiMocChongDungLai();
        jdbc.update("UPDATE users SET failed_login_count = 0, locked_until = NULL WHERE username = ?", tenSieuQuanTri);
    }

    private void datLaiMocChongDungLai() {
        jdbc.update(
                "UPDATE user_totp SET last_used_step = NULL WHERE user_id = (SELECT id FROM users WHERE username = ?)",
                tenSieuQuanTri);
    }

    @Test
    @DisplayName("⛔⛔ Đặt bí mật: thiếu mã ⇒ ADM-2023 · mã sai ⇒ 403 ADM-2024 (⛔ 401) + bị đếm · đúng mã ⇒ mã hoá, ⛔ lộ")
    void datBiMatCanMaVaKhongLoGiaTri() {
        ResponseEntity<String> thieuMa = ghi(DUONG_BI_MAT, "{\"giaTri\":\"%s\"}".formatted(GIA_TRI_BI_MAT));
        assertThat(thieuMa.getStatusCode().value()).as("%s", thieuMa.getBody()).isEqualTo(403);
        assertThat(thieuMa.getBody()).contains("ADM-2023");
        assertThat(soBiMat()).isZero();

        String maSai = PhienHttp.maTotp(sieuQuanTri.secret(), 10);
        ResponseEntity<String> sai =
                ghi(DUONG_BI_MAT, "{\"giaTri\":\"%s\",\"maXacThuc\":\"%s\"}".formatted(GIA_TRI_BI_MAT, maSai));
        assertThat(sai.getStatusCode().value())
                .as(
                        "⛔⛔ mã sai phải là 403 — 401 làm giao diện tưởng mất phiên, gửi lại mã sai rồi đá người dùng ra: %s",
                        sai.getBody())
                .isEqualTo(403);
        assertThat(sai.getBody()).contains("ADM-2024");
        assertThat(jdbc.queryForObject(
                        "SELECT failed_login_count FROM users WHERE username = ?", Integer.class, tenSieuQuanTri))
                .as("lượt sai phải vào bộ đếm khoá tài khoản — ⛔ thì dò 10⁶ mã ⛔ bao giờ bị khoá")
                .isEqualTo(1);
        assertThat(soBiMat()).isZero();

        ResponseEntity<String> dung = ghi(
                DUONG_BI_MAT,
                "{\"giaTri\":\"  %s  \",\"maXacThuc\":\"%s\"}"
                        .formatted(GIA_TRI_BI_MAT, PhienHttp.maTotp(sieuQuanTri.secret(), 1)));
        assertThat(dung.getStatusCode().value()).as("%s", dung.getBody()).isEqualTo(200);
        assertThat(dung.getBody()).contains("GIAO_DIEN").doesNotContain(GIA_TRI_BI_MAT);

        String banMa = jdbc.queryForObject("SELECT ciphertext FROM integration_secrets", String.class);
        assertThat(banMa).matches("^[A-Za-z0-9_-]+:.+$").doesNotContain(GIA_TRI_BI_MAT);
        assertThat(biMat.giaTri(LoaiBiMat.RECAPTCHA_SECRET_KEY))
                .as("đọc lúc DÙNG ra đúng bản rõ, đã bỏ khoảng trắng hai đầu")
                .contains(GIA_TRI_BI_MAT);

        for (String duong : new String[] {"/api/v1/system/cau-hinh/bi-mat", "/api/v1/system/cau-hinh"}) {
            ResponseEntity<String> doc = phienHttp.get(sieuQuanTri.phien(), duong);
            assertThat(doc.getStatusCode().value()).isEqualTo(200);
            assertThat(doc.getBody()).as("⛔ %s trả giá trị bí mật", duong).doesNotContain(GIA_TRI_BI_MAT);
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE entity_type = 'Bí mật tích hợp'", Integer.class))
                .as("lượt đặt phải để lại dấu vết kiểm toán")
                .isPositive();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE entity_type = 'Bí mật tích hợp' "
                                + "AND (coalesce(new_value::text, '') LIKE '%' || ? || '%')",
                        Integer.class, banMa.substring(banMa.indexOf(':') + 1, banMa.indexOf(':') + 12)))
                .as("⛔ bản mã ⛔ được vào nhật ký kiểm toán")
                .isZero();
        assertThat(soSuKien("INTEGRATION_SECRET_CHANGED")).isEqualTo(1);

        // Bước +2 nằm NGOÀI dung sai ±1 của máy chủ (đỏ đúng ở lượt chạy đầu) ⇒ đặt lại mốc chống dùng lại rồi dùng +1.
        datLaiMocChongDungLai();
        ResponseEntity<String> xoa = ghi(
                DUONG_BI_MAT + "/xoa", "{\"maXacThuc\":\"%s\"}".formatted(PhienHttp.maTotp(sieuQuanTri.secret(), 1)));
        assertThat(xoa.getStatusCode().value()).as("%s", xoa.getBody()).isEqualTo(200);
        assertThat(soBiMat()).isZero();
    }

    @Test
    @DisplayName("⛔ Có QUYỀN ghi bí mật mà ⛔ vai trò SUPER_ADMIN ⇒ vẫn 403")
    void quyenKhongDuThieuVaiTro() {
        String ten = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6144_quyen", VAI_TRO_CO_QUYEN_BI_MAT);
        PhienHttp.Phien phien = phienHttp.dangNhap(ten);
        ResponseEntity<String> ra =
                phienHttp.goi(phien, HttpMethod.PUT, DUONG_BI_MAT, "{\"giaTri\":\"x-y-z\",\"maXacThuc\":\"123456\"}");
        assertThat(ra.getStatusCode().value()).as("%s", ra.getBody()).isEqualTo(403);
        assertThat(ra.getBody()).contains("AUTH-3001");
        assertThat(soBiMat()).isZero();
    }

    @Test
    @DisplayName(
            "⛔⛔ Tham số nhóm SECURITY: ⛔ mã ⇒ ADM-2023, giá trị ⛔ đổi · có mã ⇒ đổi + sự kiện · nhóm thường ⛔ cần mã")
    void thamSoNhayCamCanMa() {
        String khoa = "security.password.min-length";
        String cu = jdbc.queryForObject("SELECT setting_value FROM settings WHERE setting_key = ?", String.class, khoa);
        try {
            ResponseEntity<String> thieu = ghi("/api/v1/settings/" + khoa, "{\"value\":\"9\"}");
            assertThat(thieu.getStatusCode().value()).as("%s", thieu.getBody()).isEqualTo(403);
            assertThat(thieu.getBody()).contains("ADM-2023");
            assertThat(jdbc.queryForObject(
                            "SELECT setting_value FROM settings WHERE setting_key = ?", String.class, khoa))
                    .isEqualTo(cu);

            int suKienTruoc = soSuKien("SECURITY_SETTING_CHANGED");
            ResponseEntity<String> co = ghi(
                    "/api/v1/settings/" + khoa,
                    "{\"value\":\"12\",\"maXacThuc\":\"%s\"}".formatted(PhienHttp.maTotp(sieuQuanTri.secret(), 1)));
            assertThat(co.getStatusCode().value()).as("%s", co.getBody()).isEqualTo(200);
            assertThat(co.getBody()).contains("\"canXacThucLai\":true");
            assertThat(soSuKien("SECURITY_SETTING_CHANGED")).isEqualTo(suKienTruoc + 1);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM audit_logs WHERE entity_type = 'Tham số cấu hình'", Integer.class))
                    .as("T61.42: đổi tham số trước nay ⛔ để lại dòng kiểm toán nào")
                    .isPositive();

            ResponseEntity<String> thuong =
                    ghi("/api/v1/settings/system.dashboard.auto-refresh-minutes", "{\"value\":\"5\"}");
            assertThat(thuong.getStatusCode().value())
                    .as("đối chứng: nhóm thường ⛔ đòi mã — %s", thuong.getBody())
                    .isEqualTo(200);
        } finally {
            jdbc.update("UPDATE settings SET setting_value = ? WHERE setting_key = ?", cu, khoa);
        }
    }

    @Test
    @DisplayName("⛔ Khôi phục CSDL — mã 2FA sai là 403 ADM-2024, ⛔ còn là 401 (bản cũ đá người dùng khỏi phiên)")
    void khoiPhucMaSaiKhong401() {
        ResponseEntity<String> ra = ghi(
                "/api/v1/backups/%s/restore".formatted(UUID.randomUUID()),
                "{\"confirmation\":\"x\",\"reason\":\"kiểm thử\",\"totpCode\":\"%s\"}"
                        .formatted(PhienHttp.maTotp(sieuQuanTri.secret(), 10)));
        assertThat(ra.getStatusCode().value()).as("%s", ra.getBody()).isEqualTo(403);
        assertThat(ra.getBody()).contains("ADM-2024");
    }

    @Test
    @DisplayName("⛔⛔ T54.4 — ⛔ tự thêm quyền mình ⛔ có vào vai trò, ⛔ tự gán ADMIN_HR/SUPER_ADMIN; bớt quyền thì được")
    void khongCapVuotQuyen() {
        String ten = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t544_pq", VAI_TRO_PHAN_QUYEN);
        PhienHttp.Phien phien = phienHttp.dangNhap(ten);
        UUID minh = jdbc.queryForObject("SELECT public_id FROM users WHERE username = ?", UUID.class, ten);

        ResponseEntity<String> themQuyen = phienHttp.goi(
                phien,
                HttpMethod.PUT,
                "/api/v1/admin/users/roles/%s/permissions".formatted(VAI_TRO_PHAN_QUYEN),
                "{\"permissionCodes\":[\"adm:role:view\",\"adm:role:manage\",\"adm:user:view\","
                        + "\"adm:user:assign-role\",\"hr:employee:view-sensitive\"]}");
        assertThat(themQuyen.getStatusCode().value())
                .as("%s", themQuyen.getBody())
                .isEqualTo(403);
        assertThat(themQuyen.getBody()).contains("ADM-2022").contains("hr:employee:view-sensitive");
        assertThat(soQuyen(VAI_TRO_PHAN_QUYEN)).isEqualTo(4);
        assertThat(soSuKien("PERMISSION_GRANT_BLOCKED")).isPositive();

        for (String vaiTro : new String[] {"ADMIN_HR", "SUPER_ADMIN"}) {
            ResponseEntity<String> gan = phienHttp.goi(
                    phien,
                    HttpMethod.PUT,
                    "/api/v1/admin/users/%s/roles".formatted(minh),
                    "{\"roleCodes\":[\"%s\",\"%s\"]}".formatted(VAI_TRO_PHAN_QUYEN, vaiTro));
            assertThat(gan.getStatusCode().value())
                    .as("%s: %s", vaiTro, gan.getBody())
                    .isEqualTo(403);
            assertThat(gan.getBody()).contains("ADM-2022");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id WHERE u.username = ?",
                        Integer.class,
                        ten))
                .isEqualTo(1);

        // Đối chứng (luật 9): BỚT một quyền mình có thì được — bảo đảm chặn vì VƯỢT quyền, ⛔ chặn mọi lượt sửa.
        ResponseEntity<String> bot = phienHttp.goi(
                phien,
                HttpMethod.PUT,
                "/api/v1/admin/users/roles/%s/permissions".formatted(VAI_TRO_PHAN_QUYEN),
                "{\"permissionCodes\":[\"adm:role:view\",\"adm:role:manage\",\"adm:user:view\"]}");
        assertThat(bot.getStatusCode().value()).as("%s", bot.getBody()).isEqualTo(204);
        assertThat(soQuyen(VAI_TRO_PHAN_QUYEN)).isEqualTo(3);
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<String> ghi(String duong, String than) {
        HttpMethod dongTu = duong.endsWith("/xoa") || duong.endsWith("/restore") ? HttpMethod.POST : HttpMethod.PUT;
        return phienHttp.goi(sieuQuanTri.phien(), dongTu, duong, than);
    }

    private int soBiMat() {
        return jdbc.queryForObject("SELECT count(*) FROM integration_secrets", Integer.class);
    }

    private int soQuyen(String vaiTro) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id WHERE r.code = ?",
                Integer.class,
                vaiTro);
    }

    private int soSuKien(String loai) {
        return jdbc.queryForObject("SELECT count(*) FROM security_events WHERE event_type = ?", Integer.class, loai);
    }
}
