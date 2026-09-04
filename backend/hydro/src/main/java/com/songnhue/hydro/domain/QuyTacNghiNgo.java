package com.songnhue.hydro.domain;

import java.math.BigDecimal;

/**
 * Quy tắc phân loại "nghi ngờ" của <b>một loại chỉ số</b> — T32.1.
 *
 * <p>Khai bằng dữ liệu ở khoá {@code hydro.quality.suspect-rule} (JSON, có màn hình sửa — quy tắc
 * 12), ⛔ không hard-code. {@link BoQuyTacNghiNgo} là bộ đọc.
 *
 * <h2>⚠⚠ Đây là VỎ BỌC SAI HỎNG CẢM BIẾN, ⛔ không phải ngưỡng cảnh báo</h2>
 *
 * <p>Hai thứ nghe giống nhau và hoàn toàn khác nhau, lẫn lộn chúng là hỏng cả hai:
 *
 * <table border="1">
 *   <caption>Phân biệt</caption>
 *   <tr><th></th><th>Khoảng vật lý ở đây</th><th>Ngưỡng cảnh báo (G9-a, WS-33)</th></tr>
 *   <tr><td>Trả lời câu</td><td><i>số này có thể là số thật không</i></td>
 *       <td><i>mực nước này có đáng lo không</i></td></tr>
 *   <tr><td>Phạm vi</td><td>theo <b>loại chỉ số</b> — cả hệ dùng chung</td>
 *       <td>theo <b>từng điểm đo × từng mức</b></td></tr>
 *   <tr><td>Vượt thì</td><td>đánh dấu {@code NGHI_NGO}, ⛔ vẫn ghi</td>
 *       <td>bắn cảnh báo, ⛔ không đụng tới bản ghi</td></tr>
 *   <tr><td>Ai chốt</td><td>vận hành hệ thống — biên rất rộng</td><td>Công ty — G9-a</td></tr>
 * </table>
 *
 * <p>⇒ Đặt khoảng vật lý sát số vận hành thật là biến mọi trận lũ thành "dữ liệu nghi ngờ" và người
 * trực sẽ thôi đọc nhãn ấy. Biên phải <b>rộng tới mức chỉ hỏng cảm biến mới chạm tới</b>.
 *
 * @param min cận dưới, đơn vị <b>chuẩn hoá</b> của loại chỉ số ({@code measurement_types.unit});
 *     {@code null} = không kiểm cận dưới
 * @param max cận trên, cùng đơn vị; {@code null} = không kiểm cận trên
 * @param deltaToiDaMoiGio chênh lệch tuyệt đối tối đa cho mỗi giờ so với bản ghi <b>hợp lệ</b> liền
 *     trước; {@code null} = ⛔ <b>không kiểm</b> (xem javadoc {@link BoQuyTacNghiNgo} về vì sao mặc
 *     định là không kiểm)
 */
public record QuyTacNghiNgo(BigDecimal min, BigDecimal max, BigDecimal deltaToiDaMoiGio) {

    /**
     * Quy tắc <b>không kiểm gì</b> — dùng khi loại chỉ số không có mục nào trong cấu hình.
     *
     * <p>⚠ Đây là câu trả lời "chưa ai khai quy tắc cho loại chỉ số này", ⛔ không phải "đã kiểm và
     * thấy hợp lệ". Hai thứ ấy phân biệt được ở {@link PhanLoaiChatLuong#danhGia} và phải giữ như
     * vậy: một hệ thống chưa cấu hình mà báo <i>"toàn bộ dữ liệu hợp lệ"</i> là đang khẳng định một
     * điều nó không biết.
     */
    public static final QuyTacNghiNgo KHONG_KIEM = new QuyTacNghiNgo(null, null, null);

    public QuyTacNghiNgo {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                    "Khoảng vật lý đảo ngược: min=" + min + " > max=" + max + ". Không giá trị nào lọt qua "
                            + "được, nên MỌI bản ghi sẽ thành NGHI_NGO — kiểm lại hydro.quality.suspect-rule.");
        }
        if (deltaToiDaMoiGio != null && deltaToiDaMoiGio.signum() < 0) {
            throw new IllegalArgumentException("deltaToiDaMoiGio âm (" + deltaToiDaMoiGio
                    + ") — nó là chênh lệch TUYỆT ĐỐI mỗi giờ, không có chiều.");
        }
    }

    /** Quy tắc này có kiểm được gì không — {@code false} nghĩa là chưa ai khai. */
    public boolean coKiem() {
        return min != null || max != null || deltaToiDaMoiGio != null;
    }
}
