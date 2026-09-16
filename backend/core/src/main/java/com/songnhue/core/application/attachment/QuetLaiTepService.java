package com.songnhue.core.application.attachment;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.core.application.job.JobService;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.domain.attachment.Attachment;
import com.songnhue.core.domain.attachment.ScanStatus;
import com.songnhue.core.domain.job.Job;
import com.songnhue.core.infra.attachment.AttachmentRepository;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * Quét lại tệp tải lên TRƯỚC khi có máy quét — T61.24 (QuanTran chốt 15/09/2026: tự chạy khi khởi động).
 *
 * <h2>Vì sao</h2>
 *
 * Tới T61.4 ClamAV ⛔ chạy ở môi trường nào ⇒ mọi tệp đã tải lên — gồm tài liệu hồ sơ CBNV — mang
 * {@code SKIPPED} và vẫn {@code READY}. Bật ClamAV chỉ quét tệp MỚI; tệp cũ giữ {@code SKIPPED} vĩnh viễn
 * nếu ⛔ ai làm gì. Cùng lượt nhặt {@code ERROR} (máy quét hỏng hết lượt thử — {@link VirusScanHandler#khiHetLuotThu}).
 *
 * <h2>Cách chạy</h2>
 *
 * <ul>
 *   <li>{@link #kiemKhiKhoiDong()} — có máy quét + còn tệp + ⛔ có job {@code VIRUS_RESCAN} nào đang chờ/chạy ⇒
 *       đặt một job. ⛔ Có nút bấm nào để quên (tiền lệ {@code MaHoaLaiService}).
 *   <li>Mỗi job là MỘT ĐỢT: quét <b>tuần tự</b> (⛔ song song — mỗi tệp nạp trọn vào heap, tới 120 MB) cho tới
 *       khi hết {@link #NGAN_SACH}, rồi đặt đợt kế tiếp từ con trỏ id. Ngân sách &lt; {@code STALE_AFTER} 30′
 *       của worker — một job chạy quá mốc ấy bị trả về hàng đợi và chạy HAI lần song song.
 *   <li>Mỗi tệp: đọc trong giao dịch → quét NGOÀI giao dịch (tới 30 s, ⛔ giữ kết nối CSDL) → ghi trong
 *       giao dịch sau khi kiểm lại trạng thái (người dùng xoá tệp giữa chừng thì bỏ qua).
 * </ul>
 *
 * <h2>Kết cục mỗi tệp</h2>
 *
 * Sạch ⇒ {@code CLEAN}. Nhiễm ⇒ {@code INFECTED} + {@code QUARANTINED} — ⚠ tệp đang tải xuống được sẽ
 * <b>thôi</b> tải được, và đó là điều đúng. Máy quét ⛔ kết luận được ⇒ <b>giữ nguyên</b>: một tệp cũ đang
 * {@code READY} ⛔ bị khoá chỉ vì máy quét trục trặc; lượt khởi động sau nhặt lại.
 *
 * <p>Máy quét chết hẳn (mọi tệp của một đợt đều lỗi) ⇒ DỪNG chuỗi, ⛔ đi hết kho với 30 s chờ mỗi tệp.
 */
@Component
public class QuetLaiTepService implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(QuetLaiTepService.class);

    static final List<ScanStatus> CAN_QUET_LAI = List.of(ScanStatus.SKIPPED, ScanStatus.ERROR);
    static final int CO_LO = 50;
    static final Duration NGAN_SACH = Duration.ofMinutes(10);
    /** Số tệp lỗi liên tiếp tối thiểu để kết luận "máy quét chết", ⛔ phải một tệp hỏng lẻ. */
    static final int LOI_LIEN_TIEP_DUNG = 3;

    private final AttachmentRepository repository;
    private final VirusScanHandler mayQuet;
    private final JobService jobs;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate giaoDich;

    public QuetLaiTepService(
            AttachmentRepository repository,
            VirusScanHandler mayQuet,
            JobService jobs,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.mayQuet = mayQuet;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.giaoDich = new TransactionTemplate(transactionManager);
        this.giaoDich.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public String jobType() {
        return JobTypes.VIRUS_RESCAN;
    }

    /**
     * 3 lượt (worker lùi 1′ rồi 5′). ⛔⛔ Đừng hạ về 1: ở lượt deploy ĐẦU TIÊN {@code app} lên cùng lúc với
     * {@code clamav} ({@code depends_on: service_started}), mà clamd cần 1–2 phút nạp CSDL chữ ký ⇒ đợt đầu
     * gặp "máy quét chết" và — với 1 lượt — chuỗi dừng tới lần khởi động sau, tức đúng ngày bật ClamAV lại
     * ⛔ quét lại tệp nào. Thử lại một đợt là vô hại: tệp đã có kết luận rơi khỏi truy vấn.
     */
    @Override
    public short maxAttempts() {
        return 3;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void kiemKhiKhoiDong() {
        try {
            kichHoatNeuCan();
        } catch (RuntimeException e) {
            log.error("⚠ Không kiểm được tệp cần quét lại (T61.24)", e);
        }
    }

    /** @return job vừa đặt; rỗng khi chưa có máy quét, ⛔ còn tệp, hoặc đã có đợt đang chờ/chạy */
    public Optional<Job> kichHoatNeuCan() {
        if (!mayQuet.daCauHinh()) {
            return Optional.empty();
        }
        long con = repository.countByScanStatusInAndDeletedAtIsNull(CAN_QUET_LAI);
        if (con == 0 || jobs.coViecDangCho(JobTypes.VIRUS_RESCAN)) {
            return Optional.empty();
        }
        log.warn("Còn {} tệp {} — đặt job {}", con, CAN_QUET_LAI, JobTypes.VIRUS_RESCAN);
        return Optional.of(jobs.enqueue(JobTypes.VIRUS_RESCAN, payload(0), JobTypes.VIRUS_RESCAN, maxAttempts()));
    }

    @Override
    public void handle(JobContext context) {
        long sau = objectMapper.readTree(context.payload()).path("sau").asLong(0);
        KetQua kq = chayDot(sau, NGAN_SACH, context::progress);
        context.resultJson(kq.json());
        if (kq.conTiep()) {
            // Khoá chống trùng khác đợt hiện tại: đợt này còn RUNNING nên cùng khoá sẽ trả lại chính nó.
            jobs.enqueue(
                    JobTypes.VIRUS_RESCAN,
                    payload(kq.idCuoi()),
                    JobTypes.VIRUS_RESCAN + ":" + kq.idCuoi(),
                    maxAttempts());
        }
        if (kq.mayQuetChet()) {
            throw new IllegalStateException("Máy quét ⛔ trả lời được " + kq.loi() + " tệp liên tiếp — dừng quét lại");
        }
    }

    /** Một đợt. Tách khỏi {@link #handle} để bài kiểm gọi thẳng, ⛔ cần worker. */
    public KetQua chayDot(long sau, Duration nganSach, IntConsumer tienDo) {
        long batDau = System.nanoTime();
        long conTro = sau;
        int sach = 0;
        int nhiem = 0;
        int loi = 0;
        int loiLienTiep = 0;
        while (true) {
            List<Long> lo = repository.idCanQuetLai(CAN_QUET_LAI, conTro, Limit.of(CO_LO));
            if (lo.isEmpty()) {
                return new KetQua(sach, nhiem, loi, conTro, false, false);
            }
            for (Long id : lo) {
                if (Duration.ofNanos(System.nanoTime() - batDau).compareTo(nganSach) >= 0) {
                    return new KetQua(sach, nhiem, loi, conTro, true, false);
                }
                conTro = id;
                switch (quetMot(id)) {
                    case SACH -> {
                        sach++;
                        loiLienTiep = 0;
                    }
                    case NHIEM -> {
                        nhiem++;
                        loiLienTiep = 0;
                    }
                    case LOI -> {
                        loi++;
                        loiLienTiep++;
                    }
                    default -> loiLienTiep = 0; // BO_QUA
                }
                if (loiLienTiep >= LOI_LIEN_TIEP_DUNG && sach + nhiem == 0) {
                    log.error("⚠ Máy quét ⛔ trả lời {} tệp liên tiếp — dừng chuỗi quét lại", loiLienTiep);
                    return new KetQua(sach, nhiem, loi, conTro, false, true);
                }
            }
            tienDo.accept(Math.min(99, sach + nhiem + loi));
        }
    }

    enum KetCuc {
        SACH,
        NHIEM,
        LOI,
        BO_QUA
    }

    private KetCuc quetMot(long id) {
        Attachment doc = giaoDich.execute(s -> repository
                .findById(id)
                .filter(a -> a.getDeletedAt() == null && CAN_QUET_LAI.contains(a.getScanStatus()))
                .orElse(null));
        if (doc == null) {
            return KetCuc.BO_QUA;
        }
        String verdict;
        try {
            verdict = mayQuet.quet(doc);
        } catch (IOException | RuntimeException e) {
            log.warn("Quét lại tệp {} lỗi: {}", doc.getPublicId(), e.toString());
            return KetCuc.LOI;
        }
        VirusScanHandler.KetLuan ketLuan = VirusScanHandler.phanLoai(verdict);
        if (ketLuan == VirusScanHandler.KetLuan.LOI) {
            log.warn("Quét lại tệp {} — máy quét ⛔ kết luận được: {}", doc.getPublicId(), verdict);
            return KetCuc.LOI;
        }
        return giaoDich.execute(s -> repository
                .findById(id)
                .filter(a -> a.getDeletedAt() == null && CAN_QUET_LAI.contains(a.getScanStatus()))
                .map(a -> {
                    if (ketLuan == VirusScanHandler.KetLuan.SACH) {
                        a.markClean();
                        repository.save(a);
                        return KetCuc.SACH;
                    }
                    a.markInfected(verdict);
                    repository.save(a);
                    log.error("⚠ Quét lại: tệp CŨ {} nhiễm mã độc, đã cách ly: {}", a.getPublicId(), verdict);
                    return KetCuc.NHIEM;
                })
                .orElse(KetCuc.BO_QUA));
    }

    private static String payload(long sau) {
        return "{\"sau\":%d}".formatted(sau);
    }

    /** @param conTiep hết ngân sách giờ mà còn tệp ⇒ đặt đợt kế · @param mayQuetChet dừng chuỗi */
    public record KetQua(int sach, int nhiem, int loi, long idCuoi, boolean conTiep, boolean mayQuetChet) {
        String json() {
            return "{\"sach\":%d,\"nhiem\":%d,\"loi\":%d,\"idCuoi\":%d,\"conTiep\":%b,\"mayQuetChet\":%b}"
                    .formatted(sach, nhiem, loi, idCuoi, conTiep, mayQuetChet);
        }
    }
}
