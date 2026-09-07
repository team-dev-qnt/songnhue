package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng của màn hình <b>Dữ liệu nghi ngờ</b> — T32.7.
 *
 * <p>⭐ Mọi thứ người duyệt cần để quyết định <i>Duyệt</i> hay <i>Loại bỏ</i> phải nằm trong record
 * này, vì đó là toàn bộ những gì họ thấy. Ba mảnh chịu lực:
 *
 * <ul>
 *   <li>{@code lyDoMay} — vì sao bộ phân loại bắt dòng này. Thiếu nó thì cờ đỏ không hành động
 *       được: người duyệt không phân biệt được "cảm biến hỏng" (⇒ loại bỏ) với "vừa mở cống"
 *       (⇒ duyệt), mà hai việc ấy ngược nhau.
 *   <li>{@code giaTri} + {@code donVi} — ⛔ luôn đi cạnh nhau. Một ô số không nhãn sẽ được đọc bằng
 *       đơn vị người xem đang nghĩ tới, và nguồn trả cm còn hệ thống lưu m.
 *   <li>{@code rawLogId} — đường về nguyên văn response đã sinh ra dòng này. Câu hỏi <i>"số này
 *       parse từ đâu ra"</i> là câu duy nhất phân biệt được lỗi cảm biến với lỗi bộ bóc tách.
 * </ul>
 *
 * @param id khoá của {@code hydro_readings}; ⚠ đủ để định danh vì {@code id} sinh trên bảng cha
 * @param diemDoPublicId để giao diện liên kết sang hồ sơ điểm đo, ⛔ không lộ khoá nội bộ
 * @param trangThai {@link ReadingQuality#NGHI_NGO} hoặc {@link ReadingQuality#XOA} — ⛔ màn hình này
 *     ⛔ không phục vụ {@code HOP_LE}
 * @param lyDoNguoi lời người duyệt ({@code review_note}); {@code null} khi chưa ai xử lý
 */
public record SoDoNghiNgo(
        long id,
        Instant mocDo,
        UUID diemDoPublicId,
        String diemDoCode,
        String diemDoName,
        String loaiChiSoCode,
        String loaiChiSoName,
        String donVi,
        BigDecimal giaTri,
        ReadingQuality trangThai,
        String lyDoMay,
        String lyDoNguoi,
        ReadingSource nguon,
        Instant mocGhi,
        Long rawLogId) {}
