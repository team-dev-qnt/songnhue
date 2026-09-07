package com.songnhue.core.application.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.MailPort;

/**
 * Cài đặt {@link MailPort} — lớp mỏng bọc {@link EmailSender}.
 *
 * <h2>Vì sao cần một lớp bọc thay vì cho module nghiệp vụ dùng thẳng {@code EmailSender}</h2>
 *
 * <p>Hai lý do, cả hai đều đo được:
 *
 * <ul>
 *   <li>{@code EmailSender} nằm ở {@code core.application}, mà module nghiệp vụ chỉ được import
 *       {@code core.common.*} và {@code <module>.spi} — luật {@code ModuleBoundaryTest}. Đây ⛔
 *       không phải hình thức: nó giữ cho một lượt đổi cách gửi thư ⛔ không kéo theo bốn module.
 *   <li>{@code EmailSender} là bean <b>có điều kiện</b> — nó chỉ tồn tại khi có {@code SMTP_HOST}
 *       (xem {@code MailConfig}). Nơi gọi mà tiêm thẳng nó thì môi trường dev ⛔ không khởi động
 *       được. Lớp này tiêm {@code ObjectProvider} và biến "chưa cấu hình" thành một giá trị trả về,
 *       ⛔ không thành một lỗi lúc nạp context.
 * </ul>
 */
@Component
public class MailDispatcher implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final ObjectProvider<EmailSender> emailSender;

    public MailDispatcher(ObjectProvider<EmailSender> emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public boolean send(String toAddress, String subject, String body, String linkUrl) {
        if (toAddress == null || toAddress.isBlank()) {
            // ⛔ Địa chỉ rỗng ⛔ không phải "chưa cấu hình SMTP". Nơi gọi phải kiểm trước, và một
            //    lượt gọi thiếu địa chỉ là một lỗi lập trình đáng nhìn thấy ngay.
            throw new IllegalArgumentException("Địa chỉ người nhận rỗng");
        }
        EmailSender sender = emailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Chưa cấu hình SMTP — bỏ qua thư '{}'", subject);
            return false;
        }
        sender.send(toAddress, subject, body, linkUrl);
        return true;
    }
}
