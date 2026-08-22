package com.songnhue.operations.application;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;
import com.songnhue.core.spi.SettingPort;

@Component
public class OperationsScheduler {

    private static final Logger log = LoggerFactory.getLogger(OperationsScheduler.class);

    private final JobPort jobPort;
    private final SettingPort settingPort;

    public OperationsScheduler(JobPort jobPort, SettingPort settingPort) {
        this.jobPort = jobPort;
        this.settingPort = settingPort;
    }

    /** 03:00 hằng ngày — Đối soát trạng thái công trình. */
    @Scheduled(cron = "0 0 3 * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void scheduleStatusReconcile() {
        boolean enabled = settingPort.getBoolean("ops.status-reconcile.enabled", true);
        if (!enabled) {
            return;
        }

        String dedupKey = "STATUS_RECONCILE:" + LocalDate.now(DateTimeUtils.ZONE_VN);
        JobRequest request = new JobRequest("STATUS_RECONCILE", "{}", dedupKey, (short) 1);
        jobPort.enqueue(request);
        log.info("Đã đặt việc đối soát trạng thái công trình {}", dedupKey);
    }
}
