package com.songnhue.core.application.job;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.core.common.config.AppProperties;
import com.songnhue.core.common.web.RequestContext;
import com.songnhue.core.domain.job.Job;
import com.songnhue.core.infra.job.JobRepository;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;

/**
 * Worker trong tiến trình — vòng lặp nhặt và chạy job (architecture-review.md §6.3).
 *
 * <p><b>Ba ranh giới transaction, cố ý tách rời:</b>
 *
 * <ol>
 *   <li><b>Nhặt việc</b> — {@code lockNextPending} + {@code markRunning} phải nằm trong <i>cùng
 *       một</i> transaction. Khoá dòng của {@code FOR UPDATE SKIP LOCKED} chỉ sống tới hết
 *       transaction; tách làm hai là hai worker cùng nhận một job.
 *   <li><b>Chạy handler</b> — <b>ngoài</b> mọi transaction. Việc nền chạy hàng phút; giữ một
 *       transaction (và một connection) suốt thời gian đó là cách chắc chắn nhất để cạn connection
 *       pool trong khi request của người dùng đứng chờ.
 *   <li><b>Ghi kết quả</b> — transaction riêng, ngắn.
 * </ol>
 *
 * <p><b>Vì sao {@link TransactionTemplate} chứ không {@code @Transactional}.</b> Các bước trên gọi
 * lẫn nhau trong cùng một đối tượng, mà lời gọi nội bộ không đi qua proxy Spring nên annotation bị
 * bỏ qua <i>lặng lẽ</i>. Cùng lý do đã ghi ở {@code RefreshTokenService} (WS-5).
 *
 * <p>Lên ≥2 node thì lớp này <b>không cần sửa</b>: {@code SKIP LOCKED} vốn đã cho nhiều tiến trình
 * cùng nhặt việc. Chỉ job theo lịch mới cần ShedLock — xem {@code SchedulerConfig}.
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    /** Số luồng chạy job song song. Hệ này vài nghìn bản ghi/ngày — 4 là dư. */
    private static final int POOL_SIZE = 4;

    /**
     * Job {@code RUNNING} quá lâu mà không ai đụng tới coi như node giữ nó đã chết.
     *
     * <p>Phải dài hơn hẳn job chạy lâu nhất, nếu không job đang chạy bình thường bị cướp và chạy lần
     * hai song song với lần một.
     */
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    /**
     * Giãn dần giữa các lần thử: 1 phút → 5 phút → 15 phút.
     *
     * <p>Thử lại ngay lập tức là vô nghĩa với nguyên nhân hỏng phổ biến nhất (dịch vụ ngoài đang
     * chập chờn) và còn góp phần dìm nó thêm.
     */
    private static final Duration[] BACKOFF = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15)};

    private final JobRepository repository;
    private final TransactionTemplate transactions;
    private final Map<String, JobHandler> handlers;
    private final boolean enabled;
    private final String workerId;

    private final ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("job-worker-" + thread.threadId());
        thread.setDaemon(true);
        return thread;
    });

    /** Đếm việc đang chạy để không nhặt quá sức — hàng đợi nằm ở DB, không dồn vào bộ nhớ. */
    private final AtomicInteger inFlight = new AtomicInteger();

    public JobWorker(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            List<JobHandler> handlerBeans,
            AppProperties appProperties) {

        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.enabled = appProperties.isWorkerEnabled();
        this.workerId = RequestContext.newTraceId();
        this.handlers = index(handlerBeans);

        log.info(
                "JobWorker {} — {} (đã đăng ký {} loại việc: {})",
                workerId,
                enabled ? "BẬT" : "TẮT theo cấu hình WORKER_ENABLED",
                handlers.size(),
                handlers.keySet());
    }

    /**
     * Hai handler cùng {@code jobType} là lỗi cấu hình phải chặn <b>lúc khởi động</b>.
     *
     * <p>Để chạy tiếp thì việc nào rơi vào loại đó sẽ được xử lý bởi một trong hai handler, tuỳ thứ
     * tự Spring nạp bean — không xác định được và không có lỗi nào báo ra.
     */
    private static Map<String, JobHandler> index(List<JobHandler> beans) {
        Map<String, JobHandler> byType = new HashMap<>();
        for (JobHandler handler : beans) {
            JobHandler existing = byType.put(handler.jobType(), handler);
            if (existing != null) {
                throw new IllegalStateException("Hai handler cùng nhận job loại '" + handler.jobType() + "': "
                        + existing.getClass().getName() + " và "
                        + handler.getClass().getName());
            }
        }
        return Map.copyOf(byType);
    }

    @Scheduled(fixedDelayString = "${app.worker.poll-interval-ms:5000}")
    public void poll() {
        if (!enabled || handlers.isEmpty()) {
            return;
        }
        try {
            int capacity = POOL_SIZE - inFlight.get();
            if (capacity <= 0) {
                return;
            }
            for (Job job : claim(capacity)) {
                inFlight.incrementAndGet();
                pool.submit(() -> {
                    try {
                        run(job);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
            }
        } catch (RuntimeException e) {
            // Vòng lặp nhặt việc chết là cả hàng đợi đứng im mà không có triệu chứng nào ngoài
            // "job mãi không chạy". Ghi log rồi để lượt sau thử lại.
            log.error("Vòng nhặt việc lỗi — sẽ thử lại ở lượt sau", e);
        }
    }

    /** Trả job treo về hàng đợi. Tách khỏi {@link #poll()} vì chỉ cần chạy thưa. */
    @Scheduled(fixedDelayString = "${app.worker.stale-check-interval-ms:300000}")
    public void releaseStaleJobs() {
        if (!enabled) {
            return;
        }
        int released = transactions.execute(
                status -> repository.releaseStaleJobs(Instant.now().minus(STALE_AFTER)));
        if (released > 0) {
            log.warn("Trả {} job treo về hàng đợi — nhiều khả năng một node đã dừng đột ngột", released);
        }
    }

    private List<Job> claim(int capacity) {
        return transactions.execute(status -> {
            List<Long> ids = repository.lockNextPending(capacity);
            if (ids.isEmpty()) {
                return List.of();
            }
            repository.markRunning(ids, workerId);
            return repository.findAllById(ids);
        });
    }

    private void run(Job job) {
        // Nối log của worker với request đã đặt job — không có nó thì lỗi ở đây là một dòng log
        // mồ côi, không tra ngược được ai đã bấm nút gì.
        RequestContext.setTraceId(job.getTraceId() != null ? job.getTraceId() : RequestContext.newTraceId());
        try {
            JobHandler handler = handlers.get(job.getJobType());
            if (handler == null) {
                fail(job, "Không có handler nào nhận job loại '" + job.getJobType() + "'", false);
                return;
            }

            handler.handle(new JobContext(
                    job.getPublicId(),
                    job.getJobType(),
                    job.getPayload(),
                    job.getRequestedBy(),
                    percent -> transactions.executeWithoutResult(status -> repository
                            .findByPublicId(job.getPublicId())
                            .ifPresent(fresh -> {
                                fresh.updateProgress(percent);
                                repository.save(fresh);
                            }))));

            succeed(job);
        } catch (Exception e) {
            log.error(
                    "Job {} loại {} thất bại ở lần thử {}", job.getPublicId(), job.getJobType(), job.getAttempts(), e);
            fail(job, e.getClass().getSimpleName() + ": " + e.getMessage(), true);
        } finally {
            RequestContext.clear();
        }
    }

    private void succeed(Job job) {
        transactions.executeWithoutResult(
                status -> repository.findByPublicId(job.getPublicId()).ifPresent(fresh -> {
                    fresh.markSucceeded(fresh.getResult());
                    repository.save(fresh);
                }));
        log.info("Job {} loại {} xong", job.getPublicId(), job.getJobType());
    }

    /**
     * @param retryable {@code false} cho lỗi cấu hình (không có handler) — thử lại 3 lần cũng vẫn
     *     không có handler, chỉ tổ làm nhiễu log và trì hoãn lúc người vận hành nhìn thấy vấn đề
     */
    private void fail(Job job, String error, boolean retryable) {
        transactions.executeWithoutResult(
                status -> repository.findByPublicId(job.getPublicId()).ifPresent(fresh -> {
                    Instant retryAt = retryable && fresh.hasAttemptsLeft()
                            ? Instant.now().plus(BACKOFF[Math.min(fresh.getAttempts() - 1, BACKOFF.length - 1)])
                            : null;
                    fresh.markFailed(error, retryAt);
                    repository.save(fresh);
                }));
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
        try {
            // Chờ job đang chạy kết thúc tử tế. Cắt ngang giữa chừng thì job nằm lại ở RUNNING và
            // phải đợi hết STALE_AFTER mới có ai đụng tới.
            if (!pool.awaitTermination(20, TimeUnit.SECONDS)) {
                log.warn("Còn job đang chạy khi tắt — sẽ được trả về hàng đợi sau {}", STALE_AFTER);
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }
}
