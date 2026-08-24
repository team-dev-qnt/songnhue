package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.identity.UserStatus;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Vòng đời <b>đổi mật khẩu</b>, gọi qua HTTP thật — cả chuỗi filter, cả cookie.
 *
 * <h2>Vì sao bài kiểm này ra đời</h2>
 *
 * Anh Quân báo {@code POST /auth/change-password} trả <b>403</b> khi đang thử luồng 2FA. Nhật ký
 * máy chủ kể đúng trình tự:
 *
 * <pre>
 *   POST /auth/change-password → 204   ← thành công, CSDL ghi password_changed_at
 *   POST /auth/change-password → 403   ← "header có, cookie thiếu"
 *   POST /auth/change-password → 403   ← "header thiếu, cookie thiếu"
 * </pre>
 *
 * Backend <b>không hỏng</b>: đổi mật khẩu thu hồi mọi phiên và xoá cookie CSRF, đúng §4.1. Lỗi nằm
 * ở giao diện — nó không rời khỏi biểu mẫu nên người dùng bấm gửi lại vào một phiên đã chết.
 *
 * <h2>Vậy bài kiểm ở backend để làm gì</h2>
 *
 * Để khoá <b>hợp đồng</b> mà giao diện dựa vào. FE phải kết thúc phiên sau khi đổi mật khẩu, và nó
 * làm vậy vì backend cam kết ba điều: trả 204, xoá cookie CSRF, và giết token đang cầm. Ba điều đó
 * tới giờ <b>chưa có bài kiểm nào</b> — chúng chỉ được mô tả trong tài liệu. Nếu sau này ai đó nới
 * ra "giữ lại phiên hiện tại cho đỡ phiền", giao diện sẽ đá người dùng ra vô cớ, và không gì bắt
 * được.
 *
 * <p>⚠ Bài kiểm đi qua HTTP chứ không gọi thẳng {@code PasswordChangeService}: hai trong ba cam kết
 * (cookie, và việc token cũ chết) <b>không tồn tại ở tầng service</b> — chúng nằm ở controller và ở
 * chuỗi filter. Gọi thẳng service là kiểm đúng phần không hỏng.
 */
class ChangePasswordHttpTest extends IntegrationTestBase {

    private static final String MAT_KHAU_CU = "MatKhauCu@2026";
    private static final String MAT_KHAU_MOI = "MatKhauMoi@2026";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("⭐⭐ Đổi mật khẩu: 204, xoá cookie CSRF, và token đang cầm chết ngay")
    void doiMatKhauThuHoiPhienHienTai() {
        String username = taoNguoiDung("doimatkhau");

        Phien phien = dangNhap(username, MAT_KHAU_CU);

        // Trước khi đổi: token dùng được.
        assertThat(goiMe(phien).getStatusCode())
                .as("chưa đổi mật khẩu thì phiên phải còn dùng được — nếu đỏ ở đây thì bài kiểm "
                        + "đang chứng minh nhầm chuyện")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> doi = http.exchange(
                "/api/v1/auth/change-password",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"currentPassword":"%s","newPassword":"%s"}"""
                                .formatted(MAT_KHAU_CU, MAT_KHAU_MOI),
                        headerCua(phien)),
                String.class);

        assertThat(doi.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Cam kết 0 — 204 KHÔNG có thân, và không có envelope. Ghim lại vì giao diện dựa vào
        // đúng điều này.
        //
        // ⚠⚠ `conventions.md` §2.1 hứa "envelope thống nhất 100% endpoint", nhưng 204 là ngoại lệ
        //    KHÔNG TRÁNH ĐƯỢC: `ResponseEnvelopeAdvice` là một `ResponseBodyAdvice`, mà Spring
        //    không gọi advice khi handler trả `void` — không thân thì không converter nào chạy.
        //    Hiện có 24 endpoint như vậy.
        //
        //    Lời hứa sai đó đã trả giá: `apiClient.unwrap` được viết theo nó nên không kiểm mã
        //    trạng thái, và axios đặt `data = ''` cho thân rỗng → `''.success` là `undefined` →
        //    hàm ném `SYS-0001 "Thao tác không thành công"` **sau khi máy chủ đã commit**. Người
        //    dùng thấy báo lỗi, bấm gửi lại, và nhận đúng 403 AUTH-0005 ở bài kiểm dưới đây.
        //
        //    Bài kiểm ở phía kia: `admin-app/src/shared/apiClientNoContent.test.ts`. Đổi cam kết
        //    này (cho 204 mang envelope chẳng hạn) thì phải sửa cả hai nơi.
        assertThat(doi.getBody())
                .as("204 phải có thân RỖNG — giao diện coi 'không có thân' là thành công")
                .isNull();

        // Cam kết 1 — xoá cookie CSRF. Giao diện dựa vào đây: còn cookie thì trình duyệt còn gửi
        // kèm một vé của phiên đã chết, và lỗi hiện ra thành "vé không khớp" thay vì "hết phiên".
        assertThat(cookieBiXoa(doi.getHeaders(), "XSRF-TOKEN"))
                .as("phản hồi phải xoá cookie XSRF-TOKEN (Max-Age=0)")
                .isTrue();
        assertThat(cookieBiXoa(doi.getHeaders(), "refresh_token"))
                .as("phải xoá luôn cookie refresh — không thì trình duyệt còn giữ vé vào một "
                        + "family vừa bị thu hồi")
                .isTrue();

        // Cam kết 2 — access token đang cầm chết ngay, không chờ hết hạn 30'. Đây là lý do giao
        // diện BẮT BUỘC phải rời khỏi biểu mẫu: mọi lượt gọi tiếp theo đều hỏng.
        assertThat(goiMe(phien).getStatusCode())
                .as("đổi mật khẩu phải thu hồi cả phiên đang thao tác (§4.1) — giữ lại thì không "
                        + "trả lời được câu 'làm sao biết phiên này là của chủ nhân'")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Cam kết 3 — mật khẩu mới dùng được, và người dùng không còn bị bắt đổi nữa.
        Phien phienMoi = dangNhap(username, MAT_KHAU_MOI);
        assertThat(goiMe(phienMoi).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(goiMe(phienMoi).getBody())
                .as("must_change_password phải hạ xuống, nếu không giao diện đẩy người dùng "
                        + "quay lại biểu mẫu vô hạn")
                .contains("\"mustChangePassword\":false");
    }

    @Test
    @DisplayName("⛔ Gửi lại đúng lượt đổi đó lần thứ hai → 403 AUTH-0005, đúng điều anh Quân gặp")
    void guiLaiLanHaiBi403() {
        String username = taoNguoiDung("guilai");
        Phien phien = dangNhap(username, MAT_KHAU_CU);

        String than =
                """
                {"currentPassword":"%s","newPassword":"%s"}""".formatted(MAT_KHAU_CU, MAT_KHAU_MOI);

        assertThat(http.exchange(
                                "/api/v1/auth/change-password",
                                HttpMethod.POST,
                                new HttpEntity<>(than, headerCua(phien)),
                                String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // Lượt thứ hai: trình duyệt thật đã mất cookie (máy chủ vừa xoá) nhưng vẫn còn vé trong
        // bộ nhớ — tái hiện đúng bằng cách gửi header mà KHÔNG gửi cookie.
        HttpHeaders khongCookie = headerCua(phien);
        khongCookie.remove(HttpHeaders.COOKIE);

        ResponseEntity<String> lai = http.exchange(
                "/api/v1/auth/change-password", HttpMethod.POST, new HttpEntity<>(than, khongCookie), String.class);

        assertThat(lai.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(lai.getBody())
                .as("mã lỗi phải là AUTH-0005 (CSRF) — đúng thứ đã hiện trong nhật ký thật")
                .contains("AUTH-0005");
    }

    // -------------------------------------------------------------------------

    private record Phien(String accessToken, String csrfToken, String cookie) {}

    private Long donViGoc() {
        return jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
    }

    @Transactional
    String taoNguoiDung(String hau) {
        String username = "kiemtra_" + hau;
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@songnhue.test");
        user.setFullName("Người dùng kiểm thử " + hau);
        user.setPasswordHash(passwords.hash(MAT_KHAU_CU));
        user.setMustChangePassword(false);
        user.setStatus(UserStatus.ACTIVE);
        // `users.org_unit_id` là NOT NULL — mọi tài khoản phải thuộc một đơn vị. Dùng đơn vị gốc
        // do migration seed (mã `CTY`), không tự tạo thêm: cây tổ chức là dữ liệu chịu tải, bài
        // kiểm không được đẻ ra nhánh lạ trong đó.
        user.setOrgUnitId(donViGoc());
        // Không gán vai trò nào: đổi mật khẩu là `@AuthenticatedEndpoint`, không đòi quyền. Không
        // vai trò cũng có nghĩa là không rơi vào nhóm bắt buộc 2FA, nên đăng nhập ra thẳng
        // AUTHENTICATED và bài kiểm không phải dựng mã TOTP.
        users.save(user);
        return username;
    }

    private Phien dangNhap(String username, String matKhau) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = http.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"username":"%s","password":"%s"}""".formatted(username, matKhau),
                        headers),
                String.class);

        assertThat(response.getStatusCode())
                .as("đăng nhập phải thành công: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);

        String body = response.getBody();
        assertThat(body).contains("\"stage\":\"AUTHENTICATED\"");

        List<String> setCookie = response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        return new Phien(giaTriJson(body, "accessToken"), giaTriCookie(setCookie, "XSRF-TOKEN"), gopCookie(setCookie));
    }

    private HttpHeaders headerCua(Phien phien) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(phien.accessToken());
        headers.set("X-CSRF-Token", phien.csrfToken());
        headers.set(HttpHeaders.COOKIE, phien.cookie());
        return headers;
    }

    private ResponseEntity<String> goiMe(Phien phien) {
        return http.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headerCua(phien)), String.class);
    }

    /** Một cookie coi là bị xoá khi máy chủ đặt lại nó với {@code Max-Age=0}. */
    private static boolean cookieBiXoa(HttpHeaders headers, String ten) {
        return headers.getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(dong -> dong.startsWith(ten + "="))
                .anyMatch(dong -> dong.contains("Max-Age=0"));
    }

    private static String gopCookie(List<String> setCookie) {
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
    private static String giaTriJson(String json, String truong) {
        String khoa = "\"" + truong + "\":\"";
        int bat = json.indexOf(khoa);
        assertThat(bat).as("không thấy trường %s trong %s", truong, json).isNotNegative();
        int dau = bat + khoa.length();
        return json.substring(dau, json.indexOf('"', dau));
    }
}
