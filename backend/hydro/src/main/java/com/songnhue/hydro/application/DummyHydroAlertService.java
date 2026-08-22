package com.songnhue.hydro.application;

import org.springframework.stereotype.Service;

import com.songnhue.core.spi.HydroAlertPort;

/**
 * Cài đặt tạm cho Phase 1. Phase 2 sẽ thay bằng lô-gic thật đọc từ bảng hydro_alerts.
 */
@Service
public class DummyHydroAlertService implements HydroAlertPort {

    @Override
    public boolean hasActiveAlert(Long constructionId) {
        // Mức ưu tiên 3 (Cảnh báo ngưỡng) chưa có ở Phase 1.
        return false;
    }
}
