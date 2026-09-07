package com.songnhue.hydro.application;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.hydro.domain.StationDisplayStatus;
import com.songnhue.hydro.domain.TinHieuDiemDo;
import com.songnhue.hydro.infra.PollerRepository;

/**
 * Rà <b>mất tín hiệu</b> theo từng điểm đo, và <b>sự vắng mặt</b> của cả đường ingest — T31.8 · T31.9.
 *
 * <h2>⭐ Hai câu hỏi khác nhau, và chúng cần hai cơ chế</h2>
 *
 * <ol>
 *   <li><b>Trạm nào im lặng</b> — {@link StationDisplayStatus#suyRa} trên {@code hydro_latest}. Đây
 *       là vế đọc còn thiếu của T28.20 và T28.36: hàm suy trạng thái và khoá
 *       {@code hydro.station.signal-loss-frames} đã có từ WS-28 mà <b>chưa ai gọi</b>. Luật 15 nói
 *       thẳng: tới WS-31 mà vẫn không ai gọi thì phải gỡ hàm. Ở đây nối vế đọc, ⛔ không gỡ.
 *   <li>⭐⭐ <b>Cả hệ chưa từng ingest được lần nào</b> — đo bằng {@code sync_logs}, ⛔ không bằng độ
 *       tươi dữ liệu. {@code HydroFreshnessRegistrar} cố ý <b>im lặng</b> khi bảng còn rỗng (đăng ký
 *       muộn), để một cảnh báo critical không kêu suốt quãng WS-29 → WS-31; nó ghi rõ nợ ấy và giao
 *       cho T31.9. Đây là chỗ trả nợ: hỏi <i>"đã có lượt chạy nào thành công chưa"</i>, một câu hỏi
 *       mà một chỉ số về <i>dữ liệu</i> về nguyên tắc không trả lời được.
 * </ol>
 *
 * <h2>⭐ Cảnh báo phát ở CHUYỂN TRẠNG THÁI, ⛔ không phát theo trạng thái</h2>
 *
 * <p>Job này chạy mỗi {@code 5} phút. "Đang mất tín hiệu thì báo" nghĩa là <b>288 thông báo mỗi
 * ngày</b> cho một trạm hỏng — và một chuông kêu liên tục vì một lý do ai cũng biết là một chuông sẽ
 * bị tắt, rồi vẫn tắt vào ngày trạm khác hỏng thật (§10.42). Nên: chỉ báo trạm <b>vừa mới</b> mất, và
 * báo lại khi <b>vừa trở lại</b>. Cùng khuôn {@link ApiSourceHealthService}.
 *
 * <p>⚠ Một thông báo <b>gộp</b> cho cả lượt, ⛔ không phải một thông báo mỗi trạm: nguồn hỏng thì cả
 * 19 trạm cùng im, và 19 email cùng lúc là cách chắc chắn nhất để không ai đọc email thứ hai.
 *
 * <h2>⚠ Trí nhớ nằm trong tiến trình, và điều đó phải nói ra</h2>
 *
 * <p>Tập "đã báo" là một trường, ⛔ không phải một cột. Hệ quả: <b>khởi động lại thì báo lại</b> các
 * trạm đang im lặng. Đó là đánh đổi có chủ đích, và vế được chọn là vế ồn hơn: sau một lượt deploy,
 * người trực <i>nên</i> được nói cho biết hiện có những gì đang hỏng. Một cột trạng thái trong CSDL
 * sẽ tránh được tiếng ồn ấy nhưng mở lại đúng cái bẫy mà {@link StationDisplayStatus} đã từ chối —
 * người ghi duy nhất là job này, nên job chết là trạng thái đóng băng ở "bình thường".
 *
 * <p>⚠ Lên ≥2 node thì mỗi node có trí nhớ riêng ⇒ thông báo nhân đôi. Khi ấy bật ShedLock cho lượt
 * đặt việc (khung đã có sẵn, {@code app.shedlock-enabled}) — ⛔ không phải chuyển tập này xuống CSDL.
 *
 * <h2>⚠ Người nhận: theo QUYỀN, và đây là hệ quả trực tiếp của OI-05</h2>
 *
 * <p>Đúng thiết kế (G11) thì cảnh báo của một điểm đo phải tới người phụ trách <b>đơn vị</b> của nó.
 * Nhưng {@code stations.org_unit_id} đang NULL cả 19 dòng vì Công ty chưa chốt 7 hay 8 Xí nghiệp
 * (OI-05) — gửi theo đơn vị hôm nay là gửi vào <b>tập rỗng</b>, tức là một cảnh báo không tới ai mà
 * mọi bài kiểm vẫn xanh (luật 7). ⇒ tạm gửi theo quyền quản lý điểm đo. ⬜ Đổi lại khi OI-05 chốt.
 */
@Component
public class HydroSignalLossHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(HydroSignalLossHandler.class);

    static final String SU_KIEN_MAT = "HYDRO_STATION_SIGNAL_LOST";
    static final String SU_KIEN_TRO_LAI = "HYDRO_STATION_SIGNAL_BACK";
    static final String SU_KIEN_IM_LANG = "HYDRO_INGEST_SILENT";
    static final String SU_KIEN_INGEST_TRO_LAI = "HYDRO_INGEST_BACK";

    /** ⚠ Người có quyền sửa hồ sơ điểm đo — xem khối OI-05 ở javadoc lớp. */
    static final String QUYEN_TRAM = "hyd:station:manage";

    /** Người cấu hình nguồn — họ là người xử lý được việc "cả đường ingest im lặng". */
    static final String QUYEN_NGUON = "hyd:api-source:manage";

    /** Liệt kê tối đa ngần này mã trong một thông báo; phần còn lại nói bằng con số. */
    private static final int TRAN_LIET_KE = 20;

    private final PollerRepository poller;
    private final HydroSettings settings;
    private final NotificationPort notifications;

    /** Trạm đã báo mất tín hiệu — xem khối "trí nhớ nằm trong tiến trình". */
    private final AtomicReference<Set<Long>> daBaoMat = new AtomicReference<>(Set.of());

    /** Đã báo "cả đường ingest im lặng" chưa — chống réo mỗi 5 phút. */
    private final AtomicBoolean daBaoImLang = new AtomicBoolean(false);

    public HydroSignalLossHandler(PollerRepository poller, HydroSettings settings, NotificationPort notifications) {
        this.poller = poller;
        this.settings = settings;
        this.notifications = notifications;
    }

    @Override
    public String jobType() {
        return HydroJobTypes.SIGNAL_LOSS;
    }

    /**
     * ⛔ Không {@code @Transactional}: bốn truy vấn đọc độc lập và không có lượt ghi nào. Mở một giao
     * dịch chỉ để đọc là giữ một kết nối lâu hơn cần, và lớp này chạy mỗi 5 phút suốt đời hệ thống.
     */
    @Override
    public void handle(JobContext context) {
        Duration khung = settings.khungNguon();
        int soKhung = settings.soKhungMatTinHieu();
        Instant bayGio = Instant.now();

        raTungTram(bayGio, khung, soKhung);
        raCaDuongIngest(bayGio, khung, soKhung);
    }

    /** T31.8 — trạm nào vừa mất tín hiệu, trạm nào vừa trở lại. */
    private void raTungTram(Instant bayGio, Duration khung, int soKhung) {
        List<TinHieuDiemDo> tinHieu = poller.tinHieuDiemDo();
        List<TinHieuDiemDo> matTinHieu = tinHieu.stream()
                .filter(t -> t.trangThai(bayGio, khung, soKhung) == StationDisplayStatus.MAT_TIN_HIEU)
                .toList();
        long chuaCoDuLieu = tinHieu.stream()
                .filter(t -> t.trangThai(bayGio, khung, soKhung) == StationDisplayStatus.CHUA_CO_DU_LIEU)
                .count();

        Set<Long> dangMat = matTinHieu.stream().map(TinHieuDiemDo::stationId).collect(toLinkedSet());
        Set<Long> truoc = daBaoMat.getAndSet(Set.copyOf(dangMat));
        // ⚠ "Mới mất" lọc theo ID rồi mới lấy mã, ⛔ không ghép hai danh sách song song theo chỉ số:
        //   hai danh sách phải cùng thứ tự là một bất biến không ai canh, và nó sẽ vỡ đúng vào ngày
        //   có người thêm một bộ lọc vào một trong hai.
        List<String> moiMat = matTinHieu.stream()
                .filter(t -> !truoc.contains(t.stationId()))
                .map(TinHieuDiemDo::code)
                .toList();
        long soTroLai = truoc.stream().filter(id -> !dangMat.contains(id)).count();

        log.info(
                "Rà tín hiệu điểm đo: {} trạm, {} mất tín hiệu ({} mới), {} chưa có dữ liệu, {} vừa trở lại "
                        + "(ngưỡng {} khung × {})",
                tinHieu.size(),
                dangMat.size(),
                moiMat.size(),
                chuaCoDuLieu,
                soTroLai,
                soKhung,
                khung);

        if (!moiMat.isEmpty()) {
            notifications.notify(NotifyRequest.targeted(
                    SU_KIEN_MAT,
                    "%d điểm đo vừa mất tín hiệu".formatted(moiMat.size()),
                    """
                    Các điểm đo sau không có bản ghi mới quá %d khung (%s/khung): %s

                    ⚠ Trạm mất tín hiệu bị LOẠI khỏi đánh giá ngưỡng và hiện marker xám trên bản đồ —
                    giá trị cũ của một trạm đã chết không được dùng để kết luận mực nước hiện tại.

                    ⛔ Nguồn không có API lịch sử: khoảng thời gian im lặng này KHÔNG lấy lại được.
                    Xem docs/runbook/poller-chet.md mục 4."""
                            .formatted(soKhung, khung, gonDanhSach(moiMat)),
                    NotifySeverity.WARNING,
                    QUYEN_TRAM,
                    List.of()));
        }
        if (soTroLai > 0) {
            notifications.notify(NotifyRequest.targeted(
                    SU_KIEN_TRO_LAI,
                    "%d điểm đo đã có tín hiệu trở lại".formatted(soTroLai),
                    "Số liệu của các điểm đo này đang về bình thường trở lại.",
                    NotifySeverity.INFO,
                    QUYEN_TRAM,
                    List.of()));
        }
    }

    /**
     * ⭐⭐ T31.9 — <b>đo SỰ VẮNG MẶT</b>, không đếm lỗi.
     *
     * <p>Hai trạng thái khác hẳn nhau và cả hai đều đáng báo động, nên chúng có hai câu riêng:
     *
     * <ul>
     *   <li><b>chưa từng</b> có lượt ingest thành công — hệ vừa dựng mà chưa chạy, hoặc mã số chưa
     *       dán. {@code HydroFreshnessRegistrar} về nguyên tắc câm ở trạng thái này;
     *   <li>lượt gần nhất đã quá {@code soKhung × khung} — poller đã chạy rồi chết.
     * </ul>
     */
    private void raCaDuongIngest(Instant bayGio, Duration khung, int soKhung) {
        Optional<Instant> ganNhat = poller.mocIngestThanhCongGanNhat();
        Duration hanTuoi = khung.multipliedBy(Math.max(1, soKhung));
        boolean imLang = ganNhat.isEmpty() || ganNhat.get().isBefore(bayGio.minus(hanTuoi));

        if (!imLang) {
            if (daBaoImLang.compareAndSet(true, false)) {
                log.info("Đường ingest thuỷ văn đã trở lại — lượt thành công gần nhất {}", ganNhat.orElse(null));
                notifications.notify(NotifyRequest.targeted(
                        SU_KIEN_INGEST_TRO_LAI,
                        "Đường lấy dữ liệu thuỷ văn đã trở lại",
                        "Lượt đồng bộ thành công gần nhất: %s.".formatted(ganNhat.orElse(null)),
                        NotifySeverity.INFO,
                        QUYEN_NGUON,
                        List.of()));
            }
            return;
        }

        String vanDe = ganNhat.isEmpty()
                ? "⛔ CHƯA TỪNG có lượt đồng bộ thành công nào. Nhiều khả năng nguồn chưa được dán mã số "
                        + "(Quản trị › Nguồn dữ liệu), hoặc worker việc nền đang tắt (WORKER_ENABLED)."
                : "Lượt đồng bộ thành công gần nhất là %s — đã quá %d khung (%s/khung)."
                        .formatted(ganNhat.get(), soKhung, khung);
        log.error("⛔ Đường ingest thuỷ văn im lặng. {}", vanDe);

        if (!daBaoImLang.compareAndSet(false, true)) {
            return;
        }
        notifications.notify(NotifyRequest.targeted(
                SU_KIEN_IM_LANG,
                "Không lấy được dữ liệu thuỷ văn",
                """
                %s

                ⛔ Nguồn KHÔNG có API lịch sử. Mỗi khung 10 phút trôi qua là số đo của 19 điểm đo
                mất VĨNH VIỄN — không backfill được, không chờ được.

                Việc phải làm ngay: docs/runbook/poller-chet.md"""
                        .formatted(vanDe),
                NotifySeverity.CRITICAL,
                QUYEN_NGUON,
                List.of()));
    }

    private static java.util.stream.Collector<Long, ?, Set<Long>> toLinkedSet() {
        return java.util.stream.Collectors.toCollection(LinkedHashSet::new);
    }

    private static String gonDanhSach(List<String> ma) {
        if (ma.size() <= TRAN_LIET_KE) {
            return String.join(", ", ma);
        }
        return String.join(", ", ma.subList(0, TRAN_LIET_KE))
                + " … và %d điểm đo nữa".formatted(ma.size() - TRAN_LIET_KE);
    }
}
