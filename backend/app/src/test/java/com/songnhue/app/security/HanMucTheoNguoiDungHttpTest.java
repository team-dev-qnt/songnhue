package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Hai cán bộ sau MỘT IP ⛔ ăn chung ngân sách hạn mức — T61.17.</b>
 *
 * <p>Trước bản vá, {@code RateLimitFilter} khoá mọi xô theo IP. Nếu Công ty ra Internet qua một IP
 * NAT thì 50 cán bộ chung 100 lượt API/phút và 10 lượt kết xuất/giờ — cả cơ quan nhận {@code SYS-0002}
 * lúc ⛔ ai làm gì bất thường.
 *
 * <p>⚠ Đo {@code X-RateLimit-Remaining}, ⛔ đo 200 vs 429: trần API là 100 nên hai lượt gọi nào cũng
 * trả 200 ở cả hai trạng thái (luật 9).
 *
 * <p>⚠ Mọi lượt gọi dùng CÙNG MỘT thực thể {@link PhienHttp} ⇒ cùng một {@code X-Real-IP} — đúng hình
 * dạng NAT.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HanMucTheoNguoiDungHttpTest extends IntegrationTestBase {

    private static final String DUONG_API = "/api/v1/auth/me";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp mayNat;
    private PhienHttp.Phien canBoA;
    private PhienHttp.Phien canBoB;

    @BeforeAll
    void dangNhap() {
        mayNat = new PhienHttp(http);
        canBoA = mayNat.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6117_a"));
        canBoB = mayNat.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6117_b"));
    }

    @Test
    @DisplayName("⛔⛔ Hai người dùng sau CÙNG một IP có HAI xô API riêng")
    void haiNguoiCungIpKhongChungXo() {
        int a1 = conLai(mayNat.get(canBoA, DUONG_API));
        int a2 = conLai(mayNat.get(canBoA, DUONG_API));
        int b1 = conLai(mayNat.get(canBoB, DUONG_API));

        assertThat(a1)
                .as("⛔ Thiếu `X-RateLimit-Remaining` thì bài này ⛔ đo được gì")
                .isGreaterThanOrEqualTo(0);
        assertThat(a2)
                .as("ĐỐI CHỨNG: cùng người, cùng IP phải đếm chung — nếu ⛔ giảm thì xô ⛔ đếm gì cả")
                .isLessThan(a1);
        assertThat(b1)
                .as(
                        """
                        ⛔⛔ Người B vừa gọi LẦN ĐẦU mà xô đã bị người A tiêu ⇒ khoá vẫn là IP. Sau \
                        một NAT, 50 cán bộ chung 100 lượt/phút và cả cơ quan nhận SYS-0002.""")
                .isGreaterThan(a2);
    }

    @Test
    @DisplayName("⛔⛔ Token GIẢ mang `sub` khác nhau ⛔ tách được xô — khoá chỉ lấy từ claims ĐÃ KIỂM")
    void tokenGiaKhongTachXo() {
        HttpHeaders h1 = new HttpHeaders();
        h1.set("X-Real-IP", "203.0.113.61");
        h1.setBearerAuth(jwtGia(UUID.randomUUID()));
        HttpHeaders h2 = new HttpHeaders();
        h2.set("X-Real-IP", "203.0.113.61");
        h2.setBearerAuth(jwtGia(UUID.randomUUID())); // ⛔ ĐỔI ĐÚNG MỘT THỨ: `sub`

        int truoc = conLai(http.exchange(DUONG_API, HttpMethod.GET, new HttpEntity<>(h1), String.class));
        int sau = conLai(http.exchange(DUONG_API, HttpMethod.GET, new HttpEntity<>(h2), String.class));

        assertThat(truoc).isGreaterThanOrEqualTo(0);
        assertThat(sau)
                .as(
                        """
                        ⛔⛔ Hai token chữ ký SAI, chỉ khác `sub`, lại rơi vào hai xô ⇒ khoá đọc từ token \
                        CHƯA KIỂM. Kẻ gọi xoay `sub` là xoay xô — hạn mức né được là hạn mức ⛔ tồn tại.""")
                .isLessThan(truoc);
    }

    /** JWT đúng HÌNH DẠNG, chữ ký rác — thứ một bản vá đọc {@code sub} trước bước kiểm sẽ tin. */
    private static String jwtGia(UUID sub) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String dau = b64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String than = b64.encodeToString(
                "{\"sub\":\"%s\",\"exp\":4102444800}".formatted(sub).getBytes(StandardCharsets.UTF_8));
        return dau + "." + than + "." + b64.encodeToString("chu-ky-rac".getBytes(StandardCharsets.UTF_8));
    }

    private static int conLai(ResponseEntity<String> ra) {
        String v = ra.getHeaders().getFirst("X-RateLimit-Remaining");
        return v == null ? -1 : Integer.parseInt(v);
    }
}
