package com.songnhue.core.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Bật các thành phần của Common Platform.
 *
 * <p>{@code @EnableJpaAuditing} làm cho {@code created_at/by} và {@code updated_at/by} của
 * {@code BaseEntity} tự điền — không lập trình viên nào phải nhớ set tay, và cũng không ai set sai.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableConfigurationProperties({AppProperties.class, CryptoProperties.class, StorageProperties.class})
public class CorePlatformConfig {}
