package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
 * <b>Xoá tài khoản — nút mở ra giao diện (T61.21)</b>: tự xoá mình bị chặn, xoá người khác thì người ấy
 * mất quyền NGAY, kể cả với access token còn hạn.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XoaTaiKhoanHttpTest extends IntegrationTestBase {

    private static final String VAI_TRO = "KIEMTRA_T6121_XOA_TK";

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
    void dangNhap() {
        boVaiTro();
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES (?, 'Kiểm thử xoá tài khoản', FALSE)", VAI_TRO);
        int so = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN ('adm:user:view', 'adm:user:update')",
                VAI_TRO);
        assertThat(so).as("chống tập rỗng: mã quyền đổi tên?").isEqualTo(2);
        phienHttp = new PhienHttp(http);
        quanTri = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6121_qt", VAI_TRO));
    }

    @AfterAll
    void boVaiTro() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO);
    }

    @Test
    @DisplayName("⛔⛔ Tự xoá tài khoản của chính mình ⇒ 403 ADM-2020, tài khoản còn nguyên")
    void tuXoaMinhBiChan() {
        UUID minh = jdbc.queryForObject("SELECT public_id FROM users WHERE username = 'kiemtra_t6121_qt'", UUID.class);
        ResponseEntity<String> ra = phienHttp.goi(quanTri, HttpMethod.DELETE, "/api/v1/admin/users/" + minh, null);
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ra.getBody()).contains("ADM-2020");
        assertThat(phienHttp.get(quanTri, "/api/v1/auth/me").getStatusCode())
                .as("⛔ lượt bị chặn ⛔ được làm mất phiên của chính người thao tác")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("⭐⭐ Xoá người khác ⇒ 204, người ấy mất quyền NGAY dù access token còn hạn, biến khỏi danh sách")
    void xoaNguoiKhacMatQuyenNgay() {
        PhienHttp phienNan = new PhienHttp(http);
        String ten = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6121_nan");
        PhienHttp.Phien nan = phienNan.dangNhap(ten);
        assertThat(phienNan.get(nan, "/api/v1/auth/me").getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID id = jdbc.queryForObject("SELECT public_id FROM users WHERE username = ?", UUID.class, ten);

        ResponseEntity<String> xoa = phienHttp.goi(quanTri, HttpMethod.DELETE, "/api/v1/admin/users/" + id, null);
        assertThat(xoa.getStatusCode()).as("%s", xoa.getBody()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(phienNan.get(nan, "/api/v1/auth/me").getStatusCode())
                .as("⛔ tài khoản đã xoá mà token cũ còn gọi được là xoá chỉ trên giấy")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(phienHttp.get(quanTri, "/api/v1/admin/users").getBody()).doesNotContain(ten);
    }
}
