package com.songnhue.content.infra;

import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình của MOD-01.
 *
 * <p>Module này ⛔ <b>không</b> khai bean hạ tầng nào ở đây — cùng khuôn với {@code HydroConfig}. ⚠ Từng khai
 * {@code RecaptchaProperties}; T61.44 gỡ lớp ấy vì khoá bí mật nay đọc qua {@code BiMatTichHopPort} (giao diện
 * trước, {@code RECAPTCHA_SECRET_KEY} làm giá trị mồi).
 */
@Configuration
public class ContentConfig {}
