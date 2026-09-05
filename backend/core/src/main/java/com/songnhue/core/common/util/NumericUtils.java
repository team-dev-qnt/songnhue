package com.songnhue.core.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Quy tắc làm tròn và tính toán số — MỘT chỗ duy nhất.
 *
 * <p>Quy tắc 2 của dự án: <b>NUMERIC/BigDecimal cho mọi số đo và tiền, cấm float/double.</b>
 * {@code double} không biểu diễn chính xác được số thập phân hệ 10; cộng dồn 2.700 bản ghi mực nước
 * mỗi ngày thì sai số tích luỹ đủ để lệch kết quả báo cáo tổng hợp.
 *
 * <p>Scale chuẩn (phụ lục function-spec.md): mực nước và lưu lượng scale 3 · lượng mưa scale 1 ·
 * tiền scale 0 (đồng) · phần trăm scale 2.
 */
public final class NumericUtils {

    /** Mực nước (m), lưu lượng (m³/s). */
    public static final int SCALE_MEASUREMENT = 3;

    /** Lượng mưa (mm). */
    public static final int SCALE_RAINFALL = 1;

    /** Tiền — đơn vị đồng, không có phần lẻ. */
    public static final int SCALE_MONEY = 0;

    public static final int SCALE_PERCENT = 2;

    /** HALF_UP: cách làm tròn người Việt quen dùng, khác mặc định HALF_EVEN của Java. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private NumericUtils() {}

    public static BigDecimal measurement(BigDecimal value) {
        return scale(value, SCALE_MEASUREMENT);
    }

    public static BigDecimal rainfall(BigDecimal value) {
        return scale(value, SCALE_RAINFALL);
    }

    public static BigDecimal money(BigDecimal value) {
        return scale(value, SCALE_MONEY);
    }

    public static BigDecimal percent(BigDecimal value) {
        return scale(value, SCALE_PERCENT);
    }

    public static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, ROUNDING);
    }

    /**
     * So sánh giá trị, BỎ QUA scale.
     *
     * <p>{@code new BigDecimal("1.50").equals(new BigDecimal("1.5"))} trả về {@code false} vì
     * {@code equals} so cả scale — đây là bẫy kinh điển của BigDecimal. Luôn dùng hàm này khi so
     * sánh số đo.
     */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return a.compareTo(b) == 0;
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) > 0;
    }

    public static boolean gte(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) >= 0;
    }

    public static boolean lt(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) < 0;
    }

    /** Tổng bỏ qua null — dữ liệu quan trắc thiếu là chuyện thường, không được làm hỏng phép cộng. */
    public static BigDecimal sum(Collection<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Trung bình cộng của các giá trị khác null.
     *
     * <p>Trả {@code null} khi không có giá trị nào — KHÔNG trả 0. "Không có dữ liệu" và "giá trị
     * bằng 0" là hai chuyện khác nhau; trả 0 sẽ làm biểu đồ mực nước vẽ ra đáy sông giả.
     */
    public static BigDecimal average(Collection<BigDecimal> values, int scale) {
        if (values == null) {
            return null;
        }
        var present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal total = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(present.size()), scale, ROUNDING);
    }

    /** Đổi cm sang m (nguồn thủy văn trả cm — phụ lục function-spec.md, chốt B6). */
    public static BigDecimal centimetersToMeters(BigDecimal centimeters) {
        return centimeters == null ? null : measurement(centimeters.movePointLeft(2));
    }
}
