package com.songnhue.content.application;

import org.springframework.stereotype.Service;

import com.songnhue.core.spi.SettingPort;

/**
 * Luật của <b>biểu mẫu liên hệ</b>: trường nào hiện, trường nào bắt buộc. CN-01.4 — T36.7.
 *
 * <h2>⭐⭐ Một bất biến được SUY RA, ⛔ không phải một khoá thứ tư</h2>
 *
 * <p>{@code ck_contacts_lien_lac} đòi <b>ít nhất một</b> cách liên hệ ngược. Nên khi Công ty tắt ô
 * Số điện thoại, email <b>trở thành bắt buộc</b> — dù khoá {@code email.required} vẫn đang tắt.
 *
 * <p>⛔ Cách sai là thêm một khoá thứ tư kiểu {@code at-least-one} rồi trông chờ người vận hành đặt
 * ba khoá cho nhất quán: tổ hợp <i>"tắt điện thoại + email không bắt buộc"</i> sẽ để người dân điền
 * xong biểu mẫu rồi nhận một lỗi ⛔ không sửa được (biểu mẫu ⛔ không còn ô nào để điền cho hợp lệ).
 * Suy ra ở <b>một chỗ</b> — chính lớp này — thì cả giao diện lẫn tầng kiểm đều nhìn cùng một câu
 * trả lời, và tổ hợp chết ấy ⛔ không tồn tại.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28): lớp này ⛔ KHÔNG còn giữ captcha</h2>
 *
 * <p>reCAPTCHA đã chuyển sang {@link InboundSubmissionGate} ở T36.9 — nó là bảo đảm của <b>mọi</b>
 * lượt gửi từ ngoài vào, ⛔ không riêng biểu mẫu liên hệ, và {@code FeedbackService} là đường thứ
 * hai chứng minh điều đó. Ba khoá còn lại ở đây <b>chỉ</b> nói về hình dạng biểu mẫu liên hệ; ⛔
 * đừng thêm một luật dùng chung vào lớp này lần nữa.
 */
@Service
public class ContactFormPolicy {

    /** ⚠ Khớp từng chữ với migration {@code V202609061067} — luật 14, một khoá hai nơi nhớ. */
    public static final String KHOA_HIEN_DIEN_THOAI = "site.contact.field.phone.enabled";

    public static final String KHOA_EMAIL_BAT_BUOC = "site.contact.field.email.required";
    public static final String KHOA_DIEN_THOAI_BAT_BUOC = "site.contact.field.phone.required";

    private final SettingPort settings;

    public ContactFormPolicy(SettingPort settings) {
        this.settings = settings;
    }

    public boolean hienDienThoai() {
        return settings.getBoolean(KHOA_HIEN_DIEN_THOAI, true);
    }

    /** ⭐ Tắt ô Số điện thoại ⇒ email bắt buộc, dù khoá {@code email.required} đang tắt. */
    public boolean emailBatBuoc() {
        return settings.getBoolean(KHOA_EMAIL_BAT_BUOC, false) || !hienDienThoai();
    }

    /** ⛔ Ô đã tắt thì ⛔ không thể bắt buộc — trả {@code false} bất kể khoá đặt gì. */
    public boolean dienThoaiBatBuoc() {
        return hienDienThoai() && settings.getBoolean(KHOA_DIEN_THOAI_BAT_BUOC, false);
    }
}
