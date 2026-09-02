package com.songnhue.hydro.application;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.hydro.infra.HydroMaintenanceRepository;

/**
 * Dọn dữ liệu thuỷ văn quá hạn lưu — T29.7.
 *
 * <h2>⭐ Hai hạn lưu KHÁC NHAU, và chênh nhau tới hai bậc độ lớn</h2>
 *
 * <ul>
 *   <li>{@code hydro.raw-retention-days} (mặc định <b>90 ngày</b>) — nguyên văn response và nhật ký
 *       đồng bộ. Raw chỉ có giá trị khi cần đối chiếu <i>"số này parse từ đâu ra"</i> hoặc khi nguồn
 *       đổi định dạng; cả hai đều là việc của vài tuần gần nhất. Giữ 5 năm là giữ ~2 GB văn bản để
 *       trả lời một câu hỏi không ai hỏi, trong khi ngân sách bộ nhớ VPS đã tính chặt.
 *   <li>{@code hydro.retention-years} (mặc định <b>5 năm</b>, chốt D5) — số đo và cả những số đo của
 *       mã chưa khai. ⚠ Nhóm sau dùng hạn lưu <b>của số đo</b> chứ không của raw: chúng là số đo
 *       thật của trạm thật, chỉ thiếu mỗi phần khai báo.
 * </ul>
 *
 * <h2>⚠⚠ MỘT QUYẾT ĐỊNH, MỘT CÁI ĐỒNG HỒ — đọc trước khi "dọn dẹp" chỗ này</h2>
 *
 * <p>Mốc cắt lấy từ {@link HydroMaintenanceRepository#ngayHienTai()}, tức {@code current_date} của
 * <b>chính phiên CSDL</b> sắp phán xét nó. ⛔ Tuyệt đối <b>không</b> quay lại
 * {@code LocalDate.now(...)}: bản đầu làm đúng như vậy và nó hỏng <b>tất định mọi đêm</b> —
 * container chạy {@code -Duser.timezone=UTC} còn job chạy 04:30 giờ VN = 21:30 UTC ngày hôm trước,
 * nên ngày phía Java luôn lớn hơn {@code current_date} đúng một. Đặt hạn lưu raw bằng <b>7</b> —
 * biên dưới mà chính migration khai là hợp lệ — cho mốc cắt {@code current_date - 6}, sàn an toàn
 * 7 ngày từ chối, và vì đó là lời gọi đầu tiên nên <b>toàn bộ phần dọn còn lại không chạy</b>.
 *
 * <p>Bộ test cũ về nguyên tắc mù trước lớp lỗi ấy: bài đơn vị mock chính lớp chạm tới sàn (luật 4),
 * còn trong JVM test thì đồng hồ Java và phiên CSDL cùng một múi giờ nên độ lệch không tồn tại.
 * Thứ bắt được nó phải là một lượt gọi thật <b>ở đúng biên</b> — xem
 * {@code HydroRetentionTest.bienDuoiCuaUiPhaiDiQuaDuoc}.
 *
 * <h2>⛔ Xoá là không phục hồi được — nguồn không có API lịch sử</h2>
 *
 * <p>Hàm trong CSDL có <b>sàn an toàn 7 ngày</b> và ném ngoại lệ với mọi mốc cắt mới hơn thế. Đó là
 * lưới chặn cho đúng một loại lỗi rất dễ mắc: nhầm đơn vị (ngày ↔ tháng ↔ năm) khi đọc tham số.
 *
 * <p>⚠ Vì xoá theo <b>tháng trọn vẹn</b> (DROP PARTITION), hạn lưu thực tế luôn dài hơn con số cấu
 * hình — tối đa thêm một tháng. Ghi vào log mỗi lượt để không ai đọc con số 90 ngày rồi trông đợi
 * đúng 90 ngày.
 *
 * <h2>⛔ Số lần thử KHÔNG khai ở đây</h2>
 *
 * <p>{@link JobHandler#maxAttempts()} <b>không có người đọc trong toàn kho</b>: {@code JobWorker}
 * lấy {@code max_attempts} từ cột của bảng {@code jobs}, mà cột ấy do
 * {@code JobService.enqueue(…, request.maxAttempts())} ghi — tức từ {@code JobRequest} của <i>nơi
 * đặt việc</i>. Ghi đè phương thức ấy ở đây là khai một con số không điều khiển gì (luật 15), và
 * tệ hơn: nó <b>trông như</b> đã chặn việc thử lại một thao tác XOÁ. Con số thật đặt ở
 * {@link HydroMaintenanceScheduler}, nơi nó có hiệu lực.
 */
@Component
public class HydroRetentionHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(HydroRetentionHandler.class);

    private final HydroMaintenanceRepository repository;
    private final HydroSettings settings;

    public HydroRetentionHandler(HydroMaintenanceRepository repository, HydroSettings settings) {
        this.repository = repository;
        this.settings = settings;
    }

    @Override
    public String jobType() {
        return HydroJobTypes.RETENTION;
    }

    @Override
    @Transactional
    public void handle(JobContext context) {
        LocalDate homNay = repository.ngayHienTai();

        int soNgayGiuRaw = settings.soNgayGiuRawLog();
        LocalDate cutoffRaw = homNay.minusDays(soNgayGiuRaw);
        int rawDropped = repository.dropPartitionsBefore("hydro_raw_logs", cutoffRaw);
        int syncDeleted = repository.purgeSyncLogsBefore(cutoffRaw);

        int soNamGiuDuLieu = settings.soNamGiuDuLieu();
        LocalDate cutoffDuLieu = homNay.minusYears(soNamGiuDuLieu);
        int readingsDropped = repository.dropPartitionsBefore("hydro_readings", cutoffDuLieu);
        int unmappedDeleted = repository.purgeUnmappedBefore(cutoffDuLieu);

        log.info(
                "Dọn dữ liệu thuỷ văn quá hạn — raw: {} partition (giữ {} ngày, mốc {}), nhật ký đồng bộ: {} dòng; "
                        + "số đo: {} partition (giữ {} năm, mốc {}), mã chưa khai: {} dòng. "
                        + "⚠ Xoá theo tháng trọn vẹn nên hạn lưu thực tế dài hơn con số cấu hình tối đa một tháng.",
                rawDropped,
                soNgayGiuRaw,
                cutoffRaw,
                syncDeleted,
                readingsDropped,
                soNamGiuDuLieu,
                cutoffDuLieu,
                unmappedDeleted);
    }
}
