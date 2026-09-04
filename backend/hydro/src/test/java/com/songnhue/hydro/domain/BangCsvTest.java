package com.songnhue.hydro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bộ dựng CSV — T34.7/T34.8.
 *
 * <p>⭐ Mỗi bài dưới đây bắt một lớp hỏng <b>không có triệu chứng</b>: tệp vẫn mở được trong Excel,
 * vẫn có đủ hàng, và con số bên trong thì sai — hoặc tệ hơn, chạy một lệnh.
 */
class BangCsvTest {

    @Test
    @DisplayName("⭐⭐ Dấu thập phân đổi sang dấu PHẨY — Excel vi-VN đọc `4.93` thành 493")
    void decimalSeparatorFollowsTheVietnameseLocale() {
        assertThat(BangCsv.so("4.930")).isEqualTo("4,930");

        assertThat(BangCsv.so(null))
                .as("⛔ Ô rỗng ra CSV là ô RỖNG, ⛔ không phải \"0\" — quy tắc 16 áp cho cả bản kết "
                        + "xuất, và một số 0 trong Excel là thứ người ta cộng vào tổng")
                .isEmpty();

        // ⚠ Vế phân biệt: chuỗi ⛔ không phải số thì ⛔ không được đụng vào.
        assertThat(BangCsv.so("Chưa phân tuyến")).isEqualTo("Chưa phân tuyến");
    }

    @Test
    @DisplayName("⭐⭐ Ô bắt đầu bằng `=` bị chặn — cột Lý do của BC-12 mang chữ do NGƯỜI DÙNG gõ")
    void formulaInjectionIsDefused() {
        String ra = new BangCsv().dong("=cmd|'/c calc'!A1").toString();

        assertThat(ra)
                .as(
                        """
                        ⛔⛔ Excel THI HÀNH ô bắt đầu bằng `=`, `+`, `-`, `@` như một công thức khi mở \
                        tệp. Ở đây nguy cơ có thật chứ ⛔ không lý thuyết: cột Lý do của BC-12 mang \
                        `review_note` — chữ do người duyệt gõ vào, và bản kết xuất thì được gửi cho \
                        người khác mở.""")
                .startsWith("\"'=cmd");

        // ⚠ Vế phân biệt: chặn bằng dấu nháy đứng TRƯỚC, ⛔ không phải xoá ký tự đầu — một ghi chú
        //   mở đầu bằng dấu trừ là chuyện bình thường, xoá nó đi là làm sai nội dung.
        assertThat(new BangCsv().dong("-5 cm so với hôm qua").toString()).contains("-5 cm so với hôm qua");
    }

    @Test
    @DisplayName("⭐ Dòng lệch số cột NÉM ngay — một tệp lệch cột vẫn MỞ ĐƯỢC và im lặng đẩy dữ liệu sang bên")
    void aRaggedRowIsRejected() {
        BangCsv b = new BangCsv().dong("A", "B", "C");

        assertThatThrownBy(() -> b.dong("1", "2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("có 2 ô nhưng tiêu đề có 3");
    }

    @Test
    @DisplayName("⭐ BOM UTF-8 đứng trước mọi thứ — thiếu nó thì Excel đoán bảng mã theo địa phương")
    void theFileStartsWithAUtf8Bom() {
        byte[] ra = new BangCsv().dong("Cống Liên Mạc").byteUtf8Bom();

        assertThat(new byte[] {ra[0], ra[1], ra[2]}).isEqualTo(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        assertThat(new String(ra, 3, ra.length - 3, StandardCharsets.UTF_8))
                .as("⚠ Vế phân biệt: BOM ⛔ không được nuốt mất ký tự đầu của nội dung")
                .startsWith("\"Cống Liên Mạc\"");
    }

    @Test
    @DisplayName("⛔ Dấu nháy kép trong nội dung được nhân đôi — RFC 4180, nếu không thì ô bị cắt")
    void quotesAreDoubled() {
        assertThat(new BangCsv().dong("Cống \"Liên Mạc\"").toString()).contains("\"Cống \"\"Liên Mạc\"\"\"");
    }

    @Test
    @DisplayName("⛔ Đếm dòng KỂ CẢ tiêu đề — con số ấy là vế chống tập rỗng của bản kết xuất")
    void rowCountIncludesTheHeader() {
        BangCsv b = new BangCsv().dong("A", "B").dong("1", "2");
        assertThat(b.soDong()).isEqualTo(2);
    }
}
