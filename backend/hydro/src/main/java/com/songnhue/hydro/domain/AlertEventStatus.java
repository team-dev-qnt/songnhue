package com.songnhue.hydro.domain;

/**
 * Trạng thái một lần vượt ngưỡng — <b>T33.3</b>.
 *
 * <p>Ba giá trị, khớp {@code ck_alert_events_status} của migration {@code V202609041062}.
 *
 * <table border="1">
 *   <caption>Ai sinh ra giá trị nào</caption>
 *   <tr><th>Giá trị</th><th>Ai đặt</th><th>Nghĩa</th></tr>
 *   <tr>
 *     <td>{@code DANG_XAY_RA}</td>
 *     <td>máy, lúc điều kiện đúng lần đầu</td>
 *     <td>⚠ <b>Chưa chắc đã là một cảnh báo thật.</b> Còn phải xem {@code confirmed_at}: NULL nghĩa
 *         là điều kiện chưa giữ đủ {@code delay_minutes}, ⛔ chưa gửi thông báo, ⛔ chưa tính vào
 *         mắt xích 3 của trạng thái công trình</td>
 *   </tr>
 *   <tr>
 *     <td>{@code DA_XU_LY}</td>
 *     <td>máy <b>hoặc</b> người</td>
 *     <td>Kết thúc. Phân biệt bằng {@code resolved_by}: NULL = giá trị tự về dưới ngưỡng; có id =
 *         người trực bấm đóng</td>
 *   </tr>
 *   <tr>
 *     <td>{@code FALSE_ALARM}</td>
 *     <td>máy <b>hoặc</b> người</td>
 *     <td>Máy đặt khi điều kiện hết <i>trước</i> lúc xác nhận — một cú nhiễu cảm biến, và ⛔ chưa ai
 *         nhận thông báo nào về nó. Người đặt khi xem lại và bác bỏ một cảnh báo đã gửi</td>
 *   </tr>
 * </table>
 *
 * <p>⛔ <b>Cố ý KHÔNG có giá trị thứ tư</b> cho "máy tự đóng". Nó đã được mang bởi
 * {@code resolved_by IS NULL}, và một enum bốn nhánh cho một câu hỏi nhị phân là bốn nhánh mà mọi
 * lượt đọc sau này phải nhớ — trong đó có mắt xích 3, thứ ghi kết quả xuống một cột của
 * {@code constructions}.
 */
public enum AlertEventStatus {
    DANG_XAY_RA,
    DA_XU_LY,
    FALSE_ALARM
}
