package com.songnhue.hr.domain;

/**
 * Mười loại sự kiện của timeline công tác — CN-04.4, nguyên văn đặc tả.
 *
 * <h2>⛔ ĐÚNG mười, và {@link #BO_NHIEM_MIEN_NHIEM} cố ý là MỘT giá trị</h2>
 *
 * <p>Đặc tả liệt kê <i>"Bổ nhiệm/Miễn nhiệm"</i> là một trong mười, và <i>"Nghỉ việc/hưu"</i> cũng
 * vậy. Tách ra cho "rõ hơn" là làm lệch con số 10 mà giao diện lọc và báo cáo đang dựa vào, và nó
 * đảo một quyết định nghiệp vụ trong im lặng — hình dạng T46.5 đã trả giá. Hành vi cụ thể nằm ở
 * {@code title} và số quyết định.
 *
 * <p>⛔ Ba nơi phải khớp: enum này · {@code ck_employee_events_type} · union TS
 * {@code EmployeeEventType}.
 */
public enum EmployeeEventType {
    TUYEN_DUNG,
    /** Ký mới hoặc gia hạn hợp đồng lao động. */
    HOP_DONG,
    DIEU_DONG,
    /** ⛔ MỘT giá trị, đúng đặc tả — xem javadoc lớp. */
    BO_NHIEM_MIEN_NHIEM,
    NANG_LUONG,
    KHEN_THUONG,
    KY_LUAT,
    DAO_TAO,
    NGHI_DAI_HAN,
    /** ⛔ MỘT giá trị: nghỉ việc và nghỉ hưu. */
    NGHI_VIEC_HUU
}
