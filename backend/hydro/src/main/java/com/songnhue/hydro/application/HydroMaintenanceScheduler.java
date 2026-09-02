package com.songnhue.hydro.application;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;

/**
 * Hẹn giờ hai việc bảo trì của MOD-03 — <b>chỉ đặt việc vào hàng đợi, không tự làm gì</b>.
 *
 * <h2>Vì sao tách làm hai lớp thay vì để {@code @Scheduled} chạy thẳng công việc</h2>
 *
 * <p>Việc chạy thẳng trong phương thức {@code @Scheduled} không có trạng thái, không thử lại được,
 * và không hiện ở đâu cả — hỏng thì im lặng cho tới khi ai đó tình cờ đọc log. Đẩy vào hàng đợi thì
 * dùng lại toàn bộ bộ máy đã có: trạng thái, số lần thử, backoff, màn hình theo dõi, và job treo
 * được thu hồi. Cùng lý lẽ với {@code MaintenanceScheduler} của Core (§9.6).
 *
 * <p>⚠ Không cần ShedLock: khoá chống trùng là ngày ({@code HYDRO_PARTITION:2026-09-02}) và
 * {@code uq_jobs_dedup_active} là chỉ mục duy nhất trong CSDL. Hai node cùng hẹn giờ thì node thứ
 * hai va chỉ mục và nhận lại chính job node thứ nhất vừa tạo — <b>CSDL đã là điểm đồng bộ</b>.
 *
 * <p>⚠ {@code @EnableScheduling} nằm ở {@code SchedulingConfig} với {@code @Profile("!migrate")}.
 * ⛔ Đừng khai thêm ở đây: một luồng không-daemon trong tiến trình migrator giữ JVM sống mãi, và
 * triệu chứng là container đứng {@code Up} vô hạn, {@code app} kẹt ở {@code Created},
 * <b>không một dòng lỗi nào</b> (§9.11.5, đã xảy ra thật ngày 17/8).
 *
 * <h2>Vì sao đặt lệch giờ với việc bảo trì của Core</h2>
 *
 * <p>Core chạy 02:00 (sao lưu) · 03:15 · 03:30 · 03:45. Hai việc dưới đây đặt ở 04:15 và 04:30 —
 * <b>sau</b> bản sao lưu, vì cả hai đều XOÁ hoặc đổi cấu trúc. Chạy trước bản sao lưu thì bản sao
 * lưu của đêm ấy không còn chứa những gì vừa bị dọn, và đúng lúc điều tra sự cố thì đó lại là thứ
 * cần.
 */
@Component
public class HydroMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(HydroMaintenanceScheduler.class);

    /**
     * Việc TẠO partition hỏng thì thử lại vô hại — lượt sau vẫn có ngày mai, và hàm idempotent.
     *
     * <p>⚠⚠ Con số này là <b>nơi DUY NHẤT</b> quyết định số lần thử. {@code JobHandler.maxAttempts()}
     * <b>không có người đọc trong toàn kho</b>: {@code JobWorker} lấy {@code max_attempts} từ cột
     * của bảng {@code jobs}, mà cột ấy do {@code JobService.enqueue(…, request.maxAttempts())} ghi.
     * Ghi đè phương thức kia là khai một con số không điều khiển gì — và <b>trông như</b> đã điều
     * khiển, đó mới là phần đắt.
     */
    private static final short THU_LAI_TAO_PARTITION = 2;

    /**
     * ⛔ Việc XOÁ chỉ thử <b>MỘT</b> lần.
     *
     * <p>{@code HYDRO_RETENTION} chạy {@code DROP PARTITION} trên hai bảng và {@code DELETE} trên
     * hai bảng nữa — không phục hồi được, và nguồn không có API lịch sử. Thử lại tự động một thao
     * tác xoá là cách một lỗi cấu hình biến thành nhiều lượt xoá trong cùng một đêm. Hỏng thì để nó
     * hiện FAILED trên màn hình theo dõi việc nền và chờ người đọc — hạn lưu 90 ngày không gấp tới
     * mức phải thử lại sau một phút.
     */
    private static final short THU_LAI_DON_DU_LIEU = 1;

    private final JobPort jobs;

    public HydroMaintenanceScheduler(JobPort jobs) {
        this.jobs = jobs;
    }

    /**
     * 04:15 hằng ngày.
     *
     * <p>Chạy mỗi ngày dù chỉ cần mỗi tháng — hàm trong CSDL idempotent, gọi thừa vô hại, và một
     * việc chạy hằng ngày thì cái chết của nó lộ ra sau một ngày chứ không sau một tháng.
     */
    @Scheduled(cron = "0 15 4 * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void schedulePartition() {
        enqueueDaily(HydroJobTypes.PARTITION, THU_LAI_TAO_PARTITION);
    }

    /**
     * 04:30 hằng ngày — dọn dữ liệu quá hạn lưu.
     *
     * <p>⚠ Sau {@link #schedulePartition} <b>15 phút</b>, không cùng lúc: dọn partition cũ và tạo
     * partition mới đụng cùng một cây phân mảnh, và một trong hai sẽ phải chờ khoá.
     */
    @Scheduled(cron = "0 30 4 * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void scheduleRetention() {
        enqueueDaily(HydroJobTypes.RETENTION, THU_LAI_DON_DU_LIEU);
    }

    /**
     * ⚠ Khoá chống trùng dùng ngày <b>giờ VN</b>, và ở đây là đúng: nó chỉ cần <i>ổn định trong một
     * lượt chạy</i> và trùng với ngày làm việc mà người vận hành nhìn thấy trên màn hình việc nền.
     * ⛔ Đừng nhầm nó với mốc cắt hạn lưu — mốc ấy phải đọc {@code current_date} của phiên CSDL, vì
     * nó bị so với một giá trị do chính CSDL tính (xem {@code HydroRetentionHandler}).
     */
    private void enqueueDaily(String jobType, short soLanThu) {
        String dedupKey = jobType + ":" + LocalDate.now(DateTimeUtils.ZONE_VN);
        jobs.enqueue(new JobRequest(jobType, "{}", dedupKey, soLanThu));
        log.debug("Đã đặt việc bảo trì thuỷ văn {} (tối đa {} lượt thử)", dedupKey, soLanThu);
    }
}
