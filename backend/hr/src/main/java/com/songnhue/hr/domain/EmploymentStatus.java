package com.songnhue.hr.domain;

/**
 * Trạng thái làm việc — CN-04.2, sáu giá trị đặc tả liệt kê đích danh.
 *
 * <p>⛔⛔ {@code ck_employees_terminated_pairs} ràng buộc <b>hai chiều</b> ở CSDL: có
 * {@code terminated_at} ⟺ trạng thái là {@link #NGHI_VIEC} hoặc {@link #NGHI_HUU}. Để lọt tổ hợp
 * <i>"đã nghỉ mà vẫn ĐANG LÀM"</i> là làm mọi phép đếm quân số sai mà ⛔ không màn hình nào báo —
 * cùng hình dạng {@code ck_maintenance_logs_severity} của MOD-02.
 *
 * <p>Bộ ba enum ↔ TS ↔ CHECK, xem {@link Gender}.
 */
public enum EmploymentStatus {
    DANG_LAM,
    THU_VIEC,
    THAI_SAN,
    KHONG_LUONG,
    NGHI_VIEC,
    NGHI_HUU;

    /**
     * Đã rời Công ty — <b>một</b> định nghĩa cho cả kho.
     *
     * <p>⚠ Câu *"chỉ NV 'Đang làm'"* của đặc tả CN-04.6 ⛔ <b>không</b> có nghĩa
     * {@code status = DANG_LAM}: nó đối lập với <i>đã nghỉ</i>, ⛔ không đối lập với thử việc / thai
     * sản / nghỉ ⛔ không lương. Một người đang nghỉ thai sản vẫn là người của Công ty và vẫn có số
     * điện thoại trong danh bạ; loại họ ra là một quyết định nhân sự ⛔ không ai duyệt.
     *
     * <p>⛔⛔ Bộ hai giá trị này còn có <b>một bản chép thứ hai</b> ⛔ không tham chiếu được về đây:
     * JPQL của {@code EmployeeRepository.hopDongSapHetHan} phải viết literal
     * {@code NOT IN (NGHI_VIEC, NGHI_HUU)} vì JPQL ⛔ không gọi được phương thức Java. Đó là luật 14
     * ở dạng ⛔ không gỡ được bằng mã ⇒ sửa một bên phải sửa bên kia, và
     * {@code DanhBaHttpTest.chiHienNguoiCON_LAM_VIEC} là phép kiểm nhớ hộ cho vế danh bạ.
     */
    public boolean daNghi() {
        return this == NGHI_VIEC || this == NGHI_HUU;
    }

    /** Còn tính vào quân số: danh bạ nội bộ (CN-04.6) chỉ hiện người còn đi làm. */
    public boolean conLamViec() {
        return !daNghi();
    }

    /**
     * Tên các trạng thái <b>đã nghỉ</b>, dạng chuỗi — cho câu SQL của danh bạ.
     *
     * <p>⛔ Suy từ chính {@link #daNghi()} chứ ⛔ không liệt kê tay: một hằng số liệt kê tay là bản
     * chép **thứ ba** của cùng một luật, và ngày ai đó thêm {@code NGHI_MAT_SUC} thì hai bản nói
     * khác nhau mà ⛔ không một dòng lỗi nào.
     */
    public static java.util.List<String> tenCacTrangThaiDaNghi() {
        return java.util.Arrays.stream(values())
                .filter(EmploymentStatus::daNghi)
                .map(Enum::name)
                .toList();
    }
}
