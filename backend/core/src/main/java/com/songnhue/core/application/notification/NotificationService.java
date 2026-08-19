package com.songnhue.core.application.notification;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.job.JobService;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.domain.notification.Notification;
import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationRecipient;
import com.songnhue.core.domain.notification.NotificationSeverity;
import com.songnhue.core.domain.notification.RecipientStatus;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.infra.notification.NotificationRecipientRepository;
import com.songnhue.core.infra.notification.NotificationRepository;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyChannel;
import com.songnhue.core.spi.NotifyRequest;

/**
 * Gửi thông báo — pattern P4, mặt tiền duy nhất cho mọi module (T6.6).
 *
 * <p><b>Ghi trước, gửi sau.</b> Lời gọi {@link #notify} chỉ ghi thông báo và danh sách người nhận
 * vào DB rồi trả về ngay; việc gửi email do job nền làm. Lý do: SMTP có thể chậm hàng chục giây hoặc
 * chết hẳn, mà thông báo thường đi kèm một thao tác nghiệp vụ (duyệt bài, ghi nhận sự cố). Gửi đồng
 * bộ nghĩa là máy chủ thư hỏng thì <b>người dùng không lưu được dữ liệu</b> — đánh đổi sai hoàn
 * toàn.
 *
 * <p>Kênh {@code IN_APP} là ngoại lệ: nó "đã gửi" ngay khi dòng dữ liệu tồn tại, không cần đi đâu cả.
 */
@Service
public class NotificationService implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Khoá bật/tắt từng kênh trong bảng {@code settings} — quy tắc 12, đổi không cần deploy. */
    private static final String KEY_CHANNEL_ENABLED = "notification.channel.%s.enabled";

    private final NotificationRepository notifications;
    private final NotificationRecipientRepository recipients;
    private final RecipientResolver resolver;
    private final UserRepository users;
    private final SettingService settings;
    private final JobService jobs;

    public NotificationService(
            NotificationRepository notifications,
            NotificationRecipientRepository recipients,
            RecipientResolver resolver,
            UserRepository users,
            SettingService settings,
            JobService jobs) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.resolver = resolver;
        this.users = users;
        this.settings = settings;
        this.jobs = jobs;
    }

    /**
     * Gửi một thông báo nghiệp vụ.
     *
     * @return thông báo đã ghi; danh sách người nhận rỗng nghĩa là không ai đủ điều kiện nhận
     */
    @Transactional
    public Notification notify(NotificationRequest request) {
        List<Long> userIds = resolver.resolve(request.relatedOrgUnitIds(), request.extraUserIds());
        return dispatch(request, userIds, false);
    }

    /**
     * Thông báo hệ thống do Admin gửi (M5.13 — T6.14).
     *
     * @param userIds danh sách người nhận; rỗng = gửi toàn bộ tài khoản đang hoạt động
     */
    @Transactional
    public Notification broadcast(NotificationRequest request, List<Long> userIds) {
        List<Long> targets = userIds == null || userIds.isEmpty() ? users.findAllActiveIds() : userIds;
        return dispatch(request, targets, true);
    }

    private Notification dispatch(NotificationRequest request, List<Long> userIds, boolean broadcast) {
        Notification notification = new Notification(request.eventType(), request.title(), severityOrDefault(request));
        notification.setBody(request.body());
        notification.setLinkUrl(request.linkUrl());
        notification.setRefType(request.refType());
        notification.setRefId(request.refId());
        notification.setBroadcast(broadcast);
        AuthContext.current().ifPresent(user -> notification.setCreatedBy(user.userId()));

        Notification saved = notifications.saveAndFlush(notification);

        List<NotificationRecipient> rows = new ArrayList<>();
        boolean needsDispatchJob = false;
        for (NotificationChannel channel : request.channels()) {
            boolean enabled = isChannelEnabled(channel);
            for (Long userId : userIds) {
                NotificationRecipient row = new NotificationRecipient(saved.getId(), userId, channel);
                if (!enabled) {
                    row.markSkipped("Kênh đang tắt theo cấu hình");
                } else if (channel == NotificationChannel.IN_APP) {
                    // Không phải gửi đi đâu cả — dòng dữ liệu này CHÍNH LÀ thông báo.
                    row.markSent();
                } else {
                    needsDispatchJob = true;
                }
                rows.add(row);
            }
        }
        recipients.saveAll(rows);

        if (needsDispatchJob) {
            // Một job cho cả đợt, khoá chống trùng theo kênh: nhiều thông báo sinh liên tiếp vẫn chỉ
            // xếp một lượt gửi đang chờ, và lượt đó quét hết những gì còn PENDING.
            jobs.enqueue(JobTypes.NOTIFICATION_DISPATCH, "{}", JobTypes.NOTIFICATION_DISPATCH + ":EMAIL", (short) 3);
        }

        log.info(
                "Thông báo {} ({}) — {} người nhận, {} lượt gửi",
                saved.getPublicId(),
                request.eventType(),
                userIds.size(),
                rows.size());
        return saved;
    }

    // ---- Hộp thư của người dùng ----------------------------------------------

    @Transactional(readOnly = true)
    // ---- Hợp đồng cho module nghiệp vụ (core.spi) -------------------------------
    //
    // Bản dịch từ `NotifyRequest` (kiểu của SPI) sang `NotificationRequest` (kiểu của tầng
    // application, tham chiếu enum domain). Hai enum trùng khít tên hằng và có
    // `NotificationEnumParityTest` canh — thêm mức ở một bên mà quên bên kia là CI đỏ, không phải
    // một lỗi ánh xạ chờ tới lúc chạy mới lộ.

    @Override
    public void notify(NotifyRequest request) {
        notify(translate(request));
    }

    @Override
    public void broadcast(NotifyRequest request, List<Long> userIds) {
        broadcast(translate(request), userIds);
    }

    private static NotificationRequest translate(NotifyRequest request) {
        return new NotificationRequest(
                request.eventType(),
                request.title(),
                request.body(),
                NotificationSeverity.valueOf(request.severity().name()),
                request.linkUrl(),
                request.refType(),
                request.refId(),
                request.relatedOrgUnitIds(),
                request.extraUserIds(),
                request.channels().stream().map(NotificationService::translate).toList());
    }

    private static NotificationChannel translate(NotifyChannel channel) {
        return NotificationChannel.valueOf(channel.name());
    }

    public Page<InboxEntry> inbox(Long userId, Pageable pageable) {
        return recipients.findInbox(userId, NotificationChannel.IN_APP, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return recipients.countByUserIdAndChannelAndReadAtIsNull(userId, NotificationChannel.IN_APP);
    }

    /** Đánh dấu đã đọc — chỉ đọc được thông báo của chính mình. */
    @Transactional
    public void markRead(Long recipientRowId, Long userId) {
        NotificationRecipient row = recipients
                .findByIdAndUserId(recipientRowId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        row.markRead();
        recipients.save(row);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return recipients.markAllRead(userId, NotificationChannel.IN_APP);
    }

    @Transactional(readOnly = true)
    public Notification get(java.util.UUID publicId) {
        return notifications
                .findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    // ---- Nội bộ ---------------------------------------------------------------

    private boolean isChannelEnabled(NotificationChannel channel) {
        String key = KEY_CHANNEL_ENABLED.formatted(
                channel.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        // Mặc định TẮT khi thiếu khoá cấu hình: bật nhầm một kênh gửi ra ngoài (SMS, web push) tệ
        // hơn nhiều so với việc thiếu một kênh và phải bật tay.
        return settings.getBoolean(key, false);
    }

    private static NotificationSeverity severityOrDefault(NotificationRequest request) {
        return request.severity() == null ? NotificationSeverity.INFO : request.severity();
    }

    /** Dùng cho job gửi: các lượt còn chờ trên một kênh. */
    @Transactional(readOnly = true)
    public List<NotificationRecipient> pending(NotificationChannel channel, Pageable pageable) {
        return recipients.findPending(channel, RecipientStatus.PENDING, pageable);
    }
}
