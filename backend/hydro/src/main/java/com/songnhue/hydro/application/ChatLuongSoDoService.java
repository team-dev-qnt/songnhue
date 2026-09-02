package com.songnhue.hydro.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.ChanDoanChatLuong;
import com.songnhue.hydro.domain.KhoaSoDo;
import com.songnhue.hydro.domain.PhanLoaiChatLuong;
import com.songnhue.hydro.domain.QuyTacNghiNgo;
import com.songnhue.hydro.domain.ReadingRow;
import com.songnhue.hydro.domain.SoDoTruoc;
import com.songnhue.hydro.infra.PollerRepository;

/**
 * Vế chất lượng của <b>đường ingest</b> — T32.1 · T32.3.
 *
 * <p>Tách khỏi {@code TelemetryIngestService} vì đây là một câu hỏi khác: lớp kia trả lời <i>"lấy về
 * và lưu được không"</i>, lớp này trả lời <i>"con số vừa lấy về có tin được không"</i>. Chúng có
 * nhịp đổi khác nhau — quy tắc nghi ngờ sẽ được chỉnh nhiều lần khi đã có chuỗi số đo thật, còn
 * luồng lấy dữ liệu thì không.
 *
 * <p>⛔ Lớp này ⛔ <b>không mở giao dịch</b> và ⛔ không ghi CSDL — nó chỉ đọc, kết luận, và báo.
 */
@Service
public class ChatLuongSoDoService {

    private static final Logger log = LoggerFactory.getLogger(ChatLuongSoDoService.class);

    /** Mã sự kiện thông báo — khớp nhãn ở giao diện thông báo. */
    static final String SU_KIEN_NGHI_NGO = "HYDRO_READING_SUSPECT";

    private final HydroSettings settings;
    private final PollerRepository poller;
    private final NotificationPort notifications;

    public ChatLuongSoDoService(HydroSettings settings, PollerRepository poller, NotificationPort notifications) {
        this.settings = settings;
        this.poller = poller;
        this.notifications = notifications;
    }

    /**
     * ⭐⭐ Mở một <b>phiên phân loại</b> — chốt quy tắc và mốc so sánh <b>một lần</b> cho cả lượt.
     *
     * <p>Đây là chỗ một bất biến được biến từ <i>lời dặn</i> thành <i>hình dạng</i> (luật 12): 28 số
     * đo của cùng một khung phải được đánh giá trên cùng một mốc. Nếu mỗi dòng tự đi đọc lại
     * {@code hydro_latest} thì lượt ghi của chính vòng lặp này làm dịch mốc, và hai bản ghi giống
     * hệt nhau ra hai kết luận khác nhau <b>tuỳ thứ tự</b> — đúng hình dạng §10.13 (<i>cột dẫn xuất
     * trộn hai nguồn khác chiều lọc thì kết quả phụ thuộc ai bấm F5 sau cùng</i>). Với một
     * {@link Phien} thì không có API nào để đọc lại.
     *
     * @param loaiChiSoId id loại chỉ số đang ingest — dùng để lấy mốc so sánh
     * @param maLoaiChiSo mã nghiệp vụ của loại chỉ số ấy — khoá tra trong cấu hình quy tắc
     */
    public Phien moPhien(long loaiChiSoId, String maLoaiChiSo) {
        return new Phien(settings.quyTacNghiNgo().cho(maLoaiChiSo), poller.soDoHopLeGanNhat(loaiChiSoId));
    }

    /**
     * Quy tắc và mốc so sánh đã chốt của <b>một</b> lượt ingest.
     *
     * <p>⛔ Không có phương thức nào nạp lại dữ liệu — xem {@link #moPhien}.
     */
    public record Phien(QuyTacNghiNgo quyTac, Map<Long, SoDoTruoc> mocSoSanh) {

        /**
         * Phân loại một số đo của điểm đo {@code stationId}.
         *
         * <p>⚠ {@code stationId} <b>chỉ dùng để TRA MỐC CỦA CHÍNH NÓ</b> trong {@link #mocSoSanh},
         * ⛔ không đi tiếp vào {@link PhanLoaiChatLuong#danhGia} — hàm ấy không có tham số nào nhận
         * nó. Đó là cách cấm lệnh T32.2 (⛔ cấm so chéo hai điểm đo) được ép bằng kiểu dữ liệu.
         *
         * <p>⚠ Điểm đo chưa từng có bản hợp lệ nào ⇒ {@code null}, và đó là câu trả lời đúng: "chưa
         * có mốc để so" khác hẳn "mốc trước là 0".
         */
        public ChanDoanChatLuong danhGia(long stationId, BigDecimal giaTri, Instant mocDo) {
            return PhanLoaiChatLuong.danhGia(giaTri, mocDo, quyTac, mocSoSanh.get(stationId));
        }
    }

    /**
     * ⭐ Đánh thức người duyệt khi có số đo nghi ngờ <b>VỪA GHI MỚI</b> — T32.3.
     *
     * <h2>⚠⚠ Lọc theo {@code daGhi}, ⛔ không theo {@code soDo}</h2>
     *
     * <p>Poller chạy 2 phút/lần trên nguồn cập nhật 10 phút/lần ⇒ <b>4/5 lượt gọi trả về đúng dữ
     * liệu cũ</b>. Phát thông báo theo <i>những gì nhận được</i> nghĩa là một bản ghi đáng ngờ duy
     * nhất đánh thức người trực <b>5 lần mỗi khung</b> cho tới khi có người xử lý — và một chuông
     * kêu sai nhịp là một chuông sẽ bị tắt. Đây chính là lý do {@code HydroTimeSeriesWriter} phải
     * trả về <i>khoá của dòng đã ghi</i> chứ không chỉ một con số.
     *
     * <p>⛔ Một thông báo cho cả lô, ⛔ không phải mỗi bản ghi một thông báo: một cảm biến hỏng làm
     * cả 19 trạm vượt vỏ bọc cùng lúc là chuyện có thật, và 19 thư trong một phút cũng là một cách
     * làm hỏng chuông.
     *
     * <p>⚠ Nhắm bằng quyền {@code hyd:measurement:review} — đúng những người bấm được nút Duyệt /
     * Loại bỏ. ⛔ Không gửi theo đơn vị: {@code stations.org_unit_id} còn NULL cả 19 dòng (OI-05
     * chưa chốt), nên gửi theo đơn vị hôm nay là gửi cho <b>tập rỗng</b> — một thông báo không ai
     * nhận, xanh trọn vẹn (luật 7).
     *
     * @param daGhi khoá của những dòng lượt ghi vừa rồi <b>thật sự</b> tạo mới
     */
    public void baoNguoiDuyet(ApiSource nguon, List<ReadingRow> soDo, List<KhoaSoDo> daGhi) {
        if (daGhi.isEmpty()) {
            return;
        }
        Set<KhoaSoDo> khoaMoi = new HashSet<>(daGhi);
        List<ReadingRow> dangNgo = soDo.stream()
                .filter(r -> r.chanDoan().dangNgo())
                .filter(r -> khoaMoi.contains(new KhoaSoDo(r.stationId(), r.measuredAt())))
                .toList();
        if (dangNgo.isEmpty()) {
            return;
        }

        // Lý do của dòng đầu đi vào thân thư: một câu cụ thể ("giá trị 493 ngoài khoảng [-10 … 30]")
        // nói được nhiều hơn "có N bản ghi đáng ngờ" — người đọc biết ngay là cảm biến hay vận hành.
        ReadingRow mau = dangNgo.get(0);
        String them = dangNgo.size() > 1 ? " (và %d bản ghi khác)".formatted(dangNgo.size() - 1) : "";
        notifications.notify(NotifyRequest.targeted(
                SU_KIEN_NGHI_NGO,
                "%d số đo thuỷ văn cần duyệt (nguồn %s)".formatted(dangNgo.size(), nguon.getCode()),
                mau.chanDoan().moTa() + them
                        + ". Vào Thuỷ văn › Dữ liệu nghi ngờ để duyệt hoặc loại bỏ. ⚠ Các bản ghi này ĐÃ ĐƯỢC "
                        + "GHI và vẫn nằm trong CSDL, nhưng bị loại khỏi báo cáo, biểu đồ và cảnh báo ngưỡng "
                        + "cho tới khi có người duyệt.",
                NotifySeverity.WARNING,
                "hyd:measurement:review",
                List.of()));

        log.warn(
                "⚠ Nguồn {}: {} số đo mới bị đánh dấu NGHI_NGO. Ví dụ: {}",
                nguon.getCode(),
                dangNgo.size(),
                mau.chanDoan().moTa());
    }
}
