package com.songnhue.hydro.domain;

/**
 * Bản ghi số đo này do đâu mà có.
 *
 * <p>Phân biệt được hai nguồn là điều kiện để trả lời một câu hỏi vận hành có thật: <i>"khoảng trống
 * ngày 3/9 là do poller chết hay do nguồn không phát?"</i> — nếu ai đó đã nhập tay bù vào thì hai
 * tình huống ấy trông giống hệt nhau trên biểu đồ.
 *
 * <p>Ràng buộc {@code ck_hydro_readings_nguoi_nhap} ép luôn ở tầng CSDL: chỉ {@link #MANUAL} mới
 * được mang {@code created_by} và {@code note}. Máy ghi thì không mượn tên ai — cùng tinh thần quy
 * tắc 18 (<i>bịa một bước chuyển là bịa một chữ ký</i>).
 */
public enum ReadingSource {

    /** Poller lấy về từ nguồn bên thứ 3. {@code created_by} luôn NULL. */
    API,

    /**
     * Người trực nhập tay khi API gián đoạn (WS-32/T32.7).
     *
     * <p>⛔ Không phải đường thay thế cho poller: nó tồn tại để một khoảng mất tín hiệu dài không
     * biến thành một khoảng trống vĩnh viễn, và mỗi dòng đều có người chịu trách nhiệm.
     */
    MANUAL
}
