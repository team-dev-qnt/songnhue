package com.songnhue.core.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Gửi email — kênh thứ hai sau thông báo trong ứng dụng (chốt B7: v1 không có SMS).
 *
 * <p>Bản v1 gửi <b>thư thuần văn bản</b>, không HTML. Đây là lựa chọn có chủ đích: thư HTML kéo theo
 * bộ template, ảnh nhúng, và khác biệt hiển thị giữa các trình đọc thư — một khối công việc riêng
 * cho thứ mà nội dung chỉ là "có việc mới, bấm vào đây". Đường dẫn nằm ngay trong thân thư.
 *
 * <p>⚠ Bean này chỉ tồn tại khi có {@code SMTP_HOST} — xem {@code MailConfig}. Thiếu cấu hình thư
 * thì job gửi đánh dấu {@code SKIPPED} thay vì thất bại liên tục, và thông báo trên giao diện vẫn
 * tới nơi bình thường.
 *
 * <p>Cố ý <b>không</b> dùng {@code @Component} + {@code @ConditionalOnBean}: Spring chỉ bảo đảm
 * {@code @ConditionalOnBean} cho lớp auto-configuration, còn với bean quét theo {@code @Component}
 * thì kết quả phụ thuộc thứ tự nạp. Chạy thử WS-6 gặp đúng chuyện đó — SMTP cấu hình đầy đủ mà bean
 * vẫn không được tạo, và mọi email lặng lẽ chuyển thành {@code SKIPPED}.
 */
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;

    /**
     * Địa chỉ người gửi, đọc từ {@code SMTP_FROM}.
     *
     * <p>Để ở biến môi trường chứ không ở bảng {@code settings}: nó phải khớp với miền mà máy chủ
     * thư cho phép gửi thay mặt, tức là thuộc về cấu hình hạ tầng của từng môi trường. Đặt vào bảng
     * cấu hình thì Admin sửa được thành một địa chỉ mà SMTP từ chối, và triệu chứng là thư lặng lẽ
     * không tới nơi.
     */
    private final String fromAddress;

    /**
     * <b>T61.23 — chuyển hướng thư</b> ({@code MAIL_REDIRECT_TO}, chốt 15/09/2026). Staging dùng CHUNG
     * SMTP thật với production (T50.13) và mang dữ liệu nhân bản từ production ⇒ ⛔ chuyển hướng thì một
     * lượt thử trên staging gửi thư tới hộp thư THẬT của cán bộ và người dân đã gửi liên hệ.
     *
     * <p>Đặt ở đây — chỗ MỌI thư đi qua ({@code MailDispatcher} và {@code NotificationDispatchHandler}
     * đều gọi {@link #send}) — ⛔ ở từng nơi gọi (luật 12). {@code null} = gửi thẳng.
     */
    private final String redirectTo;

    public EmailSender(JavaMailSender mailSender, String fromAddress) {
        this(mailSender, fromAddress, null);
    }

    public EmailSender(JavaMailSender mailSender, String fromAddress, String redirectTo) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.redirectTo = redirectTo == null || redirectTo.isBlank() ? null : redirectTo.trim();
    }

    /**
     * @param linkUrl đường dẫn tới màn hình liên quan, có thể rỗng
     * @throws org.springframework.mail.MailException khi máy chủ thư từ chối — job sẽ thử lại
     */
    public void send(String toAddress, String subject, String body, String linkUrl) {
        String than = linkUrl == null || linkUrl.isBlank() ? body : body + "\n\nXem chi tiết: " + linkUrl;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        if (redirectTo == null) {
            message.setTo(toAddress);
            message.setSubject(subject);
            message.setText(than);
        } else {
            // Người nhận gốc nằm ở TIÊU ĐỀ (nhìn thấy ngay trong hộp thư) và đầu thân thư.
            message.setTo(redirectTo);
            message.setSubject("[CHUYỂN HƯỚNG → " + toAddress + "] " + subject);
            message.setText("Thư này lẽ ra gửi tới: " + toAddress + " (MAIL_REDIRECT_TO đang bật)\n\n" + than);
        }

        mailSender.send(message);
        log.debug("Đã gửi email tới {}", redirectTo == null ? toAddress : redirectTo + " (chuyển hướng)");
    }
}
