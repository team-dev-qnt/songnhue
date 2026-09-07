package com.songnhue.content.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình của MOD-01.
 *
 * <p>⚠ Chỉ khai {@link EnableConfigurationProperties}. Module này ⛔ <b>không</b> khai bean hạ tầng
 * nào ở đây — cùng khuôn với {@code HydroConfig}.
 */
@Configuration
@EnableConfigurationProperties(RecaptchaProperties.class)
public class ContentConfig {}
