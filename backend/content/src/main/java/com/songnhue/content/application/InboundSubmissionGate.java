package com.songnhue.content.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.songnhue.content.infra.RecaptchaClient;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.spi.BiMatTichHopPort;
import com.songnhue.core.spi.LoaiBiMat;
import com.songnhue.core.spi.SettingPort;
import com.songnhue.core.spi.VeBieuMauPort;

/**
 * <b>Cổng DUY NHẤT cho mọi thứ người lạ trên Internet gửi vào hệ thống</b> — T36.9.
 *
 * <h2>⛔⛔ Vì sao bảo đảm phải nằm ở CHỖ DỮ LIỆU ĐI QUA, ⛔ không ở nơi gọi</h2>
 *
 * <p>Luật 12, và nó có một hoá đơn: XSS lưu trữ lọt qua <b>2/3</b> đường ghi {@code settings} vì
 * bộ lọc được đặt ở hai trong ba nơi gọi thay vì ở chỗ dữ liệu đi qua. Cùng hình dạng lỗi đã lặp
 * lại ở T27.7 (ba điểm ghi được nối, điểm ghi <b>thứ tư</b> ra đời cùng đợt mang lại đúng lỗi cũ).
 *
 * <p>Trước lượt này, {@code ContactService} là đường công khai <b>duy nhất</b> nhận chữ, nên bốn
 * bảo đảm dưới đây sống trong chính nó và điều đó ⛔ không sai. Lượt T36.8 thêm đường <b>thứ
 * hai</b> ({@code FeedbackService}) — và đó chính là thời điểm một bảo đảm phải rời khỏi nơi gọi,
 * ⛔ không phải thời điểm chép nó sang.
 *
 * <h2>Bốn bảo đảm, và chúng ⛔ không thay thế nhau</h2>
 *
 * <ol>
 *   <li>{@link #chuanHoa} — cắt khoảng trắng, <b>loại ký tự điều khiển</b>, rỗng ⇒ {@code null}.
 *   <li>{@link #batBuoc} — trường thiếu trả {@code SYS-0003} kèm <i>tên trường</i>, ⛔ không phải
 *       một câu chung chung mà người dân ⛔ không biết sửa ô nào.
 *   <li>{@link #gioiHanDai} — cột {@code TEXT} ⛔ không chặn gì; ⛔ không có ngưỡng thì một lượt
 *       gửi nhét được vài megabyte vào một hàng và màn hình quản trị lãnh hậu quả.
 *   <li>{@link #kiemNguoiThat} — reCAPTCHA v3, <b>mặc định TẮT</b> (G13 chưa có khoá).
 * </ol>
 *
 * <p>⚠ Cái <b>không</b> nằm ở đây, và đó là cố ý: hạn mức tần suất. Nó do
 * {@code RateLimitFilter} lo trên tiền tố {@code /api/v1/public} — một bộ đếm thứ hai ở tầng này
 * là hai nơi phải nhớ, và cái ở dưới sẽ ⛔ không ai để ý khi cái ở trên đổi.
 *
 * <h2>⛔⛔ "Bật captcha mà thiếu khoá bí mật" = CHƯA BẬT, và phải KÊU</h2>
 *
 * <p>Ba lựa chọn, hai trong số đó sai — <b>chặn biểu mẫu</b> (một lỗi cấu hình của <i>ta</i> làm
 * đứt kênh phản ánh của người dân, kể cả phản ánh về sự cố công trình), <b>im lặng nhận</b> (một
 * công tắc bật trên màn hình mà ⛔ không làm gì, chỉ tệ hơn luật 15 vì nó <i>trông như</i> đã
 * đọc), và <b>nhận + ghi {@code ERROR} mỗi lượt</b>. ⇒ Chọn cái thứ ba: {@link #captchaBatBuoc()}
 * phân biệt được <i>chưa bật</i> với <i>bật mà hỏng</i> (luật 9) — hai trạng thái ấy trả cùng
 * {@code false} nhưng chỉ một trong hai ghi log.
 *
 * <h2>⚠ Khoá đổi tên từ {@code site.contact.recaptcha.*} sang {@code site.recaptcha.*}</h2>
 *
 * <p>Đổi được <b>miễn phí</b> và chỉ đúng ở lượt này: cả hai khoá cũ <b>chưa từng được seed</b> —
 * T36.6 đã gỡ chúng khỏi migration vì cổng chưa có nơi đọc. Chúng chỉ tồn tại dưới dạng chuỗi
 * trong mã, nên ⛔ không có hàng dữ liệu nào phải chuyển. Ngày G13 về mà khoá vẫn mang tiền tố
 * {@code contact} thì bật captcha cho trang Góp ý sẽ đòi <b>khoá thứ ba</b>, và lúc ấy đổi tên là
 * một migration đụng vào dữ liệu đang chạy.
 */
@Service
public class InboundSubmissionGate {

    private static final Logger log = LoggerFactory.getLogger(InboundSubmissionGate.class);

    /** ⚠ Cố ý ⛔ chưa seed — khoá VẮNG chính là "tắt". Xem javadoc lớp và {@code V202609061067}. */
    public static final String KHOA_CAPTCHA_BAT = "site.recaptcha.enabled";

    public static final String KHOA_CAPTCHA_DIEM_TOI_THIEU = "site.recaptcha.min-score-percent";

    private final SettingPort settings;
    private final RecaptchaClient captcha;
    private final BiMatTichHopPort khoa;
    private final VeBieuMauPort ve;

    public InboundSubmissionGate(
            SettingPort settings, RecaptchaClient captcha, BiMatTichHopPort khoa, VeBieuMauPort ve) {
        this.settings = settings;
        this.captcha = captcha;
        this.khoa = khoa;
        this.ve = ve;
    }

    /**
     * Cắt khoảng trắng hai đầu và <b>loại ký tự điều khiển</b>; rỗng ⇒ {@code null}.
     *
     * <p>Ký tự điều khiển ⛔ không phải chuyện thẩm mỹ: chúng làm hỏng bản xuất CSV về sau và có
     * thể chèn dòng giả vào nhật ký. Giữ {@code \n} và {@code \t} vì nội dung là văn bản nhiều
     * dòng thật.
     */
    public String chuanHoa(String s) {
        if (s == null) {
            return null;
        }
        String sach = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").trim();
        return sach.isEmpty() ? null : sach;
    }

    /** Trường bắt buộc — thiếu thì {@code SYS-0003} kèm <b>tên trường</b>. */
    public void batBuoc(String giaTri, String truong) {
        if (giaTri == null) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail(truong, "BAT_BUOC", "");
        }
    }

    /** Trần độ dài — cột {@code TEXT} ⛔ không chặn gì, xem javadoc lớp. */
    public void gioiHanDai(String giaTri, int toiDa, String truong) {
        if (giaTri != null && giaTri.length() > toiDa) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail(truong, "QUA_DAI", String.valueOf(toiDa));
        }
    }

    /**
     * Kiểm người thật — <b>⛔ không làm gì</b> khi captcha chưa bật.
     *
     * <p>⛔⛔ <b>HỎNG THÌ MỞ</b>: Google ⛔ không trả lời ⇒ cho qua. Đây là kênh phản ánh của người
     * dân về công trình thuỷ lợi; một sự cố mạng phía ta ⛔ không được biến thành <i>"⛔ không báo
     * được sạt kênh"</i>. Mã sai hoặc điểm thấp thì <b>ĐÓNG</b> — hai trạng thái ấy phân biệt được
     * ở {@link RecaptchaClient} bằng giá trị trả về so với ngoại lệ.
     *
     * <p>⭐ T73.9 (ASVS 11.1.2) — trước captcha là VÉ biểu mẫu: do máy chủ ký khi người dùng bắt đầu điền, phải đủ tuổi tối
     * thiểu và chưa quá 24 giờ ({@code VeBieuMauService}). Kiểm ở ĐÂY — chỗ cả liên hệ lẫn góp ý đi qua — chứ ⛔ ở
     * controller (luật 12). Vé ⛔ "hỏng thì mở" như captcha: nó ⛔ gọi mạng, ⛔ có sự cố phía ta nào để tha.
     *
     * @throws BusinessRuleException {@code CMS-2025} khi vé thiếu/giả/quá hạn hoặc gửi quá nhanh · {@code CMS-2021}
     *     khi Google <b>từ chối</b> mã
     */
    public void kiemNguoiThat(String maCaptcha, String veBieuMau) {
        ve.kiem(veBieuMau, java.time.Instant.now());
        if (captchaBatBuoc() && !captcha.hopLe(maCaptcha, diemToiThieuPhanTram())) {
            throw new BusinessRuleException(ErrorCode.CMS_2021);
        }
    }

    /**
     * ⚠ {@code false} có <b>hai</b> nguyên nhân ⛔ không giống nhau — xem javadoc lớp.
     *
     * <p>Công khai (⛔ không {@code private}) vì nó là câu trả lời cho <i>trạng thái cấu hình</i>,
     * và một bài kiểm cần khẳng định được cả hai nhánh.
     */
    /** Khoá thông báo quyền riêng tư — T61.39. ⛔ seed giá trị: nội dung là văn bản pháp lý của Công ty. */
    public static final String KHOA_THONG_BAO_RIENG_TU = "site.privacy.notice";

    /**
     * Cổng có đang công bố một thông báo quyền riêng tư ⛔ — <b>T61.39</b>.
     *
     * <p>⚠⚠ Đây là điều kiện để ô <i>đồng ý</i> trở thành BẮT BUỘC. Ngược lại — chưa có thông báo mà
     * vẫn bắt tick — là dựng ra một <b>bằng chứng đồng ý giả</b>: người dân đồng ý với một trang
     * RỖNG, và bản ghi mang một mốc thời gian trông như đã tuân thủ NĐ 13/2023.
     */
    public boolean coThongBaoRiengTu() {
        return settings.getString(KHOA_THONG_BAO_RIENG_TU)
                .filter(vb -> !vb.isBlank())
                .isPresent();
    }

    /**
     * Ép ô đồng ý khi — và chỉ khi — đã có thông báo.
     *
     * @return mốc thời gian đồng ý để lưu vào bản ghi; {@code null} khi cổng chưa có thông báo
     * @throws ValidationException {@code SYS-0003} khi có thông báo mà người gửi ⛔ tick
     */
    public java.time.Instant kiemDongY(Boolean dongY) {
        if (!coThongBaoRiengTu()) {
            return null;
        }
        if (!Boolean.TRUE.equals(dongY)) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail("dongY", "BAT_BUOC", "");
        }
        return java.time.Instant.now();
    }

    public boolean captchaBatBuoc() {
        if (!settings.getBoolean(KHOA_CAPTCHA_BAT, false)) {
            return false;
        }
        if (khoa.giaTri(LoaiBiMat.RECAPTCHA_SECRET_KEY).isEmpty()) {
            log.error(
                    "⛔ `{}` đang BẬT nhưng thiếu khoá bí mật reCAPTCHA (Quản trị › Cấu hình hệ thống, hoặc RECAPTCHA_SECRET_KEY) — mọi biểu mẫu "
                            + "công khai vẫn nhận (⛔ không chặn kênh phản ánh của người dân vì một lỗi cấu "
                            + "hình của ta), nhưng ⛔ KHÔNG có lớp chống spam nào ngoài hạn mức tần suất.",
                    KHOA_CAPTCHA_BAT);
            return false;
        }
        return true;
    }

    /**
     * Điểm tối thiểu tính theo <b>phần trăm nguyên</b> (50 = 0,5 — ngưỡng Google khuyến nghị).
     *
     * <p>⛔ ⛔ Cố ý ⛔ không trả {@code double}: quy tắc 2 cấm {@code float}/{@code double} ngoài gói
     * quan sát, ⛔ không có ngoại lệ <i>"chỗ này ⛔ không phải tiền"</i> — mỗi ngoại lệ là một chỗ
     * người sau viện dẫn để mở thêm ngoại lệ nữa. Bản đầu của T36.6 trả {@code double} và
     * {@code CodingRuleTest.noBinaryFloatingPoint} đỏ ngay, <b>đỏ đúng</b>.
     */
    public int diemToiThieuPhanTram() {
        return Math.clamp(settings.getInt(KHOA_CAPTCHA_DIEM_TOI_THIEU, 50), 0, 100);
    }
}
