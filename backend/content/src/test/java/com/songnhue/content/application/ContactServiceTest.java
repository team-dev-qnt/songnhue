package com.songnhue.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.content.domain.Contact;
import com.songnhue.content.infra.ContactRepository;
import com.songnhue.content.infra.RecaptchaClient;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.SettingPort;

/**
 * <b>Luật kiểm tra của biểu mẫu liên hệ.</b> CN-01.4.
 *
 * <p>Bài này canh phần <i>quyết định nhận hay từ chối</i>; phần "đi qua HTTP có đúng mã trạng
 * thái không" là việc của {@code ContactHttpTest} ở module app. Hai bài canh hai thứ khác nhau
 * và không thay thế nhau được (luật 5).
 */
class ContactServiceTest {

    private ContactRepository kho;
    private NotificationPort thongBao;
    private JobPort hangDoi;
    private ContactService dichVu;

    @BeforeEach
    void chuanBi() {
        kho = mock(ContactRepository.class);
        when(kho.save(any(Contact.class))).thenAnswer(i -> i.getArgument(0));
        // ⚠ Bài này canh đúng phần QUYẾT ĐỊNH NHẬN HAY TỪ CHỐI. Phần quy trình và phần thư đi qua
        //   HTTP/hàng đợi thật ở `ContactWorkflowHttpTest` và `ContactEmailSlaHttpTest` (luật 5):
        //   mock một Workflow engine rồi khẳng định trạng thái đổi là kiểm chính cái mock.
        //
        // ⚠⚠ `settings` PHẢI là mock có hành vi: `tiepNhan` đọc công tắc thư xác nhận, và mock trần
        //   trả `false` cho `getBoolean` — bài kiểm sẽ vẫn xanh, nhưng nó ⛔ không còn đi qua nhánh
        //   đặt việc. Nói ra để lượt sau ⛔ không tưởng nhánh ấy đã được phủ ở đây.
        thongBao = mock(NotificationPort.class);
        hangDoi = mock(JobPort.class);
        SettingPort thamSo = mock(SettingPort.class);
        when(thamSo.getBoolean(any(), anyBoolean())).thenReturn(true);

        // ⚠ `luatBieuMau` là mock TRẦN ⇒ mọi vế bắt buộc trả `false` và captcha coi như tắt. Đó là
        //   cấu hình MẶC ĐỊNH, đúng thứ bài này muốn canh. Hai nhánh còn lại (bật bắt buộc, bật
        //   captcha) đi qua HTTP thật ở `ContactFormPolicyHttpTest` — luật 5.
        dichVu = new ContactService(
                kho, thongBao, hangDoi, thamSo, mock(ContactFormPolicy.class), mock(RecaptchaClient.class));
    }

    @Test
    @DisplayName("⭐ Đủ trường + có email → nhận")
    void duTruongThiNhan() {
        Contact c = dichVu.tiepNhan("Nguyễn Văn A", "a@example.invalid", null, "Chủ đề", "Nội dung", null);
        assertThat(c.getFullName()).isEqualTo("Nguyễn Văn A");
        verify(kho).save(any(Contact.class));
    }

    @Test
    @DisplayName("⭐ Chỉ có điện thoại vẫn nhận — email KHÔNG bắt buộc")
    void chiCoDienThoaiVanNhan() {
        Contact c = dichVu.tiepNhan("Trần Thị B", "  ", "0243354xxxx", "Chủ đề", "Nội dung", null);
        assertThat(c.getEmail()).as("chuỗi toàn khoảng trắng phải hoá null").isNull();
        assertThat(c.getPhone()).isEqualTo("0243354xxxx");
    }

    @Test
    @DisplayName("⛔ Không email lẫn điện thoại → từ chối, KHÔNG ghi gì")
    void thieuCaHaiDuongLienLac() {
        assertThatThrownBy(() -> dichVu.tiepNhan("A", null, null, "Chủ đề", "Nội dung", null))
                .isInstanceOf(ValidationException.class);
        verify(kho, never()).save(any());
    }

    @Test
    @DisplayName("⛔ Thiếu họ tên / tiêu đề / nội dung → từ chối từng trường một")
    void thieuTruongBatBuoc() {
        assertThatThrownBy(() -> dichVu.tiepNhan(null, "a@example.invalid", null, "Chủ đề", "Nội dung", null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> dichVu.tiepNhan("A", "a@example.invalid", null, "   ", "Nội dung", null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> dichVu.tiepNhan("A", "a@example.invalid", null, "Chủ đề", null, null))
                .isInstanceOf(ValidationException.class);
        verify(kho, never()).save(any());
    }

    @Test
    @DisplayName("⛔ Nội dung quá 5.000 ký tự → từ chối; cột TEXT không tự chặn gì")
    void noiDungQuaDai() {
        String dai = "x".repeat(5_001);
        assertThatThrownBy(() -> dichVu.tiepNhan("A", "a@example.invalid", null, "Chủ đề", dai, null))
                .isInstanceOf(ValidationException.class);
        verify(kho, never()).save(any());
    }

    @Test
    @DisplayName("⭐ Cắt ký tự điều khiển nhưng GIỮ xuống dòng và tab")
    void catKyTuDieuKhienGiuXuongDong() {
        Contact c = dichVu.tiepNhan("A", "a@example.invalid", null, "Chủ đề", "Dòng một\nDòng hai\tcó tab ", null);

        assertThat(c.getContent())
                .as("ký tự điều khiển làm hỏng bản xuất CSV và chèn được dòng giả vào nhật ký")
                .isEqualTo("Dòng một\nDòng hai\tcó tab");
    }

    @Test
    @DisplayName("⭐ Cắt khoảng trắng hai đầu của mọi trường")
    void catKhoangTrangHaiDau() {
        Contact c = dichVu.tiepNhan("  A  ", "  a@example.invalid  ", null, "  Chủ đề  ", "  Nội dung  ", null);
        assertThat(c.getFullName()).isEqualTo("A");
        assertThat(c.getEmail()).isEqualTo("a@example.invalid");
        assertThat(c.getSubject()).isEqualTo("Chủ đề");
        assertThat(c.getContent()).isEqualTo("Nội dung");
    }

    // === T36.3 — hai chiều thư của một lượt gửi biểu mẫu ======================

    @Test
    @DisplayName("⭐⭐ Nhận xong thì BÁO cán bộ và ĐẶT VIỆC gửi thư xác nhận — cùng một lượt")
    void nhanXongThiBaoVaDatViec() {
        dichVu.tiepNhan("Nguyễn Văn A", "a@example.invalid", null, "Chủ đề", "Nội dung", null);

        // ⛔ `targeted` chứ ⛔ không `alert`: cộng Ban điều hành vào mỗi lượt người dân điền biểu mẫu
        //    là cách chắc chắn nhất để vài tuần sau ⛔ không ai đọc thông báo nữa.
        var thu = org.mockito.ArgumentCaptor.forClass(com.songnhue.core.spi.NotifyRequest.class);
        verify(thongBao).notify(thu.capture());
        assertThat(thu.getValue().targetPermission()).isEqualTo(ContactService.QUYEN_XU_LY);
        assertThat(thu.getValue().relatedOrgUnitIds())
                .as("⛔ `targetPermission` đã khai thì `relatedOrgUnitIds` bị RecipientResolver bỏ qua "
                        + "— để rác ở đây là mời người sau tưởng nó có tác dụng")
                .isEmpty();

        var viec = org.mockito.ArgumentCaptor.forClass(com.songnhue.core.spi.JobRequest.class);
        verify(hangDoi).enqueue(viec.capture());
        assertThat(viec.getValue().payload())
                .as("⛔⛔ Payload nằm nguyên văn trong bảng `jobs` và lọt vào bản sao lưu — địa chỉ "
                        + "email của người dân ⛔ KHÔNG được chép thêm một bản vào đó (NĐ 13/2023)")
                .doesNotContain("a@example.invalid")
                .contains("contactPublicId");
    }

    @Test
    @DisplayName("⛔ Chỉ để lại điện thoại ⇒ vẫn báo cán bộ, nhưng ⛔ KHÔNG đặt việc gửi thư")
    void khongCoEmailThiKhongDatViecGuiThu() {
        dichVu.tiepNhan("Trần Thị B", null, "0243354xxxx", "Chủ đề", "Nội dung", null);

        verify(thongBao).notify(any(com.songnhue.core.spi.NotifyRequest.class));
        verify(hangDoi, never()).enqueue(any());
    }
}
