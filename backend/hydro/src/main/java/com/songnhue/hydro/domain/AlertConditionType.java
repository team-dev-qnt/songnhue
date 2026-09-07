package com.songnhue.hydro.domain;

/**
 * Bốn dạng điều kiện của một ngưỡng cảnh báo — CN-03.6, WS-33 (T33.2).
 *
 * <p>⚠ Tên hằng ở đây <b>trùng khít</b> giá trị của {@code ck_alert_rules_condition_type} trong CSDL
 * và của {@code LOAI_DIEU_KIEN} ở {@code hydroVocabulary.ts}. Ba nơi phải nhớ cùng một danh sách nên
 * ba nơi có một phép kiểm nhớ hộ ({@code HydroEnumSchemaTest}) — luật 14.
 *
 * <h2>⛔ Đây KHÔNG phải vỏ bọc chất lượng của WS-32</h2>
 *
 * <p>Hai cơ chế đọc cùng một con số và trả lời hai câu hỏi khác hẳn nhau; trộn chúng là hỏng cả hai:
 *
 * <table border="1">
 *   <caption>Hai câu hỏi khác nhau về cùng một số đo</caption>
 *   <tr><th></th><th>{@code QuyTacNghiNgo} (WS-32)</th><th>{@code AlertRule} (WS-33)</th></tr>
 *   <tr><td>Hỏi gì</td><td><i>"Con số này có phải số THẬT không"</i></td>
 *       <td><i>"Con số thật này có đáng lo không"</i></td></tr>
 *   <tr><td>Ai đặt</td><td>Kỹ thuật, suy từ dải đo của cảm biến</td>
 *       <td><b>Công ty</b>, theo nghiệp vụ phòng chống lụt bão (G9)</td></tr>
 *   <tr><td>Vi phạm thì sao</td><td>Gắn cờ {@code NGHI_NGO}, chờ người duyệt</td>
 *       <td>Phát cảnh báo tới người trực, đổi màu marker GIS</td></tr>
 *   <tr><td>Phạm vi</td><td>Theo <b>loại chỉ số</b> (một dải cho mọi trạm)</td>
 *       <td>Theo <b>điểm đo × loại chỉ số × mức</b> (SRS §3.3.3 cấm dùng chung một ngưỡng)</td></tr>
 * </table>
 *
 * <p>⛔ Hệ quả trực tiếp: một số đo {@code NGHI_NGO} ⛔ <b>không bao giờ</b> được đem đi đánh giá
 * ngưỡng (T33.5, quy tắc 14) — hỏi <i>"có đáng lo không"</i> về một con số ta còn chưa tin là thật
 * thì câu trả lời nào cũng vô nghĩa, và nó sẽ đánh thức người trực lúc nửa đêm vì một cảm biến hỏng.
 */
public enum AlertConditionType {

    /** Vượt <b>trên</b> {@code threshold_value}. Dạng thường gặp nhất: mực nước lên quá mức báo động. */
    GT,

    /** Xuống <b>dưới</b> {@code threshold_value}. Cạn kiệt nguồn nước tưới, bể hút thiếu nước bơm. */
    LT,

    /**
     * Ra <b>ngoài</b> khoảng {@code [threshold_value, threshold_value_high]}.
     *
     * <p>⚠ Dạng duy nhất bắt buộc có {@code threshold_value_high} — {@code ck_alert_rules_khoang_du_hai_can}
     * ép ở CSDL, ⛔ không để tầng ứng dụng nhớ hộ.
     */
    OUT_OF_RANGE,

    /**
     * <b>Tốc độ đổi</b> vượt {@code threshold_value} đơn vị mỗi giờ.
     *
     * <p>⭐ Dạng duy nhất cần <b>hai</b> số đo, và là dạng bắt được thứ ba dạng kia mù: nước lên
     * <i>nhanh</i> trong khi vẫn còn dưới mọi mức báo động. Một cống mở sai chiều lúc triều cường
     * cho ra đúng hình dạng ấy.
     *
     * <p>⛔ Hai số đo phải cùng <b>một điểm đo</b> — xem {@link SoDoTruoc}, vốn cố ý ⛔ không mang
     * id trạm nào để phép so chéo hai điểm đo là thứ <i>không viết ra được</i> ở tầng này (T32.2).
     */
    RATE_OF_CHANGE
}
