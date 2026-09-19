package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
 * <b>Phản hồi JSON của API mang {@code Content-Disposition: attachment; filename="api.json"} — T73.3 (ASVS 14.4.2).</b>
 *
 * <p>Mở thẳng một URL API trên thanh địa chỉ thì trình duyệt TẢI tệp chứ ⛔ hiển thị nó như một trang — lớp thứ hai
 * sau {@code nosniff}. {@code fetch}/XHR bỏ qua header này, nên giao diện ⛔ đổi gì.
 *
 * <h2>Hai vế</h2>
 *
 * <ol>
 *   <li>JSON thành công · JSON LỖI · JSON cổng công khai đều mang header. Lượt phá 19/09/2026 (dời header vào
 *       {@code KhongLuuDemFilter}) đỏ đúng ở vế LỖI 401 — đường giải lỗi dựng lại phản hồi và header đặt ở filter
 *       mất.
 *   <li>Endpoint tải tệp giữ ĐÚNG MỘT {@code Content-Disposition}, và là tên tệp của CHÍNH nó — ⛔ bị ghi đè thành
 *       {@code api.json}. ⚠ Bản nháp đầu khẳng định một giá trị đặt sẵn sẽ bị NHÂN ĐÔI; lượt phá bác điều ấy: Spring
 *       7.0.9 ghi header của {@code ResponseEntity} bằng cách THAY. Vế này vì vậy canh vế "ghi đè", ⛔ canh vế
 *       "nhân đôi".
 * </ol>
 */
class TenTepApiHttpTest extends IntegrationTestBase {

    private static final String TEN_TEP_API = "attachment; filename=\"api.json\"";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("⛔ JSON thành công · JSON lỗi 401 · JSON cổng công khai đều mang filename=\"api.json\"")
    void jsonMangTenTepApi() {
        PhienHttp may = new PhienHttp(http);
        may.doiIp();
        PhienHttp.Phien phien = may.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t733_json"));

        ResponseEntity<String> co = may.get(phien, "/api/v1/auth/me");
        assertThat(co.getStatusCode().value()).isEqualTo(200);
        assertThat(co.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION)).containsExactly(TEN_TEP_API);

        HttpHeaders khach = new HttpHeaders();
        khach.set("X-Real-IP", "198.51.100.173");
        ResponseEntity<String> loi =
                http.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(khach), String.class);
        assertThat(loi.getStatusCode().value()).isEqualTo(401);
        assertThat(loi.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION))
                .as("phản hồi LỖI cũng là JSON của API — mở thẳng trên thanh địa chỉ cũng phải tải về")
                .containsExactly(TEN_TEP_API);

        ResponseEntity<String> congKhai =
                http.exchange("/api/v1/public/site-config", HttpMethod.GET, new HttpEntity<>(khach), String.class);
        assertThat(congKhai.getStatusCode().value()).isEqualTo(200);
        assertThat(congKhai.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION)).containsExactly(TEN_TEP_API);
    }

    @Test
    @DisplayName("⛔⛔ Endpoint TẢI TỆP giữ ĐÚNG MỘT Content-Disposition của chính nó — ⛔ hai, ⛔ api.json")
    void taiTepGiuTenCuaNo() {
        PhienHttp may = new PhienHttp(http);
        may.doiIp();
        PhienHttp.Phien phien = may.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t733_tep", "TECHNICIAN"));

        ResponseEntity<String> tep = may.get(phien, "/api/v1/hyd/stations/import/template");
        assertThat(tep.getStatusCode().value()).as("thân: %s", tep.getBody()).isEqualTo(200);
        List<String> header = tep.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).as("đúng một Content-Disposition").hasSize(1);
        assertThat(header.get(0)).contains("mau-nhap-vi-tri-diem-do.csv").doesNotContain("api.json");
    }
}
