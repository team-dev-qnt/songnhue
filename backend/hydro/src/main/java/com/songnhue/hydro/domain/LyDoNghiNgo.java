package com.songnhue.hydro.domain;

/**
 * Vì sao một bản ghi bị đánh dấu {@link ReadingQuality#NGHI_NGO} — T32.1.
 *
 * <h2>⭐ Vì sao là enum chứ không phải một chuỗi tự do</h2>
 *
 * <p>Hai lý do dưới đây đòi <b>hai cách xử lý ngược nhau</b> của người duyệt, nên gộp chúng thành
 * một dòng chữ là xoá mất chính thông tin cần (§10.68-B — bản cũ của bước SSH cho <i>cùng một vân
 * tay</i> cho ba nguyên nhân cần ba cách xử lý khác nhau):
 *
 * <ul>
 *   <li>{@link #NGOAI_KHOANG_VAT_LY} — con số <b>không thể đúng</b> ở bất kỳ tình huống vận hành
 *       nào. Duyệt lên hợp lệ là đưa một số vô nghĩa vào báo cáo; đường đi đúng gần như luôn là
 *       xoá, và đi kiểm cảm biến.
 *   <li>{@link #NHAY_QUA_NHANH} — con số <b>có thể đúng</b>: mở cống, xả lũ, bơm tiêu đều làm mực
 *       nước đổi rất nhanh trong ít phút. Đây là lý do người duyệt tồn tại — máy không phân biệt
 *       được "cảm biến nhiễu" với "vừa mở cống", còn người trực thì biết.
 * </ul>
 *
 * <p>⛔ <b>Không có lý do nào so sánh giữa hai điểm đo</b>, và đó là một cấm lệnh nghiệp vụ, không
 * phải một chỗ chưa làm — xem {@link PhanLoaiChatLuong}.
 */
public enum LyDoNghiNgo {

    /** Giá trị nằm ngoài khoảng vật lý khai ở {@code hydro.quality.suspect-rule}. */
    NGOAI_KHOANG_VAT_LY,

    /** Chênh lệch so với bản ghi <b>hợp lệ</b> liền trước vượt {@code deltaToiDaMoiGio}. */
    NHAY_QUA_NHANH
}
