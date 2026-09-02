package com.songnhue.hydro.application;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.util.DateTimeUtils;
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
 * <h2>⛔ Xoá là không phục hồi được — nguồn không có API lịch sử</h2>
 *
 * <p>Hàm trong CSDL có <b>sàn an toàn 7 ngày</b> và ném ngoại lệ với mọi mốc cắt mới hơn thế. Đó là
 * lưới chặn cho đúng một loại lỗi rất dễ mắc: nhầm đơn vị (ngày ↔ tháng ↔ năm) khi đọc tham số. Với
 * sàn ấy, một lỗi như vậy chỉ làm job đỏ chứ không xoá mất dữ liệu tuần này.
 *
 * <p>⚠ Vì xoá theo <b>tháng trọn vẹn</b> (DROP PARTITION), hạn lưu thực tế luôn dài hơn con số cấu
 * hình — tối đa thêm một tháng. Ghi vào log mỗi lượt để không ai đọc con số 90 ngày rồi trông đợi
 * đúng 90 ngày.
 */
@Component
public class HydroRetentionHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(HydroRetentionHandler.class);

    private final HydroMaintenanceRepository repository;
    private final HydroSettings settings;
    private final Clock clock;

    /**
     * ⚠ {@code @Autowired} tường minh vì lớp có <b>hai</b> hàm dựng. Không có nó, Spring không chọn
     * hàm nào cả và đi tìm hàm dựng không tham số — lỗi hiện ra là
     * {@code No default constructor found}, một thông báo <b>không nhắc gì</b> tới nguyên nhân thật.
     */
    @Autowired
    public HydroRetentionHandler(HydroMaintenanceRepository repository, HydroSettings settings) {
        // ⛔ Không đọc đồng hồ theo múi giờ máy chủ (ArchUnit canh). Hạn lưu tính theo ngày hành
        //   chính Việt Nam: "xoá dữ liệu trước ngày X" là một câu nói của người, không phải của UTC.
        this(repository, settings, Clock.system(DateTimeUtils.ZONE_VN));
    }

    HydroRetentionHandler(HydroMaintenanceRepository repository, HydroSettings settings, Clock clock) {
        this.repository = repository;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public String jobType() {
        return HydroJobTypes.RETENTION;
    }

    /**
     * ⚠ Một lần thử là đủ.
     *
     * <p>Đây là việc XOÁ không phục hồi được và nó có ngày mai. Thử lại tự động một việc xoá là cách
     * một lỗi cấu hình biến thành nhiều lượt xoá trong cùng một đêm.
     */
    @Override
    public short maxAttempts() {
        return 1;
    }

    @Override
    @Transactional
    public void handle(JobContext context) {
        LocalDate homNay = LocalDate.now(clock);

        int soNgayGiuRaw = settings.soNgayGiuRawLog();
        LocalDate cutoffRaw = homNay.minusDays(soNgayGiuRaw);
        int rawDropped = repository.dropPartitionsBefore("hydro_raw_logs", cutoffRaw);
        int syncDeleted = repository.purgeSyncLogsBefore(
                cutoffRaw.atStartOfDay(DateTimeUtils.ZONE_VN).toInstant());

        int soNamGiuDuLieu = settings.soNamGiuDuLieu();
        LocalDate cutoffDuLieu = homNay.minusYears(soNamGiuDuLieu);
        int readingsDropped = repository.dropPartitionsBefore("hydro_readings", cutoffDuLieu);
        int unmappedDeleted = repository.purgeUnmappedBefore(
                cutoffDuLieu.atStartOfDay(DateTimeUtils.ZONE_VN).toInstant());

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
