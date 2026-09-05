package com.songnhue.core.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Tạo {@link EmailSender} khi và chỉ khi đã cấu hình máy chủ thư.
 *
 * <p><b>Vì sao điều kiện đặt trên tham số cấu hình chứ không trên bean.</b> Bản đầu dùng
 * {@code @Component} + {@code @ConditionalOnBean(JavaMailSender.class)}. Cách đó <b>không đáng
 * tin</b>: Spring chỉ bảo đảm {@code @ConditionalOnBean} cho lớp auto-configuration (chạy sau khi
 * quét xong bean thường), còn với một bean quét theo {@code @Component} thì kết quả phụ thuộc thứ
 * tự nạp — và ở đây nó rơi vào trường hợp xấu.
 *
 * <p>Triệu chứng lúc chạy thử: {@code SMTP_HOST} cấu hình đầy đủ, Mailpit đang chạy, nhưng
 * {@code EmailSender} không được tạo và mọi lượt gửi bị đánh dấu {@code SKIPPED} kèm ghi chú "chưa
 * cấu hình máy chủ thư" — thông báo sai hoàn toàn so với nguyên nhân thật.
 *
 * <p>{@code matchIfMissing = false} có chủ đích: không cấu hình SMTP thì <b>không</b> tạo bean. Ở
 * môi trường thiếu thư, gửi thất bại lặp lại tốn nhiều công điều tra hơn là bỏ qua có ghi chú.
 */
@Configuration
@ConditionalOnProperty(name = "spring.mail.host", matchIfMissing = false)
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public EmailSender emailSender(JavaMailSender mailSender, @Value("${app.notification.from}") String fromAddress) {
        log.info("Kênh email BẬT — thư gửi từ địa chỉ {}", fromAddress);
        return new EmailSender(mailSender, fromAddress);
    }
}
