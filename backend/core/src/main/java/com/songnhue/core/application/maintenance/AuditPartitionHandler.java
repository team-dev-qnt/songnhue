package com.songnhue.core.application.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.job.JobContext;
import com.songnhue.core.application.job.JobHandler;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.infra.audit.AuditMaintenanceRepository;

/**
 * Giữ runway partition cho {@code audit_logs} — nợ WS-2/T2.6, trả ở T6.8.
 *
 * <p>WS-2 đã cài hàm {@code core_ensure_audit_partitions} trong DB nhưng <b>chưa có ai gọi nó định
 * kỳ</b>. Migration tạo sẵn 12 tháng, nên thiếu job này hệ thống vẫn chạy tốt suốt một năm rồi mới
 * lộ vấn đề — đúng kiểu nợ dễ quên nhất.
 *
 * <p>Hết runway không làm hỏng việc ghi: bản ghi rơi vào partition {@code DEFAULT} (lưới an toàn của
 * WS-2). Nhưng {@code DEFAULT} có bản ghi <i>chính là</i> tín hiệu job này đã chết, nên ở đây ghi
 * cảnh báo — WS-7/T7.9 sẽ biến nó thành metric.
 */
@Component
public class AuditPartitionHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditPartitionHandler.class);

    /** Giữ luôn ≥6 tháng phía trước. Rộng tay vì partition rỗng gần như không tốn gì. */
    private static final int MONTHS_AHEAD = 6;

    private final AuditMaintenanceRepository repository;

    public AuditPartitionHandler(AuditMaintenanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public String jobType() {
        return JobTypes.AUDIT_PARTITION;
    }

    @Override
    @Transactional
    public void handle(JobContext context) {
        int created = repository.ensurePartitions(MONTHS_AHEAD);
        log.info("Bảo trì partition nhật ký: tạo thêm {} partition (giữ runway {} tháng)", created, MONTHS_AHEAD);

        long stranded = repository.countInDefaultPartition();
        if (stranded > 0) {
            log.error(
                    "⚠ {} bản ghi nhật ký nằm ở partition DEFAULT — job bảo trì partition đã chết một "
                            + "thời gian. Xem docs/runbook/audit-partition.md",
                    stranded);
        }
    }
}
