package com.songnhue.core.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật các thành phần của Common Platform.
 *
 * <p>{@code @EnableJpaAuditing} làm cho {@code created_at/by} và {@code updated_at/by} của
 * {@code BaseEntity} tự điền — không lập trình viên nào phải nhớ set tay, và cũng không ai set sai.
 *
 * <p>{@code @EnableScheduling} phục vụ job dọn token hết hạn (WS-5). WS-6 chuyển các tác vụ định kỳ
 * sang hàng đợi job trong DB + ShedLock; annotation này vẫn giữ vì vài việc dọn dẹp hạ tầng không
 * cần tới bộ máy đó.
 */
@Configuration
@EnableScheduling
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableConfigurationProperties({
    AppProperties.class,
    CryptoProperties.class,
    StorageProperties.class,
    JwtProperties.class
})
public class CorePlatformConfig {}
