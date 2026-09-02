package com.songnhue.hydro.domain;

/**
 * Chất lượng một bản ghi số đo — <b>đúng hai mức</b> (function-spec.md CN-03.3, chốt F2).
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
 * Bộ canh cho các truy vấn còn lại dựng ở WS-32/T32.4.
 *
 * <p>⛔ Không thêm mức thứ ba. Ba mức thì mỗi truy vấn phải trả lời "mức giữa tính hay không tính",
 * và hai người sẽ trả lời hai kiểu.
 */
public enum ReadingQuality {

    /** Qua bộ quy tắc chuẩn hoá — được dùng cho báo cáo, cảnh báo, hiển thị. */
    HOP_LE,

    /**
     * Vượt khoảng vật lý hoặc nhảy quá delta/giờ của loại chỉ số ({@code hydro.quality.suspect-rule}).
     *
     * <p>⛔ Vẫn ghi vào bảng chính, ⛔ vẫn giữ nguyên giá trị. Việc của người có quyền
     * {@code hyd:measurement:review} là duyệt lên {@link #HOP_LE} hoặc loại bỏ — và bước chuyển ấy
     * đi qua Workflow engine (WS-32/T32.5), ⛔ không sửa cột trực tiếp.
     */
    NGHI_NGO
}
