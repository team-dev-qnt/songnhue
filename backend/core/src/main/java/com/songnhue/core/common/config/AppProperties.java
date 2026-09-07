package com.songnhue.core.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tham số HẠ TẦNG của ứng dụng.
 *
 * <p>⚠ Phân biệt rõ với bảng {@code settings}: <b>tham số nghiệp vụ</b> (giờ hành chính, retention,
 * ngưỡng, chu kỳ polling…) nằm ở bảng {@code settings} có UI sửa — quy tắc 12 của dự án. Chỗ này
 * chỉ giữ thứ gắn với cách triển khai, đổi là phải deploy lại.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Bật khi lên ≥2 node để job theo lịch không chạy trùng (architecture-review.md §6.2). */
    private boolean shedlockEnabled = false;

    /** Tắt ở node chỉ phục vụ HTTP, khi tách worker ra tiến trình riêng (§6.4). */
    private boolean workerEnabled = true;

    public boolean isShedlockEnabled() {
        return shedlockEnabled;
    }

    public void setShedlockEnabled(boolean shedlockEnabled) {
        this.shedlockEnabled = shedlockEnabled;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }
}
