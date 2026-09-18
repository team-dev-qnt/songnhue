package com.songnhue.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.spi.SettingPort;

/**
 * <b>Hình dạng biểu mẫu liên hệ — phần logic thuần.</b> CN-01.4, T36.7.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * <p>Bài này ⛔ <b>không</b> còn kiểm captcha: reCAPTCHA đã chuyển sang
 * {@link InboundSubmissionGate} ở T36.9 vì nó là bảo đảm của <b>mọi</b> lượt gửi từ ngoài vào, ⛔
 * không riêng biểu mẫu liên hệ. Ba nhánh ấy nay ở {@code InboundSubmissionGateTest} — cùng nội
 * dung, cùng lý lẽ, chỉ đổi chỗ theo mã.
 */
class ContactFormPolicyTest {

    private SettingPort settings;
    private ContactFormPolicy luat;

    @BeforeEach
    void chuanBi() {
        settings = mock(SettingPort.class);
        // ⚠ Mặc định: mọi khoá trả về CHÍNH giá trị fallback mà nơi gọi truyền vào — mô phỏng đúng
        //   trạng thái "khoá chưa seed".
        when(settings.getBoolean(org.mockito.ArgumentMatchers.anyString(), anyBoolean()))
                .thenAnswer(i -> i.getArgument(1));
        luat = new ContactFormPolicy(settings);
    }

    /**
     * ⛔ <b>Đổi 08/09/2026 — T28.49.</b> Tên cũ: <i>"⛔ không trường nào bắt buộc"</i>.
     *
     * <p>Mock ở lớp này trả về <b>đối số thứ hai</b> của {@code getBoolean(khoá, mặcĐịnh)}, tức nó
     * đo đúng <b>mặc định trong MÃ</b> — trạng thái khi khoá ⛔ chưa seed. Mặc định của
     * {@code email.required} đổi {@code false} → {@code true} cùng lượt {@code V202609081071} đặt
     * lại hàng seed, để hai bên ⛔ không nói hai câu khác nhau (luật 3).
     */
    @Test
    @DisplayName("⭐ Mặc định T28.49: hiện đủ ba ô, và EMAIL bắt buộc")
    void macDinh() {
        assertThat(luat.hienDienThoai()).isTrue();
        assertThat(luat.hienHoTen()).isTrue();
        assertThat(luat.hienTieuDe()).isTrue();
        assertThat(luat.emailBatBuoc())
                .as("mặc định trong MÃ phải khớp `default_value` của hàng seed — lệch nhau thì một "
                        + "môi trường thiếu hàng settings lặng lẽ quay về chính sách CŨ")
                .isTrue();
        assertThat(luat.dienThoaiBatBuoc()).isFalse();
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
}
