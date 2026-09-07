package com.songnhue.hydro.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.hydro.infra.HydroMaintenanceRepository;

/**
 * Giữ runway partition cho hai bảng time-series của MOD-03 — T29.6.
 *
 * <p>Chép khuôn {@code AuditPartitionHandler} (T6.8) vì đó là cùng một bài toán và cùng một cái bẫy:
 * migration tạo sẵn 12 tháng, nên <b>thiếu job này hệ thống vẫn chạy tốt suốt một năm rồi mới lộ
 * vấn đề</b> — đúng kiểu nợ dễ quên nhất, và lúc lộ ra thì người phát hiện là người trực ca đêm.
 *
 * <p>Hết runway ⛔ không làm hỏng việc ghi: bản ghi rơi vào partition {@code DEFAULT}. Điều đó là cố
 * ý — với một nguồn không có API lịch sử, một lượt {@code INSERT} hỏng là mất dữ liệu vĩnh viễn,
 * nên thà ghi chậm còn hơn không ghi. Nhưng {@code DEFAULT} có bản ghi <i>chính là</i> tín hiệu job
 * này đã chết, nên ở đây ghi {@code ERROR} cho cả hai bảng.
 */
@Component
public class HydroPartitionHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(HydroPartitionHandler.class);

    /** Giữ luôn ≥6 tháng phía trước. Rộng tay vì partition rỗng gần như không tốn gì. */
    private static final int MONTHS_AHEAD = 6;

    private static final String[] BANG = {"hydro_raw_logs", "hydro_readings"};

    private final HydroMaintenanceRepository repository;

    public HydroPartitionHandler(HydroMaintenanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public String jobType() {
        return HydroJobTypes.PARTITION;
    }

    @Override
    @Transactional
    public void handle(JobContext context) {
        int created = repository.ensurePartitions(MONTHS_AHEAD);
        log.info("Bảo trì partition thuỷ văn: tạo thêm {} partition (giữ runway {} tháng)", created, MONTHS_AHEAD);

        for (String bang : BANG) {
            long stranded = repository.countInDefaultPartition(bang);
            if (stranded > 0) {
                log.error(
                        "⚠ {} bản ghi của {} nằm ở partition DEFAULT — job bảo trì partition đã chết một "
                                + "thời gian. Xem docs/runbook/poller-chet.md",
                        stranded,
                        bang);
            }
        }
    }
}
