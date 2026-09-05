package com.songnhue.hydro.application;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;

/**
 * Hẹn giờ việc tính lại bảng tổng hợp ngày — T34.1. <b>Chỉ đặt việc vào hàng đợi.</b>
 *
 * <h2>Vì sao 5 phút, ⛔ không phải 2 phút và cũng ⛔ không phải hằng đêm</h2>
 *
 * <ul>
 *   <li><b>⛔ Không bám nhịp poller (2').</b> Poller ghi số đo; bảng tổng hợp là <i>ngày</i>. Chạy
 *       cùng nhịp là tính lại đúng một kỳ năm lần cho mỗi khung mà kết quả chỉ đổi ở chữ số cuối,
 *       và nó đặt hai việc khác bản chất lên cùng một con số — <i>"một công tắc cho hai bóng đèn
 *       cũng là lỗi"</i>.
 *   <li><b>⛔ Không để hằng đêm.</b> BC-13 là <b>phép đo duy nhất của NFR-03</b> (T37.1: 7 ngày
 *       liên tục, 1008 khung). Một bảng chỉ cập nhật lúc 4 giờ sáng nghĩa là suốt cả ngày quan sát
 *       ⛔ không ai nhìn thấy poller đang bỏ sót khung — và tới lúc nhìn thấy thì đã mất một ngày
 *       của phép đo, mà hỏng giữa chừng là <b>đếm lại từ đầu</b>.
 * </ul>
 *
 * <p>5 phút cũng chính là chu kỳ làm mới của cổng công khai (OI-09), nên độ trễ tối đa mà người dùng
 * gặp trên một con số tổng hợp không lớn hơn độ trễ họ vốn đã gặp.
 *
 * <h2>⚠ Hai khoá chống trùng KHÁC nhau, và đó là chủ ý</h2>
 *
 * <p>Lượt 5 phút dùng khoá cố định {@code HYDRO_AGG_REBUILD} — lượt trước còn chạy thì lượt sau
 * nhận lại chính nó, ⛔ không chồng lên. Lượt hằng ngày dùng khoá kèm ngày, để nó ⛔ <b>không</b> bị
 * nuốt vào một lượt drain đang chạy: nếu bị nuốt thì phần <i>cắm lại cờ</i> — toàn bộ lý do nó tồn
 * tại — ⛔ không bao giờ chạy, và triệu chứng là một lưới an toàn <b>trông như</b> đang hoạt động.
 * Đúng hình dạng §10.42: một bản vá làm hệ thống sống sót qua lỗi cũng làm tắt chuông báo lỗi ấy.
 *
 * <p>Hai lượt chạy song song là an toàn: mỗi kỳ chỉ thuộc về lượt {@code DELETE … RETURNING} được
 * hàng cờ bẩn (xem {@code HydroAggRepository.nhanKy}).
 */
@Component
public class HydroAggScheduler {

    /** Tính lại là idempotent và ⛔ không xoá gì ngoài chính hàng nó vừa dựng — thử lại vô hại. */
    private static final short THU_LAI = 3;

    private final JobPort jobs;

    public HydroAggScheduler(JobPort jobs) {
        this.jobs = jobs;
    }

    /**
     * Mỗi 5 phút, giây thứ 20.
     *
     * <p>⚠ Lệch hẳn khỏi giây 45 của poller: đặt trùng giây là hai việc cùng tranh connection ở đúng
     * lúc lượt ingest đang mở giao dịch ghi.
     */
    @Scheduled(cron = "20 */5 * * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void scheduleDrain() {
        jobs.enqueue(new JobRequest(HydroJobTypes.AGG_REBUILD, "{}", HydroJobTypes.AGG_REBUILD, THU_LAI));
    }

    /**
     * 04:45 hằng ngày — sau partition (04:15) và retention (04:30).
     *
     * <p>⚠ Thứ tự ấy quan trọng: retention {@code DROP PARTITION} những tháng quá hạn, và lượt cắm
     * cờ chỉ nhìn hai ngày gần nhất nên ⛔ không bao giờ chạm tới phần vừa bị dọn. Đảo thứ tự thì
     * cũng chưa hỏng, nhưng lúc ấy hai việc đụng cùng cây phân mảnh và một trong hai phải chờ khoá.
     */
    @Scheduled(cron = "0 45 4 * * *", zone = DateTimeUtils.ZONE_VN_ID)
    public void scheduleCamLaiCo() {
        String dedupKey = HydroJobTypes.AGG_REBUILD + ":cam-co:" + LocalDate.now(DateTimeUtils.ZONE_VN);
        jobs.enqueue(new JobRequest(HydroJobTypes.AGG_REBUILD, HydroAggHandler.PAYLOAD_CAM_LAI_CO, dedupKey, THU_LAI));
    }
}
