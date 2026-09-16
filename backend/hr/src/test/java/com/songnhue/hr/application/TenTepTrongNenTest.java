package com.songnhue.hr.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Tên mục trong bản nén hồ sơ CBNV ⛔ được mang đường dẫn — T61.40</b> (ASVS 12.3.2, "zip-slip").
 *
 * <p>{@code attachments.original_name} lưu <b>nguyên văn</b> tên người dùng đặt: {@code AttachmentService}
 * chỉ ngẫu nhiên hoá KHOÁ lưu trữ. Một tệp tên {@code ../../../../.bashrc} ⇒ mục ZIP mang đúng đường
 * dẫn ấy ⇒ một công cụ giải nén ngây thơ ghi ra NGOÀI thư mục đích trên máy người nhận.
 */
class TenTepTrongNenTest {

    @Test
    @DisplayName("⛔⛔ Mọi đoạn đường dẫn bị bỏ — cả kiểu POSIX lẫn kiểu Windows")
    void boMoiDoanDuongDan() {
        assertThat(HoSoTaiLieuService.tenTepTrongNen("../../../../.bashrc")).isEqualTo(".bashrc");
        assertThat(HoSoTaiLieuService.tenTepTrongNen("..\\..\\Windows\\system32\\hosts"))
                .isEqualTo("hosts");
        assertThat(HoSoTaiLieuService.tenTepTrongNen("/etc/passwd")).isEqualTo("passwd");
        assertThat(HoSoTaiLieuService.tenTepTrongNen("thu-muc/quyet-dinh.pdf")).isEqualTo("quyet-dinh.pdf");
    }

    @Test
    @DisplayName("⭐ Tên thường — kể cả tiếng Việt có dấu và khoảng trắng — GIỮ NGUYÊN")
    void tenThuongGiuNguyen() {
        assertThat(HoSoTaiLieuService.tenTepTrongNen("Quyết định bổ nhiệm 2026.pdf"))
                .as("⛔ bóp méo tên tệp của người dùng: đó là thứ họ tìm trong bản nén")
                .isEqualTo("Quyết định bổ nhiệm 2026.pdf");
    }

    @Test
    @DisplayName("⛔ Các ca rỗng / chỉ có dấu chấm ⇒ một tên vô hại, ⛔ chuỗi rỗng (ZipEntry rỗng là tệp hỏng)")
    void caRongRaTenVoHai() {
        assertThat(HoSoTaiLieuService.tenTepTrongNen(null)).isEqualTo("khong-ten");
        assertThat(HoSoTaiLieuService.tenTepTrongNen("   ")).isEqualTo("khong-ten");
        assertThat(HoSoTaiLieuService.tenTepTrongNen("..")).isEqualTo("khong-ten");
        assertThat(HoSoTaiLieuService.tenTepTrongNen("a/b/")).isEqualTo("khong-ten");
    }
}
