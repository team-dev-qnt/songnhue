package com.songnhue.core.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Tên tệp tiếng Việt phải về tới máy người dùng NGUYÊN VẸN — T61.40</b> (ASVS 12.3.4, RFC 5987).
 *
 * <p>Đường tải qua MinIO presigned đã đúng từ đầu ({@code ObjectStorage}); năm đường phát tệp TRỰC
 * TIẾP thì chỉ có {@code filename="…"} ASCII ⇒ {@code Quyết định 2026.pdf} về máy thành
 * {@code Quy_t____nh_2026.pdf}. Cùng một hệ, hai đường tải, hai kết quả.
 */
class ContentDispositionTest {

    @Test
    @DisplayName("⭐⭐ Có ĐỦ hai dạng: ASCII cho máy khách cũ, filename* cho phần còn lại")
    void coDuHaiDang() {
        String h = HttpHeaderText.contentDisposition("Quyết định bổ nhiệm 2026.pdf");

        assertThat(h).startsWith("attachment; filename=\"");
        assertThat(h)
                .as("⛔ thiếu filename*: đó chính là vế mang được dấu tiếng Việt")
                .contains("filename*=UTF-8''");
        assertThat(h)
                .as("phần mã hoá phải là percent-encoding, ⛔ dấu + của form-urlencoded")
                .contains("Quy%E1%BA%BFt%20%C4%91%E1%BB%8Bnh")
                .doesNotContain("+");
    }

    @Test
    @DisplayName("⛔ Ký tự phá header (CR/LF/nháy kép) ⛔ lọt vào bất kỳ dạng nào")
    void khongPhaDuocHeader() {
        String h = HttpHeaderText.contentDisposition("a\r\nX-Injected: 1\"b.pdf");

        assertThat(h).doesNotContain("\r").doesNotContain("\n");
        assertThat(h.chars().filter(c -> c == '"').count())
                .as("chỉ hai dấu nháy của chính header")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("⭐ Dạng inline giữ nguyên kiểu — ảnh vẫn hiện TRONG trang, ⛔ bật hộp thoại tải")
    void inlineGiuKieu() {
        assertThat(HttpHeaderText.contentDispositionInline("ảnh.jpg"))
                .startsWith("inline; filename=\"")
                .contains("filename*=UTF-8''%E1%BA%A3nh.jpg");
    }

    @Test
    @DisplayName("⛔ Tên rỗng / null ⇒ 'tep', ⛔ header hỏng")
    void tenRong() {
        assertThat(HttpHeaderText.contentDisposition(null)).contains("filename=\"tep\"");
        assertThat(HttpHeaderText.contentDisposition("  ")).contains("filename=\"tep\"");
    }
}
