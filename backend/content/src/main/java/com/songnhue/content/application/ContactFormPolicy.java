package com.songnhue.content.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.songnhue.content.infra.RecaptchaProperties;
import com.songnhue.core.spi.SettingPort;

/**
 * Luật của biểu mẫu liên hệ: trường nào hiện, trường nào bắt buộc, có kiểm captcha ⛔ không.
 * CN-01.4 — T36.6 + T36.7.
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
 * <h2>⛔⛔ "Bật captcha mà thiếu khoá bí mật" = CHƯA BẬT, và phải KÊU</h2>
 *
 * <p>Ba lựa chọn, và hai trong số đó sai:
 *
 * <ul>
 *   <li><b>Chặn biểu mẫu</b> — một lỗi cấu hình của <i>ta</i> làm đứt kênh phản ánh của người dân,
 *       kể cả phản ánh về sự cố công trình. Cái giá sai.
 *   <li><b>Im lặng nhận</b> — công tắc bật trên màn hình mà ⛔ không làm gì; đúng thứ luật 15 gọi là
 *       một công tắc ⛔ chưa ai đọc, chỉ tệ hơn vì nó <i>trông như</i> đã đọc.
 *   <li><b>Nhận, và ghi {@code ERROR} mỗi lượt</b> — trạng thái sai cấu hình <b>nhìn thấy được</b>.
 * </ul>
 *
 * <p>⇒ Chọn cái thứ ba. {@link #captchaBatBuoc()} phân biệt được <i>chưa bật</i> với <i>bật mà
 * hỏng</i> (luật 9) — hai trạng thái ấy trả cùng {@code false} nhưng chỉ một trong hai ghi log.
 */
@Service
public class ContactFormPolicy {

    private static final Logger log = LoggerFactory.getLogger(ContactFormPolicy.class);

    /** ⚠ Khớp từng chữ với migration {@code V202609061067} — luật 14, một khoá hai nơi nhớ. */
    public static final String KHOA_HIEN_DIEN_THOAI = "site.contact.field.phone.enabled";

    public static final String KHOA_EMAIL_BAT_BUOC = "site.contact.field.email.required";
    public static final String KHOA_DIEN_THOAI_BAT_BUOC = "site.contact.field.phone.required";
    public static final String KHOA_CAPTCHA_BAT = "site.contact.recaptcha.enabled";
    public static final String KHOA_CAPTCHA_DIEM_TOI_THIEU = "site.contact.recaptcha.min-score-percent";

    private final SettingPort settings;
    private final RecaptchaProperties recaptcha;

    public ContactFormPolicy(SettingPort settings, RecaptchaProperties recaptcha) {
        this.settings = settings;
        this.recaptcha = recaptcha;
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

    /**
     * Có phải kiểm captcha cho lượt gửi này ⛔ không.
     *
     * <p>⚠ {@code false} có <b>hai</b> nguyên nhân, và chúng ⛔ không giống nhau: <i>Công ty chưa
     * bật</i> (im lặng, đúng ý) và <i>đã bật mà thiếu {@code RECAPTCHA_SECRET_KEY}</i> (ghi
     * {@code ERROR}). Xem javadoc lớp.
     */
    public boolean captchaBatBuoc() {
        if (!settings.getBoolean(KHOA_CAPTCHA_BAT, false)) {
            return false;
        }
        if (!recaptcha.coKhoa()) {
            log.error(
                    "⛔ `{}` đang BẬT nhưng thiếu biến môi trường RECAPTCHA_SECRET_KEY — biểu mẫu "
                            + "liên hệ vẫn nhận (⛔ không chặn kênh phản ánh của người dân vì một lỗi "
                            + "cấu hình của ta), nhưng ⛔ KHÔNG có lớp chống spam nào ngoài hạn mức tần suất.",
                    KHOA_CAPTCHA_BAT);
            return false;
        }
        return true;
    }

    /**
     * Điểm tối thiểu, tính theo <b>phần trăm nguyên</b> (50 = 0,5 — ngưỡng Google khuyến nghị).
     *
     * <h2>⛔ Vì sao ⛔ KHÔNG trả {@code double}</h2>
     *
     * <p>Bản đầu trả {@code double} và luật ArchUnit {@code CodingRuleTest.noBinaryFloatingPoint}
     * đỏ ngay — <b>đỏ đúng</b>. Quy tắc 2 của dự án cấm {@code float}/{@code double} ngoài gói
     * quan sát, ⛔ không có ngoại lệ "chỗ này ⛔ không phải tiền": mỗi ngoại lệ là một chỗ người sau
     * viện dẫn để mở thêm một ngoại lệ nữa.
     *
     * <p>⭐ Và ở đây ⛔ không cần {@code double} thật: điểm của Google có <b>một chữ số thập
     * phân</b>, so sánh với ngưỡng là một phép so hai số nguyên nếu cả hai nhân 100. {@link
     * com.songnhue.content.infra.RecaptchaClient} đọc điểm bằng {@code BigDecimal} rồi so ở cùng
     * thang đó.
     */
    public int diemToiThieuPhanTram() {
        return Math.clamp(settings.getInt(KHOA_CAPTCHA_DIEM_TOI_THIEU, 50), 0, 100);
    }
}
