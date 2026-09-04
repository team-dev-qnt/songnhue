package com.songnhue.hydro.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;

/**
 * Việc nền tính lại bảng tổng hợp ngày — T34.1.
 *
 * <h2>⛔ Handler này KHÔNG mang {@code @Transactional}</h2>
 *
 * <p>Khác {@code HydroPartitionHandler} và {@code HydroRetentionHandler}: mỗi kỳ tổng hợp phải là
 * <b>một giao dịch riêng</b> (xem {@link HydroAggService}), nên một giao dịch bọc quanh cả lượt
 * drain sẽ nuốt mất sự tách bạch ấy — {@code REQUIRES_NEW} vẫn tách được, nhưng giao dịch ngoài khi
 * ấy chỉ giữ một connection nhàn rỗi suốt lượt chạy mà ⛔ không bảo vệ điều gì.
 *
 * <h2>Hai đường vào, một handler</h2>
 *
 * <ul>
 *   <li><b>5 phút/lượt</b> — rút hàng đợi cờ bẩn. Đây là đường chính; trigger trên
 *       {@code hydro_readings} cắm cờ nên hàng đợi luôn phản ánh đúng những gì vừa đổi.
 *   <li><b>Hằng ngày</b> — payload {@code {"camLaiCo":true}}: cắm lại cờ cho hai ngày gần nhất
 *       <i>trước</i> khi rút. Lưới an toàn cho trường hợp job ⛔ không chạy suốt nhiều ngày.
 * </ul>
 *
 * <p>⚠ Đọc cờ bằng phép so chuỗi thô chứ ⛔ không nạp {@code ObjectMapper}: payload ở đây do chính
 * {@link HydroAggScheduler} sinh ra, cố định hai dạng, và một phụ thuộc thêm chỉ để đọc một
 * boolean là chỗ để hỏng thêm. ⚠ Nếu ngày nào payload này nhận tham số từ người dùng thì
 * <b>phải</b> đổi sang parse thật — ⛔ đừng nới phép so chuỗi.
 */
@Component
public class HydroAggHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(HydroAggHandler.class);

    /** Payload của lượt hằng ngày. Khớp đúng chuỗi mà {@link HydroAggScheduler} phát ra. */
    static final String PAYLOAD_CAM_LAI_CO = "{\"camLaiCo\":true}";

    private final HydroAggService dichVu;

    public HydroAggHandler(HydroAggService dichVu) {
        this.dichVu = dichVu;
    }

    @Override
    public String jobType() {
        return HydroJobTypes.AGG_REBUILD;
    }

    @Override
    public void handle(JobContext context) {
        if (PAYLOAD_CAM_LAI_CO.equals(context.payload())) {
            dichVu.camLaiCoGanDay();
        }
        context.progress(10);

        int xong = dichVu.chayMotLuot();
        context.progress(100);
        log.debug("Lượt tổng hợp ngày xong: {} kỳ", xong);
    }
}
