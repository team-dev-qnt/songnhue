package com.songnhue.core.infra.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.application.notification.InboxEntry;
import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationRecipient;
import com.songnhue.core.domain.notification.RecipientStatus;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    /**
     * Hộp thư trong ứng dụng — ghép sẵn nội dung thông báo.
     *
     * <p>Join ở đây thay vì trả entity rồi để tầng trên nạp thêm: hộp thư là màn hình mở nhiều nhất
     * trong ngày, và một truy vấn phụ cho mỗi dòng là bài toán N+1 đúng ở chỗ tệ nhất.
     */
    @Query(
            """
            SELECT new com.songnhue.core.application.notification.InboxEntry(
                r.id, n.title, n.body, n.linkUrl, n.severity, n.eventType, n.broadcast, n.createdAt, r.readAt)
              FROM NotificationRecipient r
              JOIN Notification n ON n.id = r.notificationId
             WHERE r.userId = :userId AND r.channel = :channel
               AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP)
             ORDER BY r.id DESC
            """)
    Page<InboxEntry> findInbox(
            @Param("userId") Long userId, @Param("channel") NotificationChannel channel, Pageable pageable);

    /** Badge "chưa đọc" — chạy trên chỉ mục riêng {@code ix_notification_recipients_inbox}. */
    long countByUserIdAndChannelAndReadAtIsNull(Long userId, NotificationChannel channel);

    Optional<NotificationRecipient> findByIdAndUserId(Long id, Long userId);

    /**
     * Các lượt gửi còn chờ trên một kênh — nguồn việc cho job gửi email.
     *
     * <p>Giới hạn số lượng mỗi lượt: một sự cố lớn có thể sinh hàng trăm lượt gửi, và nạp hết vào
     * bộ nhớ rồi gửi trong một giao dịch dài là cách chắc chắn để job bị coi là treo.
     */
    @Query("SELECT r FROM NotificationRecipient r WHERE r.channel = :channel AND r.status = :status ORDER BY r.id")
    List<NotificationRecipient> findPending(
            @Param("channel") NotificationChannel channel, @Param("status") RecipientStatus status, Pageable pageable);

    @Query("UPDATE NotificationRecipient r SET r.readAt = CURRENT_TIMESTAMP "
            + "WHERE r.userId = :userId AND r.channel = :channel AND r.readAt IS NULL")
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    int markAllRead(@Param("userId") Long userId, @Param("channel") NotificationChannel channel);
}
