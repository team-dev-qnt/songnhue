package com.songnhue.content.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Đánh dấu đã đọc chỉ ghi dấu MỘT lần.</b> CN-01.4.
 *
 * <p>Màn hình quản trị gọi lượt đánh dấu mỗi khi người dùng mở một dòng — mở đi mở lại là chuyện
 * bình thường. Nếu mỗi lượt mở đều ghi đè {@code read_by}/{@code read_at} thì câu hỏi <i>"ai là
 * người đầu tiên thấy phản ánh này, lúc mấy giờ"</i> không còn trả lời được, mà đó chính là câu
 * hỏi duy nhất hai cột ấy sinh ra để trả lời.
 */
class ContactTest {

    private static Contact mau() {
        return new Contact("Nguyễn Văn A", "a@example.invalid", null, "Chủ đề", "Nội dung");
    }

    @Test
    @DisplayName("⭐ Bản ghi mới ở trạng thái MOI và chưa có dấu đọc")
    void moiTaoThiChuaDoc() {
        Contact c = mau();
        assertThat(c.getStatus()).isEqualTo(ContactStatus.MOI);
        assertThat(c.getReadAt()).isNull();
        assertThat(c.getReadBy()).isNull();
    }

    @Test
    @DisplayName("⭐ Lượt đọc đầu ghi cả người lẫn mốc thời gian")
    void luotDauGhiDau() {
        Contact c = mau();
        Instant luc = Instant.parse("2026-08-29T02:00:00Z");

        c.danhDauDaDoc(7L, luc);

        assertThat(c.getStatus()).isEqualTo(ContactStatus.DA_DOC);
        assertThat(c.getReadBy()).isEqualTo(7L);
        assertThat(c.getReadAt()).isEqualTo(luc);
    }

    @Test
    @DisplayName("⛔ Lượt đọc thứ hai KHÔNG ghi đè người và mốc của lượt đầu")
    void luotSauKhongGhiDe() {
        Contact c = mau();
        Instant dau = Instant.parse("2026-08-29T02:00:00Z");
        c.danhDauDaDoc(7L, dau);

        c.danhDauDaDoc(99L, Instant.parse("2026-08-29T09:00:00Z"));

        assertThat(c.getReadBy())
                .as("người đọc đầu tiên là dữ liệu, không phải người mở gần nhất")
                .isEqualTo(7L);
        assertThat(c.getReadAt()).isEqualTo(dau);
    }

    @Test
    @DisplayName("⭐ Giữ nguyên nội dung người dân gửi — không cắt, không sửa")
    void giuNguyenNoiDung() {
        Contact c = new Contact("Trần Thị B", null, "0243354xxxx", "Kênh N4", "Đoạn qua xã bị bồi lắng.");
        assertThat(c.getFullName()).isEqualTo("Trần Thị B");
        assertThat(c.getEmail()).isNull();
        assertThat(c.getPhone()).isEqualTo("0243354xxxx");
        assertThat(c.getSubject()).isEqualTo("Kênh N4");
        assertThat(c.getContent()).isEqualTo("Đoạn qua xã bị bồi lắng.");
    }
}
