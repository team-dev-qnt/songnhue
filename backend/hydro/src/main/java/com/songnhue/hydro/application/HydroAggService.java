package com.songnhue.hydro.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.hydro.domain.KyTongHop;
import com.songnhue.hydro.infra.HydroAggRepository;

/**
 * Rút hàng đợi kỳ bẩn và tính lại {@code hydro_agg_daily} — T34.1.
 *
 * <h2>⭐ Một giao dịch cho MỘT kỳ, ⛔ không phải một giao dịch cho cả lượt</h2>
 *
 * <p>Gộp cả lượt drain vào một giao dịch thì một kỳ hỏng làm quay lui toàn bộ những kỳ đã tính
 * đúng, và lượt chạy sau gặp lại y nguyên tình huống ấy — hàng đợi <b>không bao giờ vơi</b>, trong
 * khi mọi thứ vẫn báo "job FAILED, sẽ thử lại". Tách nhỏ thì một kỳ hỏng chỉ giữ lại đúng cờ bẩn
 * của nó.
 *
 * <p>⚠ {@code REQUIRES_NEW} + {@link TransactionTemplate}, ⛔ không phải {@code @Transactional} trên
 * một phương thức rồi gọi từ vòng lặp trong cùng lớp: lời gọi ấy ⛔ không đi qua proxy nên ⛔ không
 * có giao dịch nào — §10.20, dự án đã sập vì đúng chỗ này hai lần.
 *
 * <h2>⛔ Vì sao một kỳ hỏng KHÔNG làm hỏng cả lượt</h2>
 *
 * <p>Lượt drain ghi nhận lỗi rồi <b>đi tiếp</b>. Lý do: cờ bẩn của kỳ hỏng đã quay lui cùng giao
 * dịch của nó, nên nó tự nằm lại hàng đợi; còn dừng cả lượt thì một kỳ hỏng cố định (dữ liệu vi phạm
 * một ràng buộc CHECK, chẳng hạn) sẽ chặn vĩnh viễn mọi kỳ xếp sau nó. Đây là <i>báo cáo trọn vẹn
 * quan trọng hơn dừng sớm</i> (luật 11), áp ở tầng ứng dụng.
 *
 * <p>⚠ Nhưng lượt drain vẫn phải <b>hỏng</b> nếu có kỳ hỏng, nếu không việc nền báo SUCCEEDED cho
 * một việc chưa làm xong — đúng thứ hợp đồng {@code JobHandler} cấm. Nên đếm và ném ở cuối.
 */
@Service
public class HydroAggService {

    private static final Logger log = LoggerFactory.getLogger(HydroAggService.class);

    /**
     * Số kỳ tối đa cho một lượt drain.
     *
     * <p>19 điểm đo × 2 loại chỉ số = 38 kỳ cho ngày hôm nay; lượt nạp lần đầu (§9 của migration) có
     * thêm mỗi ngày lịch sử một bộ như thế. Trần này giữ cho một lượt job ⛔ không chạy hàng chục
     * phút và ⛔ không giữ connection quá lâu; phần còn lại rơi sang lượt kế tiếp 5 phút sau, và
     * hàng đợi thì ⛔ không mất gì.
     */
    static final int TRAN_MOI_LUOT = 500;

    private final HydroAggRepository kho;
    private final TransactionTemplate giaoDichKy;

    public HydroAggService(HydroAggRepository kho, PlatformTransactionManager transactionManager) {
        this.kho = kho;
        this.giaoDichKy = new TransactionTemplate(transactionManager);
        this.giaoDichKy.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Rút hàng đợi một lượt.
     *
     * @return số kỳ đã tính lại thành công
     * @throws IllegalStateException khi có ít nhất một kỳ hỏng — việc nền phải hiện FAILED
     */
    public int chayMotLuot() {
        List<KyTongHop> cho = kho.kyBan(TRAN_MOI_LUOT);
        if (cho.isEmpty()) {
            return 0;
        }

        int xong = 0;
        int bo = 0;
        KyTongHop kyHongDauTien = null;
        RuntimeException loiDauTien = null;

        for (KyTongHop ky : cho) {
            try {
                Boolean daTinh = giaoDichKy.execute(tx -> {
                    // ⚠⚠ NHẬN TRƯỚC, TÍNH SAU — thứ tự này là bắt buộc, xem javadoc
                    //    HydroAggRepository. Đảo lại là mở một cửa sổ mất số đo IM LẶNG.
                    if (!kho.nhanKy(ky)) {
                        return false;
                    }
                    kho.tinhLai(ky);
                    return true;
                });
                if (Boolean.TRUE.equals(daTinh)) {
                    xong++;
                } else {
                    bo++;
                }
            } catch (RuntimeException e) {
                if (kyHongDauTien == null) {
                    kyHongDauTien = ky;
                    loiDauTien = e;
                }
                log.error("Tính lại kỳ tổng hợp {} hỏng — cờ bẩn giữ nguyên, sẽ thử lại", ky.khoa(), e);
            }
        }

        int conLai = kho.demKyBan();
        log.info("Tổng hợp ngày: {} kỳ đã tính, {} kỳ người khác đã nhận, {} kỳ còn trong hàng đợi", xong, bo, conLai);

        if (kyHongDauTien != null) {
            throw new IllegalStateException(
                    "Tính lại kỳ tổng hợp hỏng, kỳ đầu tiên: " + kyHongDauTien.khoa(), loiDauTien);
        }
        return xong;
    }

    /** Lưới an toàn hằng ngày — cắm lại cờ cho hai ngày gần nhất. */
    public int camLaiCoGanDay() {
        int so = kho.camLaiCoGanDay();
        log.info("Cắm lại cờ tổng hợp cho {} kỳ của hai ngày gần nhất", so);
        return so;
    }

    /** Số kỳ đang chờ — màn hình theo dõi và bài kiểm đọc con số này. */
    public int soKyDangCho() {
        return kho.demKyBan();
    }
}
