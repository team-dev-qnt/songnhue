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

    /** Đã rời Công ty — hai trạng thái này bắt buộc có ngày nghỉ việc. */
    public boolean daNghi() {
        return this == NGHI_VIEC || this == NGHI_HUU;
    }

    /** Còn tính vào quân số: danh bạ nội bộ (CN-04.6) chỉ hiện người còn đi làm. */
    public boolean conLamViec() {
        return !daNghi();
    }
}
