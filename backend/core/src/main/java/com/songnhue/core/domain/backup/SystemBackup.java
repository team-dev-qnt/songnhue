package com.songnhue.core.domain.backup;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một lượt sao lưu — thành hay bại (T7.1, T7.4).
 *
 * <p><b>Không kế thừa {@code BaseEntity}</b>, cùng lý do với {@code Job}: đây là bản ghi vận hành,
 * không phải dữ liệu nghiệp vụ. Không xoá mềm (dọn theo retention), không khoá lạc quan (chỉ có một
 * tiến trình ghi vào một dòng, và nó ghi đúng hai lần: lúc bắt đầu và lúc kết thúc).
 *
 * <p><b>Bản ghi được tạo TRƯỚC khi gọi {@code pg_dump}, ở trạng thái {@link BackupStatus#RUNNING}.</b>
 * Ngược đời ở chỗ nó ghi một việc chưa xong, nhưng đó là điểm mấu chốt: tiến trình chết giữa chừng —
 * máy chủ mất điện, container bị OOM-kill — thì vẫn còn lại một dòng {@code RUNNING} treo, và người
 * vận hành nhìn thấy. Chỉ ghi khi thành công thì lượt hỏng không để lại dấu vết nào, mà đó lại chính
 * là lượt cần nhìn thấy nhất.
 */
@Entity
@Table(name = "system_backups")
public class SystemBackup {

    /** Cắt bớt thông báo lỗi: pg_dump hỏng vì hết đĩa có thể trả về hàng nghìn dòng. */
    private static final int ERROR_MESSAGE_MAX = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackupStatus status = BackupStatus.RUNNING;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private BackupTrigger triggerType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "server_version", length = 50)
    private String serverVersion;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected SystemBackup() {
        // JPA
    }

    public SystemBackup(String fileName, BackupTrigger triggerType) {
        this.fileName = fileName;
        this.triggerType = triggerType;
    }

    public void markSucceeded(String filePath, long sizeBytes, String checksum, String serverVersion) {
        this.filePath = filePath;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksum;
        this.serverVersion = serverVersion;
        this.status = BackupStatus.SUCCEEDED;
        finish();
    }

    public void markFailed(String errorMessage) {
        this.errorMessage = truncate(errorMessage);
        this.status = BackupStatus.FAILED;
        finish();
    }

    private void finish() {
        this.finishedAt = Instant.now();
        this.durationMs = Duration.between(startedAt, finishedAt).toMillis();
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= ERROR_MESSAGE_MAX ? value : value.substring(0, ERROR_MESSAGE_MAX) + "…";
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public BackupStatus getStatus() {
        return status;
    }

    public BackupTrigger getTriggerType() {
        return triggerType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
