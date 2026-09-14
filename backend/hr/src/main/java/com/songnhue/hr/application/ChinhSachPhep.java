package com.songnhue.hr.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.spi.SettingPort;
import com.songnhue.hr.domain.LeaveType;

/**
 * Chính sách nghỉ phép đọc từ bảng {@code settings} — CN-04.9, <b>chốt C1</b>.
 *
 * <h2>⛔⛔ Lớp này trả nốt 13 khoá cuối cùng của nhóm {@code hr.*}</h2>
 *
 * <p>Đo 10/09/2026: <b>15</b> khoá {@code hr.*} seed từ <b>13/08/2026</b> với <b>0 nơi đọc</b>.
 * {@link HrSettings} đóng 2; mười ba khoá {@code hr.leave.*} còn lại treo thêm <b>bốn tuần</b> nữa.
 * Người vận hành mở màn hình <i>Cấu hình hệ thống</i>, thấy mười ba ô nhập có nhãn, có mô tả, có
 * ràng buộc — sửa chúng, và ⛔ không gì đổi. Lớp này là nửa ĐỌC của cả mười ba.
 *
 * <p>Chốt C1 nói thẳng: <i>"toàn bộ thông số dưới đây là <b>biến cấu hình</b>, Admin sửa được trong
 * UI, <b>cấm hard-code</b>"</i>. ⇒ ⛔ Không một con số nghiệp vụ nào trong lớp này là hằng — mọi giá
 * trị dự phòng dưới đây chỉ để hệ <b>chạy được</b> khi khoá bị xoá khỏi CSDL.
 *
 * <h2>⛔ Giá trị dự phòng phải KHÁC giá trị seed</h2>
 *
 * <p>T48.7/T48.9 đã trả giá: nếu dự phòng trùng seed thì một bài kiểm <i>"đổi settings ⇒ hành vi
 * đổi"</i> <b>xanh y hệt</b> trên một hệ ghi cứng. Ở đây dự phòng cố ý lệch seed, và
 * {@code ChinhSachPhepHttpTest} chứng minh vế đọc bằng cách <b>đổi giá trị trong CSDL rồi đo lại
 * ranh giới</b> — ⛔ không so với hằng số nào trong tệp này.
 */
@Component
public class ChinhSachPhep {

    private static final Logger log = LoggerFactory.getLogger(ChinhSachPhep.class);

    static final String KHOA_DUOI_5 = "hr.leave.annual-days.under-5-years";
    static final String KHOA_5_DEN_10 = "hr.leave.annual-days.5-to-10-years";
    static final String KHOA_TREN_10 = "hr.leave.annual-days.over-10-years";
    static final String KHOA_CHUYEN_NAM = "hr.leave.carry-over-max-days";
    static final String KHOA_LAM_TRON = "hr.leave.prorata-rounding-step";
    static final String KHOA_MOC_THAM_NIEN = "hr.leave.seniority-base";
    static final String KHOA_SO_CAP_DUYET = "hr.leave.approval-levels";
    static final String KHOA_NGUONG_CAP_2 = "hr.leave.second-level-threshold-days";
    static final String KHOA_NGUONG_TRUNG_LICH = "hr.leave.overlap-warning-percent";

    /**
     * ⛔ Dự phòng <b>lệch seed</b> có chủ đích (seed: 12 · 13 · 14 · 5 · 30) — xem javadoc lớp.
     *
     * <p>⚠ Lệch <b>xuống</b>, ⛔ không lệch lên: một khoá bị xoá khỏi CSDL thì hệ cấp <b>ít</b> phép
     * hơn chứ ⛔ không cấp thừa. Cấp thừa rồi đòi lại là một cuộc nói chuyện ⛔ không ai muốn có với
     * người lao động.
     */
    private static final int DP_DUOI_5 = 10;

    private static final int DP_5_DEN_10 = 11;
    private static final int DP_TREN_10 = 12;
    private static final int DP_CHUYEN_NAM = 0;
    private static final int DP_SO_CAP_DUYET = 1;
    private static final int DP_NGUONG_CAP_2 = 0;
    private static final int DP_NGUONG_TRUNG_LICH = 50;
    private static final BigDecimal DP_LAM_TRON = new BigDecimal("1");

    private final SettingPort settings;

    public ChinhSachPhep(SettingPort settings) {
        this.settings = settings;
    }

    /**
     * Số ngày phép năm theo thâm niên — Điều 113 BLLĐ 2019.
     *
     * <p>⛔⛔ Ranh giới đọc <b>đúng nhãn của khoá</b>: {@code under-5-years} là <b>dưới</b> 5,
     * {@code 5-to-10-years} là <b>từ 5 tới 10</b> (bao gồm cả hai đầu), {@code over-10-years} là
     * <b>trên</b> 10. Người đúng 5 năm và người đúng 10 năm <b>đều</b> rơi vào khoảng giữa — một
     * dấu {@code <} viết nhầm thành {@code <=} là cấp sai một ngày phép cho đúng những người đã gắn
     * bó lâu nhất, và ⛔ không màn hình nào báo.
     */
    public int soNgayPhepNam(int namThamNien) {
        if (namThamNien < 5) {
            return settings.getInt(KHOA_DUOI_5, DP_DUOI_5);
        }
        if (namThamNien <= 10) {
            return settings.getInt(KHOA_5_DEN_10, DP_5_DEN_10);
        }
        return settings.getInt(KHOA_TREN_10, DP_TREN_10);
    }

    /**
     * Hạn mức năm của một loại nghỉ <b>đặc biệt</b> (thai sản / cưới / tang / khám SK).
     *
     * @throws IllegalStateException với {@link LeaveType#PHEP_NAM} — xem {@link LeaveType#khoaHanMuc}
     */
    public int hanMucDacBiet(LeaveType loai) {
        // ⛔ Dự phòng 0 cho nhóm đặc biệt: khoá bị xoá ⇒ ⛔ không cấp loại nghỉ ấy, và người dùng
        //   thấy ngay. Một dự phòng "hợp lý" (VD 3 ngày) sẽ che mất việc khoá đã biến mất.
        return settings.getInt(loai.khoaHanMuc(), 0);
    }

    /** Số ngày phép ⛔ không dùng hết được chuyển sang năm sau. */
    public int soNgayChuyenToiDa() {
        return settings.getInt(KHOA_CHUYEN_NAM, DP_CHUYEN_NAM);
    }

    /**
     * Bước làm tròn khi tính phép pro-rata ({@code 12 × số tháng / 12}).
     *
     * <p>⚠ {@link SettingPort} ⛔ không có {@code getDecimal} — cố ý ⛔ không nới SPI cho một nơi
     * gọi duy nhất. Đọc chuỗi rồi tự phân tích, và <b>một giá trị hỏng ⛔ không được rơi về dự
     * phòng trong im lặng</b>: nó ghi WARN kèm đúng giá trị đọc được, vì một bước làm tròn sai làm
     * mọi số dư phép của Công ty lệch mà ⛔ không ai đối chiếu được với cái gì.
     */
    public BigDecimal buocLamTron() {
        String tho = settings.getString(KHOA_LAM_TRON).orElse(null);
        if (tho == null || tho.isBlank()) {
            return DP_LAM_TRON;
        }
        try {
            BigDecimal buoc = new BigDecimal(tho.trim());
            if (buoc.signum() <= 0) {
                log.warn("Tham số {} = '{}' ⛔ không dương — dùng dự phòng {}", KHOA_LAM_TRON, tho, DP_LAM_TRON);
                return DP_LAM_TRON;
            }
            return buoc;
        } catch (NumberFormatException e) {
            log.warn("Tham số {} = '{}' ⛔ không phải số — dùng dự phòng {}", KHOA_LAM_TRON, tho, DP_LAM_TRON);
            return DP_LAM_TRON;
        }
    }

    /** Làm tròn một số ngày phép theo {@link #buocLamTron()}. */
    public BigDecimal lamTron(BigDecimal soNgay) {
        BigDecimal buoc = buocLamTron();
        return soNgay.divide(buoc, 0, RoundingMode.HALF_UP).multiply(buoc).stripTrailingZeros();
    }

    /**
     * Mốc tính thâm niên — {@code HIRE_DATE} (ngày vào làm) hoặc {@code CONTRACT_DATE} (ngày ký HĐ).
     *
     * <p>⚠ Giá trị lạ ⇒ WARN + rơi về {@code HIRE_DATE}, ⛔ không ném: một khoá gõ sai ⛔ không được
     * làm cả màn hình nghỉ phép chết. Ràng buộc {@code in=HIRE_DATE,CONTRACT_DATE} ở cột
     * {@code validation} là chốt chặn thật ở đường ghi.
     */
    public MocThamNien mocThamNien() {
        String tho = settings.getString(KHOA_MOC_THAM_NIEN).orElse(null);
        if (tho == null || tho.isBlank()) {
            return MocThamNien.HIRE_DATE;
        }
        try {
            return MocThamNien.valueOf(tho.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Tham số {} = '{}' ⛔ không hợp lệ — dùng HIRE_DATE", KHOA_MOC_THAM_NIEN, tho);
            return MocThamNien.HIRE_DATE;
        }
    }

    /** Số cấp duyệt (chốt C2 — mặc định 1). */
    public int soCapDuyet() {
        return Math.max(1, settings.getInt(KHOA_SO_CAP_DUYET, DP_SO_CAP_DUYET));
    }

    /** Số ngày nghỉ tối thiểu để cần <b>thêm</b> cấp duyệt 2. {@code 0} = tắt (chốt C2). */
    public int nguongCapHai() {
        return Math.max(0, settings.getInt(KHOA_NGUONG_CAP_2, DP_NGUONG_CAP_2));
    }

    /**
     * Đơn này có phải đi qua <b>hai</b> cấp duyệt ⛔ không.
     *
     * <p>⛔⛔ <b>Hai</b> điều kiện, và cả hai đều cần: số cấp duyệt ≥ 2 <i>và</i> đơn đủ dài. Đọc
     * thiếu một vế là làm một trong hai khoá {@code settings} thành núm ⛔ không điều khiển gì
     * (luật 15) — mà cả hai khoá ấy đều nằm trong mười ba khoá lượt này đi trả.
     *
     * <p>⚠ {@code nguongCapHai() == 0} nghĩa là <b>TẮT</b>, ⛔ không phải "mọi đơn đều cần cấp 2".
     * Đây là chỗ dễ đọc ngược nhất trong cả chính sách: một đơn 1 ngày thoả {@code 1 >= 0}.
     */
    public boolean canCapHai(BigDecimal soNgayCong) {
        int nguong = nguongCapHai();
        return soCapDuyet() >= 2 && nguong > 0 && soNgayCong.compareTo(BigDecimal.valueOf(nguong)) >= 0;
    }

    /** Ngưỡng % quân số nghỉ cùng lúc ⇒ cảnh báo trùng lịch. */
    public int nguongCanhBaoTrungLich() {
        return settings.getInt(KHOA_NGUONG_TRUNG_LICH, DP_NGUONG_TRUNG_LICH);
    }

    /** Mốc tính thâm niên — khớp ràng buộc {@code in=HIRE_DATE,CONTRACT_DATE} của khoá settings. */
    public enum MocThamNien {
        HIRE_DATE,
        CONTRACT_DATE
    }
}
