package com.songnhue.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.content.infra.RecaptchaProperties;
import com.songnhue.core.spi.SettingPort;

/**
 * <b>Luật biểu mẫu liên hệ — phần logic thuần.</b> CN-01.4, T36.6 + T36.7.
 *
 * <h2>Vì sao nhánh captcha ở ĐÂY chứ ⛔ không ở bài kiểm HTTP</h2>
 *
 * <p>Ba khoá {@code site.contact.recaptcha.*} <b>chưa được seed</b> — cố ý: ⛔ không dòng mã nào của
 * cổng đọc chúng, và {@code PortalSettingsReadTest} đỏ đúng khi bản đầu seed chúng (quy tắc 15).
 * Nên một bài kiểm HTTP muốn bật captcha sẽ chạy một câu {@code UPDATE} tác động <b>0 hàng</b>, rồi
 * xanh vì captcha đang tắt — <i>xanh vì một lý do khác với lý do nó khẳng định</i>, đúng hình dạng
 * xanh giả mà dự án này đã gặp nhiều lần.
 *
 * <p>Ở tầng này thì {@code SettingPort} là mock, nên hai trạng thái <i>chưa bật</i> và <i>bật mà
 * thiếu khoá</i> phân biệt được thật (luật 9).
 */
class ContactFormPolicyTest {

    private SettingPort settings;
    private RecaptchaProperties recaptcha;
    private ContactFormPolicy luat;

    @BeforeEach
    void chuanBi() {
        settings = mock(SettingPort.class);
        recaptcha = new RecaptchaProperties();
        // ⚠ Mặc định: mọi khoá trả về CHÍNH giá trị fallback mà nơi gọi truyền vào — mô phỏng đúng
        //   trạng thái "khoá chưa seed", tức là trạng thái THẬT của ba khoá reCAPTCHA hôm nay.
        when(settings.getBoolean(org.mockito.ArgumentMatchers.anyString(), anyBoolean()))
                .thenAnswer(i -> i.getArgument(1));
        when(settings.getInt(org.mockito.ArgumentMatchers.anyString(), anyInt()))
                .thenAnswer(i -> i.getArgument(1));
        luat = new ContactFormPolicy(settings, recaptcha);
    }

    @Test
    @DisplayName("⭐ Mặc định: hiện ô điện thoại, ⛔ không trường nào bắt buộc, captcha TẮT")
    void macDinh() {
        assertThat(luat.hienDienThoai()).isTrue();
        assertThat(luat.emailBatBuoc()).isFalse();
        assertThat(luat.dienThoaiBatBuoc()).isFalse();
        assertThat(luat.captchaBatBuoc())
                .as("⛔ G13 chưa về ⇒ captcha phải TẮT, kể cả khi ⛔ không có hàng `settings` nào")
                .isFalse();
    }

    @Test
    @DisplayName("⭐⭐ Tắt ô Số điện thoại ⇒ email TỰ bắt buộc, và điện thoại thôi bắt buộc")
    void tatDienThoaiThiSuyRa() {
        when(settings.getBoolean(eq(ContactFormPolicy.KHOA_HIEN_DIEN_THOAI), anyBoolean()))
                .thenReturn(false);
        // ⚠ Cố ý bật `phone.required` để chứng minh nó bị BỎ QUA — một ô đã tắt ⛔ không thể bắt
        //   buộc, và tổ hợp ấy nếu để lọt sẽ khoá chết biểu mẫu.
        when(settings.getBoolean(eq(ContactFormPolicy.KHOA_DIEN_THOAI_BAT_BUOC), anyBoolean()))
                .thenReturn(true);

        assertThat(luat.emailBatBuoc())
                .as("⛔ `ck_contacts_lien_lac` đòi ít nhất một cách liên hệ ngược; tắt điện thoại "
                        + "mà ⛔ không suy ra điều này là để người dân điền xong rồi nhận lỗi ⛔ "
                        + "không sửa được")
                .isTrue();
        assertThat(luat.dienThoaiBatBuoc()).isFalse();
    }

    @Test
    @DisplayName("⛔⛔ Bật captcha mà THIẾU khoá bí mật ⇒ coi như CHƯA BẬT")
    void batCaptchaMaThieuKhoa() {
        when(settings.getBoolean(eq(ContactFormPolicy.KHOA_CAPTCHA_BAT), anyBoolean()))
                .thenReturn(true);
        // `RECAPTCHA_SECRET_KEY` rỗng — trạng thái thật hôm nay (G13 chưa về).

        assertThat(luat.captchaBatBuoc())
                .as("⛔ Chặn biểu mẫu vì một lỗi cấu hình của TA là làm đứt kênh phản ánh của "
                        + "người dân — kể cả phản ánh về sự cố công trình")
                .isFalse();
    }

    @Test
    @DisplayName("⭐⭐ Có ĐỦ công tắc lẫn khoá bí mật ⇒ captcha BẬT — vế phân biệt hai trạng thái")
    void duCaHaiThiBat() {
        when(settings.getBoolean(eq(ContactFormPolicy.KHOA_CAPTCHA_BAT), anyBoolean()))
                .thenReturn(true);
        recaptcha.setSecret("khoa-gia-cho-bai-kiem");

        // ⭐ ⛔ Không có vế này thì `captchaBatBuoc()` trả `false` ở MỌI đầu vào cũng xanh trọn vẹn —
        //    một khẳng định ⛔ không phân biệt được hai trạng thái thì ⛔ không khẳng định gì (luật 9).
        assertThat(luat.captchaBatBuoc()).isTrue();
    }

    @Test
    @DisplayName("⭐ Ngưỡng điểm là PHẦN TRĂM NGUYÊN, kẹp trong 0–100 — ⛔ không dùng số thực")
    void nguongDiemLaSoNguyen() {
        assertThat(luat.diemToiThieuPhanTram())
                .as("mặc định 50 = 0,5 — ngưỡng Google khuyến nghị")
                .isEqualTo(50);

        when(settings.getInt(eq(ContactFormPolicy.KHOA_CAPTCHA_DIEM_TOI_THIEU), anyInt()))
                .thenReturn(999);
        assertThat(luat.diemToiThieuPhanTram())
                .as("⛔ Một giá trị ngoài dải phải bị KẸP, ⛔ không được truyền xuống thành một "
                        + "ngưỡng ⛔ không lượt gửi nào qua nổi")
                .isEqualTo(100);

        when(settings.getInt(eq(ContactFormPolicy.KHOA_CAPTCHA_DIEM_TOI_THIEU), anyInt()))
                .thenReturn(-5);
        assertThat(luat.diemToiThieuPhanTram()).isZero();
    }
}
