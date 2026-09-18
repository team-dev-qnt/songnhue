package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.TestHttp;

/**
 * <b>Tài liệu API phải TẮT ĐƯỢC ở tầng ứng dụng — T61.40</b> (ASVS 14.2.2).
 *
 * <h2>Trạng thái trước bản vá</h2>
 *
 * <p>Thứ DUY NHẤT giữ bản đồ API khỏi Internet là một dòng {@code include} trong cấu hình nginx
 * ({@code chan-tai-lieu-api.conf}). Ứng dụng ⛔ có công tắc nào: một {@code server{}} mới quên dòng
 * ấy, hay một front-end khác đứng trước, là phát nguyên danh mục endpoint — và ứng dụng phục vụ
 * ngay. Javadoc của {@code OpenApiConfig} từ WS-4 khai *"trên production nginx chặn"*, tức nó mô tả
 * đúng một lớp phòng thủ <b>ở ngoài</b> mình.
 *
 * <h2>⚠⚠ Vì sao bài này phải ĐẶT LẠI thuộc tính, ⛔ dựa vào hồ sơ mặc định</h2>
 *
 * <p>{@code application.yml:11} khai {@code spring.profiles.default: local}, và hồ sơ {@code local}
 * <b>cố ý BẬT</b> tài liệu API (lập trình viên cần nó). Nên mọi bài tích hợp chạy với Swagger BẬT —
 * một bài khẳng định *"404"* ở đó sẽ đỏ, còn khẳng định *"200"* thì ⛔ nói gì về production.
 * ⇒ Lớp này chạy với đúng giá trị mà {@code compose.prod.yml} sinh ra ({@code SWAGGER_ENABLED} ⛔ đặt
 * ⇒ {@code false}).
 */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class TaiLieuApiTatHttpTest extends IntegrationTestBase {

    @Autowired
    private TestHttp http;

    @Test
    @DisplayName("⛔⛔ Với cấu hình production, ứng dụng ⛔ phục vụ bản đồ API — ⛔ chỉ nhờ nginx chặn hộ")
    void ungDungKhongPhucVuTaiLieuApi() {
        for (String duong : java.util.List.of("/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html")) {
            ResponseEntity<String> tl = http.getForEntity(duong, String.class);
            assertThat(tl.getStatusCode().value())
                    .as("%s vẫn phục vụ: %s", duong, tl.getBody())
                    .isIn(401, 403, 404);
        }
    }

    @Test
    @DisplayName("⭐ Đối chứng: công tắc nằm ở application.yml với mặc định TẮT, và ⛔ tệp env nào bật nó")
    void congTacMacDinhTat() throws Exception {
        String cauHinh = Files.readString(timTuGocKho("backend/app/src/main/resources/application.yml"));
        assertThat(cauHinh).as("⛔ ghi cứng true, và ⛔ bỏ hẳn công tắc").contains("enabled: ${SWAGGER_ENABLED:false}");

        for (String ten : java.util.List.of("prod.env.example", "staging.env.example")) {
            String env = Files.readString(timTuGocKho("deploy/env/" + ten));
            assertThat(env).as("%s ⛔ được bật tài liệu API", ten).doesNotContain("SWAGGER_ENABLED=true");
        }
    }
    /** Cùng khuôn với {@code ComposeEnvCompletenessTest}: thư mục chạy của surefire ⛔ phải gốc kho. */
    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        throw new AssertionError("⛔ tìm thấy " + duongDanTuongDoi + " tính từ " + System.getProperty("user.dir"));
    }
}
