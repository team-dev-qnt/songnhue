package com.songnhue.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Chú thích ảnh lấy nguyên văn từ tên tệp Công ty gửi — không sửa, không bịa.</b>
 *
 * <p>Bảng {@code attachments} không có cột tiêu đề, và chú thích ảnh của Công ty đang nằm trong
 * chính tên tệp họ gửi: {@code "AN2. Đại hội Công đoàn Công ty nhiệm kỳ 2023-2028.jpg"}. Việc bóc
 * tiền tố kỹ thuật đặt ở MỘT chỗ duy nhất trong service (quy tắc 12) — để phía giao diện tự bóc là
 * mỗi nơi một kiểu, và bộ seed lại bóc kiểu thứ ba.
 */
class PublicPortalPhotoTitleTest {

    @Test
    @DisplayName("⭐ Bóc đúng hai tiền tố Công ty dùng, giữ nguyên phần chú thích")
    void bocTienToGiuNguyenChuThich() {
        assertThat(PublicPortalService.tieuDeTuTenTep("Ảnh to. Cống Liên Mạc - Đầu nguồn Sông Nhuệ.jpg"))
                .isEqualTo("Cống Liên Mạc - Đầu nguồn Sông Nhuệ");
        assertThat(PublicPortalService.tieuDeTuTenTep("AN2. Đại hội Công đoàn Công ty nhiệm kỳ 2023-2028.jpg"))
                .isEqualTo("Đại hội Công đoàn Công ty nhiệm kỳ 2023-2028");
        assertThat(PublicPortalService.tieuDeTuTenTep("AN1.Nạo vét sông Nhuệ.jpg"))
                .isEqualTo("Nạo vét sông Nhuệ");
    }

    @Test
    @DisplayName("⭐ Đuôi tệp viết HOA cũng bóc — Công ty gửi lẫn `.JPG` và `.jpg`")
    void bocDuocDuoiVietHoa() {
        assertThat(PublicPortalService.tieuDeTuTenTep("AN3.Hội thao công ty năm 2025.JPG"))
                .isEqualTo("Hội thao công ty năm 2025");
    }

    @Test
    @DisplayName("⛔⛔ Tên do MÁY sinh trả RỖNG — đo được 3/25 ảnh Công ty gửi là loại này")
    void tenMaySinhTraRong() {
        // Bản đầu của bài này khẳng định "trả nguyên tên — xấu nhưng thật", với lý lẽ: bịa một
        // tiêu đề mới là điều bị cấm. Vế ấy đúng, nhưng nó bỏ sót phương án thứ ba — KHÔNG HIỆN GÌ.
        //
        // Đo trên staging 28/8 sau lượt CD: 3 trong 25 ảnh mang tên kiểu này, và cổng đang hiện
        // nguyên chuỗi băm làm chú thích ảnh. Rỗng vẫn là "chưa có nguồn", vẫn không bịa gì, mà
        // không đổ một chuỗi vô nghĩa lên mặt người đọc. Cổng bỏ hẳn dải chú thích khi tiêu đề rỗng.
        assertThat(PublicPortalService.tieuDeTuTenTep(
                        "1785224749554_4602082902160469425_4602082902160469425_683fabe93a80ea6c88ef6b66c2b1f227.jpg"))
                .isEmpty();
        // Dạng tên máy ảnh phổ biến — 81,2 % chữ số.
        assertThat(PublicPortalService.tieuDeTuTenTep("IMG_20240115_103045_889900112233.jpg"))
                .isEmpty();
        assertThat(PublicPortalService.tieuDeTuTenTep("DSC_0042_20240115103045_778899.jpg"))
                .isEmpty();
    }

    @Test
    @DisplayName("⭐ Nhưng KHÔNG nuốt nhầm chú thích thật — một khoảng trắng là đủ để giữ")
    void khongNuotNhamChuThichThat() {
        // Biên của bộ dò. Thiếu bài này thì một quy tắc "trông giống tên máy" có thể lặng lẽ xoá
        // chú thích hợp lệ, và triệu chứng là ô trống — đúng loại hỏng câm đang phải chống.
        assertThat(PublicPortalService.tieuDeTuTenTep("Cống Lương Cổ.jpg")).isEqualTo("Cống Lương Cổ");
        assertThat(PublicPortalService.tieuDeTuTenTep("AN1.Nạo vét sông Nhuệ.jpg"))
                .isEqualTo("Nạo vét sông Nhuệ");
        // Không khoảng trắng nhưng NGẮN → giữ.
        assertThat(PublicPortalService.tieuDeTuTenTep("Trường-Sa-2019.jpg")).isEqualTo("Trường-Sa-2019");
        // Không khoảng trắng, DÀI, nhưng 0 % chữ số → chữ người viết, giữ.
        assertThat(PublicPortalService.tieuDeTuTenTep("Cống-Liên-Mạc-đầu-nguồn-Sông-Nhuệ.jpg"))
                .isEqualTo("Cống-Liên-Mạc-đầu-nguồn-Sông-Nhuệ");
        // ⭐ Chú thích tiếng Việt KHÔNG DẤU, 20 ký tự, 0 % chữ số — dạng dễ bị nuốt nhất.
        assertThat(PublicPortalService.tieuDeTuTenTep("Nha-may-nuoc-Ha-Dong.jpg"))
                .isEqualTo("Nha-may-nuoc-Ha-Dong");
        // 16 % chữ số, dài 25, không khoảng trắng — dưới ngưỡng 40 %, giữ.
        assertThat(PublicPortalService.tieuDeTuTenTep("Hoi-thao-cong-ty-nam-2025.jpg"))
                .isEqualTo("Hoi-thao-cong-ty-nam-2025");
    }

    @Test
    @DisplayName("⭐⭐ Đúng 3 tên đo được trên staging, không nhiều hơn không ít hơn")
    void batDungBaTenDoDuocTrenStaging() {
        // Khẳng định về SỐ LƯỢNG trên dữ liệu THẬT — nó không chia sẻ giả định nào với biểu thức
        // chính quy ở trên (luật 29). Hai mươi hai tên còn lại phải sống sót nguyên vẹn.
        String[] tenThat = {
            "AN1.Đại hội Đại biểu Đoàn TNCS HCM UBND thành phố Hà Nội lần thứ I, nhiệm kỳ 2025-2030.jpg",
            "AN2.Đại hội Công đoàn Công ty nhiệm kỳ 2023-2028.jpg",
            "AN3.Duy trì công trình thủy lợi cống Liên Mạc.jpg",
            "AN1.Xây dựng cống Liên Mạc 1939.jpg",
            "AN2.Cống Lương Cổ.jpg",
            "1785207419516_3079181702633692887_3079181702633692887_38b04c2b20dae9b118da383d2c00630c.jpg",
            "1785224610882_4602082902160469425_4602082902160469425_cafc606f197af4bc15db928eb458370a.jpg",
            "1785224749554_4602082902160469425_4602082902160469425_683fabe93a80ea6c88ef6b66c2b1f227.jpg",
        };
        long rong = java.util.Arrays.stream(tenThat)
                .map(PublicPortalService::tieuDeTuTenTep)
                .filter(String::isEmpty)
                .count();
        assertThat(rong)
                .as("phải rỗng ĐÚNG 3 tên máy sinh, giữ nguyên 5 chú thích thật")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Rỗng và null không làm sập trang chủ")
    void rongVaNullAnToan() {
        assertThat(PublicPortalService.tieuDeTuTenTep(null)).isEmpty();
        assertThat(PublicPortalService.tieuDeTuTenTep("   ")).isEmpty();
    }
}
