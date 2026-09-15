package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Cột {@code ip_address} của BA bảng nhật ký ghi IP THẬT — ⛔ không phải IP kẻ gọi tự khai (T61.14).</b>
 *
 * <p>T47.4/T47.5 vá lỗ {@code X-Forwarded-For}: trước 10/09/2026 cả bốn hạn mức né được bằng một header,
 * và <b>nặng hơn</b>, giá trị ấy được ghi vào nhật ký bảo mật, danh sách phiên và nhật ký kiểm toán. Bản vá
 * được canh bằng bài {@code X-RateLimit-Remaining} — tức canh <b>hạn mức</b>. Còn ba cột nhật ký thì 0 bài
 * nào khẳng định giá trị: {@code grep -rn "ip_address\|ipAddress" src/test} = <b>0</b> (đo 14/09/2026).
 *
 * <p>Một dấu vết kiểm toán nhận dữ liệu do chính đối tượng bị kiểm toán cung cấp thì ⛔ không còn là dấu vết.
 * Và đường ghi của ba cột ⛔ trùng đường của hạn mức: {@code ClientInfo} (phiên, sự kiện bảo mật) và
 * {@code AuditContextFilter} (kiểm toán) là hai nơi gọi {@link com.songnhue.core.common.web.ClientIp} — một
 * lượt "dọn dẹp" quay về đọc header ở MỘT nơi sẽ ⛔ làm đỏ bài hạn mức nào.
 *
 * <h2>Hai vế, vì một vế ⛔ phân biệt được hai trạng thái (luật 9)</h2>
 *
 * <ol>
 *   <li><b>Có {@code X-Real-IP} (nginx biên ghi đè) + {@code X-Forwarded-For} bịa</b> ⇒ cột mang giá trị
 *       {@code X-Real-IP}.
 *   <li><b>⛔ có {@code X-Real-IP}, chỉ có {@code X-Forwarded-For} bịa</b> ⇒ cột ⛔ được mang giá trị bịa.
 *       Đây là vế bắt <i>{@code forward-headers-strategy: framework}</i> — thứ ghi đè
 *       {@code getRemoteAddr()} bằng phần tử đầu của {@code X-Forwarded-For} (T47.5). Vế 1 một mình xanh ở
 *       cả hai trạng thái.
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IpThatTrongNhatKyHttpTest extends IntegrationTestBase {

    private static final String VAI_TRO = "KIEMTRA_IPTHAT";
    private static final String TIEN_TO = "IPTHAT-";

    /** Dải tài liệu RFC 5737 — ⛔ trùng IP giả lập nào của {@code PhienHttp} (10.x.x.x). */
    private static final String IP_THAT = "203.0.113.61";

    private static final String IP_BIA = "198.51.100.66";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthorityLoader authorities;

    private String tenDangNhap;
    private long userId;

    @BeforeAll
    void dungNen() {
        don();
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử IP thật', 'Tạm, xoá ở @AfterAll', FALSE, now())",
                VAI_TRO);
        int gan = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN (?, ?)",
                VAI_TRO,
                "adm:org-unit:view",
                "adm:org-unit:manage");
        assertThat(gan)
                .as("⚠ Chống tập rỗng: mã quyền đổi tên thì mọi bài dưới đỏ với 403")
                .isEqualTo(2);
        tenDangNhap = PhienHttp.taoNguoiDung(users, passwords, jdbc, "ipthat", VAI_TRO);
        userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, tenDangNhap);
    }

    @AfterAll
    void donSach() {
        don();
    }

    @Test
    @DisplayName("⛔⛔ Vế 1 — có X-Real-IP + X-Forwarded-For bịa: phiên · sự kiện bảo mật · kiểm toán đều mang IP THẬT")
    void coXRealIpThiBaCotMangIpThat() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Real-IP", IP_THAT);
        h.set("X-Forwarded-For", IP_BIA);
        String[] token = dangNhap(h);

        assertThat(ipPhienMoiNhat())
                .as("sessions.ip_address — danh sách phiên để 'đăng xuất từ xa'")
                .isEqualTo(IP_THAT);
        assertThat(ipSuKienDangNhapMoiNhat())
                .as("security_events.ip_address của LOGIN_SUCCESS")
                .isEqualTo(IP_THAT);

        HttpHeaders ghi = headerPhien(token);
        ghi.set("X-Real-IP", IP_THAT);
        ghi.set("X-Forwarded-For", IP_BIA);
        UUID donVi = taoDonVi("V1", ghi);
        assertThat(ipKiemToan(donVi))
                .as("audit_logs.ip_address của lượt tạo đơn vị")
                .isEqualTo(IP_THAT);
    }

    @Test
    @DisplayName("⛔⛔ Vế 2 — CHỈ có X-Forwarded-For bịa: ⛔ cột nào mang giá trị bịa (bắt forward-headers-strategy)")
    void chiCoXForwardedForThiKhongCotNaoMangGiaTriBia() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Forwarded-For", IP_BIA);
        String[] token = dangNhap(h);

        String ipPhien = ipPhienMoiNhat();
        assertThat(ipPhien)
                .as(
                        """
                        sessions.ip_address = `%s` — đúng giá trị kẻ gọi tự khai qua X-Forwarded-For.

                        `server.forward-headers-strategy: framework` cho `ForwardedHeaderFilter` ghi đè \
                        `getRemoteAddr()` bằng phần tử ĐẦU của X-Forwarded-For. `ClientIp` phải bóc hết \
                        `ServletRequestWrapper` để chạm tới địa chỉ socket thật (T47.5).""",
                        ipPhien)
                .isNotEqualTo(IP_BIA)
                .as("⚠ Đối chứng phải-khác-rỗng: cột NULL cũng 'khác IP bịa' mà ⛔ chứng minh gì")
                .isNotBlank();
        assertThat(ipSuKienDangNhapMoiNhat()).isNotEqualTo(IP_BIA).isEqualTo(ipPhien);

        HttpHeaders ghi = headerPhien(token);
        ghi.set("X-Forwarded-For", IP_BIA);
        UUID donVi = taoDonVi("V2", ghi);
        assertThat(ipKiemToan(donVi)).isNotEqualTo(IP_BIA).isEqualTo(ipPhien);
    }

    // -------------------------------------------------------------------------

    /** Đăng nhập với ĐÚNG bộ header được truyền — ⛔ qua {@code PhienHttp.dangNhap} vì nó tự đặt X-Real-IP. */
    private String[] dangNhap(HttpHeaders h) {
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = http.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"username":"%s","password":"%s"}"""
                                .formatted(tenDangNhap, PhienHttp.MAT_KHAU),
                        h),
                String.class);
        assertThat(r.getStatusCode())
                .as("đăng nhập phải thành công: %s", r.getBody())
                .isEqualTo(HttpStatus.OK);
        List<String> setCookie = r.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        String cookie = String.join(
                "; ", setCookie.stream().map(c -> c.split(";", 2)[0]).toList());
        String csrf = setCookie.stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring("XSRF-TOKEN=".length()).split(";", 2)[0])
                .findFirst()
                .orElseThrow();
        return new String[] {PhienHttp.giaTriJson(r.getBody(), "accessToken"), csrf, cookie};
    }

    private static HttpHeaders headerPhien(String[] token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token[0]);
        h.set("X-CSRF-Token", token[1]);
        h.set(HttpHeaders.COOKIE, token[2]);
        return h;
    }

    private UUID taoDonVi(String hau, HttpHeaders h) {
        UUID cha = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);
        ResponseEntity<String> tao = http.exchange(
                "/api/v1/org-units",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"code":"%s","name":"Phòng kiểm thử IP %s","parentPublicId":"%s","unitType":"PHONG_BAN"}"""
                                .formatted(TIEN_TO + hau, hau, cha),
                        h),
                String.class);
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì khẳng định kiểm toán xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        Matcher m = Pattern.compile("\"publicId\"\\s*:\\s*\"([0-9a-f-]{36})\"").matcher(tao.getBody());
        assertThat(m.find())
                .as("thân phản hồi phải mang publicId: %s", tao.getBody())
                .isTrue();
        return UUID.fromString(m.group(1));
    }

    private String ipPhienMoiNhat() {
        return jdbc.queryForObject(
                "SELECT host(ip_address) FROM sessions WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                userId);
    }

    private String ipSuKienDangNhapMoiNhat() {
        return jdbc.queryForObject(
                "SELECT host(ip_address) FROM security_events WHERE user_id = ? AND event_type = 'LOGIN_SUCCESS' "
                        + "ORDER BY id DESC LIMIT 1",
                String.class,
                userId);
    }

    private String ipKiemToan(UUID donVi) {
        List<String> ip = jdbc.queryForList(
                "SELECT host(ip_address) FROM audit_logs WHERE entity_public_id = ?", String.class, donVi);
        assertThat(ip)
                .as("⚠ Chống tập rỗng: lượt tạo đơn vị phải sinh ít nhất một dòng audit_logs")
                .isNotEmpty();
        assertThat(ip)
                .as("mọi dòng kiểm toán của cùng một lượt ghi phải cùng một IP")
                .containsOnly(ip.get(0));
        return ip.get(0);
    }

    private void don() {
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO);
        authorities.invalidateAll();
    }
}
