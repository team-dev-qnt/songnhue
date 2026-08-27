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
    @DisplayName("⛔ Tên không theo quy ước thì trả về NGUYÊN TÊN — xấu nhưng thật")
    void tenLaThiTraNguyenVan() {
        // Ảnh tải từ điện thoại: tên là một chuỗi băm. Bịa một tiêu đề đẹp cho nó mới là điều bị
        // cấm (luật 16) — thà hiện chuỗi khó đọc còn hơn hiện một câu không ai viết ra.
        assertThat(PublicPortalService.tieuDeTuTenTep("1785224749554_4602082902160469425_abc.jpg"))
                .isEqualTo("1785224749554_4602082902160469425_abc");
    }

    @Test
    @DisplayName("Rỗng và null không làm sập trang chủ")
    void rongVaNullAnToan() {
        assertThat(PublicPortalService.tieuDeTuTenTep(null)).isEmpty();
        assertThat(PublicPortalService.tieuDeTuTenTep("   ")).isEmpty();
    }
}
