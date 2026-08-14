package com.songnhue.core.domain.job;

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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một việc chạy nền — pattern P5 (implement.md §2, architecture-review.md §6.3).
 *
 * <p><b>Không kế thừa {@code BaseEntity}</b>: job không phải dữ liệu nghiệp vụ. Nó không có xoá mềm
 * (chạy xong thì dọn theo retention), không có khoá lạc quan (tranh chấp giải bằng
 * {@code FOR UPDATE SKIP LOCKED} ở tầng DB, không phải bằng cột {@code version}), và không có
 * {@code updated_by} vì thứ ghi vào nó là worker chứ không phải người dùng.
 *
 * <p>{@code publicId} vẫn có, vì client cần một mã để hỏi tiến độ (T6.10).
 */
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "job_type", nullable = false, length = 60)
    private String jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "priority", nullable = false)
    private Short priority = 5;

    /** Sớm nhất được nhặt. Retry đẩy mốc này về tương lai — đó là toàn bộ cơ chế backoff. */
    @Column(name = "available_at", nullable = false)
    private Instant availableAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "attempts", nullable = false)
    private Short attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private Short maxAttempts = 3;

    @Column(name = "progress", nullable = false)
    private Short progress = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result")
    private String result;

    @Column(name = "last_error")
    private String lastError;

    /** Ai đang giữ job. Còn giá trị mà quá lâu không xong nghĩa là node giữ nó đã chết. */
    @Column(name = "locked_by", length = 120)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    /** Chống chạy trùng: chỉ mục duy nhất chỉ áp cho job chưa kết thúc (§6.3). */
    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "org_unit_id")
    private Long orgUnitId;

    /** traceId của request đã đặt job — nối được log của người dùng với log của worker. */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Job() {}

    public Job(String jobType, String payload) {
        this.jobType = jobType;
        this.payload = payload == null || payload.isBlank() ? "{}" : payload;
    }

    public void markSucceeded(String result) {
        this.status = JobStatus.SUCCEEDED;
        this.result = result;
        this.progress = 100;
        this.finishedAt = Instant.now();
        releaseLock();
    }

    /**
     * Ghi nhận thất bại.
     *
     * @param retryAt còn lượt thử lại thì đây là mốc được nhặt lại; {@code null} = hết lượt
     */
    public void markFailed(String error, Instant retryAt) {
        this.lastError = truncate(error);
        if (retryAt == null) {
            this.status = JobStatus.FAILED;
            this.finishedAt = Instant.now();
        } else {
            this.status = JobStatus.PENDING;
            this.availableAt = retryAt;
        }
        releaseLock();
    }

    public boolean hasAttemptsLeft() {
        return attempts < maxAttempts;
    }

    public void updateProgress(int percent) {
        this.progress = (short) Math.clamp(percent, 0, 100);
    }

    private void releaseLock() {
        this.lockedBy = null;
        this.lockedAt = null;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        // Cột TEXT nên không bắt buộc, nhưng stack trace dài vô ích trong bảng hàng đợi —
        // chi tiết đầy đủ đã nằm ở log kèm traceId.
        return error.length() <= 2000 ? error : error.substring(0, 2000) + "…";
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getJobType() {
        return jobType;
    }

    public String getPayload() {
        return payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setPriority(Short priority) {
        this.priority = priority;
    }

    public Short getPriority() {
        return priority;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(Instant availableAt) {
        this.availableAt = availableAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Short getAttempts() {
        return attempts;
    }

    public Short getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Short maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Short getProgress() {
        return progress;
    }

    public String getResult() {
        return result;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
