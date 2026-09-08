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
 * <p>⛔ Cách sai là thêm một khoá kiểu {@code at-least-one} rồi trông chờ người vận hành đặt
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

    /** T28.49 — tắt ô Họ tên là cho phép gửi phản ánh ⛔ KHÔNG kèm danh tính. */
    public static final String KHOA_HIEN_HO_TEN = "site.contact.field.full-name.enabled";

    public static final String KHOA_HIEN_TIEU_DE = "site.contact.field.subject.enabled";

    private final SettingPort settings;

    public ContactFormPolicy(SettingPort settings) {
        this.settings = settings;
    }

    public boolean hienDienThoai() {
        return settings.getBoolean(KHOA_HIEN_DIEN_THOAI, true);
    }

    /**
     * ⭐ Tắt ô Số điện thoại ⇒ email bắt buộc, dù khoá {@code email.required} đang tắt.
     *
     * <p>⚠ Mặc định trong mã đổi {@code false} → <b>{@code true}</b> ngày 08/09/2026 (T28.49), để
     * <b>khớp</b> hàng seed vừa được {@code V202609081071} đặt lại. Luật 3 nói canh giá trị ĐÃ
     * GIẢI chứ ⛔ không canh mặc định — nhưng khi hai bên bất đồng thì một môi trường THIẾU hàng
     * settings (bảng chưa seed, hoặc ai đó xoá khoá) sẽ lặng lẽ quay về chính sách CŨ. Hai chỗ nói
     * cùng một câu thì trạng thái ấy ⛔ không tồn tại.
     */
    public boolean emailBatBuoc() {
        return settings.getBoolean(KHOA_EMAIL_BAT_BUOC, true) || !hienDienThoai();
    }

    /**
     * Có hiện ô <b>Họ và tên</b> ⛔ không — T28.49.
     *
     * <p>⛔ Tắt ô này ⛔ <b>không</b> làm liên hệ thành ẩn danh hoàn toàn: email vẫn bắt buộc, nên
     * Công ty vẫn trả lời được. Thứ mất đi là <i>danh tính tự khai</i>, và đó là điều kiện để một
     * người dân dám phản ánh việc họ ⛔ không muốn gắn tên mình vào.
     *
     * <p>⛔⛔ Email người gửi vẫn ⛔ <b>KHÔNG BAO GIỜ</b> được công bố ra cổng (NĐ 13/2023) — "tắt
     * ô Họ tên" là quyết định về <i>thu thập</i>, ⛔ không phải về <i>công bố</i>. Hai chuyện khác
     * nhau và chỉ chuyện thứ hai mới có thể vi phạm pháp luật.
     */
    public boolean hienHoTen() {
        return settings.getBoolean(KHOA_HIEN_HO_TEN, true);
    }

    /** Có hiện ô <b>Tiêu đề</b> ⛔ không — T28.49. Tắt thì người gửi đi thẳng vào nội dung. */
    public boolean hienTieuDe() {
        return settings.getBoolean(KHOA_HIEN_TIEU_DE, true);
    }

    /** ⛔ Ô đã tắt thì ⛔ không thể bắt buộc — trả {@code false} bất kể khoá đặt gì. */
    public boolean dienThoaiBatBuoc() {
        return hienDienThoai() && settings.getBoolean(KHOA_DIEN_THOAI_BAT_BUOC, false);
    }
}
