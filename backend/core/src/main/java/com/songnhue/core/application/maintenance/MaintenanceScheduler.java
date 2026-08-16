package com.songnhue.core.application.maintenance;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.songnhue.core.application.backup.BackupService;
import com.songnhue.core.application.job.JobService;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.common.util.DateTimeUtils;

/**
 * Hẹn giờ các việc bảo trì — <b>chỉ đặt việc vào hàng đợi, không tự làm gì</b>.
 *
 * <p><b>Vì sao tách làm hai lớp thay vì để {@code @Scheduled} chạy thẳng công việc.</b> Việc chạy
 * thẳng trong phương thức {@code @Scheduled} không có trạng thái, không thử lại được, và không hiện
 * ở đâu cả — hỏng thì im lặng cho tới khi ai đó tình cờ đọc log. Đẩy vào hàng đợi thì dùng lại toàn
 * bộ bộ máy đã có: trạng thái, số lần thử, backoff, màn hình theo dõi, và job treo được thu hồi.
 *
 * <p><b>Vì sao không cần ShedLock ở đây.</b> Khoá chống trùng là ngày (VD
 * {@code TOKEN_CLEANUP:2026-08-14}), mà {@code uq_jobs_dedup_active} là chỉ mục duy nhất trong DB.
 * Hai node cùng hẹn giờ thì node thứ hai va chỉ mục và nhận lại chính job node thứ nhất vừa tạo —
 * <b>DB đã là điểm đồng bộ</b>, thêm một cơ chế khoá nữa chỉ là hai nguồn sự thật cho cùng một việc.
 * ShedLock giữ lại cho những việc theo lịch <i>không</i> đi qua hàng đợi (xem
 * {@code SchedulerLockConfig}).
 */
@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    /** Việc bảo trì hỏng không được kéo theo cảnh báo giả — 2 lần là đủ, lượt sau vẫn có ngày mai. */
    private static final short MAX_ATTEMPTS = 2;

    private final JobService jobService;
    private final BackupService backupService;

    public MaintenanceScheduler(JobService jobService, BackupService backupService) {
        this.jobService = jobService;
        this.backupService = backupService;
    }

    /**
     * 02:00 hằng đêm — sao lưu CSDL (T7.1).
     *
     * <p>Đặt sớm hơn các việc dọn dẹp ở 03:15+ có chủ đích: sao lưu phải chụp trạng thái <b>trước</b>
     * khi job dọn xoá token hết hạn và job đã xong. Chạy sau thì bản sao lưu không còn chứa những
     * dòng vừa bị dọn — thường vô hại, nhưng khi điều tra sự cố thì đúng những dòng đó là thứ cần.
     *
     * <p>Tắt được bằng tham số {@code backup.schedule-enabled} trên UI, và <b>ghi cảnh báo mỗi đêm
     * khi đang tắt</b>: hệ thống chạy không có bản sao lưu là trạng thái phải nhìn thấy được, không
     * phải một dòng cấu hình nằm im.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void scheduleBackup() {
        if (!backupService.isScheduleEnabled()) {
            log.warn("Sao lưu tự động đang TẮT (backup.schedule-enabled=false) — đêm nay không có bản sao lưu nào");
            return;
        }
        String dedupKey = JobTypes.DB_BACKUP + ":" + LocalDate.now(DateTimeUtils.ZONE_VN);
        jobService.enqueue(JobTypes.DB_BACKUP, "{\"trigger\":\"SCHEDULED\"}", dedupKey, (short) 1);
        log.info("Đã đặt việc sao lưu {}", dedupKey);
    }

    /** 03:15 hằng ngày — ngoài giờ hành chính và lệch giờ với job sao lưu 02:00 (WS-7). */
    @Scheduled(cron = "0 15 3 * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void scheduleTokenCleanup() {
        enqueueDaily(JobTypes.TOKEN_CLEANUP);
    }

    /** 03:30 hằng ngày. Chạy mỗi ngày dù chỉ cần mỗi tháng — hàm trong DB idempotent, gọi thừa vô hại. */
    @Scheduled(cron = "0 30 3 * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void scheduleAuditPartition() {
        enqueueDaily(JobTypes.AUDIT_PARTITION);
    }

    /** 03:45 hằng ngày. */
    @Scheduled(cron = "0 45 3 * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void scheduleJobPurge() {
        enqueueDaily(JobTypes.JOB_PURGE);
    }

    /**
     * 04:00 ngày mùng 1 hằng tháng — kết xuất nhật ký quá hạn (G7).
     *
     * <p>Hằng tháng chứ không hằng ngày: đây là việc XOÁ dữ liệu không phục hồi được, chạy thưa thì
     * mỗi lượt đều có dấu vết rõ ràng trong log và dễ soát lại. Khoá chống trùng theo tháng.
     */
    @Scheduled(cron = "0 0 4 1 * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void scheduleAuditArchive() {
        String dedupKey = JobTypes.AUDIT_ARCHIVE + ":"
                + LocalDate.now(DateTimeUtils.ZONE_VN).withDayOfMonth(1);
        jobService.enqueue(JobTypes.AUDIT_ARCHIVE, "{}", dedupKey, (short) 1);
        log.info("Đã đặt việc kết xuất nhật ký tháng {}", dedupKey);
    }

    private void enqueueDaily(String jobType) {
        String dedupKey = jobType + ":" + LocalDate.now(DateTimeUtils.ZONE_VN);
        jobService.enqueue(jobType, "{}", dedupKey, MAX_ATTEMPTS);
        log.debug("Đã đặt việc bảo trì {}", dedupKey);
    }
}
