package com.songnhue.core.common.observability;

import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.songnhue.core.application.backup.BackupService;
import com.songnhue.core.domain.backup.SystemBackup;

/**
 * Trạng thái sao lưu trong {@code /actuator/health} (T7.3, T7.8 · M5.12).
 *
 * <p><b>Chỉ số này báo DOWN, không phải chỉ cảnh báo.</b> Tranh cãi được — hệ thống vẫn phục vụ
 * người dùng bình thường khi sao lưu chết. Nhưng không có PITR, không có replica
 * ({@code architecture-review.md} §6.5), nên hệ thống chạy quá 26 giờ không có bản sao lưu là hệ
 * thống đang chạy <b>không có lưới an toàn</b>. Xếp nó vào mức "cảnh báo nhẹ" là mời người ta quen
 * mắt với nó.
 *
 * <p>⚠ Vì vậy chỉ số này <b>không</b> được đưa vào nhóm {@code readiness}: readiness quyết định
 * container có nhận request hay không, và ngừng phục vụ cả hệ thống vì bản sao lưu cũ là phản ứng
 * sai hoàn toàn. Cấu hình nhóm ở {@code application.yml}.
 */
@Component("backup")
public class BackupHealthIndicator implements HealthIndicator {

    private final BackupService backupService;

    public BackupHealthIndicator(BackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    public Health health() {
        if (!backupService.isScheduleEnabled()) {
            return Health.down()
                    .withDetail("reason", "Sao lưu tự động đang TẮT (backup.schedule-enabled=false)")
                    .build();
        }

        Optional<SystemBackup> last = backupService.lastSuccessful();
        if (last.isEmpty()) {
            return Health.down()
                    .withDetail("reason", "Chưa từng có bản sao lưu thành công nào")
                    .build();
        }

        Duration age = backupService.ageOfLastSuccess().orElse(Duration.ZERO);
        Duration threshold = backupService.staleThreshold();
        Health.Builder builder = age.compareTo(threshold) > 0 ? Health.down() : Health.up();

        return builder.withDetail("lastSuccessAt", last.get().getFinishedAt())
                .withDetail("ageHours", age.toHours())
                .withDetail("thresholdHours", threshold.toHours())
                .withDetail("fileName", last.get().getFileName())
                .build();
    }
}
