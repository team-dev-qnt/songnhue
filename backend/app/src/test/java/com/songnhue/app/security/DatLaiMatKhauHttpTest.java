package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

/**
 * <b>T61.31 · CN-05.2 — quản trị viên đặt lại mật khẩu; và T61.36 — chính chủ được BÁO.</b>
 *
 * <h2>Khoảng trống đo được ngày 15/09/2026</h2>
 *
 * <p>Đặc tả CN-05.2 đòi <i>"đặt lại mật khẩu tài khoản nội bộ"</i>, danh mục quyền có sẵn
 * {@code adm:user:reset-password} <b>từ 13/08</b> — và {@code UserAdminController} có <b>0 endpoint</b>
 * cho nó, {@code grep} toàn kho ra <b>0 lời gọi</b>. Hệ ⛔ có luồng "quên mật khẩu" qua thư, nên người
 * quên mật khẩu ⛔ có đường nào ngoài việc dựng lại tài khoản.
 *
 * <p>Bài đi bằng HTTP vì ba cam kết nằm ở tầng controller/filter: quyền RIÊNG (⛔ dùng chung
 * {@code adm:user:update}), bước nhập lại mã 2FA (T61.42), và chốt chặn tự đặt lại cho chính mình.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatLaiMatKhauHttpTest extends IntegrationTestBase {

    private static final String VAI_TRO = "KIEMTRA_T6131_DAT_LAI_MK";
    private static final String MAT_KHAU_MOI = "MatKhauTam2026xyz";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.PhienHaiBuoc quanTri;
    private String tenQuanTri;

    @BeforeAll
    void dungVaiTro() {
        boVaiTro();
        jdbc.update(
                "INSERT INTO roles (code, name, is_system) VALUES (?, 'Kiểm thử đặt lại mật khẩu', FALSE)", VAI_TRO);
        int so = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN ('adm:user:view', 'adm:user:reset-password')",
                VAI_TRO);
        assertThat(so)
                .as("chống tập rỗng: `adm:user:reset-password` phải CÓ trong danh mục (seed 13/08)")
                .isEqualTo(2);

        phienHttp = new PhienHttp(http);
        tenQuanTri = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6131_qt", VAI_TRO);
        jdbc.update("UPDATE users SET two_factor_required = TRUE WHERE username = ?", tenQuanTri);
        quanTri = phienHttp.dangNhapHaiBuoc(tenQuanTri);
    }

    @AfterAll
    void boVaiTro() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO);
    }

    private ResponseEntity<String> datLai(String publicId, String matKhau, String maXacThuc) {
        return phienHttp.goi(
                quanTri.phien(),
                HttpMethod.POST,
                "/api/v1/admin/users/" + publicId + "/dat-lai-mat-khau",
                "{\"matKhauTam\":\"%s\",\"maXacThuc\":\"%s\"}".formatted(matKhau, maXacThuc));
    }

    /**
     * Mã TOTP dùng được cho lượt gọi kế tiếp.
     *
     * <p>⚠⚠ ⛔ đi bằng cách tăng dần độ lệch bước: {@code verifyOrThrow} chỉ nhận drift <b>±1</b>, nên
     * lệch 2 trở đi là {@code ADM-2024} — bản đầu của bài này đỏ đúng như vậy ở 3/5 bài. Cách đúng là
     * giữ nguyên một bước và <b>xoá mốc chống dùng lại</b>, vì cả lớp chạy trong cùng một bước 30 giây.
     */
    private String maMoi() {
        jdbc.update(
                "UPDATE user_totp SET last_used_step = NULL WHERE user_id = (SELECT id FROM users WHERE username = ?)",
                tenQuanTri);
        return PhienHttp.maTotp(quanTri.secret(), 1);
    }

    private String publicIdCua(String username) {
        return jdbc.queryForObject("SELECT public_id::text FROM users WHERE username = ?", String.class, username);
    }

    @Test
    @DisplayName("⭐⭐ Đặt lại được: mật khẩu mới dùng đăng nhập được, buộc đổi ở lần đăng nhập tới, phiên cũ bị thu hồi")
    void datLaiDuocVaThuHoiPhien() {
        String nanNhan = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6131_nn");
        PhienHttp phienNanNhan = new PhienHttp(http);
        PhienHttp.Phien cuaHo = phienNanNhan.dangNhap(nanNhan);
        assertThat(phienNanNhan.get(cuaHo, "/api/v1/auth/me").getStatusCode().value())
                .as("đối chứng: phiên của nạn nhân ĐANG dùng được trước lượt đặt lại")
                .isEqualTo(200);

        ResponseEntity<String> tl = datLai(publicIdCua(nanNhan), MAT_KHAU_MOI, maMoi());
        assertThat(tl.getStatusCode().value()).as("%s", tl.getBody()).isEqualTo(204);

        assertThat(jdbc.queryForObject(
                        "SELECT must_change_password FROM users WHERE username = ?", Boolean.class, nanNhan))
                .as("người đặt BIẾT mật khẩu tạm ⇒ nó phải chết sau lần đăng nhập đầu")
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM sessions s JOIN users u ON u.id = s.user_id "
                                + "WHERE u.username = ? AND s.revoked_at IS NULL",
                        Integer.class,
                        nanNhan))
                .as("⛔ để phiên cũ sống: nếu lý do đặt lại là 'nghi bị chiếm' thì đó là vá một nửa")
                .isZero();

        // ⚠ `postForEntity` với thân là String ⛔ đặt Content-Type ⇒ 400 trước khi tới logic (đo được
        //   ở lượt chạy đầu). Đi qua `dangJson` để có cả JSON header lẫn IP riêng của lớp.
        ResponseEntity<String> dn = phienHttp.dangJson(
                "/api/v1/auth/login", "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(nanNhan, MAT_KHAU_MOI));
        assertThat(dn.getStatusCode().value())
                .as("mật khẩu tạm phải dùng được ngay: %s", dn.getBody())
                .isEqualTo(200);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM security_events e JOIN users u ON u.id = e.user_id "
                                + "WHERE u.username = ? AND e.event_type = 'PASSWORD_RESET_BY_ADMIN'",
                        Integer.class,
                        nanNhan))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔⛔ T61.36 — CHÍNH CHỦ được báo, và có người nhận thật (⛔ chỉ một dòng security_events)")
    void chinhChuDuocBao() {
        String nanNhan = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6136_nn");
        ResponseEntity<String> tl = datLai(publicIdCua(nanNhan), MAT_KHAU_MOI, maMoi());
        assertThat(tl.getStatusCode().value()).as("%s", tl.getBody()).isEqualTo(204);

        Integer soNguoiNhan = jdbc.queryForObject(
                "SELECT count(*) FROM notification_recipients r "
                        + "JOIN notifications n ON n.id = r.notification_id "
                        + "JOIN users u ON u.id = r.user_id "
                        + "WHERE n.event_type = 'PASSWORD_RESET_BY_ADMIN' AND u.username = ?",
                Integer.class,
                nanNhan);
        assertThat(soNguoiNhan)
                .as("⛔⛔ Một thông báo ⛔ người nhận im lặng y như ⛔ có thông báo — và người duy nhất "
                        + "biết 'tôi ⛔ làm việc này' là người duy nhất ⛔ được báo trước bản vá")
                .isPositive();
    }

    @Test
    @DisplayName("⛔ Thiếu mã 2FA ⇒ ADM-2023, và mật khẩu KHÔNG đổi")
    void thieuMaHaiBuocThiTuChoi() {
        String nanNhan = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6131_nn2");
        String hashTruoc =
                jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, nanNhan);

        ResponseEntity<String> tl = datLai(publicIdCua(nanNhan), MAT_KHAU_MOI, "");
        assertThat(tl.getStatusCode().value()).as("%s", tl.getBody()).isEqualTo(403);
        assertThat(tl.getBody()).contains("ADM-2023");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, nanNhan))
                .isEqualTo(hashTruoc);
    }

    @Test
    @DisplayName("⛔⛔ Tự đặt lại cho CHÍNH MÌNH bị chặn (ADM-2025) — cửa này ⛔ hỏi mật khẩu cũ")
    void tuDatLaiBiChan() {
        ResponseEntity<String> tl = datLai(publicIdCua(tenQuanTri), MAT_KHAU_MOI, maMoi());

        assertThat(tl.getStatusCode().value()).as("%s", tl.getBody()).isEqualTo(403);
        assertThat(tl.getBody()).contains("ADM-2025");
    }

    @Test
    @DisplayName("⛔ Mật khẩu tạm phải qua chính sách độ mạnh — ⛔ có cửa sau cho '123'")
    void matKhauTamPhaiQuaChinhSach() {
        String nanNhan = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6131_nn3");
        ResponseEntity<String> tl = datLai(publicIdCua(nanNhan), "123", maMoi());

        assertThat(tl.getStatusCode().value()).as("%s", tl.getBody()).isEqualTo(422);
        assertThat(tl.getBody()).contains("matKhauTam");
    }
}
