package com.songnhue.core.infra.job;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.job.Job;
import com.songnhue.core.domain.job.JobStatus;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByPublicId(UUID publicId);

    /**
     * Nhặt việc — <b>trái tim của hàng đợi</b> (architecture-review.md §6.3).
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} là thứ cho phép nhiều luồng (và sau này nhiều node) cùng lấy
     * việc mà không giẫm lên nhau: luồng nào đã khoá dòng nào thì luồng khác <i>bỏ qua</i> dòng đó
     * thay vì xếp hàng chờ. Không có {@code SKIP LOCKED} thì các worker tuần tự hoá lẫn nhau và thêm
     * worker cũng không nhanh hơn.
     *
     * <p>⚠ Khoá dòng chỉ giữ tới hết transaction, nên lời gọi này và lệnh đánh dấu {@code RUNNING}
     * <b>bắt buộc nằm trong cùng một transaction</b>. Tách ra là hai worker cùng nhận một job.
     *
     * <p>Thứ tự {@code priority, available_at, id} khớp với chỉ mục riêng {@code ix_jobs_pickup} (chỉ
     * chứa job {@code PENDING}) — hàng đợi có tồn đọng vẫn không phải quét cả bảng.
     */
    @Query(
            value =
                    """
            SELECT id FROM jobs
             WHERE status = 'PENDING'
               AND available_at <= now()
             ORDER BY priority, available_at, id
             LIMIT :batchSize
               FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    List<Long> lockNextPending(@Param("batchSize") int batchSize);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
            UPDATE jobs
               SET status = 'RUNNING',
                   started_at = now(),
                   locked_by = :worker,
                   locked_at = now(),
                   attempts = attempts + 1
             WHERE id IN (:ids)
            """,
            nativeQuery = true)
    int markRunning(@Param("ids") List<Long> ids, @Param("worker") String worker);

    /**
     * Trả job treo về hàng đợi.
     *
     * <p>Node chết giữa chừng để lại job ở {@code RUNNING} vĩnh viễn — không ai chạy, cũng không ai
     * báo lỗi. Với worker trong tiến trình thì mất điện hay deploy đúng lúc là đủ để xảy ra.
     * {@code attempts} đã tăng lúc nhặt nên job hỏng thật vẫn hết lượt thử, không quay vòng vô hạn.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
            UPDATE jobs
               SET status = CASE WHEN attempts < max_attempts THEN 'PENDING' ELSE 'FAILED' END,
                   locked_by = NULL,
                   locked_at = NULL,
                   last_error = 'Worker giữ job đã dừng đột ngột — job được trả về hàng đợi',
                   finished_at = CASE WHEN attempts >= max_attempts THEN now() ELSE NULL END
             WHERE status = 'RUNNING'
               AND locked_at < :staleBefore
            """,
            nativeQuery = true)
    int releaseStaleJobs(@Param("staleBefore") Instant staleBefore);

    /** Dọn job đã kết thúc — bảng hàng đợi không phải nơi lưu lịch sử. */
    @Modifying
    @Query(
            value = "DELETE FROM jobs WHERE status IN ('SUCCEEDED', 'CANCELLED') AND finished_at < :cutoff",
            nativeQuery = true)
    int deleteFinishedBefore(@Param("cutoff") Instant cutoff);

    long countByStatusIn(List<JobStatus> statuses);

    /**
     * Job chưa kết thúc mang khoá chống trùng này — dùng đúng chỉ mục
     * {@code uq_jobs_dedup_active}, nên là một lần tra khoá chứ không phải quét bảng.
     */
    Optional<Job> findFirstByDedupKeyAndStatusIn(String dedupKey, List<JobStatus> statuses);
}
