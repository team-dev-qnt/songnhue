package com.songnhue.core.application.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Kênh email chỉ BẬT khi có máy chủ thư <b>thật</b> — <b>T50.9</b>.
 *
 * <h2>⛔⛔ Vì sao bài này ra đời: một bảo đảm chỉ tồn tại trong chú thích</h2>
 *
 * <p>{@code MailConfig} khẳng định từ WS-6 rằng <i>"không cấu hình SMTP thì <b>không</b> tạo bean"</i>.
 * Đo staging 10/09/2026 thì câu ấy <b>sai</b>: {@code application.yml} khai
 * {@code host: ${SMTP_HOST:}} — mặc định <b>chuỗi rỗng</b> — nên thuộc tính <b>luôn có mặt</b>,
 * {@code @ConditionalOnProperty} <b>luôn khớp</b>, bean <b>luôn</b> được tạo, và Spring đâm vào
 * {@code localhost:587}:
 *
 * <pre>
 *   log:  "Kênh email BẬT — thư gửi từ địa chỉ no-reply@songnhue.com"
 *   db :  notification_recipients EMAIL → 154 FAILED · 88 SKIPPED · 0 SENT
 *   err:  MailConnectException: Couldn't connect to host, port: localhost, 587
 * </pre>
 *
 * <p>⇒ Kênh thông báo <b>thứ hai</b> — kênh duy nhất tới được người ⛔ không ngồi nhìn chuông trong
 * ứng dụng — <b>chưa từng gửi nổi một thư nào</b>, và ⛔ không có gì báo điều đó. Đúng lúc ấy một
 * nguồn thuỷ văn hỏng 3323 lượt liên tiếp suốt 9 ngày (T50.1).
 *
 * <p><b>Luật 3, lần thứ ba</b> sau §10.38 và §10.78: <i>"rỗng" khác "chưa đặt"</i>. Bài này tồn tại
 * vì <b>một chú thích ⛔ không phải một cổng kiểm</b> — hình dạng lỗi lặp nhiều nhất của dự án.
 *
 * <p>⚠ Phạm vi tự khai (luật 28): bài này canh <b>bean {@link EmailSender}</b>.
 * {@code MailSenderAutoConfiguration} của Spring Boot dính đúng bẫy rỗng-vs-vắng-mặt ấy và
 * <b>⛔ không</b> sửa được từ đây, nên {@code MailHealthIndicator} vẫn đỏ khi {@code SMTP_HOST}
 * rỗng. Cái xanh ở đây ⛔ <b>không</b> có nghĩa "email đã chạy" — nó chỉ có nghĩa
 * <i>"⛔ không còn giả vờ chạy"</i>.
 */
class MailConfigTest {

    private final ApplicationContextRunner boChay = new ApplicationContextRunner()
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withPropertyValues("app.notification.from=no-reply@songnhue.com")
            .withUserConfiguration(MailConfig.class);

    @Test
    @DisplayName("⛔⛔ SMTP_HOST RỖNG ⇒ ⛔ KHÔNG tạo EmailSender — đúng ca đã cho 154 FAILED trên staging")
    void anEmptyHostMustNotEnableTheEmailChannel() {
        boChay.withPropertyValues("spring.mail.host=").run(ctx -> assertThat(ctx)
                .as(
                        """
                        ⛔ `${SMTP_HOST:}` mặc định là chuỗi RỖNG nên thuộc tính LUÔN có mặt. Một điều \
                        kiện hỏi "thuộc tính có tồn tại không" sẽ luôn khớp, bean vẫn dựng, và mọi thư \
                        đâm vào localhost:587 — 154 lượt FAILED, 0 lượt SENT, ⛔ không ai được báo.""")
                .doesNotHaveBean(EmailSender.class));
    }

    @Test
    @DisplayName("⛔ SMTP_HOST toàn khoảng trắng cũng ⛔ KHÔNG bật — một chuỗi trắng ⛔ không phải một máy chủ")
    void aBlankHostMustNotEnableTheEmailChannel() {
        boChay.withPropertyValues("spring.mail.host=   ")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(EmailSender.class));
    }

    @Test
    @DisplayName("⛔ Thiếu hẳn SMTP_HOST cũng ⛔ KHÔNG bật")
    void aMissingHostMustNotEnableTheEmailChannel() {
        boChay.run(ctx -> assertThat(ctx).doesNotHaveBean(EmailSender.class));
    }

    @Test
    @DisplayName("⚠ ĐỐI CHỨNG: có máy chủ thư thật thì kênh PHẢI bật")
    void aRealHostMustEnableTheEmailChannel() {
        // Luật 7 + luật 29. Thiếu vế này thì một điều kiện viết sai kiểu "luôn luôn sai" cũng làm ba
        // bài trên xanh — và nó sẽ tắt email ở CẢ môi trường đã cấu hình đúng, tức đổi một khuyết
        // tật ồn ào lấy một khuyết tật im lặng.
        boChay.withPropertyValues("spring.mail.host=smtp.songnhue.com")
                .run(ctx -> assertThat(ctx).hasSingleBean(EmailSender.class));
    }

    // ------------------------------------------------------------------------- T61.23

    @Test
    @DisplayName("⛔⛔ T61.23 — MAIL_REDIRECT_TO trên PRODUCTION ⇒ DỪNG khởi động (thư 200 cán bộ dồn về một hộp)")
    void redirectOnProductionMustFailStartup() {
        boChay.withPropertyValues(
                        "spring.mail.host=smtp.songnhue.com",
                        "management.metrics.tags.environment=production",
                        "app.notification.redirect-to=dev@songnhue.com")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause().hasMessageContaining("PRODUCTION"));
    }

    @Test
    @DisplayName("⛔⛔ T61.23 — staging bật SMTP mà THIẾU MAIL_REDIRECT_TO ⇒ DỪNG (dữ liệu nhân bản, hộp thư thật)")
    void stagingWithoutRedirectMustFailStartup() {
        boChay.withPropertyValues(
                        "spring.mail.host=smtp.songnhue.com",
                        "management.metrics.tags.environment=staging",
                        "app.notification.redirect-to=  ")
                .run(ctx ->
                        assertThat(ctx).hasFailed().getFailure().rootCause().hasMessageContaining("MAIL_REDIRECT_TO"));
    }

    @Test
    @DisplayName("⚠ ĐỐI CHỨNG T61.23 — production ⛔ chuyển hướng và staging CÓ chuyển hướng đều khởi động được")
    void validCombinationsStart() {
        boChay.withPropertyValues(
                        "spring.mail.host=smtp.songnhue.com", "management.metrics.tags.environment=production")
                .run(ctx -> assertThat(ctx).hasSingleBean(EmailSender.class));
        boChay.withPropertyValues(
                        "spring.mail.host=smtp.songnhue.com",
                        "management.metrics.tags.environment=staging",
                        "app.notification.redirect-to=dev@songnhue.com")
                .run(ctx -> assertThat(ctx).hasSingleBean(EmailSender.class));
    }

    @Test
    @DisplayName("⭐⭐ T61.23 — có chuyển hướng: thư tới ĐÚNG địa chỉ chuyển hướng, người nhận gốc nằm ở tiêu đề")
    void redirectRewritesRecipientAndKeepsOriginalVisible() {
        JavaMailSender may = mock(JavaMailSender.class);
        List<SimpleMailMessage> daGui = new ArrayList<>();
        doAnswer(inv -> daGui.add(inv.getArgument(0))).when(may).send(any(SimpleMailMessage.class));

        new EmailSender(may, "no-reply@songnhue.com", " dev@songnhue.com ")
                .send("canbo@songnhue.com", "Có đơn nghỉ phép", "Nội dung", "https://admin/x");
        new EmailSender(may, "no-reply@songnhue.com", "")
                .send("canbo@songnhue.com", "Có đơn nghỉ phép", "Nội dung", null);

        assertThat(daGui).hasSize(2);
        assertThat(daGui.get(0).getTo()).containsExactly("dev@songnhue.com");
        assertThat(daGui.get(0).getSubject()).contains("canbo@songnhue.com").endsWith("Có đơn nghỉ phép");
        assertThat(daGui.get(0).getText()).contains("canbo@songnhue.com").contains("Xem chi tiết: https://admin/x");
        assertThat(daGui.get(1).getTo())
                .as("đối chứng: chuỗi rỗng ⛔ phải chuyển hướng về địa chỉ rỗng")
                .containsExactly("canbo@songnhue.com");
        assertThat(daGui.get(1).getSubject()).isEqualTo("Có đơn nghỉ phép");
    }
}
