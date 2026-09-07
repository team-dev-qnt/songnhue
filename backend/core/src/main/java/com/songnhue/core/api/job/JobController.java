package com.songnhue.core.api.job;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.job.JobService;
import com.songnhue.core.common.security.AuthenticatedEndpoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Tra tiến độ tác vụ nền — {@code /api/v1/jobs/**} (T6.10, conventions.md §1.3).
 *
 * <p>Endpoint đặt việc nằm ở từng chức năng (kết xuất báo cáo, nhập dữ liệu…) và trả
 * {@code 202 + jobId}; chỗ này là nơi FE hỏi "xong chưa".
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "00-core · Tác vụ nền", description = "Theo dõi tiến độ việc chạy nền")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Tiến độ một tác vụ nền do chính mình đặt")
    @AuthenticatedEndpoint(
            reason = "Chỉ xem được việc của chính mình — JobService.getOwn đối chiếu người đặt, "
                    + "nên không cần quyền riêng cho từng loại việc")
    public JobDtos.JobStatusView status(@PathVariable UUID jobId) {
        return JobDtos.JobStatusView.of(jobService.getOwn(jobId));
    }
}
