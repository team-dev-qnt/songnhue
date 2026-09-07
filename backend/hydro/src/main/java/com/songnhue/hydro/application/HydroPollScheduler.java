package com.songnhue.hydro.application;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.infra.Bhh40Parser;
import com.songnhue.hydro.infra.VanTayLop;

/**
 * Đặt lượt polling vào hàng đợi theo <b>cron đọc từ cấu hình</b> — T31.1 · T31.2 · T31.3 · T31.11.
 *
 * <h2>⭐ Nhịp tim cố định + tự chấm cron, ⛔ KHÔNG dùng {@code SchedulingConfigurer}</h2>
 *
 * <p>Yêu cầu là <i>"đổi {@code hydro.polling.cron} trên màn hình thì lượt gọi kế tiếp đi theo giá trị
 * mới, ⛔ không phải khởi động lại"</i>. Cách quen thuộc là một {@code Trigger} động qua
 * {@code SchedulingConfigurer}. ⛔ Bị loại, vì hai lý do đo được:
 *
 * <ol>
 *   <li>⚠⚠ <b>{@code TaskSchedulingAutoConfiguration} của Spring Boot mang
 *       {@code @ConditionalOnMissingBean(SchedulingConfigurer.class)}.</b> Khai một bean
 *       {@code SchedulingConfigurer} ở đây là làm bean {@code taskScheduler} của Boot <b>biến
 *       mất</b> — cùng với cấu hình {@code spring.task.scheduling.*}, tên luồng và cơ chế tắt êm —
 *       và thay bằng một executor một luồng do {@code ScheduledAnnotationBeanPostProcessor} tự dựng.
 *       Mọi {@code @Scheduled} của <i>toàn hệ</i> đổi hành vi, <b>không một dòng lỗi nào</b>. Đúng họ
 *       ba cái bẫy auto-configuration đã ghi ở §9.7 và ở javadoc {@code HydroConfig}.
 *   <li>Một {@code Trigger} chỉ được hỏi <b>sau mỗi lượt chạy</b>. Đổi cron từ "mỗi ngày" sang "mỗi
 *       2 phút" vì thế có hiệu lực… sau một ngày. Đó là <i>trông như</i> nạp lại được.
 * </ol>
 *
 * <p>⇒ Nhịp tim {@link #NHIP_MS} cố định, và mỗi nhịp tự hỏi: <b>có mốc cron nào rơi vào khoảng
 * {@code (lần kiểm trước, bây giờ]} không?</b> Ba thứ được cùng lúc:
 *
 * <ul>
 *   <li>cron đọc lại <b>mỗi nhịp</b> từ {@code settings} (đã có đệm Caffeine + dọn theo
 *       {@code SettingChangedEvent}) ⇒ đổi là có hiệu lực trong ≤ {@link #NHIP_MS};
 *   <li>⭐ <b>cron RIÊNG của từng nguồn</b> cũng chạy đúng — {@code api_sources.cron} là một cột thật,
 *       và một {@code Trigger} toàn cục thì không thể tôn trọng nó. Bốn cái núm trên màn hình
 *       <i>Nguồn dữ liệu</i> vì thế điều khiển thật (luật 15);
 *   <li>tiến trình treo/GC dài không sinh một loạt lượt gọi dồn: khoảng chỉ được hỏi <i>có hay
 *       không</i>, ⛔ không đếm số mốc.
 * </ul>
 *
 * <p>⚠ Cái giá phải nói ra: lượt gọi trễ tối đa {@link #NHIP_MS} so với giây 45 mà cron mô tả. Cửa
 * sổ nguồn đẩy dữ liệu rộng {@code x1:30 → x8:30} — bảy phút — nên mười giây là 2,4% của nó. ⛔ Đừng
 * nới nhịp này lên phút: khi ấy sai số bắt đầu ăn vào cửa sổ thật.
 *
 * <h2>⚠ Không cần ShedLock</h2>
 *
 * <p>Hai node cùng nhịp tim thì cùng đặt việc — và node thứ hai va {@code uq_jobs_dedup_active}, nhận
 * lại chính job node thứ nhất vừa tạo. <b>CSDL đã là điểm đồng bộ.</b> Cùng lý lẽ với
 * {@link HydroMaintenanceScheduler}.
 *
 * <h2>⛔ {@code @EnableScheduling} ⛔ KHÔNG khai ở đây</h2>
 *
 * <p>Nó nằm ở {@code SchedulingConfig} với {@code @Profile("!migrate")}. Khai thêm ở đây là dựng một
 * luồng không-daemon trong tiến trình migrator, và triệu chứng là container đứng {@code Up} vô hạn,
 * {@code app} kẹt ở {@code Created}, <b>không một dòng lỗi nào</b> (§9.11.5, đã xảy ra thật 17/8).
 */
@Component
public class HydroPollScheduler {

    private static final Logger log = LoggerFactory.getLogger(HydroPollScheduler.class);

    /**
     * Nhịp tim — cũng chính là <b>sai số tối đa</b> của lượt gọi so với mốc cron. Xem javadoc lớp về
     * vì sao 10 giây, và vì sao ⛔ không nới lên phút.
     */
    static final long NHIP_MS = 10_000L;

    /**
     * ⛔ Thử <b>MỘT</b> lần, và con số này đặt ở đây vì đây là nơi nó có hiệu lực
     * ({@code JobService.enqueue} ghi nó vào cột {@code jobs.max_attempts};
     * {@code JobHandler.maxAttempts()} <b>không có người đọc</b> — luật 15).
     *
     * <p>Backoff của worker là 1' → 5' → 15', mà lượt polling kế tiếp chỉ cách <b>2 phút</b>. Thử lại
     * ở tầng job không mua thêm gì, mà lại <b>giữ khoá chống trùng suốt thời gian backoff</b> — tức
     * là chặn chính lượt polling đúng giờ. Với một nguồn không có API lịch sử, mười lăm phút bị chặn
     * là một khung rưỡi mất vĩnh viễn. ⇒ <b>lượt polling kế tiếp CHÍNH LÀ lượt thử lại</b>, và nó sớm
     * hơn mọi backoff.
     */
    private static final short THU_LAI = 1;

    /**
     * Nhịp rà tín hiệu — 5 phút. ⛔ Cố ý <b>thưa hơn</b> ngưỡng mất tín hiệu (3 khung ≈ 30 phút): rà
     * dày hơn không phát hiện sớm hơn, vì điều kiện chỉ đổi khi mốc bản ghi gần nhất lùi qua ngưỡng.
     */
    static final long NHIP_RA_TIN_HIEU_MS = 300_000L;

    /** Rà tín hiệu chỉ đọc và gửi thông báo — thử lại một lượt là vô hại và đôi khi có ích. */
    private static final short THU_LAI_RA_TIN_HIEU = 2;

    private final JobPort jobs;
    private final ApiSourceService sources;

    /**
     * ⚠ Mốc cuối của khoảng đã xét. Khởi tạo bằng <b>lúc dựng bean</b>, ⛔ không phải mốc 0: khởi tạo
     * bằng mốc 0 thì nhịp đầu tiên thấy vô số mốc cron trong quá khứ và đặt việc ngay — một lượt gọi
     * ngoài lịch ở mỗi lần khởi động, đúng lúc ứng dụng còn đang ấm máy.
     */
    private final AtomicReference<Instant> lanKiemTruoc = new AtomicReference<>(Instant.now());

    /** Đệm biểu thức cron đã phân tích — và cũng là nơi nhớ "chuỗi này đã báo lỗi rồi". */
    private final Map<String, CronExpression> daPhanTich = new ConcurrentHashMap<>();

    public HydroPollScheduler(JobPort jobs, ApiSourceService sources) {
        this.jobs = jobs;
        this.sources = sources;
    }

    /**
     * ⭐ T31.11 — in <b>vân tay mã đang chạy</b> lúc khởi động.
     *
     * <p>Poller là daemon; nó không có ai bấm F5. Ba lần dự án hỏi cùng một câu <i>"cái đang chạy có
     * phải cái tôi vừa build không"</i> và ba lần câu trả lời là không (§10.53 · §10.56 · §10.67).
     * Dòng này là cách trả lời câu ấy trong một giây, ⛔ không phải bằng cách suy từ tag image.
     */
    @PostConstruct
    void inVanTay() {
        log.info(
                "Poller thuỷ văn sẵn sàng — nhịp tim {} ms · vân tay mã: {}",
                NHIP_MS,
                VanTayLop.cua(List.of(HydroPollScheduler.class, TelemetryIngestService.class, Bhh40Parser.class)));
    }

    /**
     * Nhịp tim — ⛔ <b>chỉ đặt việc vào hàng đợi</b>, tuyệt đối không mở HTTP ở đây.
     *
     * <p>Việc chạy thẳng trong phương thức {@code @Scheduled} thì hỏng là <i>im lặng</i>: không trạng
     * thái, không thử lại, không hiện ở màn hình nào (§9.6). Với một nguồn không có API lịch sử, im
     * lặng là hình dạng hỏng đắt nhất.
     *
     * <p>⚠ {@code fixedDelay} chứ ⛔ không {@code fixedRate}: một nhịp chạy lâu bất thường (CSDL kẹt)
     * ⛔ không được kéo theo một loạt nhịp bù dồn ngay sau đó.
     */
    @Scheduled(fixedDelay = NHIP_MS, initialDelay = NHIP_MS)
    public void nhipTim() {
        Instant bayGio = Instant.now();
        Instant truoc = lanKiemTruoc.getAndSet(bayGio);
        for (ApiSource nguon : sources.nguonDangHoatDong()) {
            ThamSoNguon thamSo = sources.thamSoHieuLuc(nguon);
            if (denLuot(thamSo.cron(), nguon.getCode(), truoc, bayGio)) {
                datViec(nguon);
            }
        }
    }

    /**
     * Đặt việc rà tín hiệu — T31.8/T31.9.
     *
     * <p>⚠ Ở cùng lớp với nhịp polling là chủ ý: hai việc trả lời <b>hai nửa của cùng một câu hỏi</b>
     * — "vòng lấy dữ liệu còn sống không". Tách ra một lớp thứ hai chỉ để có một cái tên đẹp hơn là
     * làm người đọc phải mở hai tệp mới thấy đủ nhịp của MOD-03.
     *
     * <p>Chu kỳ {@link #NHIP_RA_TIN_HIEU_MS} = 5 phút, ⛔ cố ý <b>thưa hơn</b> ngưỡng mất tín hiệu
     * (mặc định 3 khung ≈ 30 phút): rà dày hơn không phát hiện sớm hơn — điều kiện chỉ đổi khi mốc
     * bản ghi gần nhất lùi qua ngưỡng — mà chỉ tốn thêm truy vấn.
     *
     * <p>⚠ Khoá chống trùng ⛔ không kèm thời gian, cùng lý lẽ với {@link #datViec}: bất biến là
     * "tối đa một lượt rà đang chạy".
     */
    @Scheduled(fixedDelay = NHIP_RA_TIN_HIEU_MS, initialDelay = NHIP_RA_TIN_HIEU_MS)
    public void datViecRaTinHieu() {
        jobs.enqueue(new JobRequest(HydroJobTypes.SIGNAL_LOSS, "{}", HydroJobTypes.SIGNAL_LOSS, THU_LAI_RA_TIN_HIEU));
    }

    /**
     * Có mốc cron nào rơi vào khoảng {@code (truoc, bayGio]} không.
     *
     * <p>⚠ Khoảng <b>mở bên trái, đóng bên phải</b>: mốc đúng bằng {@code truoc} đã được xét ở nhịp
     * trước. Nửa mở nhầm bên là đặt việc hai lần cho một mốc — hôm nay khoá chống trùng che đi, và
     * đó chính là lý do phải viết đúng: một bảo đảm được che bởi một bảo đảm khác thì không ai biết
     * nó đã hỏng.
     *
     * <p>⚠ Chấm theo giờ <b>VN</b>, khớp {@code HydroMaintenanceScheduler}: một cron kiểu
     * {@code 0 0 5 * * *} phải nghĩa là 5 giờ sáng giờ Việt Nam, ⛔ không phải 5 giờ UTC — container
     * chạy {@code -Duser.timezone=UTC} nên hai cách lệch nhau <b>bảy tiếng</b>.
     */
    private boolean denLuot(String cron, String maNguon, Instant truoc, Instant bayGio) {
        CronExpression bieuThuc = phanTich(cron, maNguon);
        ZonedDateTime moc = bieuThuc.next(truoc.atZone(DateTimeUtils.ZONE_VN));
        return moc != null && !moc.toInstant().isAfter(bayGio);
    }

    /**
     * ⚠ Cron sai cú pháp ⛔ không được làm poller đứng im.
     *
     * <p>Một chuỗi gõ nhầm trên màn hình Cấu hình là <b>một cú bấm</b>, còn hậu quả — poller ngừng
     * hẳn với một nguồn không lấy lại được — là vĩnh viễn. Nên: ghi ERROR <b>một lần cho mỗi chuỗi
     * sai</b> (đệm nhớ hộ, ⛔ không réo mỗi 10 giây rồi bị bỏ qua) rồi lùi về mặc định của seed.
     */
    private CronExpression phanTich(String cron, String maNguon) {
        return daPhanTich.computeIfAbsent(cron, c -> {
            try {
                return CronExpression.parse(c);
            } catch (IllegalArgumentException e) {
                log.error(
                        "⛔ Cron '{}' của nguồn {} sai cú pháp — poller tạm chạy theo mặc định '{}'. "
                                + "Sửa ở Quản trị › Cấu hình hệ thống › hydro.polling.cron",
                        c,
                        maNguon,
                        HydroSettings.MAC_DINH_CRON,
                        e);
                return CronExpression.parse(HydroSettings.MAC_DINH_CRON);
            }
        });
    }

    /**
     * ⛔ Khoá chống trùng là {@code HYDRO_POLL:<mã nguồn>}, ⛔ <b>không</b> kèm mốc khung.
     *
     * <p>Bất biến cần giữ là <i>"mỗi nguồn tối đa một lượt polling đang chạy"</i>. Kèm mốc khung vào
     * khoá là cho phép năm lượt của cùng một khung 10' xếp hàng cùng lúc — đúng thứ khoá này sinh ra
     * để chặn. Khoá tự do lại ngay khi job kết thúc ({@code uq_jobs_dedup_active} chỉ phủ
     * {@code PENDING}/{@code RUNNING}), nên lượt 2 phút sau vẫn đặt được bình thường.
     */
    private void datViec(ApiSource nguon) {
        String dedupKey = HydroJobTypes.POLL + ":" + nguon.getCode();
        jobs.enqueue(
                new JobRequest(HydroJobTypes.POLL, HydroPollJobHandler.payloadCho(nguon.getCode()), dedupKey, THU_LAI));
        log.debug("Đã đặt lượt polling {}", dedupKey);
    }
}
