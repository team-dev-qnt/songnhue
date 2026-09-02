package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.observability.DataFreshnessRegistry;
import com.songnhue.hydro.infra.HydroLatestRepository;

/**
 * Nối {@code hydro} vào khung cảnh báo <b>dữ liệu quá hạn</b> đã dựng từ Phase 0 — T29.8.
 *
 * <h2>⭐ Vì sao chỉ số này quan trọng ngang bản sao lưu CSDL</h2>
 *
 * <p>Nguồn thuỷ văn <b>không có API lịch sử</b> (chốt G3, và đã đo lại 01/09/2026: gọi kèm tham số
 * ngày/giờ trả về byte y hệt). Poller là nơi <b>duy nhất</b> bắt được dữ liệu, và cái gì lỡ mất là
 * mất vĩnh viễn. Nghĩa là <i>"poller chết lúc 3 giờ sáng thứ Bảy"</i> phải được phát hiện trong vài
 * chục phút, chứ không phải sáng thứ Hai khi có người mở biểu đồ ra xem và thấy một đường kẻ phẳng.
 *
 * <p>Khung đo đã có sẵn từ WS-7 và <b>chờ đúng một dòng đăng ký</b> suốt từ đó: gauge
 * {@code songnhue_data_freshness_seconds}, luật cảnh báo {@code NguonDuLieuImLang} trong
 * {@code deploy/observability/alerts.yml}, và cả một runbook {@code docs/runbook/poller-chet.md}
 * viết trước cả poller. Lớp này là dòng đăng ký ấy.
 *
 * <h2>⛔ Làm mới THEO LỊCH, ⛔ không đọc CSDL bên trong hàm gauge</h2>
 *
 * <p>{@link DataFreshnessRegistry} nhận một {@code Supplier}, và Micrometer gọi supplier ấy <b>mỗi
 * lần Prometheus quét</b> — mặc định 15 giây, và nó gọi trên luồng phục vụ {@code /actuator}, ngoài
 * mọi giao dịch. Truy vấn CSDL thẳng trong đó là: một câu SQL cứ 15 giây cho tới hết đời hệ thống,
 * chạy ngoài transaction, và một lượt CSDL chậm biến thành một lượt quét chỉ số timeout — tức là
 * <b>mất chỉ số đúng lúc CSDL đang có vấn đề</b>, đúng lúc cần nó nhất (§9.9.3).
 *
 * <p>Nên supplier chỉ đọc {@link #mocGanNhat} — một ô nhớ — còn việc hỏi CSDL do
 * {@link #lamMoi()} làm theo lịch. Nhịp làm mới 60 giây là dư sức: ngưỡng cảnh báo tính bằng chục
 * phút, nên một chỉ số trễ tối đa một phút không đổi kết luận nào.
 *
 * <h2>⛔⛔ ĐĂNG KÝ MUỘN — chỉ khi đã có dữ liệu thật. Đọc trước khi "dọn cho gọn"</h2>
 *
 * <p>Bản đầu đăng ký nguồn ngay trong {@code @PostConstruct}. Đo được 02/09/2026 rằng như vậy là
 * <b>bật một cảnh báo mức critical vĩnh viễn</b>: người ghi duy nhất của {@code hydro_latest} là
 * poller, mà poller là WS-31 — nên từ lúc WS-29 lên staging cho tới lúc WS-31 xong, bảng ấy
 * <b>chắc chắn rỗng</b>. Chuỗi hệ quả đo được:
 *
 * <ul>
 *   <li>{@code DataFreshnessRegistry.ageOf} trả rỗng ⇒ {@code TelemetryHealthIndicator} vào nhánh
 *       {@code age.isEmpty()} ⇒ {@code anySilent = true} ⇒ <b>{@code /actuator/health} nhóm
 *       telemetry DOWN thường trực</b>;
 *   <li>gauge {@code songnhue_data_freshness_seconds} phát {@code -1} ⇒ luật
 *       {@code NguonDuLieuImLang} bắn sau vài phút và <b>không bao giờ tắt</b>.
 * </ul>
 *
 * <p>⚠ Một cảnh báo kêu suốt nhiều tuần vì một lý do ai cũng biết là cách nhanh nhất để người ta
 * <b>tắt cảnh báo ấy đi</b> — và nó sẽ vẫn tắt vào ngày poller chết thật. Đó chính là §10.42 ở
 * dạng khác: <i>một bản vá làm hệ thống sống sót qua lỗi cũng làm tắt chuông báo lỗi ấy</i>.
 *
 * <p>⇒ {@link #lamMoi()} chỉ đăng ký khi <b>đã đọc được một mốc thật</b>. Trước đó nguồn này chưa
 * tồn tại với hệ giám sát, và {@code TelemetryHealthIndicator} nói đúng điều đang xảy ra: <i>"chưa
 * có nguồn dữ liệu ngoài nào đăng ký"</i>.
 *
 * <p>⬜ <b>Nợ đi kèm, có tên</b>: sau khi WS-31 chạy, một hệ thống <i>chưa từng</i> ghi được dòng
 * nào sẽ im lặng thay vì báo động — đúng trạng thái đáng báo động nhất với một nguồn không lấy lại
 * được. Vế còn thiếu ấy thuộc WS-31/T31.9 (cảnh báo <i>sự vắng mặt</i>, đo bằng việc <b>không có
 * lượt ingest thành công nào</b> chứ không bằng độ tươi của bảng), ⛔ không phải bằng cách đăng ký
 * sớm ở đây.
 *
 * <h2>⚠ "Chưa từng có" khác "vừa mới có"</h2>
 *
 * <p>{@code DataFreshnessRegistry.ageOf} trả {@code Optional.empty()} khi nguồn chưa từng có dữ
 * liệu, và {@code PlatformMetrics} phát {@code -1}, ⛔ không phát {@code 0}. Phát {@code 0} nghĩa là
 * <i>"dữ liệu vừa mới cập nhật"</i> — câu khẳng định sai nguy hiểm nhất mà một chỉ số giám sát có
 * thể nói (quy tắc 16: số 0 là một câu khẳng định).
 */
@Component
public class HydroFreshnessRegistrar {

    private static final Logger log = LoggerFactory.getLogger(HydroFreshnessRegistrar.class);

    /** Nhãn của gauge — ⚠ trùng khít chuỗi trong {@code deploy/observability/alerts.yml}. */
    public static final String NGUON = "hydro-water-level";

    /** Trễ tối đa của chỉ số. Ngưỡng cảnh báo tính bằng chục phút nên một phút không đổi kết luận. */
    private static final long NHIP_LAM_MOI_MS = 60_000L;

    private final DataFreshnessRegistry freshness;
    private final HydroLatestRepository latest;

    /** ⚠ Ô nhớ mà supplier của gauge đọc — ⛔ tuyệt đối không thay bằng một truy vấn. */
    private final AtomicReference<Instant> mocGanNhat = new AtomicReference<>();

    /** Đã đăng ký với hệ giám sát chưa — xem {@link #lamMoi()} về việc vì sao đăng ký MUỘN. */
    private final AtomicBoolean daDangKy = new AtomicBoolean(false);

    public HydroFreshnessRegistrar(DataFreshnessRegistry freshness, HydroLatestRepository latest) {
        this.freshness = freshness;
        this.latest = latest;
    }

    /**
     * Hỏi CSDL mốc đo gần nhất; <b>đăng ký nguồn ở lượt đầu tiên đọc được một mốc thật</b>.
     *
     * <p>⛔ Cố ý <b>không</b> đăng ký trong {@code @PostConstruct} — xem khối "ĐĂNG KÝ MUỘN" ở
     * javadoc của lớp. Đăng ký sớm là bật một cảnh báo critical vĩnh viễn suốt quãng từ WS-29 tới
     * WS-31, và một cảnh báo kêu suốt nhiều tuần vì lý do ai cũng biết thì rồi sẽ bị tắt đi.
     *
     * <p>{@code register} gọi lại nhiều lần là vô hại ({@code Map.put}), nhưng cờ
     * {@link #daDangKy} giữ cho dòng log "đã nối" chỉ in <b>một lần</b> — một dòng log lặp mỗi phút
     * là cách làm tệp log mất giá trị chẩn đoán.
     *
     * <p>⚠ {@code initialDelay} bằng 0 là cố ý: ngay sau khi khởi động, chỉ số phải phản ánh trạng
     * thái thật thay vì trễ một phút — mỗi lượt deploy trễ một phút là mỗi lượt deploy sinh một
     * khoảng mù.
     */
    @Scheduled(fixedRate = NHIP_LAM_MOI_MS, initialDelay = 0)
    @Transactional(readOnly = true)
    public void lamMoi() {
        latest.mocDoGanNhat().ifPresent(moc -> {
            mocGanNhat.set(moc);
            if (daDangKy.compareAndSet(false, true)) {
                freshness.register(NGUON, () -> Optional.ofNullable(mocGanNhat.get()));
                log.info(
                        "Đã nối chỉ số độ tươi dữ liệu thuỷ văn ({}) — mốc đầu tiên {}, nhịp làm mới {}ms",
                        NGUON,
                        moc,
                        NHIP_LAM_MOI_MS);
            }
        });
    }
}
