package com.songnhue.core.api.notification;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.notification.InboxEntry;
import com.songnhue.core.application.notification.NotificationRequest;
import com.songnhue.core.application.notification.NotificationService;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedEndpoint;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationSeverity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Hộp thư thông báo và thông báo hệ thống — {@code /api/v1/notifications/**} (CN-05.6, M5.13).
 *
 * <p>Hai nhóm quyền khác hẳn nhau: đọc hộp thư của <b>chính mình</b> thì chỉ cần đăng nhập, còn gửi
 * thông báo cho toàn Công ty là quyền quản trị riêng.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "00-core · Thông báo", description = "Hộp thư trong ứng dụng và thông báo hệ thống")
public class NotificationController {

    private static final List<String> SORTABLE = List.of("id");

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Hộp thư của chính mình")
    @AuthenticatedEndpoint(reason = "Chỉ trả thông báo của người đang đăng nhập — lọc theo userId trong service")
    public Page<InboxEntry> inbox(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Long userId = AuthContext.require().userId();
        return notificationService.inbox(userId, PageUtils.toPageable(page, size, null, SORTABLE));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo chưa đọc — dùng cho badge trên giao diện")
    @AuthenticatedEndpoint(reason = "Đếm thông báo của chính người đang đăng nhập")
    public NotificationDtos.UnreadCount unreadCount() {
        return new NotificationDtos.UnreadCount(
                notificationService.unreadCount(AuthContext.require().userId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    @AuthenticatedEndpoint(reason = "Chỉ đánh dấu được thông báo của chính mình — service đối chiếu userId")
    public void markRead(@PathVariable Long id) {
        notificationService.markRead(id, AuthContext.require().userId());
    }

    @PostMapping("/read-all")
    @Operation(summary = "Đánh dấu toàn bộ đã đọc")
    @AuthenticatedEndpoint(reason = "Chỉ tác động tới hộp thư của chính mình")
    public NotificationDtos.MarkAllResult markAllRead() {
        return new NotificationDtos.MarkAllResult(
                notificationService.markAllRead(AuthContext.require().userId()));
    }

    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gửi thông báo hệ thống tới toàn bộ hoặc một nhóm tài khoản (M5.13)")
    @RequirePermission("adm:notification:broadcast")
    public NotificationDtos.BroadcastResult broadcast(@Valid @RequestBody NotificationDtos.BroadcastRequest request) {
        NotificationRequest payload = new NotificationRequest(
                "SYSTEM_ANNOUNCEMENT",
                request.title(),
                request.body(),
                request.severity() == null ? NotificationSeverity.INFO : request.severity(),
                request.linkUrl(),
                null,
                null,
                List.of(),
                List.of(),
                List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));

        return new NotificationDtos.BroadcastResult(
                notificationService.broadcast(payload, request.userIds()).getPublicId());
    }

    /** DTO của API thông báo. */
    public static final class NotificationDtos {

        private NotificationDtos() {}

        public record UnreadCount(long unread) {}

        public record MarkAllResult(int marked) {}

        public record BroadcastResult(java.util.UUID notificationId) {}

        /** @param userIds bỏ trống = gửi toàn bộ tài khoản đang hoạt động */
        public record BroadcastRequest(
                @NotBlank @Size(max = 255) String title,
                @NotBlank String body,
                NotificationSeverity severity,
                @Size(max = 500) String linkUrl,
                List<Long> userIds) {}
    }
}
