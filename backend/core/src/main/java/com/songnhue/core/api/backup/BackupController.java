package com.songnhue.core.api.backup;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.TotpService;
import com.songnhue.core.application.backup.BackupService;
import com.songnhue.core.application.backup.RestoreService;
import com.songnhue.core.application.identity.UserAdminService;
import com.songnhue.core.application.job.JobService;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.common.config.BackupProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.domain.backup.BackupStatus;
import com.songnhue.core.domain.backup.SystemBackup;
import com.songnhue.core.domain.identity.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Sao lưu và khôi phục — {@code /api/v1/backups/**} (M5.10, M5.11 · CN-05.5).
 *
 * <p>Hai nhóm chức năng có mức rủi ro cách nhau rất xa, nên chúng <b>không</b> dùng chung điều kiện
 * truy cập: xem và tạo bản sao lưu là việc thường ngày của quản trị viên; khôi phục là thao tác ghi
 * đè toàn bộ dữ liệu và bị chặn thêm ba lớp ở {@link #restore}.
 */
@RestController
@RequestMapping("/api/v1/backups")
@Tag(name = "05-adm · Sao lưu & khôi phục", description = "M5.10, M5.11 — pg_dump hằng đêm, RPO ≤ 24h")
public class BackupController {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final BackupService backupService;
    private final RestoreService restoreService;
    private final BackupProperties properties;
    private final JobService jobService;
    private final TotpService totpService;
    private final UserAdminService userAdminService;

    public BackupController(
            BackupService backupService,
            RestoreService restoreService,
            BackupProperties properties,
            JobService jobService,
            TotpService totpService,
            UserAdminService userAdminService) {
        this.backupService = backupService;
        this.restoreService = restoreService;
        this.properties = properties;
        this.jobService = jobService;
        this.totpService = totpService;
        this.userAdminService = userAdminService;
    }

    @GetMapping("/status")
    @Operation(summary = "Trạng thái sao lưu gần nhất — dòng hiển thị trên màn hình quản trị")
    @RequirePermission("adm:backup:view")
    public BackupDtos.StatusView status() {
        Optional<SystemBackup> last = backupService.lastSuccessful();
        Duration threshold = backupService.staleThreshold();
        Optional<Duration> age = backupService.ageOfLastSuccess();

        return new BackupDtos.StatusView(
                last.map(BackupDtos.BackupView::of).orElse(null),
                age.map(Duration::toSeconds).orElse(null),
                threshold.toHours(),
                // Chưa có bản nào cũng là "quá hạn" — hệ thống chưa từng được sao lưu là trạng thái
                // đáng báo động nhất, không phải trạng thái trung tính
                age.map(a -> a.compareTo(threshold) > 0).orElse(true),
                backupService.isScheduleEnabled(),
                properties.isRestoreConfigured());
    }

    @GetMapping
    @Operation(summary = "Lịch sử sao lưu — gồm cả lượt THẤT BẠI")
    @RequirePermission("adm:backup:view")
    public List<BackupDtos.BackupView> history() {
        return backupService.history().stream().map(BackupDtos.BackupView::of).toList();
    }

    @PostMapping
    @Operation(summary = "Sao lưu theo yêu cầu (M5.10) — chạy nền, trả về jobId để theo dõi")
    @RequirePermission("adm:backup:create")
    public BackupDtos.JobAccepted create() {
        var job = jobService.enqueue(
                JobTypes.DB_BACKUP,
                "{\"trigger\":\"MANUAL\"}",
                // Khoá chống trùng theo loại việc, KHÔNG theo ngày: người dùng bấm nút hai lần
                // trong một phút thì nhận lại cùng một job, nhưng vẫn sao lưu lại được sau khi
                // lượt trước đã xong (khác job hằng đêm — xem MaintenanceScheduler).
                JobTypes.DB_BACKUP + ":manual",
                (short) 1);
        return new BackupDtos.JobAccepted(job.getPublicId(), job.getStatus().name());
    }

    /**
     * Khôi phục dữ liệu (M5.11) — <b>ghi đè toàn bộ CSDL</b>.
     *
     * <p>Ba lớp chặn tại đây, cộng ba lớp nữa trong {@link RestoreService}:
     *
     * <ol>
     *   <li><b>Chỉ Super Admin.</b> Kiểm vai trò tường minh chứ không chỉ dựa vào quyền
     *       {@code adm:backup:restore} — quyền thì gán được cho vai trò khác qua màn hình phân
     *       quyền, mà chức năng này không được phép nới ra bằng một thao tác trên UI.
     *   <li><b>Mã TOTP tươi.</b> Cố ý bắt nhập lại mã <i>ngay lúc này</i>, không chấp nhận "đã qua
     *       2FA lúc đăng nhập". Phiên mở từ sáng trên một máy không khoá màn hình vẫn là phiên hợp
     *       lệ; bắt nhập mã chứng minh người đang ngồi đó thật sự giữ thiết bị thứ hai.
     *   <li><b>Xác nhận + lý do</b> — kiểm ở {@link RestoreService#validateRequest}.
     * </ol>
     */
    @PostMapping("/{publicId}/restore")
    @Operation(summary = "Khôi phục từ một bản sao lưu (M5.11) — Super Admin + mã 2FA + xác nhận")
    @RequirePermission("adm:backup:restore")
    public BackupDtos.JobAccepted restore(
            @PathVariable java.util.UUID publicId, @Valid @RequestBody BackupDtos.RestoreRequest request) {

        AuthenticatedUser current =
                AuthContext.current().orElseThrow(() -> new PermissionDeniedException(ErrorCode.AUTH_3001));

        if (!current.hasRole(SUPER_ADMIN)) {
            throw new PermissionDeniedException(ErrorCode.AUTH_3001);
        }

        User user = userAdminService.get(current.publicId());
        totpService.verifyLoginCode(user, request.totpCode(), ClientInfo.unknown(), java.time.Instant.now());

        SystemBackup backup = restoreService.validateRequest(publicId, request.confirmation(), request.reason());

        String payload = "{\"backupId\":\"%s\",\"reason\":%s,\"actorUsername\":%s}"
                .formatted(backup.getPublicId(), jsonString(request.reason()), jsonString(current.username()));

        var job = jobService.enqueue(JobTypes.DB_RESTORE, payload, JobTypes.DB_RESTORE + ":active", (short) 1);
        return new BackupDtos.JobAccepted(job.getPublicId(), job.getStatus().name());
    }

    private static String jsonString(String raw) {
        if (raw == null) {
            return "null";
        }
        return '"'
                + raw.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", " ")
                        .replace("\r", " ")
                + '"';
    }

    /** DTO của API sao lưu. */
    public static final class BackupDtos {

        private BackupDtos() {}

        /**
         * @param lastSuccess bản thành công gần nhất, {@code null} khi chưa từng sao lưu
         * @param ageSeconds tuổi của bản đó — FE đổi sang "cách đây N giờ"
         * @param stale đã quá ngưỡng chưa; đây là thứ tô đỏ trên màn hình
         * @param restoreAvailable môi trường này có bật khôi phục qua UI không (xem BackupProperties)
         */
        public record StatusView(
                BackupView lastSuccess,
                Long ageSeconds,
                long staleThresholdHours,
                boolean stale,
                boolean scheduleEnabled,
                boolean restoreAvailable) {}

        public record BackupView(
                java.util.UUID id,
                String fileName,
                Long sizeBytes,
                String checksumSha256,
                String status,
                String trigger,
                java.time.Instant startedAt,
                java.time.Instant finishedAt,
                Long durationMs,
                String errorMessage) {

            public static BackupView of(SystemBackup entity) {
                return new BackupView(
                        entity.getPublicId(),
                        entity.getFileName(),
                        entity.getSizeBytes(),
                        entity.getChecksumSha256(),
                        entity.getStatus().name(),
                        entity.getTriggerType().name(),
                        entity.getStartedAt(),
                        entity.getFinishedAt(),
                        entity.getDurationMs(),
                        // ⛔ Không trả đường dẫn tệp ra API: nó lộ cấu trúc thư mục máy chủ, và
                        // người dùng không làm được gì với chuỗi đó. Runbook đọc thẳng từ DB.
                        entity.getStatus() == BackupStatus.FAILED ? entity.getErrorMessage() : null);
            }
        }

        public record JobAccepted(java.util.UUID jobId, String status) {}

        /**
         * @param confirmation phải bằng {@link RestoreService#CONFIRMATION_PHRASE}
         * @param reason tối thiểu 10 ký tự — đi vào nhật ký bảo mật
         * @param totpCode mã 2FA nhập lại ngay lúc thao tác
         */
        public record RestoreRequest(
                @jakarta.validation.constraints.NotBlank String confirmation,
                @jakarta.validation.constraints.NotBlank String reason,
                @jakarta.validation.constraints.NotBlank String totpCode) {}
    }
}
