package com.songnhue.core.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.songnhue.core.common.security.PermissionInterceptor;

/**
 * Gắn {@link PermissionInterceptor} vào toàn bộ {@code /api/v1/**} — tầng 2 của phân quyền.
 *
 * <p>Đăng ký theo mẫu "phủ tất cả rồi trừ ra", không phải "liệt kê chỗ cần bảo vệ": endpoint mới
 * thêm về sau tự động nằm trong vùng canh gác. Cách ngược lại thì mỗi endpoint mới là một cơ hội
 * quên, và quên ở đây không có triệu chứng gì.
 *
 * <p>Loại trừ đúng ba nhóm, đều <b>không</b> phải endpoint nghiệp vụ:
 *
 * <ul>
 *   <li>{@code /actuator/**} — chỉ mạng nội bộ gọi được, nginx chặn từ ngoài (§4.5)
 *   <li>{@code /v3/api-docs}, {@code /swagger-ui} — tài liệu API, production nginx cũng chặn
 * </ul>
 */
@Configuration
public class WebSecurityMvcConfig implements WebMvcConfigurer {

    private static final String API_PATTERN = "/api/v1/**";

    private final PermissionInterceptor permissionInterceptor;

    public WebSecurityMvcConfig(PermissionInterceptor permissionInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns(API_PATTERN)
                .excludePathPatterns("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**");
    }
}
