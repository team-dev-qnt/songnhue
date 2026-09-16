package com.songnhue.content.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.songnhue.core.spi.BiMatTichHopPort;
import com.songnhue.core.spi.LoaiBiMat;
import com.songnhue.core.spi.MucCauHinh;
import com.songnhue.core.spi.MucCauHinh.MucDo;
import com.songnhue.core.spi.MucCauHinh.TrangThai;
import com.songnhue.core.spi.NguonTinhTrangCauHinh;
import com.songnhue.core.spi.SettingPort;

/**
 * Tình trạng cấu hình của MOD-01 — T61.41.
 *
 * <p>⚠ "Bật captcha mà thiếu khoá bí mật" là trạng thái {@link InboundSubmissionGate} cố ý cho qua (⛔ chặn kênh phản
 * ánh của người dân vì lỗi cấu hình của ta) và chỉ ghi ERROR vào log — nơi ⛔ ai đọc. Màn hình này là chỗ nó được nhìn
 * thấy.
 */
@Component
class TinhTrangCauHinhContent implements NguonTinhTrangCauHinh {

    private final SettingPort settings;
    private final BiMatTichHopPort biMat;

    TinhTrangCauHinhContent(SettingPort settings, BiMatTichHopPort biMat) {
        this.settings = settings;
        this.biMat = biMat;
    }

    @Override
    public List<MucCauHinh> mucCauHinh() {
        boolean bat = settings.getBoolean(InboundSubmissionGate.KHOA_CAPTCHA_BAT, false);
        boolean coKhoa = biMat.giaTri(LoaiBiMat.RECAPTCHA_SECRET_KEY).isPresent();
        TrangThai tt = !bat ? TrangThai.KHONG_AP_DUNG : (coKhoa ? TrangThai.DAT : TrangThai.SAI);
        return List.of(new MucCauHinh(
                "RECAPTCHA",
                "Cổng công khai",
                "Chống spam biểu mẫu liên hệ/góp ý (reCAPTCHA)",
                tt,
                MucDo.CANH_BAO,
                "Ứng dụng",
                "Cấu hình › site.recaptcha.enabled + Bí mật tích hợp › Khoá bí mật reCAPTCHA",
                switch (tt) {
                    case KHONG_AP_DUNG ->
                        "Chưa bật (G13 — Công ty chưa cấp khoá). Chỉ còn hạn mức tần suất chống spam.";
                    case DAT -> "Đang bật và có khoá bí mật.";
                    default -> "⛔ Đang BẬT mà thiếu khoá bí mật ⇒ captcha ⛔ chạy, biểu mẫu vẫn nhận mọi lượt gửi.";
                }));
    }
}
