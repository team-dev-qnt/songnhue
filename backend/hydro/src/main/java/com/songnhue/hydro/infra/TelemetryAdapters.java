package com.songnhue.hydro.infra;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.TelemetryAdapter;

/**
 * Tra ra adapter của một nguồn theo {@link AdapterType} — <b>điểm cắm của nguồn thứ hai</b> (T30.10).
 *
 * <p>Hôm nay có đúng một nguồn thật. Cách viết rẻ hơn là để {@code Bhh40Adapter} được tiêm thẳng vào
 * poller — và đó là cách khoá cứng <i>"một nguồn = một endpoint mực nước"</i> mà T30.10 cấm. Nguồn
 * lượng mưa (G3-a) khi Công ty cấp endpoint sẽ là một lớp mới cài {@link TelemetryAdapter}, thêm một
 * giá trị {@code AdapterType}, và <b>không sửa dòng nào ở đây</b>.
 *
 * <h2>⚠ Vì sao thiếu adapter là lỗi lúc TRA, không phải lúc khởi động</h2>
 *
 * <p>{@code AdapterType.MOCK} cố ý không có bean ở production, nên "đủ mọi giá trị enum đều có lớp"
 * là một bất biến <b>sai</b> — dựng nó thành phép kiểm khởi động là làm production không boot được.
 * Bất biến đúng hẹp hơn: <i>mọi nguồn đang hoạt động phải tra được adapter</i>, và chỗ biết được
 * điều đó là lúc có một hàng {@code api_sources} trong tay.
 *
 * <p>⇒ Thông báo lỗi ở {@link #cho(AdapterType)} phải nói ra <b>việc cần làm</b>, không chỉ nói cái
 * gì thiếu: javadoc của {@code AdapterType} đã cảnh báo <i>"một giá trị không có lớp tương ứng là
 * một nguồn không ai gọi được, và nó chỉ lộ ra ở lượt polling đầu tiên"</i> — dòng lỗi này là chỗ
 * duy nhất người trực đọc được lúc ấy.
 */
@Component
public class TelemetryAdapters {

    private static final Logger log = LoggerFactory.getLogger(TelemetryAdapters.class);

    private final Map<AdapterType, TelemetryAdapter> theoKieu = new EnumMap<>(AdapterType.class);

    public TelemetryAdapters(List<TelemetryAdapter> adapters) {
        for (TelemetryAdapter adapter : adapters) {
            TelemetryAdapter cu = theoKieu.put(adapter.kieu(), adapter);
            if (cu != null) {
                // Hai lớp cùng nhận một AdapterType: lượt polling sẽ chạy lớp nào là do thứ tự quét
                // classpath quyết định — tức là do một chi tiết không ai điều khiển. Dừng ở đây.
                throw new IllegalStateException("Hai adapter cùng khai %s: %s và %s"
                        .formatted(
                                adapter.kieu(),
                                cu.getClass().getSimpleName(),
                                adapter.getClass().getSimpleName()));
            }
        }
        log.info("Adapter thuỷ văn đã nạp: {}", theoKieu.keySet());
    }

    /**
     * @throws IllegalStateException khi loại nguồn ấy không có lớp cài đặt nào đang chạy
     */
    public TelemetryAdapter cho(AdapterType kieu) {
        TelemetryAdapter adapter = theoKieu.get(kieu);
        if (adapter == null) {
            throw new IllegalStateException("Không có adapter cho loại nguồn %s. Đang nạp: %s. %s"
                    .formatted(
                            kieu,
                            theoKieu.keySet(),
                            kieu == AdapterType.MOCK
                                    ? "MOCK chỉ có khi đặt app.hydro.api.mock=true — ⛔ và ⛔ không "
                                            + "bao giờ đặt ở staging/production."
                                    : "Kiểm lại cột adapter_type của nguồn này."));
        }
        return adapter;
    }

    /** Các loại nguồn gọi được trong tiến trình này — dùng cho màn hình Nguồn dữ liệu và cho bài kiểm. */
    public Set<AdapterType> daNap() {
        return Set.copyOf(theoKieu.keySet());
    }
}
