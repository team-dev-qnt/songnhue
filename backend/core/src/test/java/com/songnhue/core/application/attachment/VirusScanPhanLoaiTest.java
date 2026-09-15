package com.songnhue.core.application.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.application.attachment.VirusScanHandler.KetLuan;

/**
 * Phản hồi clamd → kết luận — T61.4. Mọi chuỗi dưới đây là <b>nguyên văn đo được</b> ngày 14/09/2026
 * trên {@code clamav/clamav:1.4.6-debian} qua đúng giao thức INSTREAM của {@link VirusScanHandler},
 * ⛔ chuỗi tự nghĩ ra.
 */
class VirusScanPhanLoaiTest {

    @Test
    @DisplayName("⛔⛔ Tệp vượt trần luồng của clamd là LỖI QUÉT, ⛔ phải NHIỄM — tệp sạch ⛔ bị cách ly")
    void vuotTranLuongLaLoi() {
        assertThat(VirusScanHandler.phanLoai("INSTREAM size limit exceeded. ERROR"))
                .as("ZIP SẠCH 115 MB nhận đúng câu này khi StreamMaxLength để mặc định 100M")
                .isEqualTo(KetLuan.LOI);
    }

    @Test
    @DisplayName("⭐ Ba kết cục đo được phân biệt được với nhau")
    void baKetCuc() {
        assertThat(VirusScanHandler.phanLoai("stream: OK")).isEqualTo(KetLuan.SACH);
        assertThat(VirusScanHandler.phanLoai("stream: Eicar-Test-Signature FOUND"))
                .isEqualTo(KetLuan.NHIEM);
        assertThat(VirusScanHandler.phanLoai("")).as("kết nối đóng ⛔ trả gì").isEqualTo(KetLuan.LOI);
        assertThat(VirusScanHandler.phanLoai(null)).isEqualTo(KetLuan.LOI);
    }

    @Test
    @DisplayName("⚠ Tên chữ ký chứa chữ `OK` vẫn là NHIỄM — bản cũ `contains(\"OK\")` dễ trượt đúng chỗ này")
    void tenChuKyChuaOk() {
        assertThat(VirusScanHandler.phanLoai("stream: Win.Trojan.OKbot-1 FOUND"))
                .isEqualTo(KetLuan.NHIEM);
    }
}
