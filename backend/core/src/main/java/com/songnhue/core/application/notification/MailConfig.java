package com.songnhue.core.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
 * <p>Không cấu hình SMTP thì <b>không</b> tạo bean. Ở môi trường thiếu thư, gửi thất bại lặp lại tốn
 * nhiều công điều tra hơn là bỏ qua có ghi chú.
 *
 * <h2>⛔⛔ T50.9 — câu trên ĐÚNG Ý ĐỊNH mà SAI THỰC TẾ suốt từ WS-6</h2>
 *
 * <p>Bản trước dùng {@code @ConditionalOnProperty(name = "spring.mail.host", matchIfMissing = false)}.
 * Nhưng {@code application.yml} khai {@code host: ${SMTP_HOST:}} — <b>mặc định là chuỗi RỖNG</b> —
 * nên thuộc tính <b>luôn có mặt</b>, điều kiện <b>luôn khớp</b>, và bean <b>luôn</b> được tạo. Spring
 * lấy máy chủ mặc định {@code localhost:587}.
 *
 * <p>Đo staging 10/09/2026 — nhật ký in đúng câu <i>"Kênh email BẬT"</i>, rồi:
 *
 * <pre>
 *   notification_recipients: EMAIL → 154 FAILED · 88 SKIPPED · 0 SENT
 *   MailConnectException: Couldn't connect to host, port: localhost, 587
 * </pre>
 *
 * <p>⇒ Kênh thông báo <b>thứ hai</b> — kênh duy nhất tới được người ⛔ không ngồi nhìn chuông trong
 * ứng dụng — <b>chưa từng gửi nổi một thư nào</b>, mà ⛔ không có gì báo điều đó. Đúng lúc ấy một
 * nguồn thuỷ văn hỏng 3323 lượt liên tiếp trong 9 ngày (T50.1).
 *
 * <p><b>Luật 3, lần thứ ba</b> sau §10.38 và §10.78: <i>"rỗng" khác "chưa đặt"</i>. Và một chú thích
 * khẳng định một bảo đảm ⛔ không có thật là hình dạng lỗi lặp nhiều nhất của dự án — nên bảo đảm nay
 * nằm ở <b>điều kiện</b>, và có {@code MailConfigTest} chứng minh cả hai vế.
 *
 * <p>⚠ Vế còn lại <b>⛔ không</b> sửa được bằng mã: {@code MailSenderAutoConfiguration} của Spring
 * Boot dính đúng bẫy ấy nên vẫn dựng {@code JavaMailSender} và {@code MailHealthIndicator} vẫn đỏ.
 * Muốn sạch hẳn thì <b>đặt {@code SMTP_HOST} thật</b>, hoặc bỏ hẳn biến khỏi môi trường.
 */
@Configuration
@ConditionalOnExpression("!'${spring.mail.host:}'.trim().isEmpty()")
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    public EmailSender emailSender(JavaMailSender mailSender, @Value("${app.notification.from}") String fromAddress) {
        log.info("Kênh email BẬT — thư gửi từ địa chỉ {}", fromAddress);
        return new EmailSender(mailSender, fromAddress);
    }
}
