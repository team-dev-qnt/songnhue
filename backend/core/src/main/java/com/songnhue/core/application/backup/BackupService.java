package com.songnhue.core.application.backup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.application.settings.SettingKeys;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.config.BackupProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.common.web.RequestContext;
import com.songnhue.core.domain.backup.BackupStatus;
import com.songnhue.core.domain.backup.BackupTrigger;
import com.songnhue.core.domain.backup.SystemBackup;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.backup.PostgresToolRunner;
import com.songnhue.core.infra.backup.SystemBackupRepository;

/**
 * Tạo bản sao lưu CSDL và quản lý vòng đời của chúng (T7.1, T7.4).
 *
 * <h2>Đây là toàn bộ cơ chế sao lưu</h2>
 *
 * <p>Không có PITR, không có WAL archiving, không có replica — chốt ở {@code architecture-review.md}
 * §6.5 với 4 rủi ro đã ghi rõ và chấp nhận. Nghĩa là bản dump đêm là <b>đường phục hồi duy nhất</b>,
 * và mọi thứ trong lớp này phải được đọc dưới góc nhìn đó.
 *
 * <h2>Ba quyết định đáng giải thích</h2>
 *
 * <p><b>1. {@code pg_dump -Fc} chứ không phải SQL thuần.</b> Định dạng custom đã nén sẵn (zlib), nên
 * không cần gzip chồng lên — thêm một tầng nén là thêm một chỗ hỏng mà không giảm được bao nhiêu
 * dung lượng. Quan trọng hơn: định dạng này khôi phục <i>chọn lọc</i> được (một bảng, bỏ qua index),
 * còn tệp SQL thuần thì chỉ có đường chạy lại từ đầu tới cuối.
 *
 * <p><b>2. Dọn bản cũ chỉ chạy SAU khi lượt mới đã thành công.</b> Dọn trước là có một khoảng thời
 * gian hệ thống không còn bản sao lưu nào đủ mới — và nếu lượt mới hỏng thì khoảng đó kéo dài tới
 * tận đêm sau. Thứ tự này đổi lấy vài phút chiếm thêm đĩa để không bao giờ có khoảng trống.
 *
 * <p><b>3. Checksum tính bằng cách đọc lại tệp từ đĩa, không phải tính trong lúc ghi.</b> Băm dòng
 * dữ liệu đang chảy chỉ chứng minh "cái tôi định ghi", còn đọc lại chứng minh "cái thật sự nằm trên
 * đĩa" — mà đó mới là thứ sẽ được dùng để khôi phục. Đĩa đầy và ghi thiếu là kiểu hỏng bị bắt đúng ở
 * đây.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int CHECKSUM_BUFFER = 64 * 1024;
    private static final int HISTORY_LIMIT = 100;

    private final BackupProperties properties;
    private final SystemBackupRepository repository;
    private final PostgresToolRunner toolRunner;
    private final SettingService settings;
    private final SecurityEventService securityEvents;

    /**
     * ⚠ Dùng {@link TransactionTemplate} chứ <b>không</b> dùng {@code @Transactional} cho ba phương
     * thức ghi nhận bên dưới. Chúng được gọi từ {@link #runBackup} <i>trong cùng lớp</i>, mà
     * self-invocation không đi qua proxy của Spring — annotation ở đó bị bỏ qua hoàn toàn, im lặng.
     * Hậu quả đúng bằng việc mất cả cơ chế: bản ghi {@code RUNNING} không được commit trước khi
     * pg_dump chạy, nên lượt hỏng vì mất điện lại chẳng để lại dấu vết nào — đúng thứ cơ chế này sinh
     * ra để giữ.
     */
    private final TransactionTemplate requiresNew;

    public BackupService(
            BackupProperties properties,
            SystemBackupRepository repository,
            PostgresToolRunner toolRunner,
            SettingService settings,
            SecurityEventService securityEvents,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.repository = repository;
        this.toolRunner = toolRunner;
        this.settings = settings;
        this.securityEvents = securityEvents;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---- Đọc trạng thái (M5.10) ---------------------------------------------

    @Transactional(readOnly = true)
    public Optional<SystemBackup> lastSuccessful() {
        return repository.findFirstByStatusOrderByFinishedAtDesc(BackupStatus.SUCCEEDED);
    }

    @Transactional(readOnly = true)
    public List<SystemBackup> history() {
        return repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, HISTORY_LIMIT));
    }

    @Transactional(readOnly = true)
    public SystemBackup get(UUID publicId) {
        return repository.findByPublicId(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /** Tuổi bản sao lưu thành công gần nhất — nguồn của health indicator và metric (T7.3). */
    @Transactional(readOnly = true)
    public Optional<Duration> ageOfLastSuccess() {
        return lastSuccessful().map(SystemBackup::getFinishedAt).map(at -> Duration.between(at, Instant.now()));
    }

    public Duration staleThreshold() {
        return Duration.ofHours(settings.getInt(SettingKeys.BACKUP_STALE_HOURS, 26));
    }

    public boolean isScheduleEnabled() {
        return settings.getBoolean(SettingKeys.BACKUP_SCHEDULE_ENABLED, true);
    }

    // ---- Tạo bản sao lưu -----------------------------------------------------

    /**
     * Chạy một lượt sao lưu. <b>Đồng bộ và có thể mất hàng phút</b> — luôn gọi từ worker hàng đợi,
     * không bao giờ gọi thẳng trong luồng xử lý request.
     *
     * @return bản ghi đã cập nhật trạng thái, kể cả khi thất bại
     */
    public SystemBackup runBackup(BackupTrigger trigger, Long requestedBy) {
        requireDumpConfigured();

        String fileName = "songnhue-%s-%s.dump"
                .formatted(
                        properties.getDatabase(),
                        LocalDateTime.now(DateTimeUtils.ZONE_VN).format(FILE_STAMP));
        Path target = backupDirectory().resolve(fileName);

        SystemBackup record = openRecord(fileName, trigger, requestedBy);
        try {
            PostgresToolRunner.ToolResult result = toolRunner.run(
                    dumpCommand(target), properties.getDumpPassword(), backupDirectory(), properties.getTimeout());

            if (!result.isSuccess()) {
                // Bản dump dở dang nguy hiểm hơn không có bản nào: nó trông như một bản sao lưu hợp
                // lệ trong danh sách file, và chỉ lộ ra là rác đúng lúc khôi phục.
                deleteQuietly(target);
                return closeFailed(
                        record, "pg_dump thoát với mã %d:%n%s".formatted(result.exitCode(), result.output()));
            }

            if (!Files.exists(target) || Files.size(target) == 0) {
                return closeFailed(
                        record, "pg_dump báo thành công nhưng tệp %s rỗng hoặc không tồn tại".formatted(target));
            }

            String checksum = sha256(target);
            SystemBackup saved = closeSucceeded(record, target, Files.size(target), checksum);
            log.info("Sao lưu xong: {} ({} byte, sha256={}…)", target, saved.getSizeBytes(), checksum.substring(0, 12));

            pruneExpired();
            return saved;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(target);
            return closeFailed(record, "Bị ngắt trong lúc sao lưu");
        } catch (IOException | RuntimeException e) {
            log.error("Sao lưu thất bại", e);
            deleteQuietly(target);
            return closeFailed(record, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private List<String> dumpCommand(Path target) {
        List<String> command = new ArrayList<>();
        command.add(properties.getPgDumpPath());
        command.add("--host=" + properties.getHost());
        command.add("--port=" + properties.getPort());
        command.add("--username=" + properties.getDumpUsername());
        command.add("--dbname=" + properties.getDatabase());
        // -Fc: định dạng custom, đã nén sẵn, khôi phục chọn lọc được
        command.add("--format=custom");
        command.add("--compress=6");
        // Không hỏi mật khẩu trên terminal: chạy nền thì lời nhắc đó là treo vĩnh viễn
        command.add("--no-password");
        // Vai trò readonly không sở hữu bảng nào; giữ lệnh gán chủ sở hữu trong bản dump sẽ làm
        // pg_restore báo lỗi quyền ở môi trường đích. Chủ sở hữu do migration đặt lại.
        command.add("--no-owner");
        command.add("--no-privileges");
        command.add("--file=" + target.toAbsolutePath());
        return command;
    }

    // ---- Dọn bản quá hạn -----------------------------------------------------

    /**
     * Xoá file của những bản quá hạn giữ. <b>Dòng ghi nhận trong CSDL được giữ lại</b> — lịch sử
     * "đêm nào sao lưu hỏng, đêm nào chạy được" là bằng chứng khi điều tra mất dữ liệu, mà nó chỉ
     * chiếm vài chục byte một dòng.
     *
     * <p>⚠ Cố ý <b>không</b> {@code @Transactional}: hàm này đọc CSDL và xoá <i>tệp</i>, không ghi
     * một dòng nào. Bản đầu có chú thích đó và {@link #runBackup} gọi nó bằng {@code this} — không
     * qua proxy nên chú thích vô hiệu. Giữ lại một chú thích đã vô hiệu thì lần đọc mã sau tưởng có
     * bảo đảm nguyên tử ở đây, mà xoá tệp thì không có giao dịch nào lùi lại được.
     */
    public int pruneExpired() {
        int retentionDays = settings.getInt(SettingKeys.BACKUP_RETENTION_DAYS, 30);
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        int removed = 0;
        for (SystemBackup expired : repository.findExpired(cutoff)) {
            if (deleteQuietly(Paths.get(expired.getFilePath()))) {
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Đã xoá {} bản sao lưu quá {} ngày", removed, retentionDays);
        }
        return removed;
    }

    // ---- Ghi nhận -----------------------------------------------------------

    /**
     * Mở bản ghi ở trạng thái {@code RUNNING} <b>trong transaction riêng</b>, commit ngay.
     *
     * <p>Phải commit trước khi {@code pg_dump} chạy: tiến trình chết giữa chừng thì dòng
     * {@code RUNNING} vẫn còn lại làm dấu vết. Giữ nó trong transaction chung với phần cập nhật kết
     * quả thì lượt hỏng vì mất điện không để lại gì cả.
     */
    private SystemBackup openRecord(String fileName, BackupTrigger trigger, Long requestedBy) {
        return requiresNew.execute(status -> {
            SystemBackup record = new SystemBackup(fileName, trigger);
            record.setRequestedBy(requestedBy);
            record.setTraceId(RequestContext.traceId());
            return repository.saveAndFlush(record);
        });
    }

    private SystemBackup closeSucceeded(SystemBackup record, Path target, long size, String checksum) {
        String serverVersion = serverVersionOrNull();
        SystemBackup saved = requiresNew.execute(status -> {
            SystemBackup managed = repository.findById(record.getId()).orElse(record);
            managed.markSucceeded(target.toAbsolutePath().toString(), size, checksum, serverVersion);
            return repository.saveAndFlush(managed);
        });
        recordEvent(SecurityEventType.BACKUP_CREATED, saved, null);
        return saved;
    }

    private SystemBackup closeFailed(SystemBackup record, String message) {
        log.error("Sao lưu THẤT BẠI: {}", message);
        SystemBackup saved = requiresNew.execute(status -> {
            SystemBackup managed = repository.findById(record.getId()).orElse(record);
            managed.markFailed(message);
            return repository.saveAndFlush(managed);
        });
        recordEvent(SecurityEventType.BACKUP_FAILED, saved, message);
        return saved;
    }

    private void recordEvent(SecurityEventType type, SystemBackup backup, String error) {
        AuthenticatedUser user = AuthContext.current().orElse(null);
        StringBuilder detail = new StringBuilder("{\"file\":\"")
                .append(backup.getFileName())
                .append("\",\"trigger\":\"")
                .append(backup.getTriggerType())
                .append('"');
        if (error != null) {
            detail.append(",\"error\":\"")
                    .append(error.replace('"', '\'').replace('\n', ' '))
                    .append('"');
        }
        detail.append('}');

        securityEvents.record(
                type,
                user == null ? "system" : user.username(),
                user == null ? null : user.userId(),
                ClientInfo.unknown(),
                detail.toString());
    }

    /** Phiên bản máy chủ, để biết trước bản dump này khôi phục được bằng client nào. */
    private String serverVersionOrNull() {
        try {
            PostgresToolRunner.ToolResult result =
                    toolRunner.run(List.of(properties.getPgDumpPath(), "--version"), "", null, Duration.ofSeconds(10));
            return result.isSuccess() ? result.output().trim() : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ---- Tiện ích ------------------------------------------------------------

    Path backupDirectory() {
        Path dir = Paths.get(properties.getDirectory());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessRuleException(ErrorCode.ADM_2008);
        }
        return dir;
    }

    private void requireDumpConfigured() {
        if (!properties.isDumpConfigured()) {
            log.error("Chưa cấu hình DB_READONLY_PASSWORD — không chạy được pg_dump");
            throw new BusinessRuleException(ErrorCode.ADM_2008);
        }
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[CHECKSUM_BUFFER];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 là thuật toán bắt buộc của mọi JRE — tới đây là môi trường chạy đã hỏng
            throw new IllegalStateException("JRE thiếu SHA-256", e);
        }
    }

    private boolean deleteQuietly(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Không xoá được {}: {}", path, e.getMessage());
            return false;
        }
    }
}
