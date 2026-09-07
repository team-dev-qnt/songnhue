package com.songnhue.core.common.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.songnhue.core.application.backup.BackupService;
import com.songnhue.core.application.job.JobService;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Ba con số của nền tảng mà Prometheus trên VM-3 đọc được (T7.9, T7.11 · trả nợ #21).
 *
 * <h2>Ba chỉ số, và vì sao đúng ba chỉ số này</h2>
 *
 * <table border="1">
 *   <caption>Chỉ số nền tảng</caption>
 *   <tr><th>Chỉ số</th><th>Trả lời câu hỏi</th><th>Hỏng thì mất gì</th></tr>
 *   <tr>
 *     <td>{@code songnhue_backup_age_seconds}</td>
 *     <td>Bản sao lưu gần nhất cũ bao lâu rồi</td>
 *     <td>Không có PITR — mất bản dump là mất đường phục hồi duy nhất</td>
 *   </tr>
 *   <tr>
 *     <td>{@code songnhue_job_queue_backlog}</td>
 *     <td>Hàng đợi còn tồn bao nhiêu việc</td>
 *     <td>Worker chết thì thông báo, kết xuất, quét virus đều đứng — <b>không lỗi nào báo ra</b></td>
 *   </tr>
 *   <tr>
 *     <td>{@code songnhue_data_freshness_seconds{source}}</td>
 *     <td>Nguồn dữ liệu này im lặng bao lâu rồi</td>
 *     <td>Dữ liệu thủy văn mất là mất vĩnh viễn (G3)</td>
 *   </tr>
 * </table>
 *
 * <p>Cả ba đều đo <b>sự vắng mặt</b> của việc lẽ ra phải xảy ra. Đó là chủ ý: những kiểu hỏng đắt
 * nhất của hệ này không ném exception nào cả, chúng chỉ đơn giản là ngừng làm việc. Đếm số lỗi thì
 * không bao giờ bắt được chúng.
 *
 * <h2>⚠ Vì sao tuổi bản sao lưu được làm mới theo lịch, không đọc thẳng trong gauge</h2>
 *
 * <p>Micrometer gọi hàm của gauge <b>mỗi lần Prometheus lấy số</b>. Truy vấn CSDL ngay trong đó
 * nghĩa là mỗi lượt lấy số là một lượt đọc DB — và tệ hơn: khi CSDL chết, lượt lấy số bị treo theo,
 * kéo sập luôn {@code /actuator/prometheus} đúng lúc cần nó nhất để biết chuyện gì đang xảy ra. Nên
 * giá trị được làm mới theo chu kỳ vào một biến, còn gauge chỉ đọc biến đó.
 */
@Component
public class PlatformMetrics implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(PlatformMetrics.class);

    /** Giá trị báo "chưa từng có" — âm để không lẫn với một con số tuổi thật. */
    private static final double NEVER = -1;

    private final BackupService backupService;
    private final JobService jobService;
    private final DataFreshnessRegistry freshness;

    private volatile double backupAgeSeconds = NEVER;
    private volatile double queueBacklog = 0;

    /** Gauge đã đăng ký theo nguồn — nguồn mới xuất hiện sau khi khởi động vẫn phải có gauge. */
    private final Map<String, Double> freshnessValues = new ConcurrentHashMap<>();

    private MeterRegistry registry;

    public PlatformMetrics(BackupService backupService, JobService jobService, DataFreshnessRegistry freshness) {
        this.backupService = backupService;
        this.jobService = jobService;
        this.freshness = freshness;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.registry = registry;

        Gauge.builder("songnhue.backup.age.seconds", this, m -> m.backupAgeSeconds)
                .description("Số giây kể từ bản sao lưu THÀNH CÔNG gần nhất; -1 = chưa từng sao lưu")
                .baseUnit("seconds")
                .register(registry);

        Gauge.builder("songnhue.job.queue.backlog", this, m -> m.queueBacklog)
                .description("Số việc đang chờ hoặc đang chạy trong hàng đợi")
                .register(registry);
    }

    /**
     * Làm mới mọi con số. 30 giây một lượt — đủ nhanh so với ngưỡng cảnh báo tính bằng giờ, đủ chậm
     * để không thành gánh nặng truy vấn.
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void refresh() {
        try {
            backupAgeSeconds = backupService
                    .ageOfLastSuccess()
                    .map(Duration::toSeconds)
                    .map(Long::doubleValue)
                    .orElse(NEVER);
            queueBacklog = jobService.backlogSize();
            refreshFreshnessGauges();
        } catch (RuntimeException e) {
            // Không ném: luồng @Scheduled chết một lần là ngừng chạy vĩnh viễn, và mọi chỉ số đứng
            // im ở giá trị cũ — trông y hệt "hệ thống bình thường".
            log.warn("Không làm mới được chỉ số nền tảng: {}", e.getMessage());
        }
    }

    /**
     * Đăng ký gauge cho nguồn dữ liệu mới xuất hiện.
     *
     * <p>Phải làm theo chu kỳ chứ không chỉ một lần lúc {@code bindTo}: nguồn thủy văn của Phase 2
     * đăng ký sau khi ứng dụng đã khởi động, và gauge tạo một lần lúc đầu sẽ không bao giờ thấy nó.
     */
    private void refreshFreshnessGauges() {
        for (Map.Entry<String, Supplier<Optional<Instant>>> entry :
                freshness.sources().entrySet()) {
            String source = entry.getKey();
            double age = entry.getValue()
                    .get()
                    .map(at -> (double) Duration.between(at, Instant.now()).toSeconds())
                    .orElse(NEVER);

            boolean isNew = !freshnessValues.containsKey(source);
            freshnessValues.put(source, age);

            if (isNew && registry != null) {
                Gauge.builder(
                                "songnhue.data.freshness.seconds",
                                freshnessValues,
                                map -> map.getOrDefault(source, NEVER))
                        .tag("source", source)
                        .description("Số giây kể từ dữ liệu mới nhất của nguồn; -1 = chưa từng có dữ liệu")
                        .baseUnit("seconds")
                        .register(registry);
                log.info("Thêm chỉ số độ tươi dữ liệu cho nguồn {}", source);
            }
        }
    }
}
