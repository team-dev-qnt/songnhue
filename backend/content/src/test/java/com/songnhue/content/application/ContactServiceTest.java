package com.songnhue.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.content.domain.Contact;
import com.songnhue.content.infra.ContactRepository;
import com.songnhue.core.common.exception.ValidationException;

/**
 * <b>Luật kiểm tra của biểu mẫu liên hệ.</b> CN-01.4.
 *
 * <p>Bài này canh phần <i>quyết định nhận hay từ chối</i>; phần "đi qua HTTP có đúng mã trạng
 * thái không" là việc của {@code ContactHttpTest} ở module app. Hai bài canh hai thứ khác nhau
 * và không thay thế nhau được (luật 5).
 */
class ContactServiceTest {

    private ContactRepository kho;
    private ContactService dichVu;

    @BeforeEach
    void chuanBi() {
        kho = mock(ContactRepository.class);
        when(kho.save(any(Contact.class))).thenAnswer(i -> i.getArgument(0));
        dichVu = new ContactService(kho);
    }

    @Test
    @DisplayName("⭐ Đủ trường + có email → nhận")
    void duTruongThiNhan() {
        Contact c = dichVu.tiepNhan("Nguyễn Văn A", "a@example.invalid", null, "Chủ đề", "Nội dung");
        assertThat(c.getFullName()).isEqualTo("Nguyễn Văn A");
        verify(kho).save(any(Contact.class));
    }

    @Test
    @DisplayName("⭐ Chỉ có điện thoại vẫn nhận — email KHÔNG bắt buộc")
    void chiCoDienThoaiVanNhan() {
        Contact c = dichVu.tiepNhan("Trần Thị B", "  ", "0243354xxxx", "Chủ đề", "Nội dung");
        assertThat(c.getEmail()).as("chuỗi toàn khoảng trắng phải hoá null").isNull();
        assertThat(c.getPhone()).isEqualTo("0243354xxxx");
    }

    @Test
    @DisplayName("⛔ Không email lẫn điện thoại → từ chối, KHÔNG ghi gì")
    void thieuCaHaiDuongLienLac() {
        assertThatThrownBy(() -> dichVu.tiepNhan("A", null, null, "Chủ đề", "Nội dung"))
                .isInstanceOf(ValidationException.class);
        verify(kho, never()).save(any());
    }

    @Test
    @DisplayName("⛔ Thiếu họ tên / tiêu đề / nội dung → từ chối từng trường một")
    void thieuTruongBatBuoc() {
        assertThatThrownBy(() -> dichVu.tiepNhan(null, "a@example.invalid", null, "Chủ đề", "Nội dung"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> dichVu.tiepNhan("A", "a@example.invalid", null, "   ", "Nội dung"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> dichVu.tiepNhan("A", "a@example.invalid", null, "Chủ đề", null))
                .isInstanceOf(ValidationException.class);
        verify(kho, never()).save(any());
    }

    @Test
    @DisplayName("⛔ Nội dung quá 5.000 ký tự → từ chối; cột TEXT không tự chặn gì")
    void noiDungQuaDai() {
        String dai = "x".repeat(5_001);
        assertThatThrownBy(() -> dichVu.tiepNhan("A", "a@example.invalid", null, "Chủ đề", dai))
                .isInstanceOf(ValidationException.class);
        verify(kho, never()).save(any());
    }

    @Test
    @DisplayName("⭐ Cắt ký tự điều khiển nhưng GIỮ xuống dòng và tab")
    void catKyTuDieuKhienGiuXuongDong() {
        Contact c = dichVu.tiepNhan("A", "a@example.invalid", null, "Chủ đề", "Dòng một\nDòng hai\tcó tab ");

        assertThat(c.getContent())
                .as("ký tự điều khiển làm hỏng bản xuất CSV và chèn được dòng giả vào nhật ký")
                .isEqualTo("Dòng một\nDòng hai\tcó tab");
    }

    @Test
    @DisplayName("⭐ Cắt khoảng trắng hai đầu của mọi trường")
    void catKhoangTrangHaiDau() {
        Contact c = dichVu.tiepNhan("  A  ", "  a@example.invalid  ", null, "  Chủ đề  ", "  Nội dung  ");
        assertThat(c.getFullName()).isEqualTo("A");
        assertThat(c.getEmail()).isEqualTo("a@example.invalid");
        assertThat(c.getSubject()).isEqualTo("Chủ đề");
        assertThat(c.getContent()).isEqualTo("Nội dung");
    }
}
