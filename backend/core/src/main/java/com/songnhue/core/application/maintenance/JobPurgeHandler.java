package com.songnhue.core.application.maintenance;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.application.job.JobService;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;

/**
 * Dọn chính bảng hàng đợi.
 *
 * <p>Bảng {@code jobs} là hàng đợi, không phải kho lịch sử. Không dọn thì mỗi lượt nhặt việc phải
 * lách qua một bảng ngày càng dài — dù có chỉ mục riêng cho job {@code PENDING} thì bảng phình vẫn
 * kéo theo autovacuum nặng dần.
 *
 * <p>Chỉ xoá job đã <b>thành công hoặc bị huỷ</b>. Job {@code FAILED} giữ lại: đó là thứ người vận
 * hành cần đọc {@code last_error} rồi quyết định chạy lại hay bỏ.
 */
@Component
public class JobPurgeHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(JobPurgeHandler.class);

    private static final Duration RETENTION = Duration.ofDays(14);

    private final JobService jobService;

    public JobPurgeHandler(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public String jobType() {
        return JobTypes.JOB_PURGE;
    }

    @Override
    public void handle(JobContext context) {
        int removed = jobService.purgeFinishedBefore(Instant.now().minus(RETENTION));
        log.info("Dọn hàng đợi: xoá {} job đã kết thúc quá {} ngày", removed, RETENTION.toDays());
    }
}
