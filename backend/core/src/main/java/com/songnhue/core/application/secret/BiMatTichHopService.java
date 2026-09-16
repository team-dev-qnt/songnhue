package com.songnhue.core.application.secret;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.domain.secret.BiMatTichHop;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.secret.BiMatTichHopRepository;
import com.songnhue.core.spi.BiMatTichHopPort;
import com.songnhue.core.spi.LoaiBiMat;

/**
 * Bí mật tích hợp sửa trên giao diện — T61.44 (architecture-review.md §12.1).
 *
 * <p>Theo khuôn {@code ApiSourceService.datMaSo} (WS-28), thứ đã chạy thật với mã số thuỷ văn:
 *
 * <ul>
 *   <li><b>Ghi một chiều.</b> ⛔ Có API đọc lại giá trị, kể cả che một phần — {@link TinhTrang} chỉ nói có/⛔ có, nguồn
 *       nào, giải mã được ⛔.
 *   <li><b>Mã hoá ngay khi nhận</b>; giải mã lúc DÙNG. Bộ đệm 60 giây giữ bản rõ trong bộ nhớ tiến trình — cùng mức lộ
 *       với biến môi trường của chính tiến trình ấy, đổi lấy việc ⛔ giải mã mỗi lượt gửi biểu mẫu.
 *   <li><b>Sự kiện bảo mật DANGER</b> mỗi lượt đặt/xoá, và {@code EXTERNAL_CREDENTIAL_DECRYPT_FAILED} khi bản mã ⛔ khớp
 *       khoá (CSDL staging nhân bản từ production mang bản mã của khoá production — đúng ca này).
 * </ul>
 *
 * <p>⛔ Xác thực lại bằng mã 2FA + kiểm vai trò SUPER_ADMIN nằm ở controller ({@code CauHinhHeThongController}).
 */
@Service
public class BiMatTichHopService implements BiMatTichHopPort {

    private static final Logger log = LoggerFactory.getLogger(BiMatTichHopService.class);

    /** Khoá bí mật dịch vụ ngoài ⛔ dài quá vài trăm ký tự; trần chặn dán nhầm cả một tệp. */
    static final int DO_DAI_TOI_DA = 512;

    private final BiMatTichHopRepository repository;
    private final CryptoService crypto;
    private final SecurityEventService securityEvents;
    private final Environment environment;

    private final Cache<LoaiBiMat, Optional<String>> dem =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60)).build();

    public BiMatTichHopService(
            BiMatTichHopRepository repository,
            CryptoService crypto,
            SecurityEventService securityEvents,
            Environment environment) {
        this.repository = repository;
        this.crypto = crypto;
        this.securityEvents = securityEvents;
        this.environment = environment;
    }

    /** Nguồn đang có hiệu lực của một bí mật. */
    public enum Nguon {
        GIAO_DIEN,
        MOI_TRUONG,
        CHUA_CO
    }

    /**
     * @param nguon nguồn đang có hiệu lực
     * @param giaiMaDuoc {@code false} khi bảng có bản mã mà khoá AES hiện có ⛔ mở được — lúc ấy giá trị mồi ở
     *     {@code .env} (nếu có) ⛔ được dùng thay, để một lượt xoay khoá hỏng ⛔ lặng lẽ đổi sang khoá cũ
     * @param coGiaTriMoi {@code .env} còn giá trị mồi — xoá trên giao diện thì giá trị ấy lại có hiệu lực
     */
    public record TinhTrang(
            LoaiBiMat loai,
            String ten,
            String moTa,
            Nguon nguon,
            boolean giaiMaDuoc,
            boolean coGiaTriMoi,
            Instant capNhatLuc) {}

    @Transactional(readOnly = true)
    public List<TinhTrang> danhSach() {
        return Arrays.stream(LoaiBiMat.values()).map(this::tinhTrang).toList();
    }

    @Transactional(readOnly = true)
    public TinhTrang tinhTrang(LoaiBiMat loai) {
        boolean coMoi = !giaTriMoi(loai).isBlank();
        Optional<BiMatTichHop> hang = repository.findByLoai(loai);
        if (hang.isPresent()) {
            boolean giaiMa = giaiMaDuoc(hang.get());
            Instant luc = Optional.ofNullable(hang.get().getUpdatedAt())
                    .orElse(hang.get().getCreatedAt());
            return new TinhTrang(loai, loai.ten(), loai.moTa(), Nguon.GIAO_DIEN, giaiMa, coMoi, luc);
        }
        return new TinhTrang(
                loai, loai.ten(), loai.moTa(), coMoi ? Nguon.MOI_TRUONG : Nguon.CHUA_CO, true, coMoi, null);
    }

    @Override
    public Optional<String> giaTri(LoaiBiMat loai) {
        return dem.get(loai, this::docKhongDem);
    }

    private Optional<String> docKhongDem(LoaiBiMat loai) {
        Optional<BiMatTichHop> hang = repository.findByLoai(loai);
        if (hang.isPresent()) {
            try {
                return Optional.of(crypto.decrypt(hang.get().getCiphertext()));
            } catch (RuntimeException e) {
                // ⛔ KHÔNG rơi về giá trị mồi: bảng NÓI có bí mật, mà đọc ra hỏng là sự cố hạ tầng phải lộ ra.
                securityEvents.record(
                        SecurityEventType.EXTERNAL_CREDENTIAL_DECRYPT_FAILED,
                        null,
                        null,
                        ClientInfo.unknown(),
                        "{\"secret\":\"%s\",\"keyId\":\"%s\"}"
                                .formatted(
                                        loai.name(), crypto.keyIdOf(hang.get().getCiphertext())));
                log.error("Không giải mã được bí mật tích hợp {} — kiểm tra khoá AES", loai);
                return Optional.empty();
            }
        }
        String moi = giaTriMoi(loai);
        return moi.isBlank() ? Optional.empty() : Optional.of(moi);
    }

    /**
     * Đặt/thay một bí mật. Giá trị: ⛔ rỗng, ⛔ khoảng trắng hay ký tự điều khiển (dán kèm xuống dòng là lỗi thường
     * gặp nhất và nó làm dịch vụ ngoài từ chối trong im lặng), ≤ {@value #DO_DAI_TOI_DA} ký tự.
     */
    @Transactional
    public TinhTrang dat(LoaiBiMat loai, String giaTri) {
        String sach = giaTri == null ? "" : giaTri.strip();
        if (sach.isEmpty()
                || sach.length() > DO_DAI_TOI_DA
                || sach.codePoints().anyMatch(c -> c <= 0x20 || c == 0x7f)) {
            throw new BusinessRuleException(ErrorCode.SYS_0003);
        }
        String banMa = crypto.encrypt(sach);
        BiMatTichHop hang = repository.findByLoai(loai).orElse(null);
        String hanhDong = hang == null ? "DAT_LAN_DAU" : "THAY";
        if (hang == null) {
            hang = new BiMatTichHop(loai, banMa);
        } else {
            hang.doiBanMa(banMa);
        }
        repository.saveAndFlush(hang);
        xoaDemSauKhiGhi(loai);
        ghiSuKien(loai, hanhDong);
        return tinhTrang(loai);
    }

    @Transactional
    public TinhTrang xoa(LoaiBiMat loai) {
        repository.findByLoai(loai).ifPresent(hang -> {
            repository.delete(hang);
            repository.flush();
            ghiSuKien(loai, "XOA");
        });
        xoaDemSauKhiGhi(loai);
        return tinhTrang(loai);
    }

    /** Dọn đệm cả TRƯỚC và SAU commit: một lượt đọc chen giữa giao dịch sẽ nạp lại giá trị cũ thêm 60 giây. */
    private void xoaDemSauKhiGhi(LoaiBiMat loai) {
        dem.invalidate(loai);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    dem.invalidate(loai);
                }
            });
        }
    }

    private void ghiSuKien(LoaiBiMat loai, String hanhDong) {
        AuthenticatedUser ai = AuthContext.current().orElse(null);
        securityEvents.record(
                SecurityEventType.INTEGRATION_SECRET_CHANGED,
                ai == null ? null : ai.username(),
                ai == null ? null : ai.userId(),
                ClientInfo.unknown(),
                "{\"secret\":\"%s\",\"action\":\"%s\"}".formatted(loai.name(), hanhDong));
        log.info("Bí mật tích hợp {}: {}", loai, hanhDong);
    }

    private boolean giaiMaDuoc(BiMatTichHop hang) {
        try {
            crypto.decrypt(hang.getCiphertext());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String giaTriMoi(LoaiBiMat loai) {
        String v = environment.getProperty(loai.thuocTinh(), "");
        return v == null ? "" : v.strip();
    }
}
