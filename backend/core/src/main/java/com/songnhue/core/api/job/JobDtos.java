package com.songnhue.core.api.job;

import java.time.Instant;
import java.util.UUID;

import com.songnhue.core.domain.job.Job;
import com.songnhue.core.domain.job.JobStatus;

/** DTO của API tác vụ nền (T6.10, conventions.md §1.3). */
public final class JobDtos {

    private JobDtos() {}

    /**
     * Trả kèm HTTP 202 khi nhận một việc chạy nền.
     *
     * <p>Hình dạng này là <b>chuẩn chung</b> cho mọi endpoint sinh việc nền — kết xuất báo cáo, nhập
     * dữ liệu, sao lưu. FE viết một lần rồi dùng lại: nhận 202 thì mở hộp thoại tiến độ và hỏi
     * {@code statusUrl} theo chu kỳ.
     */
    public record JobAccepted(UUID jobId, String statusUrl) {
        public static JobAccepted of(Job job) {
            return new JobAccepted(job.getPublicId(), "/api/v1/jobs/" + job.getPublicId());
        }
    }

    public record JobStatusView(
            UUID jobId,
            String jobType,
            JobStatus status,
            int progress,
            int attempts,
            int maxAttempts,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            /** Chỉ có khi thành công. Nội dung tuỳ loại việc — FE đọc theo loại. */
            String result,
            /**
             * Thông báo lỗi rút gọn khi thất bại.
             *
             * <p>Chi tiết đầy đủ nằm ở log theo {@code traceId}. Không đẩy stack trace ra API — đó
             * là bản đồ nội bộ hệ thống (§2.2).
             */
            String lastError) {

        public static JobStatusView of(Job job) {
            return new JobStatusView(
                    job.getPublicId(),
                    job.getJobType(),
                    job.getStatus(),
                    job.getProgress(),
                    job.getAttempts(),
                    job.getMaxAttempts(),
                    job.getCreatedAt(),
                    job.getStartedAt(),
                    job.getFinishedAt(),
                    job.getResult(),
                    job.getLastError());
        }
    }
}
