package com.songnhue.operations.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Định dạng số để IN vào văn bản hành chính — kiểu Việt Nam: {@code 1.100} · {@code 0,92}.
 *
 * <p>Một chỗ duy nhất cho bản Word của Báo cáo nhanh: mẫu của Công ty viết {@code 1.100}, {@code 43.200}
 * (dấu chấm hàng nghìn). ⛔ Dùng {@code toPlainString()} — văn bản gửi UBND sẽ ra {@code 43200.00}.
 */
public final class SoVanBan {

    private SoVanBan() {}

    /** {@code null} → chuỗi rỗng (ô trống). Phần lẻ = 0 thì bỏ. */
    public static String thapPhan(BigDecimal so) {
        if (so == null) {
            return "";
        }
        BigDecimal gon = so.stripTrailingZeros();
        if (gon.scale() < 0) {
            gon = gon.setScale(0, RoundingMode.UNNECESSARY);
        }
        String tron = gon.toPlainString();
        boolean am = tron.startsWith("-");
        if (am) {
            tron = tron.substring(1);
        }
        int cham = tron.indexOf('.');
        String nguyen = cham < 0 ? tron : tron.substring(0, cham);
        String le = cham < 0 ? "" : tron.substring(cham + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nguyen.length(); i++) {
            if (i > 0 && (nguyen.length() - i) % 3 == 0) {
                sb.append('.');
            }
            sb.append(nguyen.charAt(i));
        }
        return (am ? "-" : "") + sb + (le.isEmpty() ? "" : "," + le);
    }

    public static String soNguyen(Integer so) {
        return so == null ? "" : thapPhan(BigDecimal.valueOf(so));
    }

    /** Mực nước (m), đúng 2 chữ số lẻ: {@code 2,00}. */
    public static String mucNuoc(BigDecimal m) {
        if (m == null) {
            return "";
        }
        String s = m.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        return s;
    }
}
