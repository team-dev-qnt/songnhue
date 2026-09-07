package com.songnhue.core.common.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Tài liệu OpenAPI cho {@code /api/v1/**}, chia nhóm theo module (conventions.md §1.3).
 *
 * <p>Chia nhóm để người đọc không phải cuộn qua toàn bộ hệ thống mới thấy phần mình cần — và cũng
 * để thấy ngay ranh giới module.
 *
 * <p>⚠ Giao diện Swagger chỉ nên mở ở môi trường local/staging. Trên production, nginx chặn
 * {@code /swagger-ui/**} và {@code /v3/api-docs/**} (WS-11/T11.6) — sơ đồ API đầy đủ là thứ giúp
 * người dò tìm biết chính xác cần gọi gì.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI songnhueOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hệ thống Quản trị & Điều hành Thủy lợi Sông Nhuệ")
                        .version("v1")
                        .description(
                                """
                                API nội bộ. Mọi response đều bọc trong envelope thống nhất:
                                `{ success, data, meta?, error?, traceId }`.

                                Lỗi trả về mã trong danh mục (`conventions.md` §2.3) — client nên
                                xử lý theo `error.code`, không dựa vào câu chữ của `error.message`.
                                """)
                        .license(new License().name("Nội bộ — Công ty TNHH MTV ĐTPT Thủy lợi Sông Nhuệ")));
    }

    @Bean
    public GroupedOpenApi coreApi() {
        return group("00-core", "/api/v1/core/**", "/api/v1/auth/**");
    }

    @Bean
    public GroupedOpenApi cmsApi() {
        return group("01-cms", "/api/v1/cms/**");
    }

    @Bean
    public GroupedOpenApi opsApi() {
        return group("02-ops", "/api/v1/ops/**");
    }

    @Bean
    public GroupedOpenApi hydroApi() {
        return group("03-hyd", "/api/v1/hyd/**");
    }

    @Bean
    public GroupedOpenApi hrApi() {
        return group("04-hr", "/api/v1/hr/**");
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return group("05-adm", "/api/v1/adm/**");
    }

    /**
     * Nhóm công khai — tách riêng có chủ đích (WS-16).
     *
     * <p>Đây là danh sách <b>mọi cánh cửa mở</b> của hệ thống. Để chúng lẫn trong nhóm của module thì
     * người rà soát an ninh phải đọc từng lớp đi tìm {@code @PublicEndpoint}; tách ra thì một trang
     * tài liệu là đủ.
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return group("06-public", "/api/v1/public/**");
    }

    private static GroupedOpenApi group(String name, String... paths) {
        return GroupedOpenApi.builder().group(name).pathsToMatch(paths).build();
    }
}
