package com.songnhue.core.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import com.songnhue.core.application.backup.BackupService;
import com.songnhue.core.application.job.JobService;
import com.songnhue.core.domain.backup.BackupTrigger;
import com.songnhue.core.domain.backup.SystemBackup;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Giám sát nền tảng (T7.3, T7.9, T7.11 · trả nợ #18, #21).
 *
 * <p>Điểm chung của mọi bài kiểm ở đây: chúng chứng minh hệ thống <b>phân biệt được</b> "chưa từng
 * có" với "vừa mới có". Nhầm hai trạng thái đó là kiểu hỏng nguy hiểm nhất của một hệ giám sát — hệ
 * thống chưa từng sao lưu lần nào mà báo xanh thì cảnh báo không bao giờ kêu, và không ai biết cho
 * tới lúc cần khôi phục.
 */
class ObservabilityTest {

    @Nested
    @DisplayName("Sổ đăng ký độ tươi dữ liệu")
    class Freshness {

        @Test
        @DisplayName("Nguồn chưa đăng ký → rỗng, KHÁC với nguồn có dữ liệu tươi")
        void unknownSourceIsEmpty() {
            DataFreshnessRegistry registry = new DataFreshnessRegistry();

            assertThat(registry.ageOf("chua-dang-ky")).isEmpty();

            registry.mark("hydro-water-level");
            assertThat(registry.ageOf("hydro-water-level")).isPresent();
        }

        @Test
        @DisplayName("Nguồn đã đăng ký nhưng CHƯA TỪNG có dữ liệu → vẫn rỗng, không phải 0 giây")
        void registeredButNeverHadDataIsEmpty() {
            DataFreshnessRegistry registry = new DataFreshnessRegistry();
            registry.register("hydro-rainfall", Optional::empty);

            assertThat(registry.sources()).containsKey("hydro-rainfall");
            assertThat(registry.ageOf("hydro-rainfall"))
                    .as("trả về 0 giây ở đây là báo 'dữ liệu vừa mới về' cho một nguồn câm lặng")
                    .isEmpty();
        }

        @Test
        @DisplayName("mark(source, at) tính đúng tuổi")
        void marksAge() {
            DataFreshnessRegistry registry = new DataFreshnessRegistry();
            registry.mark("nguon", Instant.now().minus(Duration.ofMinutes(45)));

            assertThat(registry.ageOf("nguon")).get().satisfies(age -> assertThat(age.toMinutes())
                    .isBetween(44L, 46L));
        }
    }

    @Nested
    @DisplayName("Chỉ số Prometheus")
    class Metrics {

        @Test
        @DisplayName("Gauge sao lưu và hàng đợi có mặt sau lượt làm mới đầu tiên")
        void registersCoreGauges() {
            BackupService backup = mock(BackupService.class);
            JobService jobs = mock(JobService.class);
            when(backup.ageOfLastSuccess()).thenReturn(Optional.of(Duration.ofHours(3)));
            when(jobs.backlogSize()).thenReturn(12L);

            MeterRegistry registry = new SimpleMeterRegistry();
            PlatformMetrics metrics = new PlatformMetrics(backup, jobs, new DataFreshnessRegistry());
            metrics.bindTo(registry);
            metrics.refresh();

            assertThat(registry.get("songnhue.backup.age.seconds").gauge().value())
                    .isEqualTo(Duration.ofHours(3).toSeconds());
            assertThat(registry.get("songnhue.job.queue.backlog").gauge().value())
                    .isEqualTo(12d);
        }

        @Test
        @DisplayName("⚠ Chưa từng sao lưu → -1, KHÔNG phải 0 (0 nghĩa là 'vừa sao lưu xong')")
        void neverBackedUpIsMinusOne() {
            BackupService backup = mock(BackupService.class);
            JobService jobs = mock(JobService.class);
            when(backup.ageOfLastSuccess()).thenReturn(Optional.empty());
            when(jobs.backlogSize()).thenReturn(0L);

            MeterRegistry registry = new SimpleMeterRegistry();
            PlatformMetrics metrics = new PlatformMetrics(backup, jobs, new DataFreshnessRegistry());
            metrics.bindTo(registry);
            metrics.refresh();

            assertThat(registry.get("songnhue.backup.age.seconds").gauge().value())
                    .isEqualTo(-1d);
        }

        @Test
        @DisplayName("⚠ Nguồn đăng ký SAU khi khởi động vẫn có gauge — Phase 2 đăng ký lúc chạy")
        void picksUpSourcesRegisteredLater() {
            BackupService backup = mock(BackupService.class);
            JobService jobs = mock(JobService.class);
            when(backup.ageOfLastSuccess()).thenReturn(Optional.empty());
            when(jobs.backlogSize()).thenReturn(0L);

            DataFreshnessRegistry freshness = new DataFreshnessRegistry();
            MeterRegistry registry = new SimpleMeterRegistry();
            PlatformMetrics metrics = new PlatformMetrics(backup, jobs, freshness);
            metrics.bindTo(registry);
            metrics.refresh();

            assertThat(registry.find("songnhue.data.freshness.seconds").gauge())
                    .as("chưa có nguồn nào thì chưa có gauge")
                    .isNull();

            // Phase 2: poller thuỷ văn đăng ký khi ứng dụng đã chạy
            freshness.mark("hydro-water-level", Instant.now().minus(Duration.ofMinutes(10)));
            metrics.refresh();

            Gauge gauge = registry.get("songnhue.data.freshness.seconds")
                    .tag("source", "hydro-water-level")
                    .gauge();
            assertThat(gauge.value()).isBetween(595d, 610d);
        }

        @Test
        @DisplayName("Làm mới hỏng KHÔNG được ném ra — luồng @Scheduled chết là mọi chỉ số đứng im")
        void refreshSwallowsFailures() {
            BackupService backup = mock(BackupService.class);
            JobService jobs = mock(JobService.class);
            when(backup.ageOfLastSuccess()).thenThrow(new IllegalStateException("CSDL không nối được"));

            PlatformMetrics metrics = new PlatformMetrics(backup, jobs, new DataFreshnessRegistry());
            metrics.bindTo(new SimpleMeterRegistry());

            assertThat(org.assertj.core.api.Assertions.catchThrowable(metrics::refresh))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Health indicator sao lưu")
    class BackupHealth {

        @Test
        @DisplayName("⚠ Chưa từng sao lưu → DOWN")
        void downWhenNeverBackedUp() {
            BackupService backup = backupService(Optional.empty(), Optional.empty(), true);

            assertThat(new BackupHealthIndicator(backup).health().getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("Bản gần nhất trong ngưỡng → UP")
        void upWhenFresh() {
            SystemBackup last = succeededBackup();
            BackupService backup = backupService(Optional.of(last), Optional.of(Duration.ofHours(10)), true);

            assertThat(new BackupHealthIndicator(backup).health().getStatus()).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("Quá ngưỡng 26 giờ → DOWN")
        void downWhenStale() {
            SystemBackup last = succeededBackup();
            BackupService backup = backupService(Optional.of(last), Optional.of(Duration.ofHours(30)), true);

            assertThat(new BackupHealthIndicator(backup).health().getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("⚠ Tắt sao lưu tự động → DOWN, không phải UP")
        void downWhenScheduleDisabled() {
            BackupService backup =
                    backupService(Optional.of(succeededBackup()), Optional.of(Duration.ofMinutes(5)), false);

            assertThat(new BackupHealthIndicator(backup).health().getStatus())
                    .as("tắt lưới an toàn mà báo xanh thì không ai biết là đã tắt")
                    .isEqualTo(Status.DOWN);
        }

        private BackupService backupService(
                Optional<SystemBackup> last, Optional<Duration> age, boolean scheduleEnabled) {
            BackupService backup = mock(BackupService.class);
            when(backup.lastSuccessful()).thenReturn(last);
            when(backup.ageOfLastSuccess()).thenReturn(age);
            when(backup.staleThreshold()).thenReturn(Duration.ofHours(26));
            when(backup.isScheduleEnabled()).thenReturn(scheduleEnabled);
            return backup;
        }

        private SystemBackup succeededBackup() {
            SystemBackup backup = new SystemBackup("songnhue-test.dump", BackupTrigger.SCHEDULED);
            backup.markSucceeded("/tmp/songnhue-test.dump", 1024L, "a".repeat(64), "PostgreSQL 16.4");
            return backup;
        }
    }

    @Nested
    @DisplayName("Health indicator telemetry")
    class Telemetry {

        @Test
        @DisplayName("Phase 0 chưa có nguồn nào → UP, kèm ghi chú rõ là chưa có nguồn")
        void upWhenNoSourceRegistered() {
            var health = new TelemetryHealthIndicator(new DataFreshnessRegistry()).health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails().get("state").toString()).contains("Chưa có nguồn");
        }

        @Test
        @DisplayName("Nguồn im lặng quá 30 phút → DOWN")
        void downWhenSourceSilent() {
            DataFreshnessRegistry freshness = new DataFreshnessRegistry();
            freshness.mark("hydro-water-level", Instant.now().minus(Duration.ofHours(2)));

            assertThat(new TelemetryHealthIndicator(freshness).health().getStatus())
                    .isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("Nguồn vừa có dữ liệu → UP")
        void upWhenSourceFresh() {
            DataFreshnessRegistry freshness = new DataFreshnessRegistry();
            freshness.mark("hydro-water-level", Instant.now().minus(Duration.ofMinutes(2)));

            assertThat(new TelemetryHealthIndicator(freshness).health().getStatus())
                    .isEqualTo(Status.UP);
        }
    }
}
