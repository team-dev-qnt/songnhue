package com.songnhue.content.application;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;

/**
 * Hẹn giờ việc nhắc SLA của hộp thư liên hệ — T36.4.
 *
 * <h2>⛔ Chỉ ĐẶT VIỆC, ⛔ không tự làm gì</h2>
 *
 * <p>Cùng khuôn với {@code MaintenanceScheduler} của Core, và vì cùng một lý do: một phương thức
 * {@code @Scheduled} chạy thẳng công việc thì ⛔ không có trạng thái, ⛔ không thử lại được, ⛔ không
 * hiện ở màn hình Việc nền — hỏng thì im lặng cho tới khi ai đó tình cờ đọc log.
 *
 * <h2>⭐ Khoá chống trùng theo NGÀY — đây chính là phần "dedup theo ngày" của §9.13.5</h2>
 *
 * <p>{@code uq_jobs_dedup_active} là một chỉ mục duy nhất trong CSDL, nên hai node cùng hẹn giờ thì
 * node thứ hai va chỉ mục và nhận lại chính job node thứ nhất vừa tạo. <b>CSDL đã là điểm đồng
 * bộ</b> — ⛔ không cần ShedLock cho đường này.
 *
 * <p>⚠ Hệ quả nghiệp vụ của khoá theo ngày, ⛔ không phải chi tiết kỹ thuật: một liên hệ quá hạn
 * được nhắc <b>đúng một lần mỗi ngày</b> cho tới khi có người xử lý. Nhắc mỗi giờ là dạy người ta
 * lọc thư.
 *
 * <h2>Vì sao 08:30 chứ ⛔ không phải 03:00 như các việc bảo trì</h2>
 *
 * <p>Việc bảo trì chạy đêm để tránh giờ cao điểm. Việc này thì ngược lại: nó sinh ra <b>một thông
 * báo cho người</b>, và thông báo tới lúc 3 giờ sáng nằm dưới đáy hộp thư khi người ta bắt đầu làm
 * việc. 08:30 là ngay sau giờ vào làm (giờ hành chính 8–17h, khoá {@code settings} của WS-4).
 */
@Component
public class ContactScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContactScheduler.class);

    private final JobPort jobs;

    public ContactScheduler(JobPort jobs) {
        this.jobs = jobs;
    }

    @Scheduled(cron = "0 30 8 * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void quetSla() {
        String khoa = CmsJobTypes.CONTACT_SLA_REMIND + ":" + LocalDate.now(DateTimeUtils.ZONE_VN);
        jobs.enqueue(new JobRequest(CmsJobTypes.CONTACT_SLA_REMIND, "{}", khoa, (short) 2));
        log.info("Đã đặt việc nhắc SLA liên hệ {}", khoa);
    }
}
