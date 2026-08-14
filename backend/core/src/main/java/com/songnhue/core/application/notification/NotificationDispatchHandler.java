package com.songnhue.core.application.notification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.job.JobContext;
import com.songnhue.core.application.job.JobHandler;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.domain.notification.Notification;
import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationRecipient;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.infra.notification.NotificationRecipientRepository;
import com.songnhue.core.infra.notification.NotificationRepository;

/**
 * Gửi các thông báo đang chờ ở kênh email — chạy trong hàng đợi job (T6.6).
 *
 * <p><b>Một người gửi hỏng không được kéo cả đợt xuống.</b> Mỗi lượt gửi bắt lỗi riêng và ghi trạng
 * thái riêng: một địa chỉ email sai chính tả chỉ làm hỏng đúng dòng của người đó. Nếu để ngoại lệ
 * thoát ra thì cả job thất bại, và ở lần thử lại những người đã nhận sẽ nhận <i>thêm lần nữa</i>.
 *
 * <p>Chỉ ném ngoại lệ khi <b>toàn bộ</b> lượt gửi đều hỏng — đó là dấu hiệu máy chủ thư chết chứ
 * không phải dữ liệu người nhận sai, và đúng lúc nên để hàng đợi thử lại với backoff.
 */
@Component
public class NotificationDispatchHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchHandler.class);

    /** Số lượt gửi mỗi lần chạy. Giữ job ngắn để không bị coi là treo, phần còn lại để lượt sau. */
    private static final int BATCH_SIZE = 200;

    private final NotificationRecipientRepository recipients;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ObjectProvider<EmailSender> emailSender;

    public NotificationDispatchHandler(
            NotificationRecipientRepository recipients,
            NotificationRepository notifications,
            UserRepository users,
            ObjectProvider<EmailSender> emailSender) {
        this.recipients = recipients;
        this.notifications = notifications;
        this.users = users;
        this.emailSender = emailSender;
    }

    @Override
    public String jobType() {
        return JobTypes.NOTIFICATION_DISPATCH;
    }

    @Override
    @Transactional
    public void handle(JobContext context) {
        List<NotificationRecipient> pending = recipients.findPending(
                NotificationChannel.EMAIL,
                com.songnhue.core.domain.notification.RecipientStatus.PENDING,
                PageRequest.of(0, BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }

        EmailSender sender = emailSender.getIfAvailable();
        if (sender == null) {
            // Chưa cấu hình SMTP. Đánh dấu SKIPPED chứ không để PENDING mãi — hàng đợi sẽ gọi lại
            // vô hạn cho những dòng không bao giờ gửi được.
            pending.forEach(row -> row.markSkipped("Chưa cấu hình máy chủ thư"));
            recipients.saveAll(pending);
            log.warn("Bỏ qua {} email — chưa cấu hình SMTP", pending.size());
            return;
        }

        Map<Long, Notification> byId = new HashMap<>();
        Map<Long, String> emailByUser = contactEmails(pending);

        int sent = 0;
        int failed = 0;
        for (NotificationRecipient row : pending) {
            try {
                Notification notification = byId.computeIfAbsent(
                        row.getNotificationId(),
                        id -> notifications.findById(id).orElse(null));
                String address = emailByUser.get(row.getUserId());
                if (notification == null || address == null || address.isBlank()) {
                    row.markSkipped("Người nhận không có địa chỉ email");
                    continue;
                }
                sender.send(address, notification.getTitle(), notification.getBody(), notification.getLinkUrl());
                row.markSent();
                sent++;
            } catch (RuntimeException e) {
                row.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
                failed++;
                log.warn("Gửi email cho người dùng {} thất bại", row.getUserId(), e);
            }
        }
        recipients.saveAll(pending);
        log.info("Gửi email: {} thành công, {} thất bại, {} bỏ qua", sent, failed, pending.size() - sent - failed);

        if (sent == 0 && failed == pending.size()) {
            // Không dòng nào đi được và tất cả đều lỗi — máy chủ thư chết, đáng để hàng đợi thử lại.
            throw new IllegalStateException(
                    "Toàn bộ " + failed + " email đều thất bại — nhiều khả năng SMTP đang hỏng");
        }
    }

    private Map<Long, String> contactEmails(List<NotificationRecipient> rows) {
        List<Long> userIds =
                rows.stream().map(NotificationRecipient::getUserId).distinct().toList();
        Map<Long, String> result = new HashMap<>();
        for (Object[] record : users.findContactInfo(userIds)) {
            Optional.ofNullable((String) record[1]).ifPresent(email -> result.put((Long) record[0], email));
        }
        return result;
    }
}
