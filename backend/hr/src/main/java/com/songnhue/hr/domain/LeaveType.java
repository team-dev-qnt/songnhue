package com.songnhue.hr.domain;

/**
 * Loại nghỉ — CN-04.9, <b>đúng năm loại đặc tả liệt kê</b>.
 *
 * <p>⛔⛔ ⛔ Không có {@code KHONG_LUONG}. Nghe rất thiếu — nghỉ ⛔ không lương là chuyện có thật —
 * nhưng đặc tả chốt C1 liệt kê đích danh: <i>"phép năm theo thâm niên; phép đặc biệt (thai sản 180,
 * cưới 3, tang 3, khám SK 1)"</i>, và mỗi loại ở đây có <b>một khoá {@code settings} tương ứng</b>.
 * Thêm một loại thứ sáu là dựng một hạn mức ⛔ không ai duyệt. ⚠ Nghỉ ⛔ không lương <b>đã có</b> chỗ
 * khác trong hệ: {@link EmploymentStatus#KHONG_LUONG} — một <i>trạng thái công tác</i> dài ngày, ⛔
 * không phải một đơn xin nghỉ.
 *
 * <p>Bộ ba enum ↔ TypeScript ↔ {@code CHECK}, xem {@link Gender}.
 */
public enum LeaveType {

    /**
     * Phép năm — loại <b>DUY NHẤT</b> trừ vào số dư.
     *
     * <p>Bốn loại còn lại có hạn mức riêng theo năm ({@code hr.leave.special.*}) và ⛔ không đụng
     * tới số ngày phép năm. Gộp chúng vào một số dư là làm người nghỉ thai sản mất sạch phép năm.
     */
    PHEP_NAM,

    THAI_SAN,
    CUOI,
    TANG,
    KHAM_SUC_KHOE;

    /** ⛔ Chỉ {@link #PHEP_NAM} trừ số dư — xem javadoc của nó. */
    public boolean truVaoSoDuPhepNam() {
        return this == PHEP_NAM;
    }

    /**
     * Khoá {@code settings} chứa hạn mức năm của loại nghỉ <b>đặc biệt</b> này.
     *
     * <p>⛔ Ném cho {@link #PHEP_NAM}: hạn mức phép năm ⛔ không phải một con số, nó là một
     * <b>hàm của thâm niên</b> (ba khoá {@code hr.leave.annual-days.*} — cơ sở Điều 113 + bậc Điều 114, T68.10)
     * cộng số chuyển từ năm
     * trước. Trả một khoá duy nhất ở đây là mời người gọi sau đọc nhầm nó thành "12 ngày cho tất
     * cả mọi người".
     */
    public String khoaHanMuc() {
        if (this == PHEP_NAM) {
            throw new IllegalStateException(
                    "PHEP_NAM ⛔ không có hạn mức cố định — nó là hàm của thâm niên, xem ChinhSachPhep");
        }
        return switch (this) {
            case THAI_SAN -> "hr.leave.special.maternity-days";
            case CUOI -> "hr.leave.special.marriage-days";
            case TANG -> "hr.leave.special.bereavement-days";
            case KHAM_SUC_KHOE -> "hr.leave.special.health-check-days";
            default -> throw new IllegalStateException("⛔ không tới được");
        };
    }
}
