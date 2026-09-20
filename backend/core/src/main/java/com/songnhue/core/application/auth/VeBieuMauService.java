package com.songnhue.core.application.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.spi.VeBieuMauPort;

/**
 * <b>Vé biểu mẫu công khai — kiểm "tốc độ con người" (ASVS 11.1.2) — T73.9.</b>
 *
 * <p>Biểu mẫu liên hệ/góp ý xin một vé khi người dùng bắt đầu điền; lượt gửi phải mang vé ấy, và vé phải đủ <b>N giây tuổi</b>
 * ({@link #KHOA_GIAY_TOI_THIEU}) — một người điền biểu mẫu ⛔ gửi được trong chưa tới ba giây, một máy thì có. Trước
 * T73.9 thứ duy nhất chặn máy là hạn mức 10 lượt/giờ theo IP (T61.37); reCAPTCHA chờ khoá G13.
 *
 * <h2>Vì sao vé KÝ chứ ⛔ để máy khách tự khai mốc</h2>
 *
 * Một mốc do máy khách gửi lên thì máy giả được: gửi mốc của mười phút trước là qua. Vé mang mốc do MÁY CHỦ phát,
 * ký HMAC bằng khoá dẫn xuất riêng ({@link CryptoService#kyVeBieuMau}) — ⛔ thêm biến môi trường nào, ⛔ bảng nào.
 *
 * <h2>⚠ Phạm vi — nói ra (luật 28)</h2>
 *
 * <ul>
 *   <li>Vé ⛔ dùng một lần (⛔ lưu trạng thái): một máy xin vé, chờ đủ tuổi rồi dùng lại nhiều lượt trong 24 giờ. Chặn
 *       lượt lặp vẫn là việc của hạn mức IP; vé chặn lớp "gửi ngay khi tải trang" và lớp ⛔ tải trang.
 *   <li>Vé ⛔ gắn với IP hay phiên: phía cổng dựng trang từ IP của container (T61.17), gắn IP là khoá oan người thật.
 * </ul>
 */
@Service
public class VeBieuMauService implements VeBieuMauPort {

    /** Khoá {@code settings}: tuổi tối thiểu của vé (giây). 0 = ⛔ đòi tuổi, chỉ còn kiểm chữ ký và hạn. */
    public static final String KHOA_GIAY_TOI_THIEU = "security.form.min-fill-seconds";

    public static final int MAC_DINH_GIAY_TOI_THIEU = 3;

    /** Trần của {@link #KHOA_GIAY_TOI_THIEU} — phải bằng {@code max=} ở cột {@code validation} (V202609201094). */
    public static final int TRAN_GIAY_TOI_THIEU = 60;

    /** Vé quá tuổi này ⛔ còn hợp lệ — đủ dài cho người để trang mở cả buổi rồi mới viết. Hằng số bảo mật. */
    public static final Duration TUOI_TOI_DA = Duration.ofHours(24);

    private static final String PHIEN_BAN = "v1";

    /** Mốc là số giây epoch, tối đa 12 chữ số — kiểm dạng TRƯỚC khi parse, ⛔ bắt ngoại lệ rồi nuốt. */
    private static final java.util.regex.Pattern MOC = java.util.regex.Pattern.compile("[0-9]{1,12}");

    private final CryptoService crypto;
    private final SettingService settings;

    public VeBieuMauService(CryptoService crypto, SettingService settings) {
        this.crypto = crypto;
        this.settings = settings;
    }

    /** Vé phát lúc này. */
    @Override
    public String phat() {
        return phat(Instant.now());
    }

    /**
     * Vé mang mốc {@code moc}. Công khai để bài kiểm dựng được vé "đã đủ tuổi" mà ⛔ phải ngủ — mốc vẫn do máy chủ ký,
     * nên đây ⛔ phải đường cho máy khách chọn mốc.
     */
    public String phat(Instant moc) {
        String noiDung = PHIEN_BAN + "." + moc.getEpochSecond();
        return noiDung + "." + crypto.kyVeBieuMau(noiDung);
    }

    /**
     * @throws BusinessRuleException {@code CMS-2025} — thiếu vé, vé giả/hỏng, gửi quá nhanh, hoặc vé quá hạn. Cố ý
     *     MỘT mã cho mọi nhánh: phân biệt chúng chỉ giúp máy biết phải sửa gì.
     */
    @Override
    public void kiem(String ve, Instant now) {
        if (!hopLe(ve, now)) {
            throw new BusinessRuleException(ErrorCode.CMS_2025);
        }
    }

    boolean hopLe(String ve, Instant now) {
        if (ve == null) {
            return false;
        }
        String[] phan = ve.split("\\.", 3);
        if (phan.length != 3 || !PHIEN_BAN.equals(phan[0])) {
            return false;
        }
        if (!MOC.matcher(phan[1]).matches() || !crypto.dungChuKyVeBieuMau(phan[0] + "." + phan[1], phan[2])) {
            return false;
        }
        Duration tuoi = Duration.between(Instant.ofEpochSecond(Long.parseLong(phan[1])), now);
        return !tuoi.isNegative() && tuoi.compareTo(tuoiToiThieu()) >= 0 && tuoi.compareTo(TUOI_TOI_DA) <= 0;
    }

    @Override
    public long tuoiToiThieuGiay() {
        return tuoiToiThieu().toSeconds();
    }

    Duration tuoiToiThieu() {
        int giay = settings.getInt(KHOA_GIAY_TOI_THIEU, MAC_DINH_GIAY_TOI_THIEU);
        return Duration.ofSeconds(Math.max(0, Math.min(TRAN_GIAY_TOI_THIEU, giay)));
    }
}
