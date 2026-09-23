package com.songnhue.core.application.job;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.web.RequestContext;
import com.songnhue.core.domain.job.Job;
import com.songnhue.core.domain.job.JobStatus;
import com.songnhue.core.infra.job.JobRepository;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRef;
import com.songnhue.core.spi.JobRequest;

/**
 * Đặt việc vào hàng đợi và tra tiến độ — mặt tiền của pattern P5.
 *
 * <p>Mọi tác vụ chạy lâu (kết xuất báo cáo, nhập dữ liệu, quét virus, sao lưu) đều đi qua đây thay
 * vì chạy thẳng trong request: giữ request mở hàng phút là hết connection pool, người dùng bấm lại
 * vì tưởng treo, và proxy tự cắt ở 60 giây.
 */
@Service
public class JobService implements JobPort {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    /** "Chưa kết thúc" — đúng tập trạng thái mà chỉ mục {@code uq_jobs_dedup_active} áp dụng. */
    private static final List<JobStatus> ACTIVE_STATUSES = List.of(JobStatus.PENDING, JobStatus.RUNNING);

    private final JobRepository repository;
    private final TransactionTemplate transactions;

    /**
     * T68.30 — nguồn của {@link JobHandler#maxAttempts()}.
     *
     * <p>⛔⛔ <b>Phải là {@code ObjectProvider}, ⛔ được tiêm thẳng {@code List<JobHandler>}.</b>
     * {@code QuetLaiTepService} và {@code MaHoaLaiService} vừa <b>là</b> {@code JobHandler} vừa tiêm
     * thẳng {@code JobService} để tự đặt việc; tiêm sớm ở đây là dựng một <b>vòng phụ thuộc</b> và
     * ứng dụng ⛔ khởi động nổi. {@code ObjectProvider} giải lười, mà lượt giải chỉ xảy ra lúc đặt
     * việc — khi ấy mọi bean đã có. Cùng khuôn với {@code AuditArchiveHandler} và
     * {@code ObjectProvider<ArchiverJdbc>}.
     */
    private final ObjectProvider<JobHandler> handlers;

    public JobService(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            ObjectProvider<JobHandler> handlers) {
        this.repository = repository;
        this.handlers = handlers;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Đặt một việc vào hàng đợi.
     *
     * <p>Khi đã có job cùng {@code dedupKey} <b>chưa kết thúc</b>, lời gọi này trả về chính job đó
     * thay vì tạo thêm — người dùng bấm nút hai lần không sinh ra hai bản kết xuất, và job theo lịch
     * không chồng lên lượt chạy trước còn dở (§6.3).
     *
     * <p>⚠ <b>Cố ý KHÔNG đặt {@code @Transactional} lên phương thức này</b>, và cố ý kiểm tra trùng
     * <i>trước</i> khi ghi. Chỉ mục {@code uq_jobs_dedup_active} vẫn là chốt chặn cuối, nhưng khi nó
     * bắn ra thì transaction hiện tại đã bị PostgreSQL đánh dấu hỏng — <b>mọi câu lệnh tiếp theo
     * trong cùng transaction đó đều bị từ chối</b>, kể cả câu truy vấn để tìm job đang chạy. Nên phần
     * ghi nằm trong transaction riêng, và lượt tra sau khi va chỉ mục chạy trên transaction sạch.
     *
     * @param dedupKey khoá chống trùng, hoặc {@code null} nếu cho phép chạy song song nhiều bản
     * @param maxAttempts số lần thử, hoặc {@code null} để lấy theo {@link JobHandler#maxAttempts()}
     *     của loại việc này — dạng ba đối số {@link #enqueue(String, String, String)} đọc rõ hơn
     */
    public Job enqueue(String jobType, String payload, String dedupKey, Short maxAttempts) {
        Optional<Job> running = findActiveByDedupKey(dedupKey);
        if (running.isPresent()) {
            log.debug(
                    "Job {} đã có bản đang chạy — dùng lại {}",
                    dedupKey,
                    running.get().getPublicId());
            return running.get();
        }

        Job job = new Job(jobType, payload);
        job.setDedupKey(dedupKey);
        job.setMaxAttempts(soLanThu(jobType, maxAttempts));
        job.setTraceId(RequestContext.traceId());
        AuthContext.current().ifPresent(user -> {
            job.setRequestedBy(user.userId());
            job.setOrgUnitId(user.orgUnitId());
        });

        try {
            Job saved = insertInOwnTransaction(job);
            log.info("Đặt job {} loại {} (dedup={})", saved.getPublicId(), jobType, dedupKey);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Hai request lọt qua bước kiểm tra cùng lúc. Hiếm, nhưng chỉ mục vẫn giữ đúng bất biến:
            // người thua cuộc theo dõi job của người thắng.
            return findActiveByDedupKey(dedupKey).orElseThrow(() -> e);
        }
    }

    /**
     * Đặt việc, số lần thử lấy theo {@link JobHandler#maxAttempts()} của loại việc — T68.30.
     *
     * <p>Dùng dạng này khi nơi đặt việc <b>⛔ có lý do riêng</b> để khác handler. Chép lại con số
     * handler đã khai là dựng một cặp phải nhớ bằng tay (luật 14).
     */
    public Job enqueue(String jobType, String payload, String dedupKey) {
        return enqueue(jobType, payload, dedupKey, null);
    }

    /**
     * ⭐ T68.30 — <b>người đọc duy nhất</b> của {@link JobHandler#maxAttempts()}.
     *
     * <p>Nơi đặt việc khai số thì số ấy thắng; ⛔ khai thì hỏi handler; ⛔ có handler nào nhận loại
     * việc này thì về mặc định SPI.
     *
     * <p>⚠ Ca cuối ⛔ phải giả định: một job có thể được đặt cho loại việc mà module cấp handler
     * <b>chưa</b> nằm trên classpath (bộ kiểm nhắm mục tiêu, một module tắt bằng cấu hình). Ném ở đây
     * là làm đường đặt việc gãy vì một thứ chỉ ảnh hưởng tới <i>số lần thử</i> — nên rơi về mặc định,
     * và {@code JobWorker} vẫn là nơi báo *⛔ có handler* lúc việc được nhặt lên.
     */
    private short soLanThu(String jobType, Short noiDatViecKhai) {
        if (noiDatViecKhai != null) {
            return noiDatViecKhai;
        }
        return handlers.stream()
                .filter(handler -> handler.jobType().equals(jobType))
                .findFirst()
                .map(JobHandler::maxAttempts)
                .orElse(JobHandler.MAC_DINH_SO_LAN_THU);
    }

    private Job insertInOwnTransaction(Job job) {
        return transactions.execute(status -> repository.saveAndFlush(job));
    }

    @Transactional(readOnly = true)
    public Job get(UUID publicId) {
        return repository.findByPublicId(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /**
     * Tra job của <b>chính mình</b>.
     *
     * <p>Không cho tra job của người khác: kết quả job hay chứa dữ liệu nghiệp vụ (đường tải bản kết
     * xuất), mà {@code publicId} thì đoán không ra nhưng vẫn có thể bị chuyền tay. Quản trị viên tra
     * job của người khác đi qua màn hình quản trị với quyền riêng.
     */
    @Transactional(readOnly = true)
    public Job getOwn(UUID publicId) {
        // Cùng mã lỗi với "không tìm thấy" — trả 403 ở đây là xác nhận job đó có thật.
        return findOwn(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /**
     * Luật "job của chính mình" nằm ở ĐÂY và chỉ ở đây.
     *
     * <p>{@link #getOwn} và {@code findJob} là hai hình dạng trả về của cùng một luật — ném lỗi và
     * trả {@code Optional}. Viết luật hai lần là để hai bản lệch nhau, mà đây là luật bảo mật.
     *
     * <p>Cố ý <b>không</b> gắn {@code @Transactional}: phương thức private gọi từ trong cùng lớp
     * không đi qua proxy Spring nên annotation ở đây chỉ gây hiểu nhầm. Ranh giới giao dịch do hai
     * phương thức công khai gọi nó mang.
     */
    private Optional<Job> findOwn(UUID publicId) {
        Long currentUserId =
                AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
        return repository
                .findByPublicId(publicId)
                .filter(job ->
                        job.getRequestedBy() == null || job.getRequestedBy().equals(currentUserId));
    }

    // ---- Hợp đồng cho module nghiệp vụ (core.spi) -------------------------------
    //
    // `enqueue` và `findJob` dưới đây trả `JobRef` thay vì entity `Job`, vì module nghiệp vụ không
    // được import `core.domain.job` (core/spi/package-info.java).
    //
    // ⚠ `findJob` dựa trên `getOwn`, KHÔNG phải `get`: module nghiệp vụ tra tiến độ là tra hộ người
    // đang đăng nhập, và kết quả job hay chứa đường tải bản kết xuất. Dùng `get` ở đây là mở một
    // đường vòng qua đúng cái kiểm tra mà `getOwn` sinh ra để làm.

    @Override
    public JobRef enqueue(JobRequest request) {
        return toRef(enqueue(request.jobType(), request.payload(), request.dedupKey(), request.maxAttempts()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobRef> findJob(UUID publicId) {
        return findOwn(publicId).map(JobService::toRef);
    }

    private static JobRef toRef(Job job) {
        return new JobRef(
                job.getPublicId(),
                job.getJobType(),
                job.getStatus().name(),
                job.getAttempts() == null ? (short) 0 : job.getAttempts(),
                job.getCreatedAt(),
                job.getResult());
    }

    /**
     * ⚠ Cố ý <b>không</b> {@code @Transactional}, dù đây là một lượt đọc. {@link #enqueue} — người
     * gọi chính — cố ý chạy ngoài giao dịch (xem tài liệu của nó), nên nó gọi hàm này bằng
     * {@code this} và chú thích không có tác dụng. Một câu truy vấn đơn thì Spring Data tự mở giao
     * dịch của riêng nó, nên hành vi không đổi; giữ chú thích lại chỉ để nói một điều không đúng.
     */
    public Optional<Job> findActiveByDedupKey(String dedupKey) {
        if (dedupKey == null) {
            return Optional.empty();
        }
        return repository.findFirstByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES);
    }

    /** T61.24 — có job loại {@code jobType} đang chờ hoặc đang chạy. */
    public boolean coViecDangCho(String jobType) {
        return repository.existsByJobTypeAndStatusIn(jobType, ACTIVE_STATUSES);
    }

    /** Số việc đang tồn đọng — nguồn cho metric và health-check (WS-7). */
    @Transactional(readOnly = true)
    public long backlogSize() {
        return repository.countByStatusIn(ACTIVE_STATUSES);
    }

    /** Dọn job đã xong quá hạn giữ. Gọi từ job bảo trì định kỳ. */
    @Transactional
    public int purgeFinishedBefore(Instant cutoff) {
        return repository.deleteFinishedBefore(cutoff);
    }
}
