package com.songnhue.hydro.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.hydro.domain.BoLocNhatKy;
import com.songnhue.hydro.domain.LuotDongBo;
import com.songnhue.hydro.domain.MaLaTongHop;
import com.songnhue.hydro.domain.TongHopDongBo;
import com.songnhue.hydro.infra.SyncLogQueryRepository;
import com.songnhue.hydro.infra.UnmappedReadingRepository;

/**
 * Hai màn hình chẩn đoán của MOD-03 — <i>Nhật ký đồng bộ</i> (M3.16) và <i>Mã lạ từ nguồn</i>
 * (T31.13).
 *
 * <p>Đặt chung một service vì chúng trả lời <b>hai nửa của một câu hỏi</b>: nhật ký nói <i>lượt gọi
 * có về không</i>, mã lạ nói <i>số đo về rồi thì rơi đi đâu</i>. Người mở màn hình này thường mở nốt
 * màn hình kia trong cùng một phút.
 *
 * <p>⛔ Không đường ghi nào ở đây. Mọi phương thức {@code readOnly} — hai bảng bên dưới có đúng một
 * nơi ghi mỗi bảng và điều đó là một bảo đảm, không phải một sự trùng hợp.
 */
@Service
public class HydroDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(HydroDiagnosticsService.class);

    /** Cửa sổ mặc định của dải tóm tắt — một ca trực cộng một đêm. */
    public static final int SO_GIO_MAC_DINH = 24;

    /**
     * ⚠ Trần cửa sổ tóm tắt. 30 ngày × 720 lượt/ngày ≈ <b>21.600 dòng</b> cho một câu gộp — vẫn rẻ vì
     * nó chỉ đọc chỉ mục thời gian, nhưng một tham số không có trần là một tham số ai đó sẽ đặt bằng
     * số năm.
     */
    public static final int SO_GIO_TOI_DA = 24 * 30;

    private final SyncLogQueryRepository nhatKy;
    private final UnmappedReadingRepository maLa;

    public HydroDiagnosticsService(SyncLogQueryRepository nhatKy, UnmappedReadingRepository maLa) {
        this.nhatKy = nhatKy;
        this.maLa = maLa;
    }

    /**
     * Một trang nhật ký, mới nhất trước.
     *
     * <p>⚠ Đếm và lấy trang là <b>hai lượt truy vấn</b> nên về nguyên tắc có thể lệch nhau khi poller
     * ghi xen vào giữa. Chấp nhận: dòng mới luôn nằm ở <b>đầu</b> thứ tự {@code started_at DESC}, nên
     * hệ quả tệ nhất là thanh phân trang nói 721 trong khi trang cuối có 720 — ⛔ không phải một dòng
     * bị bỏ sót ở giữa. Đổi sang một lượt {@code count(*) OVER ()} là trả cái giá ấy trên <b>mọi</b>
     * lượt tải để đỡ một sai lệch thoáng qua.
     */
    @Transactional(readOnly = true)
    public Page<LuotDongBo> nhatKy(BoLocNhatKy loc, Pageable trang) {
        long tong = nhatKy.dem(loc);
        List<LuotDongBo> dong = tong == 0 ? List.of() : nhatKy.trang(loc, trang.getOffset(), trang.getPageSize());
        return new PageImpl<>(dong, trang, tong);
    }

    /**
     * Cửa sổ đo <b>đã kẹp</b> — hàm công khai để nhãn trên màn hình và con số thật sự được đo đến từ
     * <b>cùng một phép tính</b>.
     *
     * <p>⚠ Đây là hình dạng A3 ở dạng nhỏ nhất: một tham số bị kẹp trong im lặng rồi giao diện đi in
     * lại con số người dùng gửi lên. Gửi {@code soGio=8760} mà nhãn viết "8760 giờ qua" trong khi số
     * liệu chỉ nói về 720 giờ là một màn hình <b>nói dối một cách có thiện chí</b>.
     */
    public static int kepSoGio(Integer soGio) {
        return soGio == null || soGio < 1 ? SO_GIO_MAC_DINH : Math.min(soGio, SO_GIO_TOI_DA);
    }

    /**
     * @param soGio cửa sổ đo, tự kẹp qua {@link #kepSoGio(Integer)}
     */
    @Transactional(readOnly = true)
    public TongHopDongBo tongHop(Integer soGio) {
        return nhatKy.tongHop(Instant.now().minus(Duration.ofHours(kepSoGio(soGio))));
    }

    /**
     * Toàn bộ mã nguồn chưa khai, gộp theo mã.
     *
     * <p>⚠ Vượt trần thì <b>nói ra</b> — xem {@link UnmappedReadingRepository#TRAN_CANH_BAO}. Danh
     * sách vẫn trả về đủ: cắt bớt trong im lặng là đúng hình dạng A3, còn một dòng WARN thì ít nhất
     * có người đọc được.
     */
    @Transactional(readOnly = true)
    public List<MaLaTongHop> maLa() {
        List<MaLaTongHop> ket = maLa.tongHopTheoMa();
        if (ket.size() > UnmappedReadingRepository.TRAN_CANH_BAO) {
            log.warn(
                    "Danh sách mã lạ đã {} mã (> {}) — màn hình không phân trang sẽ khó đọc; đã tới lúc "
                            + "phân trang câu tổng hợp",
                    ket.size(),
                    UnmappedReadingRepository.TRAN_CANH_BAO);
        }
        return ket;
    }
}
