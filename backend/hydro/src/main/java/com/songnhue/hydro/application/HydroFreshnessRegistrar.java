package com.songnhue.hydro.application;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;

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
 * <h2>⚠ "Chưa từng có" khác "vừa mới có"</h2>
 *
 * <p>{@link #mocGanNhat} rỗng khi bảng {@code hydro_latest} chưa có dòng nào — trạng thái thật của
 * hệ thống hôm nay, trước lượt polling đầu tiên. {@code DataFreshnessRegistry.ageOf} trả
 * {@code Optional.empty()} cho trường hợp ấy và {@code PlatformMetrics} phát {@code -1}, ⛔ không
 * phát {@code 0}. Phát {@code 0} nghĩa là <i>"dữ liệu vừa mới cập nhật"</i> — câu khẳng định sai
 * nguy hiểm nhất mà một chỉ số giám sát có thể nói (quy tắc 16: số 0 là một câu khẳng định).
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

    public HydroFreshnessRegistrar(DataFreshnessRegistry freshness, HydroLatestRepository latest) {
        this.freshness = freshness;
        this.latest = latest;
    }

    @PostConstruct
    void dangKy() {
        freshness.register(NGUON, () -> Optional.ofNullable(mocGanNhat.get()));
        log.info("Đã nối chỉ số độ tươi dữ liệu thuỷ văn ({}) — nhịp làm mới {}ms", NGUON, NHIP_LAM_MOI_MS);
    }

    /**
     * Hỏi CSDL mốc đo gần nhất và cập nhật ô nhớ.
     *
     * <p>⚠ {@code initialDelay} bằng 0 là cố ý: ngay sau khi khởi động, chỉ số phải phản ánh trạng
     * thái thật thay vì "chưa từng có" trong suốt phút đầu — nếu không thì mỗi lượt deploy sinh ra
     * một phút cảnh báo giả, và cảnh báo giả lặp lại là cách nhanh nhất để người ta tắt cảnh báo.
     */
    @Scheduled(fixedRate = NHIP_LAM_MOI_MS, initialDelay = 0)
    @Transactional(readOnly = true)
    public void lamMoi() {
        latest.mocDoGanNhat().ifPresent(mocGanNhat::set);
    }
}
