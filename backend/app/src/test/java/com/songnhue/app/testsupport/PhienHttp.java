package com.songnhue.app.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.identity.UserStatus;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Đăng nhập thật qua HTTP và giữ đủ ba thứ một trình duyệt giữ: access token, vé CSRF, cookie.
 *
 * <h2>Vì sao cần một bộ trợ giúp riêng</h2>
 *
 * Phần lớn bài kiểm tích hợp của dự án gọi thẳng service. Nhanh, nhưng nó bỏ qua chính những tầng
 * hay hỏng nhất: chuỗi filter, envelope, {@code @RequirePermission} tầng 2, và <b>cả
 * {@code AuditContext}</b> — thứ {@code AuditorAwareImpl} đọc để điền {@code created_by}. Đó là nội
 * dung nợ #65 và #66.
 *
 * <p>Bài học đã trả giá đúng ở chỗ này: {@code GET /api/v1/cms/articles} trả <b>500 cho mọi lượt
 * gọi</b> suốt từ WS-13, trong khi {@code ArticleLifecycleTest} vẫn xanh — vì nó khẳng định
 * <i>bên trong</i> giao dịch, nơi nạp lười còn hoạt động. Không có đường HTTP thì không có cách nào
 * thấy.
 *
 * <p>⚠ Tài khoản do lớp này tạo <b>không mang vai trò nào</b> theo mặc định. Hai lý do: đăng nhập ra
 * thẳng {@code AUTHENTICATED} (không rơi vào nhóm bắt buộc 2FA, khỏi phải dựng mã TOTP), và bài kiểm
 * phải <b>nói rõ</b> nó cần quyền gì — cấp sẵn cả cụm quyền là cách chắc chắn nhất để một lượt kiểm
 * quyền trở nên vô nghĩa mà vẫn xanh.
 */
public final class PhienHttp {

    /** Mật khẩu dùng chung cho tài khoản kiểm thử — đủ dài, đủ loại ký tự theo chính sách WS-5. */
    public static final String MAT_KHAU = "KiemThu@2026";

    private final TestRestTemplate http;

    public PhienHttp(TestRestTemplate http) {
        this.http = http;
    }

    /** Access token, vé CSRF và cookie của một phiên đang sống. */
    public record Phien(String accessToken, String csrfToken, String cookie) {}

    /**
     * Tạo tài khoản kiểm thử kèm đúng những vai trò được kê.
     *
     * @param maVaiTro mã trong bảng {@code roles}; để trống là tài khoản không có quyền nào
     */
    public static String taoNguoiDung(
            UserRepository users, PasswordPolicyService passwords, JdbcTemplate jdbc, String hau, String... maVaiTro) {

        String username = "kiemtra_" + hau;
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = ?)", username);
        jdbc.update("DELETE FROM users WHERE username = ?", username);

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@songnhue.test");
        user.setFullName("Người dùng kiểm thử " + hau);
        user.setPasswordHash(passwords.hash(MAT_KHAU));
        user.setMustChangePassword(false);
        user.setStatus(UserStatus.ACTIVE);
        // `users.org_unit_id` là NOT NULL. Dùng đơn vị gốc do migration seed, không tự đẻ nhánh mới:
        // cây tổ chức là dữ liệu chịu tải, bài kiểm không được để lại rác trong đó.
        user.setOrgUnitId(jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class));
        users.saveAndFlush(user);

        for (String ma : maVaiTro) {
            jdbc.update(
                    """
                    INSERT INTO user_roles (user_id, role_id)
                    SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.code = ?
                    """,
                    username,
                    ma);
        }
        return username;
    }

    public Phien dangNhap(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"username":"%s","password":"%s"}""".formatted(username, MAT_KHAU),
                        headers),
                String.class);

        assertThat(response.getStatusCode())
                .as("đăng nhập phải thành công: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"stage\":\"AUTHENTICATED\"");

        List<String> setCookie = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        return new Phien(
                giaTriJson(response.getBody(), "accessToken"), giaTriCookie(setCookie, "XSRF-TOKEN"), gop(setCookie));
    }

    /** Header đầy đủ như trình duyệt gửi: Bearer + vé CSRF + cookie. */
    public HttpHeaders header(Phien phien) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(phien.accessToken());
        headers.set("X-CSRF-Token", phien.csrfToken());
        headers.set(HttpHeaders.COOKIE, phien.cookie());
        return headers;
    }

    public ResponseEntity<String> goi(Phien phien, HttpMethod verb, String duong, String than) {
        return http.exchange(duong, verb, new HttpEntity<>(than, header(phien)), String.class);
    }

    public ResponseEntity<String> get(Phien phien, String duong) {
        return http.exchange(duong, HttpMethod.GET, new HttpEntity<>(header(phien)), String.class);
    }

    private static String gop(List<String> setCookie) {
        return setCookie.stream()
                .map(dong -> dong.split(";", 2)[0])
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    private static String giaTriCookie(List<String> setCookie, String ten) {
        return setCookie.stream()
                .filter(dong -> dong.startsWith(ten + "="))
                .map(dong -> dong.split(";", 2)[0].substring(ten.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Phản hồi đăng nhập không đặt cookie " + ten));
    }

    /** Bóc một trường chuỗi khỏi envelope JSON — đủ dùng, khỏi kéo thêm thư viện vào bài kiểm. */
    public static String giaTriJson(String json, String truong) {
        String khoa = "\"" + truong + "\":\"";
        int bat = json.indexOf(khoa);
        assertThat(bat).as("không thấy trường %s trong %s", truong, json).isNotNegative();
        int dau = bat + khoa.length();
        return json.substring(dau, json.indexOf('"', dau));
    }
}
