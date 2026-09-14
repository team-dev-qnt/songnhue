package com.songnhue.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Danh mục báo cáo vận hành — CN-02.10.
 *
 * <h2>⛔⛔ Bài đáng giá nhất ở đây là bài ép BẤT BIẾN giữa hai trường</h2>
 *
 * <p>{@code khaDung = false} mà {@code lyDo = null} là một dòng bị vô hiệu trên màn hình mà ⛔
 * không nói vì sao — người vận hành đọc đó là một lỗi hệ thống và đi báo hỏng. Chiều ngược lại
 * ({@code khaDung = true} kèm lý do) thì tệ hơn: giao diện hiện một cảnh báo dưới một báo cáo đang
 * chạy tốt.
 */
class MaBaoCaoVanHanhTest {

    @Test
    @DisplayName("⛔⛔ `khaDung = false` ⟺ CÓ lý do — bất biến, ⛔ không phải quy ước")
    void khaDungVaLyDoLaMotCap() {
        for (MaBaoCaoVanHanh m : MaBaoCaoVanHanh.values()) {
            if (m.khaDung()) {
                assertThat(m.lyDo())
                        .as(
                                "⛔ %s khả dụng mà vẫn mang lý do — giao diện sẽ hiện một cảnh báo dưới "
                                        + "một báo cáo đang chạy tốt",
                                m.ma())
                        .isNull();
            } else {
                assertThat(m.lyDo())
                        .as(
                                "⛔⛔ %s bị vô hiệu mà ⛔ không nói vì sao — người vận hành đọc đó là một "
                                        + "lỗi hệ thống và đi báo hỏng",
                                m.ma())
                        .isNotNull()
                        .isNotBlank();
            }
            assertThat(m.moTa()).as("⛔ %s thiếu mô tả", m.ma()).isNotBlank();
        }
    }

    @Test
    @DisplayName("⭐ BỐN mã đã bỏ vĩnh viễn PHẢI có mặt, và lý do phải nêu MÃ CHỐT")
    void bonMaDaBoVanCoMatKemMaChot() {
        // ⛔ Liệt kê ba mã còn sống là để câu hỏi *"BC-01 đâu?"* quay lại ở mọi lượt nghiệm thu.
        assertThat(Arrays.stream(MaBaoCaoVanHanh.values()).filter(m -> !m.khaDung()))
                .extracting(MaBaoCaoVanHanh::ma)
                .containsExactlyInAnyOrder("BC-01", "BC-02", "BC-03", "BC-04");

        assertThat(MaBaoCaoVanHanh.BC_01.lyDo())
                .as("⛔ Lý do phải nêu mã chốt để người đọc tra ngược được, ⛔ không chỉ nói *đã bỏ*")
                .contains("B1/F1")
                .contains("G2");
        assertThat(MaBaoCaoVanHanh.BC_04.lyDo()).contains("A1");
    }

    @Test
    @DisplayName("⭐ BA mã còn sống đúng bằng những mã CN-02.10 chốt giữ lại")
    void baMaConSong() {
        assertThat(Arrays.stream(MaBaoCaoVanHanh.values()).filter(MaBaoCaoVanHanh::khaDung))
                .extracting(MaBaoCaoVanHanh::ma)
                .containsExactlyInAnyOrder("BC-06", "BC-09", "BC-10");
    }

    @Test
    @DisplayName("`tuMa` nhận cả mã gạch nối lẫn tên hằng; mã lạ thì ném")
    void tuMaNhanHaiDangVaNemKhiLa() {
        assertThat(MaBaoCaoVanHanh.tuMa("BC-09")).isEqualTo(MaBaoCaoVanHanh.BC_09);
        assertThat(MaBaoCaoVanHanh.tuMa("bc-09")).isEqualTo(MaBaoCaoVanHanh.BC_09);
        assertThat(MaBaoCaoVanHanh.tuMa("BC_09")).isEqualTo(MaBaoCaoVanHanh.BC_09);
        assertThatThrownBy(() -> MaBaoCaoVanHanh.tuMa("BC-99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BC-99");
    }
}
