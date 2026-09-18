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

    /**
     * ⛔⛔ T61.23 — {@code MAIL_REDIRECT_TO} ở PRODUCTION là DỪNG khởi động (luật 11): mọi thông báo của
     * 200 cán bộ lặng lẽ dồn về một hộp thư, ⛔ một dòng lỗi nào — đúng hình dạng T50.12 (kênh email
     * "BẬT" mà ⛔ ai nhận được). Nhận diện production bằng chính nhãn chỉ số {@code APP_ENVIRONMENT}.
     */
    @Bean
    public EmailSender emailSender(
            JavaMailSender mailSender,
            @Value("${app.notification.from}") String fromAddress,
            @Value("${app.notification.redirect-to:}") String redirectTo,
            @Value("${management.metrics.tags.environment:local}") String environment) {
        boolean chuyenHuong = redirectTo != null && !redirectTo.isBlank();
        String moiTruong = environment.trim();
        if (chuyenHuong && "production".equalsIgnoreCase(moiTruong)) {
            throw new IllegalStateException(
                    "MAIL_REDIRECT_TO được đặt trên PRODUCTION — mọi thư sẽ dồn về một hộp thư. Gỡ biến này khỏi .env.");
        }
        if (!chuyenHuong && "staging".equalsIgnoreCase(moiTruong)) {
            // Chiều ngược lại cũng DỪNG: một dòng dặn trong staging.env.example ⛔ phải cổng kiểm.
            throw new IllegalStateException(
                    "Staging bật SMTP mà THIẾU MAIL_REDIRECT_TO — dữ liệu nhân bản từ production, thư thử sẽ tới hộp thư"
                            + " thật của cán bộ (T61.23). Đặt MAIL_REDIRECT_TO trong .env.");
        }
        if (chuyenHuong) {
            log.warn("Kênh email BẬT — ⚠ CHUYỂN HƯỚNG mọi thư về {} (môi trường {})", redirectTo.trim(), environment);
        } else {
            log.info("Kênh email BẬT — thư gửi từ địa chỉ {}", fromAddress);
        }
        return new EmailSender(mailSender, fromAddress, redirectTo);
    }
}
