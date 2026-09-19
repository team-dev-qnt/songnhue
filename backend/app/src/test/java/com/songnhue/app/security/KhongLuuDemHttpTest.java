package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * <b>API riêng tư mang {@code Cache-Control: no-store}; cổng công khai thì ⛔ — T61.35</b> (ASVS 8.2.1, tìm ra ở
 * T61.28). Trước bản vá: 0 nơi đặt header ⇒ hồ sơ CBNV có thể nằm lại trong bộ đệm máy dùng chung.
 */
class KhongLuuDemHttpTest extends IntegrationTestBase {

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("⛔⛔ /api/v1/auth/me đã đăng nhập ⇒ 200 + no-store; chưa đăng nhập ⇒ 401 CŨNG no-store")
    void apiRiengTuKhongLuuDem() {
        PhienHttp may = new PhienHttp(http);
        PhienHttp.Phien phien = may.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6135_dem"));

        ResponseEntity<String> co = may.get(phien, "/api/v1/auth/me");
        assertThat(co.getStatusCode().value()).isEqualTo(200);
        assertThat(co.getHeaders().getCacheControl()).contains("no-store");
        // T61.40 (ASVS 14.4.4 — nhãn cũ ghi nhầm 14.4.1) — `nosniff` cho phản hồi API: hai image FE đặt header này cho
        // nội dung
        //   của CHÚNG, ⛔ image nào phục vụ `/api/**`, và nginx biên cố ý ⛔ đặt hộ (một header một chủ).
        assertThat(co.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");

        HttpHeaders trong = new HttpHeaders();
        trong.set("X-Real-IP", "198.51.100.135");
        ResponseEntity<String> khong =
                http.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(trong), String.class);
        assertThat(khong.getStatusCode().value()).isEqualTo(401);
        assertThat(khong.getHeaders().getCacheControl())
                .as("phản hồi lỗi cũng ⛔ được đệm — filter đứng ngoài xác thực")
                .contains("no-store");
    }

    @Test
    @DisplayName("⚠ ĐỐI CHỨNG: /api/v1/public/** ⛔ bị ép no-store — ISR và ảnh nhúng của cổng cần đệm")
    void congCongKhaiKhongBiEp() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Real-IP", "198.51.100.136");
        ResponseEntity<String> ra =
                http.exchange("/api/v1/public/site-config", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(ra.getStatusCode().value()).isEqualTo(200);
        assertThat(String.valueOf(ra.getHeaders().getCacheControl())).doesNotContain("no-store");
    }
}
