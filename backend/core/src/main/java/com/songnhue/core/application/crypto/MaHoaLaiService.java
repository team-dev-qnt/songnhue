package com.songnhue.core.application.crypto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.songnhue.core.application.job.JobService;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.common.config.CryptoProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.domain.job.Job;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.core.spi.MaHoaLaiPort;

/**
 * Hoàn tất một lượt xoay khoá AES — T61.11, trả nợ T51.9 (<i>"job xoay khoá vẫn có 0 tệp"</i>).
 *
 * <h2>Người vận hành làm gì</h2>
 *
 * Thêm {@code AES_KEY_V2}, <b>giữ</b> {@code AES_KEY_V1}, đổi {@code AES_KEY_ID=v2}, khởi động lại.
 * {@link #kiemKhiKhoiDong()} đếm hàng còn khoá cũ và tự đặt job {@code CRYPTO_REENCRYPT} (khoá chống trùng
 * ⇒ nhiều node / nhiều lượt khởi động vẫn một job). ⛔ Có nút bấm nào để quên.
 *
 * <h2>⛔ Vì sao ⛔ chờ job xong mới an toàn — và vì sao vẫn cần job</h2>
 *
 * Phép chống trùng CCCD so với vân tay dưới <b>mọi</b> khoá đang nạp
 * ({@code CryptoService.fingerprintsForAllKeys}) nên nó đúng NGAY sau khi đổi khoá. Job cần cho hai việc
 * khác: chỉ mục {@code uq_employee_sensitive_cccd} (so trên cả chuỗi, chốt chặn cuối khi hai lượt lưu đua
 * nhau) chỉ có hiệu lực lại khi cả cột về một khoá; và khoá cũ chỉ gỡ được khi ⛔ còn bản mã nào dùng nó.
 *
 * <h2>Kết thúc ⛔ im lặng</h2>
 *
 * Còn hàng ⛔ đổi được (bản mã hỏng, thiếu khoá, hai hồ sơ trùng CCCD lọt vào trước bản vá) ⇒ job
 * {@code FAILED} với {@code ADM-2019} mang số đếm, và {@code jobs.last_error} đọc được câu ấy
 * ({@code JobWorker.moTaLoi}). Một job xanh khi còn hàng khoá cũ là lời mời gỡ khoá cũ — tức mất dữ liệu.
 */
@Component
public class MaHoaLaiService implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(MaHoaLaiService.class);

    static final String KHOA_CHONG_TRUNG = JobTypes.CRYPTO_REENCRYPT;
    static final int CO_LO = 200;

    /**
     * Vòng tối đa. Một vòng = quét hết mọi bảng một lượt. Hàng bị người dùng lưu đè giữa chừng (ghi lại
     * bản mã khoá cũ bằng entity nạp trước đó) được vòng sau nhặt lại.
     */
    static final int SO_VONG_TOI_DA = 5;

    private final List<MaHoaLaiPort> bang;
    private final CryptoProperties khoa;
    private final JobService jobs;

    public MaHoaLaiService(List<MaHoaLaiPort> bang, CryptoProperties khoa, JobService jobs) {
        this.bang = bang;
        this.khoa = khoa;
        this.jobs = jobs;
    }

    @Override
    public String jobType() {
        return JobTypes.CRYPTO_REENCRYPT;
    }

    @Override
    public short maxAttempts() {
        return 1; // hàng hỏng thì thử lại vẫn hỏng; khởi động lại kế tiếp sẽ đặt job mới
    }

    @EventListener(ApplicationReadyEvent.class)
    public void kiemKhiKhoiDong() {
        try {
            kichHoatNeuCan();
        } catch (RuntimeException e) {
            // ⛔ Làm sập lượt khởi động vì một lượt ĐẾM là đổi một khoản nợ lấy một sự cố.
            log.error("⚠ Không kiểm được bản mã còn dùng khoá cũ (T61.11) — xoay khoá có thể dang dở", e);
        }
    }

    /** @return job vừa đặt (hoặc job đang chạy cùng khoá chống trùng); rỗng khi ⛔ còn hàng nào mang khoá cũ */
    public Optional<Job> kichHoatNeuCan() {
        Map<String, Long> con = conKhoaCu();
        long tong = con.values().stream().mapToLong(Long::longValue).sum();
        if (tong == 0) {
            return Optional.empty();
        }
        log.warn(
                "Còn {} hàng mang khoá mã hoá KHÁC khoá đang dùng '{}' {} — đặt job {}",
                tong,
                khoa.activeKeyId(),
                con,
                JobTypes.CRYPTO_REENCRYPT);
        return Optional.of(jobs.enqueue(JobTypes.CRYPTO_REENCRYPT, "{}", KHOA_CHONG_TRUNG, maxAttempts()));
    }

    /** Số hàng còn khoá cũ theo từng bảng — thứ runbook bảo đo trước khi gỡ khoá cũ. */
    public Map<String, Long> conKhoaCu() {
        Map<String, Long> ket = new LinkedHashMap<>();
        for (MaHoaLaiPort b : bang) {
            ket.put(b.bang(), b.demConKhoaCu(khoa.activeKeyId()));
        }
        return ket;
    }

    @Override
    public void handle(JobContext context) {
        KetQua kq = chay(context::progress);
        context.resultJson(kq.json());
        if (kq.conLai() > 0) {
            throw new BusinessRuleException(ErrorCode.ADM_2019, kq.khoa(), kq.soDoi(), kq.conLai());
        }
    }

    /** Chạy trọn các vòng. Tách khỏi {@link #handle} để bài kiểm gọi thẳng, ⛔ cần worker. */
    public KetQua chay(IntConsumer tienDo) {
        String k = khoa.activeKeyId();
        long banDau = conKhoaCu().values().stream().mapToLong(Long::longValue).sum();
        int doi = 0;
        int hong = 0;
        for (int vong = 1; vong <= SO_VONG_TOI_DA; vong++) {
            int doiVongNay = 0;
            hong = 0; // chỉ vòng CUỐI nói hàng nào hỏng thật — vòng trước có thể chỉ là đua với người dùng
            for (MaHoaLaiPort b : bang) {
                long sau = 0;
                MaHoaLaiPort.Lo lo;
                do {
                    lo = b.maHoaLai(k, sau, CO_LO);
                    sau = lo.idCuoi();
                    doiVongNay += lo.soDoi();
                    hong += lo.soHong();
                    if (banDau > 0) {
                        tienDo.accept((int) Math.min(99, (doi + doiVongNay) * 100 / banDau));
                    }
                } while (lo.soHang() > 0);
            }
            doi += doiVongNay;
            if (doiVongNay == 0) {
                break; // ⛔ còn tiến triển ⇒ vòng nữa chỉ lặp lại cùng hàng hỏng
            }
        }
        Map<String, Long> con = conKhoaCu();
        long conLai = con.values().stream().mapToLong(Long::longValue).sum();
        if (conLai > 0) {
            log.error(
                    "⚠ Mã hoá lại sang '{}' xong {} hàng, CÒN {} hàng khoá cũ {} — ⛔ gỡ khoá cũ", k, doi, conLai, con);
        } else {
            log.info("Mã hoá lại sang '{}' xong {} hàng — ⛔ còn bản mã nào dùng khoá cũ", k, doi);
        }
        tienDo.accept(100);
        return new KetQua(k, doi, hong, conLai, con);
    }

    /**
     * @param soHong số hàng hỏng ở vòng cuối
     * @param conLai số hàng còn khoá cũ đo SAU khi chạy — con số quyết định job đạt hay hỏng
     */
    public record KetQua(String khoa, int soDoi, int soHong, long conLai, Map<String, Long> theoBang) {
        String json() {
            StringBuilder b = new StringBuilder("{");
            theoBang.forEach((ten, n) -> b.append(b.length() > 1 ? "," : "")
                    .append('"')
                    .append(ten)
                    .append("\":")
                    .append(n));
            return "{\"khoa\":\"%s\",\"soDoi\":%d,\"soHong\":%d,\"conLai\":%d,\"conLaiTheoBang\":%s}}"
                    .formatted(khoa, soDoi, soHong, conLai, b);
        }
    }
}
