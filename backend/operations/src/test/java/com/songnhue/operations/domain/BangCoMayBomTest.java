package com.songnhue.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.common.exception.BusinessRuleException;

/**
 * 9 cỡ máy của Bảng 1 Báo cáo nhanh — biên đề xuất ở {@code V202609181086}.
 *
 * <p>⭐ Dữ liệu là PHÂN BỐ THẬT của sheet {@code TB Tiêu (KH)} (830 máy, 27 cỡ Q), ⛔ số bịa: bài kiểm
 * phải chứng minh biên đề xuất cho mọi máy của danh mục Công ty một nhà, và cột "12" ra 0 — khớp ô
 * TRỐNG của mẫu Word.
 */
class BangCoMayBomTest {

    /** Chép nguyên biên của {@code V202609181086}. */
    static BangCoMayBom bienDeXuat() {
        return BangCoMayBom.of(List.of(
                co("43", "32500", null, 1),
                co("22", "17000", "32500", 2),
                co("12", "10000", "17000", 3),
                co("8", "6000", "10000", 4),
                co("4", "3500", "6000", 5),
                co("2÷3", "2000", "3500", 6),
                co("1,1 ÷1,9", "1100", "2000", 7),
                co("1", "1000", "1100", 8),
                co("< 1", null, "1000", 9)));
    }

    static BangCoMayBom.Co co(String nhan, String tu, String den, int thuTu) {
        return new BangCoMayBom.Co(
                UUID.randomUUID(),
                nhan,
                tu == null ? null : new BigDecimal(tu),
                den == null ? null : new BigDecimal(den),
                thuTu);
    }

    /** Q (m³/h) → số máy, đo trên sheet `TB Tiêu (KH)` ngày 18/09/2026. */
    private static final Map<Integer, Integer> PHAN_BO_THAT = new LinkedHashMap<>();

    static {
        int[][] cap = {
            {540, 1}, {800, 8}, {900, 15}, {960, 7}, {980, 237}, {1000, 30}, {1100, 24}, {1120, 35},
            {1200, 32}, {1400, 55}, {1500, 27}, {1700, 3}, {1900, 6}, {1950, 28}, {2000, 15}, {2100, 4},
            {2400, 16}, {2500, 58}, {3700, 11}, {4000, 128}, {7300, 4}, {8000, 33}, {8144, 28}, {8400, 7},
            {22000, 5}, {25200, 3}, {43200, 10}
        };
        for (int[] c : cap) {
            PHAN_BO_THAT.put(c[0], c[1]);
        }
    }

    @Test
    @DisplayName("⭐⭐ 830 máy thật rơi đủ 9 cột, cột \"12\" = 0 (khớp ô TRỐNG của mẫu), tổng 9 cột = 830")
    void phanBoThatRoiDuChinCot() {
        BangCoMayBom bang = bienDeXuat();
        int[] cot = new int[bang.soCo()];
        PHAN_BO_THAT.forEach((q, soMay) -> cot[bang.xep(BigDecimal.valueOf(q))] += soMay);

        assertThat(cot).containsExactly(10, 8, 0, 72, 139, 93, 210, 30, 268);
        assertThat(java.util.Arrays.stream(cot).sum())
                .as("bất biến Bảng 1: SUM(9 cột) = Tổng số máy")
                .isEqualTo(830);
    }

    @Test
    @DisplayName("⭐ Ba cỡ Q 'mồ côi' theo nghĩa chặt của nhãn đều có nhà — 25.200 · 7.300 · 1.950")
    void baCoMoCoiCoNha() {
        BangCoMayBom bang = bienDeXuat();
        assertThat(bang.co().get(bang.xep(new BigDecimal("25200"))).nhan()).isEqualTo("22");
        assertThat(bang.co().get(bang.xep(new BigDecimal("7300"))).nhan()).isEqualTo("8");
        assertThat(bang.co().get(bang.xep(new BigDecimal("1950"))).nhan()).isEqualTo("1,1 ÷1,9");
    }

    @Test
    @DisplayName("Nửa mở [tu, den): Q nằm ĐÚNG biên thuộc cột TRÊN")
    void bienThuocCotTren() {
        BangCoMayBom bang = bienDeXuat();
        assertThat(bang.co().get(bang.xep(new BigDecimal("1000"))).nhan()).isEqualTo("1");
        assertThat(bang.co().get(bang.xep(new BigDecimal("999.99"))).nhan()).isEqualTo("< 1");
        assertThat(bang.co().get(bang.xep(new BigDecimal("32500"))).nhan()).isEqualTo("43");
    }

    @Test
    @DisplayName("⛔ Biên có KHE ⇒ OPS-2031 gọi đích danh cặp cỡ lệch")
    void bienCoKheBiTuChoi() {
        List<BangCoMayBom.Co> co = new ArrayList<>(bienDeXuat().co());
        co.set(4, co("4", "3600", "6000", 5)); // khe [3500, 3600)
        assertThat(loi(() -> BangCoMayBom.of(co)))
                .startsWith("OPS-2031:")
                .contains("\"4\"")
                .contains("\"2÷3\"");
    }

    @Test
    @DisplayName("⛔ Cỡ lớn nhất có cận trên / cỡ nhỏ nhất có cận dưới ⇒ OPS-2031")
    void haiDauPhaiMo() {
        List<BangCoMayBom.Co> co = new ArrayList<>(bienDeXuat().co());
        co.set(0, co("43", "32500", "50000", 1));
        assertThat(loi(() -> BangCoMayBom.of(co))).startsWith("OPS-2031:").contains("cận trên");

        List<BangCoMayBom.Co> co2 = new ArrayList<>(bienDeXuat().co());
        co2.set(8, co("< 1", "100", "1000", 9));
        assertThat(loi(() -> BangCoMayBom.of(co2))).startsWith("OPS-2031:").contains("cận dưới");
    }

    @Test
    @DisplayName("⛔ Q ≤ 0 ⛔ thuộc cỡ nào ⇒ OPS-2027 mang chính Q, ⛔ bỏ im lặng")
    void qKhongThuocCoNaoThiNem() {
        assertThat(loi(() -> bienDeXuat().xep(new BigDecimal("-5")))).isEqualTo("OPS-2027:-5");
    }

    /** {@code "<mã>:<đối số 0>"} — ⚠ thông điệp của {@code AppException} là MÃ, đối số ở {@code messageArgs}. */
    private static String loi(Runnable r) {
        BusinessRuleException e = catchThrowableOfType(BusinessRuleException.class, r::run);
        assertThat(e).as("phải ném BusinessRuleException").isNotNull();
        return e.errorCode().code() + ":" + e.messageArgs()[0];
    }
}
