package com.songnhue.hydro.domain;

/**
 * Trạng thái một bản ghi số đo — <b>hai mức chất lượng + một bia mộ</b> (function-spec.md CN-03.3,
 * chốt F2). Đây cũng là <b>cột trạng thái của quy trình {@code HYDRO_READING}</b> (WS-32/T32.5).
 *
 * <h2>⚠⚠ Vì sao enum này là bẫy sai số liệu dễ mắc nhất của dự án (quy tắc 14)</h2>
 *
 * <p>Bản ghi {@link #NGHI_NGO} <b>nằm chung bảng chính</b> {@code hydro_readings}, không bị đẩy sang
 * một bảng cách ly. Đó là quyết định đúng — dữ liệu nghi ngờ vẫn là dữ liệu, và nguồn không có API
 * lịch sử nên vứt đi là vứt vĩnh viễn — nhưng nó có một hệ quả sắc: <b>mọi truy vấn báo cáo, cảnh
 * báo và tổng hợp phải tự lọc {@code quality = HOP_LE}</b>, và quên một chỗ thì con số sai vẫn ra
 * đúng định dạng, vẫn vẽ được biểu đồ đẹp, và không có lỗi nào.
 *
 * <p>Ở tầng lược đồ, thứ đỡ được phần lớn rủi ro ấy là {@code hydro_latest}: nó tách sẵn
 * {@code valid_value} (giá trị HỢP LỆ gần nhất) khỏi {@code last_seen_at} (bản ghi gần nhất bất kể
 * chất lượng). Widget cổng và lớp GIS đọc {@code valid_value} nên <b>không có cách nào</b> hiện
 * nhầm một số đang bị nghi ngờ — bảo đảm đặt ở chỗ dữ liệu đi qua, không đặt ở nơi gọi (luật 12).
 * Bộ canh cho các truy vấn còn lại là {@code QualityFilterGuardTest} (T32.4).
 *
 * <h2>⭐ {@link #XOA} là bia mộ, ⛔ KHÔNG phải "mức chất lượng thứ ba"</h2>
 *
 * <p>Câu cấm nguyên bản ở đây — <i>"⛔ không thêm mức thứ ba, vì mỗi truy vấn sẽ phải trả lời mức
 * giữa tính hay không tính và hai người sẽ trả lời hai kiểu"</i> — nói về <b>thang chất lượng</b>, và
 * nó vẫn nguyên giá trị: giữa {@code HOP_LE} và {@code NGHI_NGO} ⛔ không được chèn thêm mức nào.
 *
 * <p>{@link #XOA} không nằm trên thang ấy. Nó là kết cục của bước chuyển <i>xoá mềm</i> mà chốt F2
 * đòi (<i>"Xoá — soft delete + audit ai xoá, lý do"</i>), và nó ⛔ <b>không tạo ra câu hỏi "tính hay
 * không tính"</b>: bộ lọc {@code quality = 'HOP_LE'} mà quy tắc 14 vốn đã bắt mọi truy vấn phải có
 * loại nó ra <b>miễn phí</b>, không cần thêm một vế thứ hai. Đó chính là lý do chọn cách này thay vì
 * một cột {@code deleted_at} riêng: hai cột cùng trả lời <i>"dòng này còn dùng được không"</i> là hai
 * chỗ để quên, và bộ canh T32.4 sẽ phải soi hai bất biến thay vì một (luật 14).
 *
 * <p>⚠ Hệ quả phải nhớ: đường ingest ⛔ <b>không bao giờ</b> sinh ra {@code XOA} — một bản ghi vừa
 * về không thể sinh ra đã bị xoá. {@code ReadingRow} ép điều đó ở hàm dựng.
 */
public enum ReadingQuality {

    /** Qua bộ quy tắc chuẩn hoá — được dùng cho báo cáo, cảnh báo, hiển thị. */
    HOP_LE,

    /**
     * Vượt khoảng vật lý hoặc nhảy quá delta/giờ của loại chỉ số ({@code hydro.quality.suspect-rule}).
     *
     * <p>⛔ Vẫn ghi vào bảng chính, ⛔ vẫn giữ nguyên giá trị. Việc của người có quyền
     * {@code hyd:measurement:review} là duyệt lên {@link #HOP_LE} hoặc {@link #XOA} — và bước chuyển
     * ấy đi qua Workflow engine (WS-32/T32.5), ⛔ không sửa cột trực tiếp.
     */
    NGHI_NGO,

    /**
     * Xoá mềm — người duyệt kết luận bản ghi này không dùng được, kèm lý do bắt buộc.
     *
     * <p>⛔ Dòng <b>vẫn nằm nguyên</b> trong bảng, giá trị gốc <b>không bị sửa</b>: nguồn không có
     * API lịch sử nên một lượt xoá cứng là mất vĩnh viễn (quy tắc 18), và nguyên văn response vẫn
     * nằm ở {@code hydro_raw_logs} để đối chiếu.
     *
     * <p>⛔ Không có đường quay lại: {@code XOA} là trạng thái cuối. Một dòng xoá nhầm thì nhập lại
     * bằng đường {@code MANUAL} — có người ký tên — chứ không "hoàn tác" một bước đã ghi vào chuỗi
     * băm của nhật ký kiểm toán (quy tắc 18: <i>bịa một bước chuyển là bịa một chữ ký</i>).
     */
    XOA
}
