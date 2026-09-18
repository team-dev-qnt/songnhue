package com.songnhue.operations.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.spi.HydroSnapshotPort.DiemDoVe;
import com.songnhue.operations.domain.CongTrinhGan;

/** Điểm đo của một vế Bảng 3 suy ra từ liên kết điểm đo–công trình — {@link CauHinhBaoCaoNhanhService#chonVe}. */
class ChonVeDiemDoTest {

    private static final CongTrinhGan CONG = new CongTrinhGan(7L, UUID.randomUUID(), "LCO", "Cống Lương Cổ", "CONG");

    @Test
    @DisplayName("Một liên kết đúng vai trò ⇒ dùng nó; vai trò KHÁC ⛔ được mượn")
    void motLienKet() {
        List<DiemDoVe> lk = List.of(new DiemDoVe(7L, "THUONG_LUU", "F01519", true));
        assertThat(CauHinhBaoCaoNhanhService.chonVe(CONG, lk, "THUONG_LUU").apiCode())
                .isEqualTo("F01519");
        CauHinhBaoCaoNhanhService.VeDiemDo hl = CauHinhBaoCaoNhanhService.chonVe(CONG, lk, "HA_LUU");
        assertThat(hl.apiCode()).isNull();
        assertThat(hl.lyDo()).isEqualTo("Cống Lương Cổ chưa liên kết điểm đo hạ lưu (OI-BC14)");
    }

    @Test
    @DisplayName("Nhiều điểm đo cùng vế ⇒ lấy liên kết CHÍNH nếu có đúng một; ⛔ thì ô TRỐNG kèm lý do, ⛔ chọn bừa")
    void nhieuLienKet() {
        List<DiemDoVe> motChinh =
                List.of(new DiemDoVe(7L, "HA_LUU", "F0A", false), new DiemDoVe(7L, "HA_LUU", "F0B", true));
        assertThat(CauHinhBaoCaoNhanhService.chonVe(CONG, motChinh, "HA_LUU").apiCode())
                .isEqualTo("F0B");

        List<DiemDoVe> khongChinh =
                List.of(new DiemDoVe(7L, "HA_LUU", "F0A", false), new DiemDoVe(7L, "HA_LUU", "F0B", false));
        CauHinhBaoCaoNhanhService.VeDiemDo v = CauHinhBaoCaoNhanhService.chonVe(CONG, khongChinh, "HA_LUU");
        assertThat(v.apiCode()).isNull();
        assertThat(v.lyDo()).contains("có 2 điểm đo hạ lưu").contains("liên kết chính");
    }
}
