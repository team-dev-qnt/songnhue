package com.songnhue.content.application;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.config.PortalProperties;

/**
 * Yêu cầu cổng công khai dựng lại trang chủ ngay sau khi backend sẵn sàng — WS-16.
 *
 * <p>Lý do đầy đủ nằm ở {@link PortalCache#warmUp()}. Tóm tắt: ảnh Docker của cổng được dựng ở CI
 * khi chưa có backend, nên trang chủ tĩnh trong ảnh là một trang <b>rỗng</b> — và nó không mang nhãn
 * cache nào để {@code revalidateTag} lần tới.
 *
 * <p>Chỉ đặt việc khi đã cấu hình cổng: môi trường dev không chạy {@code public-web} thì đây là một
 * dòng việc nằm chờ rồi hỏng, làm nhiễu bảng {@code jobs} mỗi lần khởi động.
 */
@Component
public class PortalWarmUpRunner {

    private final PortalCache portalCache;
    private final PortalProperties portal;

    public PortalWarmUpRunner(PortalCache portalCache, PortalProperties portal) {
        this.portalCache = portalCache;
        this.portal = portal;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (portal.isEnabled()) {
            portalCache.warmUp();
        }
    }
}
