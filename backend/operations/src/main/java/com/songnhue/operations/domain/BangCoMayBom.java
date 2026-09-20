package com.songnhue.operations.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;

/**
 * Bảng 9 cỡ máy của Bảng 1 Báo cáo nhanh — lớp THUẦN (⛔ Spring, ⛔ CSDL), kiểm bằng JUnit trần.
 *
 * <h2>⛔⛔ Một Q ⛔ thuộc cỡ nào thì NÉM, ⛔ bỏ im lặng</h2>
 *
 * <p>Bất biến của Bảng 1 lấy từ chính số mẫu Word: {@code 5+4+0+44+84+82+177 = 396 = Tổng số máy}
 * ⇒ tổng 9 cột phải bằng tổng số máy. Một Q rơi ra ngoài mọi cỡ mà bị bỏ qua thì Bảng 1 in ra hai
 * con số lệch nhau trên cùng một dòng, và văn bản ấy đi tới UBND. ⇒ {@code OPS-2027} gọi đích danh Q.
 *
 * <h2>⛔ Biên phải LIỀN NHAU</h2>
 *
 * <p>Nửa mở {@code [tu, den)}, sắp theo {@code thuTu} (1 = cỡ lớn nhất, cột trái nhất của mẫu): cỡ
 * đầu có {@code den = null} (vô cực), cỡ cuối có {@code tu = null}, và {@code tu} của cỡ trước ==
 * {@code den} của cỡ sau. Có khe thì một Q rơi vào khe (lỗi lúc lập báo cáo — muộn); có chồng thì một
 * Q thuộc hai cột (Bảng 1 đếm hai lần — ⛔ ai thấy). ⇒ Kiểm lúc SỬA BIÊN ({@code OPS-2031}), sớm nhất.
 */
public final class BangCoMayBom {

    /** Một cỡ máy. {@code tu}/{@code den} {@code null} = vô cực. */
    public record Co(UUID publicId, String nhan, BigDecimal tu, BigDecimal den, int thuTu) {}

    private final List<Co> theoThuTu;

    private BangCoMayBom(List<Co> theoThuTu) {
        this.theoThuTu = theoThuTu;
    }

    /** Dựng bảng — <b>kiểm biên liền nhau trước</b>, nên một bảng dựng được là một bảng phủ kín trục Q. */
    public static BangCoMayBom of(List<Co> co) {
        List<Co> sap = new ArrayList<>(co);
        sap.sort(Comparator.comparingInt(Co::thuTu));
        kiemLienNhau(sap);
        return new BangCoMayBom(List.copyOf(sap));
    }

    public List<Co> co() {
        return theoThuTu;
    }

    public int soCo() {
        return theoThuTu.size();
    }

    /**
     * Chỉ số cột (0 = cỡ lớn nhất) của một Q.
     *
     * <p>⛔ Q ≤ 0 bị từ chối TƯỜNG MINH: cỡ nhỏ nhất có cận dưới mở (−∞), nên thiếu chốt này thì một Q
     * âm lặng lẽ rơi vào cột "&lt; 1" — bài kiểm {@code qKhongThuocCoNaoThiNem} đỏ đúng ở đây lượt đầu.
     *
     * @throws BusinessRuleException {@code OPS-2027} khi Q ≤ 0 hoặc ⛔ thuộc cỡ nào
     */
    public int xep(BigDecimal q) {
        if (q == null || q.signum() <= 0) {
            throw new BusinessRuleException(
                    ErrorCode.OPS_2027,
                    q == null ? "(trống)" : q.stripTrailingZeros().toPlainString());
        }
        for (int i = 0; i < theoThuTu.size(); i++) {
            Co c = theoThuTu.get(i);
            boolean trenDuoi = c.tu() == null || q.compareTo(c.tu()) >= 0;
            boolean duoiTren = c.den() == null || q.compareTo(c.den()) < 0;
            if (trenDuoi && duoiTren) {
                return i;
            }
        }
        throw new BusinessRuleException(
                ErrorCode.OPS_2027, q.stripTrailingZeros().toPlainString());
    }

    /**
     * Kiểm 9 biên phủ kín trục Q, ⛔ khe, ⛔ chồng.
     *
     * @param sap danh sách <b>đã sắp theo {@code thuTu}</b>
     * @throws BusinessRuleException {@code OPS-2031} nêu đích danh cặp cỡ lệch
     */
    static void kiemLienNhau(List<Co> sap) {
        if (sap.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.OPS_2031, "danh mục cỡ máy rỗng");
        }
        Co dau = sap.get(0);
        Co cuoi = sap.get(sap.size() - 1);
        if (dau.den() != null) {
            throw new BusinessRuleException(
                    ErrorCode.OPS_2031, "cỡ \"%s\" (lớn nhất) phải để trống cận trên".formatted(dau.nhan()));
        }
        if (cuoi.tu() != null) {
            throw new BusinessRuleException(
                    ErrorCode.OPS_2031, "cỡ \"%s\" (nhỏ nhất) phải để trống cận dưới".formatted(cuoi.nhan()));
        }
        for (int i = 0; i < sap.size(); i++) {
            Co c = sap.get(i);
            if (c.tu() != null && c.den() != null && c.tu().compareTo(c.den()) >= 0) {
                throw new BusinessRuleException(
                        ErrorCode.OPS_2031, "cỡ \"%s\": cận dưới phải nhỏ hơn cận trên".formatted(c.nhan()));
            }
            if (i + 1 < sap.size()) {
                Co sau = sap.get(i + 1);
                if (c.tu() == null || sau.den() == null || c.tu().compareTo(sau.den()) != 0) {
                    throw new BusinessRuleException(
                            ErrorCode.OPS_2031,
                            "cận dưới của \"%s\" phải bằng cận trên của \"%s\"".formatted(c.nhan(), sau.nhan()));
                }
            }
        }
    }
}
