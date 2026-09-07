package com.songnhue.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.content.infra.RecaptchaClient;
import com.songnhue.content.infra.RecaptchaProperties;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.spi.SettingPort;

/**
 * <b>Cổng chung cho mọi lượt gửi từ ngoài vào — phần logic thuần.</b> T36.6 + T36.9.
 *
 * <h2>Vì sao nhánh captcha ở ĐÂY chứ ⛔ không ở bài kiểm HTTP</h2>
 *
 * <p>Hai khoá {@code site.recaptcha.*} <b>chưa được seed</b> — cố ý: ⛔ không dòng mã nào của cổng
 * đọc chúng, và {@code PortalSettingsReadTest} đỏ đúng khi bản đầu T36.6 seed chúng (quy tắc 15).
 * Nên một bài kiểm HTTP muốn bật captcha sẽ chạy một câu {@code UPDATE} tác động <b>0 hàng</b>, rồi
 * xanh vì captcha đang tắt — <i>xanh vì một lý do khác với lý do nó khẳng định</i>, đúng hình dạng
 * xanh giả mà dự án này đã gặp nhiều lần.
 *
 * <p>Ở tầng này thì {@code SettingPort} là mock, nên hai trạng thái <i>chưa bật</i> và <i>bật mà
 * thiếu khoá</i> phân biệt được thật (luật 9).
 */
class InboundSubmissionGateTest {

    private SettingPort settings;
    private RecaptchaProperties khoa;
    private InboundSubmissionGate cong;

    @BeforeEach
    void chuanBi() {
        settings = mock(SettingPort.class);
        khoa = new RecaptchaProperties();
        when(settings.getBoolean(org.mockito.ArgumentMatchers.anyString(), anyBoolean()))
                .thenAnswer(i -> i.getArgument(1));
        when(settings.getInt(org.mockito.ArgumentMatchers.anyString(), anyInt()))
                .thenAnswer(i -> i.getArgument(1));
        cong = new InboundSubmissionGate(settings, mock(RecaptchaClient.class), khoa);
    }

    @Test
    @DisplayName("⭐ `chuanHoa` LOẠI ký tự điều khiển nhưng GIỮ xuống dòng và tab")
    void chuanHoaLoaiKyTuDieuKhien() {
        // ⚠ Vế "giữ xuống dòng" chịu lực: một bản vá cắt sạch MỌI ký tự điều khiển sẽ ép nội dung
        //   nhiều dòng của người dân thành một khối chữ liền, và ⛔ không có gì đỏ ở bất kỳ đâu.
        assertThat(cong.chuanHoa("  Kênh N4\nbị bồi\tlắng  ")).isEqualTo("Kênh N4\nbị bồi\tlắng");

        // \u0007 = BEL, \u0000 = NUL. Ký tự điều khiển chèn được dòng giả vào nhật ký và làm
        //   hỏng bản xuất CSV — bất kể ai gõ ra chúng.
        assertThat(cong.chuanHoa("K\u0007\u0000nh N4"))
                .as("⛔ Vế đối chứng: chuỗi ĐẦU VÀO có ký tự điều khiển thật, ⛔ không phải một "
                        + "chuỗi sạch sẵn — nếu không thì bài này xanh cả khi `chuanHoa` chỉ trim")
                .isEqualTo("Knh N4");

        assertThat(cong.chuanHoa("   "))
                .as("rỗng sau khi cắt ⇒ null, ⛔ không phải chuỗi rỗng")
                .isNull();
        assertThat(cong.chuanHoa(null)).isNull();
    }

    @Test
    @DisplayName("⭐ `gioiHanDai` chặn ĐÚNG chỗ vượt trần — và ⛔ không chặn đúng bằng trần")
    void gioiHanDaiChanDungCho() {
        assertThatCode(() -> cong.gioiHanDai("x".repeat(10), 10, "content"))
                .as("⛔ Đúng bằng trần là HỢP LỆ — một `>=` ở đây làm mọi lượt gửi ở biên đỏ nhầm")
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> cong.gioiHanDai("x".repeat(11), 10, "content"))
                .isInstanceOf(ValidationException.class);
        assertThatCode(() -> cong.gioiHanDai(null, 10, "content"))
                .as("null là việc của `batBuoc`, ⛔ không phải của trần độ dài")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("⛔ Mặc định: captcha TẮT — G13 chưa về")
    void macDinhCaptchaTat() {
        assertThat(cong.captchaBatBuoc())
                .as("⛔ G13 chưa về ⇒ captcha phải TẮT, kể cả khi ⛔ không có hàng `settings` nào")
                .isFalse();
    }

    @Test
    @DisplayName("⛔⛔ Bật captcha mà THIẾU khoá bí mật ⇒ coi như CHƯA BẬT")
    void batCaptchaMaThieuKhoa() {
        when(settings.getBoolean(eq(InboundSubmissionGate.KHOA_CAPTCHA_BAT), anyBoolean()))
                .thenReturn(true);
        // `RECAPTCHA_SECRET_KEY` rỗng — trạng thái thật hôm nay (G13 chưa về).

        assertThat(cong.captchaBatBuoc())
                .as("⛔ Chặn biểu mẫu vì một lỗi cấu hình của TA là làm đứt kênh phản ánh của "
                        + "người dân — kể cả phản ánh về sự cố công trình")
                .isFalse();
    }

    @Test
    @DisplayName("⭐⭐ Có ĐỦ công tắc lẫn khoá bí mật ⇒ captcha BẬT — vế phân biệt hai trạng thái")
    void duCaHaiThiBat() {
        when(settings.getBoolean(eq(InboundSubmissionGate.KHOA_CAPTCHA_BAT), anyBoolean()))
                .thenReturn(true);
        khoa.setSecret("khoa-gia-cho-bai-kiem");

        // ⭐ ⛔ Không có vế này thì `captchaBatBuoc()` trả `false` ở MỌI đầu vào cũng xanh trọn vẹn —
        //    một khẳng định ⛔ không phân biệt được hai trạng thái thì ⛔ không khẳng định gì (luật 9).
        assertThat(cong.captchaBatBuoc()).isTrue();
    }

    @Test
    @DisplayName("⭐ Ngưỡng điểm là PHẦN TRĂM NGUYÊN, kẹp trong 0–100 — ⛔ không dùng số thực")
    void nguongDiemLaSoNguyen() {
        assertThat(cong.diemToiThieuPhanTram())
                .as("mặc định 50 = 0,5 — ngưỡng Google khuyến nghị")
                .isEqualTo(50);

        when(settings.getInt(eq(InboundSubmissionGate.KHOA_CAPTCHA_DIEM_TOI_THIEU), anyInt()))
                .thenReturn(999);
        assertThat(cong.diemToiThieuPhanTram())
                .as("⛔ Một giá trị ngoài dải phải bị KẸP, ⛔ không được truyền xuống thành một "
                        + "ngưỡng ⛔ không lượt gửi nào qua nổi")
                .isEqualTo(100);

        when(settings.getInt(eq(InboundSubmissionGate.KHOA_CAPTCHA_DIEM_TOI_THIEU), anyInt()))
                .thenReturn(-5);
        assertThat(cong.diemToiThieuPhanTram()).isZero();
    }
}
