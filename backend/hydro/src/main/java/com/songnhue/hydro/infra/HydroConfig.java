package com.songnhue.hydro.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình của module {@code hydro} (MOD-03).
 *
 * <p>⚠ Chỉ khai {@link EnableConfigurationProperties}. Module này <b>không</b> khai bean
 * {@code RestClient}/{@code HttpClient} trần: §9.7 đã trả giá cho ba cái bẫy auto-configuration của
 * Spring Boot, trong đó khai một bean cùng kiểu với thứ Boot tự cấu hình làm nó <b>ngừng tạo bean
 * chính</b>. Client HTTP của adapter được gói vào một kiểu riêng của dự án.
 */
@Configuration
@EnableConfigurationProperties(HydroApiProperties.class)
public class HydroConfig {}
