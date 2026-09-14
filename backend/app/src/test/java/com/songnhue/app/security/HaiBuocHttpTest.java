package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.common.util.TotpGenerator;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Mật khẩu một mình ⛔ vượt được 2FA — T61.30</b> (tìm ra ở lượt tự đánh giá ASVS 4.3.1 / 2.5.6, T61.28).
 *
 * <h2>Chuyện đã xảy ra</h2>
 *
 * {@code /auth/login} phát vé challenge sau bước mật khẩu cho CẢ tài khoản đã có 2FA; {@code /2fa/enroll}
 * chỉ đòi vé ấy, và {@code TotpService.enroll} XOÁ secret đã xác nhận. ⇒ Kẻ có mật khẩu của một quản trị
 * viên: đăng nhập → enroll (xoá 2FA của chủ) → confirm bằng ứng dụng của mình ⇒ nhận token. Kho có 0 bài
 * kiểm HTTP cho luồng 2FA trước bài này.
 *
 * <p>Đi trọn bằng HTTP thật và TOTP thật ({@link TotpGenerator} trên secret máy chủ trả về) — ⛔ mock
 * {@code TotpService} (luật 4).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HaiBuocHttpTest extends IntegrationTestBase {

    private static final String VAI_TRO = "KIEMTRA_T6130_DAT_LAI_2FA";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien quanTri;

    @BeforeAll
    void dungVaiTro() {
        boVaiTro();
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES (?, 'Kiểm thử đặt lại 2FA', FALSE)", VAI_TRO);
        int so = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN ('adm:user:view', 'adm:user:update')",
                VAI_TRO);
        assertThat(so).as("chống tập rỗng: mã quyền đổi tên?").isEqualTo(2);
        phienHttp = new PhienHttp(http);
        quanTri = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6130_qt", VAI_TRO));
    }

    @AfterAll
    void boVaiTro() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO);
    }

    @Test
    @DisplayName("⛔⛔ Tài khoản ĐÃ có 2FA: vé challenge ⛔ đăng ký lại được (AUTH-0009), 2FA cũ còn nguyên dùng được")
    void dangKyLaiBiChan() {
        String ten = taoNguoiDung2fa("t6130_nannhan");
        byte[] secret = dangKyLanDau(ten);

        // Kẻ có MẬT KHẨU đăng nhập lại
        ResponseEntity<String> dn = dangNhap(ten);
        assertThat(PhienHttp.giaTriJson(dn.getBody(), "stage")).isEqualTo("TWO_FACTOR_REQUIRED");
        String ve = PhienHttp.giaTriJson(dn.getBody(), "challengeToken");

        ResponseEntity<String> enroll = post("/api/v1/auth/2fa/enroll", "{\"challengeToken\":\"%s\"}".formatted(ve));
        assertThat(enroll.getStatusCode().value())
                .as(
                        "⛔⛔ enroll bằng vé challenge trên tài khoản đã có 2FA = vượt 2FA bằng mật khẩu: %s",
                        enroll.getBody())
                .isEqualTo(403);
        assertThat(enroll.getBody()).contains("AUTH-0009").doesNotContain("secret");

        ResponseEntity<String> confirm =
                post("/api/v1/auth/2fa/confirm", "{\"challengeToken\":\"%s\",\"code\":\"123456\"}".formatted(ve));
        assertThat(confirm.getStatusCode().value()).isEqualTo(403);
        assertThat(confirm.getBody()).contains("AUTH-0009");

        ResponseEntity<String> verify = post(
                "/api/v1/auth/2fa/verify",
                "{\"challengeToken\":\"%s\",\"code\":\"%s\",\"recoveryCode\":false}".formatted(ve, ma(secret, 1)));
        assertThat(verify.getBody())
                .as("2FA của CHỦ tài khoản phải còn nguyên sau lượt bị chặn")
                .contains("\"stage\":\"AUTHENTICATED\"");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM security_events e JOIN users u ON u.id = e.user_id "
                                + "WHERE u.username = ? AND e.event_type = 'TWO_FACTOR_REENROLL_BLOCKED'",
                        Integer.class,
                        ten))
                .as("dấu hiệu mật khẩu đã lộ phải để lại dấu vết")
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
            "⭐⭐ Quản trị viên đặt lại 2FA ⇒ xoá đăng ký + mã khôi phục + thu hồi phiên; lần đăng nhập sau đăng ký từ đầu")
    void quanTriDatLai() {
        String ten = taoNguoiDung2fa("t6130_matdt");
        dangKyLanDau(ten);
        assertThat(soDong("user_recovery_codes", ten)).isPositive();
        UUID id = jdbc.queryForObject("SELECT public_id FROM users WHERE username = ?", UUID.class, ten);

        ResponseEntity<String> ra =
                phienHttp.goi(quanTri, HttpMethod.POST, "/api/v1/admin/users/%s/dat-lai-2fa".formatted(id), null);

        assertThat(ra.getStatusCode().value()).as("%s", ra.getBody()).isEqualTo(204);
        assertThat(soDong("user_totp", ten)).isZero();
        assertThat(soDong("user_recovery_codes", ten)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM sessions s JOIN users u ON u.id = s.user_id "
                                + "WHERE u.username = ? AND s.revoked_at IS NULL",
                        Integer.class,
                        ten))
                .as("phiên đang sống có thể là của kẻ đã chiếm — phải thu hồi")
                .isZero();
        assertThat(PhienHttp.giaTriJson(dangNhap(ten).getBody(), "stage")).isEqualTo("TWO_FACTOR_ENROLL_REQUIRED");
    }

    @Test
    @DisplayName("⛔ Tự đặt lại 2FA của chính mình ⇒ 403 ADM-2021")
    void tuDatLaiBiChan() {
        // Tài khoản RIÊNG: khi chặn hỏng, lượt tự đặt lại thu hồi mọi phiên của người gọi — dùng chung `quanTri`
        // thì bài bên cạnh đỏ lây với AUTH-0002, che mất bài thật sự hỏng (đo 15/09 khi phá bản vá).
        String ten = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6130_qt_tu", VAI_TRO);
        PhienHttp.Phien tuMinh = phienHttp.dangNhap(ten);
        UUID minh = jdbc.queryForObject("SELECT public_id FROM users WHERE username = ?", UUID.class, ten);
        ResponseEntity<String> ra =
                phienHttp.goi(tuMinh, HttpMethod.POST, "/api/v1/admin/users/%s/dat-lai-2fa".formatted(minh), null);
        assertThat(ra.getStatusCode().value()).isEqualTo(403);
        assertThat(ra.getBody()).contains("ADM-2021");
    }

    // -------------------------------------------------------------------------

    private String taoNguoiDung2fa(String hau) {
        String ten = PhienHttp.taoNguoiDung(users, passwords, jdbc, hau);
        jdbc.update("UPDATE users SET two_factor_required = TRUE WHERE username = ?", ten);
        return ten;
    }

    /** Luồng đăng ký lần đầu thật: login → enroll → confirm. @return secret thô */
    private byte[] dangKyLanDau(String ten) {
        ResponseEntity<String> dn = dangNhap(ten);
        assertThat(PhienHttp.giaTriJson(dn.getBody(), "stage"))
                .as("%s", dn.getBody())
                .isEqualTo("TWO_FACTOR_ENROLL_REQUIRED");
        String ve = PhienHttp.giaTriJson(dn.getBody(), "challengeToken");
        ResponseEntity<String> enroll = post("/api/v1/auth/2fa/enroll", "{\"challengeToken\":\"%s\"}".formatted(ve));
        assertThat(enroll.getStatusCode().value()).as("%s", enroll.getBody()).isEqualTo(200);
        byte[] secret = new Base32().decode(PhienHttp.giaTriJson(enroll.getBody(), "secret"));
        ResponseEntity<String> confirm = post(
                "/api/v1/auth/2fa/confirm", "{\"challengeToken\":\"%s\",\"code\":\"%s\"}".formatted(ve, ma(secret, 0)));
        assertThat(confirm.getBody())
                .as("đối chứng: đăng ký LẦN ĐẦU phải chạy")
                .contains("\"stage\":\"AUTHENTICATED\"");
        return secret;
    }

    /** Mã TOTP ở bước hiện tại + {@code lech} (lệch 1 bước nằm trong dung sai máy chủ; tránh chống dùng lại). */
    private static String ma(byte[] secret, int lech) {
        return TotpGenerator.generate(secret, TotpGenerator.stepAt(Instant.now().getEpochSecond()) + lech);
    }

    private ResponseEntity<String> dangNhap(String ten) {
        return post(
                "/api/v1/auth/login", "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(ten, PhienHttp.MAT_KHAU));
    }

    private ResponseEntity<String> post(String duong, String than) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Real-IP", "198.51.100.130");
        return http.exchange(duong, HttpMethod.POST, new HttpEntity<>(than, h), String.class);
    }

    private int soDong(String bang, String ten) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + bang + " t JOIN users u ON u.id = t.user_id WHERE u.username = ?",
                Integer.class,
                ten);
    }
}
