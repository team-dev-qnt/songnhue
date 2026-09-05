package com.songnhue.core.common.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sổ đăng ký "dữ liệu của nguồn này cũ tới đâu rồi" — khung cảnh báo dữ liệu quá hạn (T7.11).
 *
 * <h2>Vì sao khung này được dựng ở Phase 0, trước cả nguồn dữ liệu đầu tiên</h2>
 *
 * <p>Nguồn thủy văn <b>không có API lịch sử</b> (chốt G3). Poller là nơi duy nhất bắt được dữ liệu,
 * và cái gì lỡ mất là mất vĩnh viễn — không có cách nào lấy lại. Nghĩa là "poller chết lúc 3 giờ
 * sáng thứ Bảy" phải được phát hiện trong vài chục phút, chứ không phải sáng thứ Hai khi có người mở
 * biểu đồ ra xem và thấy đường kẻ phẳng.
 *
 * <p>Đó là lý do kế hoạch Phase 0 xếp việc này vào nhóm "phải làm sớm, không được để cuối" cùng với
 * ArchUnit. Dựng khung trước thì Phase 2 chỉ còn một dòng đăng ký; dựng sau thì nó là việc dễ bị đẩy
 * lùi mãi, và mỗi tuần trì hoãn là một tuần chạy không có lưới.
 *
 * <h2>Cách dùng ở Phase 2</h2>
 *
 * <pre>{@code
 * // trong hydro: sau mỗi lượt ghi thành công
 * freshness.mark("hydro-water-level");
 *
 * // hoặc để nó tự đọc mốc mới nhất từ bảng agg
 * freshness.register("hydro-water-level", () -> latestRepository.maxObservedAt());
 * }</pre>
 *
 * <p>Mỗi nguồn đăng ký thành một nhãn của gauge {@code songnhue_data_freshness_seconds}; luật cảnh
 * báo mẫu ở {@code deploy/observability/alerts.yml}.
 */
@Component
public class DataFreshnessRegistry {

    private static final Logger log = LoggerFactory.getLogger(DataFreshnessRegistry.class);

    /**
     * Nguồn → cách lấy mốc cập nhật gần nhất.
     *
     * <p>Lưu {@link Supplier} chứ không lưu giá trị: có nguồn tự báo sau mỗi lượt ghi, có nguồn phải
     * hỏi CSDL. Lưu giá trị thì loại thứ hai phải tự dựng một vòng lặp cập nhật riêng, mà bỏ quên
     * vòng lặp đó lại là một cách khác để cảnh báo im lặng.
     */
    private final Map<String, Supplier<Optional<Instant>>> sources = new ConcurrentHashMap<>();

    /** Mốc do nguồn tự báo qua {@link #mark(String)}. */
    private final Map<String, Instant> marks = new ConcurrentHashMap<>();

    /** Đăng ký một nguồn tự tra mốc mới nhất. Gọi một lần lúc khởi động. */
    public void register(String source, Supplier<Optional<Instant>> latestAt) {
        sources.put(source, latestAt);
        log.info("Đăng ký theo dõi độ tươi dữ liệu: {}", source);
    }

    /** Báo "nguồn này vừa có dữ liệu mới". Gọi sau mỗi lượt ghi thành công. */
    public void mark(String source) {
        mark(source, Instant.now());
    }

    public void mark(String source, Instant at) {
        marks.put(source, at);
        sources.computeIfAbsent(source, key -> () -> Optional.ofNullable(marks.get(key)));
    }

    /** Tên các nguồn đang theo dõi — {@code PlatformMetrics} dùng để dựng gauge. */
    public Map<String, Supplier<Optional<Instant>>> sources() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(sources));
    }

    /**
     * Dữ liệu của nguồn này cũ bao lâu rồi.
     *
     * @return rỗng khi nguồn chưa từng có dữ liệu — <b>khác hẳn</b> với "dữ liệu tươi". Nơi gọi phải
     *     tự quyết định cách hiểu, và với dữ liệu không lấy lại được thì "chưa từng có" là tình
     *     trạng đáng báo động chứ không phải trung tính.
     */
    public Optional<Duration> ageOf(String source) {
        Supplier<Optional<Instant>> supplier = sources.get(source);
        if (supplier == null) {
            return Optional.empty();
        }
        return supplier.get().map(at -> Duration.between(at, Instant.now()));
    }
}
