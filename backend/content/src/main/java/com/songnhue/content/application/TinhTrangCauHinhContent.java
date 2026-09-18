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

    TinhTrangCauHinhContent(SettingPort settings, BiMatTichHopPort biMat, InboundSubmissionGate cong) {
        this.settings = settings;
        this.biMat = biMat;
        this.cong = cong;
    }

    private final InboundSubmissionGate cong;

    @Override
    public List<MucCauHinh> mucCauHinh() {
        return List.of(mucCaptcha(), mucRiengTu());
    }

    /**
     * T61.39 — thông báo quyền riêng tư NĐ 13/2023.
     *
     * <p>Mức CẢNH BÁO chứ ⛔ CHẶN: cổng vẫn chạy được ⛔ có nó, nhưng mỗi lượt người dân gửi biểu mẫu là
     * một lượt thu thập dữ liệu cá nhân ⛔ thông báo — vi phạm Điều 13. Nội dung là văn bản của Công ty,
     * nên ⛔ có cách nào "tự vá" ngoài việc nói ra rằng nó đang thiếu.
     */
    private MucCauHinh mucRiengTu() {
        boolean co = cong.coThongBaoRiengTu();
        return new MucCauHinh(
                "QUYEN_RIENG_TU",
                "Cổng công khai",
                "Thông báo quyền riêng tư trên biểu mẫu (NĐ 13/2023)",
                co ? TrangThai.DAT : TrangThai.THIEU,
                MucDo.CANH_BAO,
                "Ứng dụng",
                "Cấu hình › site.privacy.notice (+ site.privacy.policy-url)",
                co
                        ? "Đang hiện trên biểu mẫu Liên hệ và Góp ý; ô đồng ý là BẮT BUỘC và thời điểm đồng ý được lưu."
                        : "⛔ Chưa có thông báo ⇒ biểu mẫu ⛔ hiện ô đồng ý (tick vào một thông báo RỖNG là bằng "
                                + "chứng đồng ý GIẢ). Nội dung do Công ty cung cấp — pháp chế.");
    }

    private MucCauHinh mucCaptcha() {
        boolean bat = settings.getBoolean(InboundSubmissionGate.KHOA_CAPTCHA_BAT, false);
        boolean coKhoa = biMat.giaTri(LoaiBiMat.RECAPTCHA_SECRET_KEY).isPresent();
        TrangThai tt = !bat ? TrangThai.KHONG_AP_DUNG : (coKhoa ? TrangThai.DAT : TrangThai.SAI);
        return new MucCauHinh(
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
                });
    }
}
