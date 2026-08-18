package com.songnhue.core.common.observability;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Các nguồn dữ liệu bên ngoài có còn gửi số về không (T7.8 · M5.12 — phần telemetry).
 *
 * <p><b>Phase 0 chưa có nguồn nào, và chỉ số này cố ý báo UP khi danh sách rỗng.</b> Không phải vì
 * "không có gì để kiểm nên coi như ổn" — mà vì báo DOWN ở đây sẽ làm {@code /actuator/health} của cả
 * hệ thống đỏ suốt từ Phase 0 tới Phase 2. Một chỉ số đỏ thường trực là chỉ số bị bỏ qua, và tới lúc
 * nó đỏ vì lý do thật thì không ai còn nhìn.
 *
 * <p>Chi tiết trả về nêu rõ trạng thái "chưa có nguồn nào đăng ký" để không ai đọc nhầm màu xanh này
 * thành "poller thủy văn đang chạy tốt".
 *
 * <p>Phase 2 chỉ cần gọi {@code DataFreshnessRegistry.register("hydro-water-level", …)} là chỉ số
 * này tự có nội dung — không phải sửa lớp này.
 */
@Component("telemetry")
public class TelemetryHealthIndicator implements HealthIndicator {

    /**
     * Ngưỡng coi một nguồn là im lặng.
     *
     * <p>30 phút cho nguồn thủy văn cập nhật 10 phút một khung (G3): đủ để bỏ qua vài lượt lỡ mà
     * không sinh cảnh báo giả, đủ nhanh để không mất tới nửa ngày dữ liệu không lấy lại được.
     * Ngưỡng riêng cho từng nguồn thuộc về Phase 2, khi đã biết nhịp thật của từng nguồn.
     */
    private static final Duration SILENT_THRESHOLD = Duration.ofMinutes(30);

    private final DataFreshnessRegistry freshness;

    public TelemetryHealthIndicator(DataFreshnessRegistry freshness) {
        this.freshness = freshness;
    }

    @Override
    public Health health() {
        Map<String, ?> sources = freshness.sources();
        if (sources.isEmpty()) {
            return Health.up()
                    .withDetail("state", "Chưa có nguồn dữ liệu ngoài nào đăng ký (Phase 2 sẽ đăng ký nguồn thủy văn)")
                    .build();
        }

        Health.Builder builder = Health.up();
        boolean anySilent = false;

        for (String source : sources.keySet()) {
            Optional<Duration> age = freshness.ageOf(source);
            if (age.isEmpty()) {
                anySilent = true;
                builder.withDetail(source, "chưa từng có dữ liệu");
            } else {
                long seconds = age.get().toSeconds();
                if (age.get().compareTo(SILENT_THRESHOLD) > 0) {
                    anySilent = true;
                }
                builder.withDetail(source, seconds + "s trước");
            }
        }

        return anySilent ? builder.down().build() : builder.build();
    }
}
