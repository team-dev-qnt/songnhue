package com.songnhue.app.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Đăng ký {@link TestHttp} làm bean để lớp kiểm thử tiêm bằng {@code @Autowired} — giữ nguyên cách
 * dùng cũ của {@code TestRestTemplate}, thứ Boot 4 tự cấp mà nay đã bị xoá (T11.69).
 *
 * <p>⚠ Nạp qua {@code @Import} trên {@link IntegrationTestBase} chứ ⛔ không để Spring tự quét: lớp
 * quét cả {@code com.songnhue} nên một {@code @TestConfiguration} lang thang sẽ vào MỌI context,
 * kể cả context của bài kiểm ⛔ không dựng máy chủ web — nơi {@code local.server.port} ⛔ không tồn tại.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestHttpConfig {

    @Bean
    TestHttp testHttp(Environment environment) {
        return new TestHttp(environment);
    }
}
