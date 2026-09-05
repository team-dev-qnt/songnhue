package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Dòng {@code alert_events} đang mở của một quy tắc — ⭐ <b>chính là hysteresis</b> (T33.3).
 *
 * <h2>Vì sao trạng thái này phải đọc từ CSDL ở MỖI lượt đánh giá</h2>
 *
 * <p>§6.4 chốt <i>"app stateless tuyệt đối"</i>. Một {@code Map<ruleId, dangVuot>} trong heap là
 * cách viết ngắn hơn và sai: nó mất sạch ở mỗi lượt deploy, và lượt deploy nào rơi vào giữa một đợt
 * lũ thì cảnh báo đang mở hoặc bắn lại từ đầu, hoặc im lặng đóng. Cả hai đều là <i>hỏng trong im
 * lặng</i> — không log, không dòng nào sai, chỉ là một cái chuông không kêu.
 *
 * <p>Chỉ mục {@code ux_alert_events_mot_cai_dang_mo (rule_id) WHERE status = 'DANG_XAY_RA'} ép bảo
 * đảm <i>"mỗi quy tắc tối đa một cảnh báo đang mở"</i> ở tầng CSDL. Lượt ghi thứ hai đâm vào chỉ mục
 * ấy, ⛔ không đâm vào một biến mà ai đó phải nhớ đồng bộ.
 *
 * @param xacNhanLuc mốc điều kiện đã giữ đủ {@code delay_minutes}. ⚠ {@code null} nghĩa là <b>chưa
 *     phải cảnh báo thật</b>: chưa ai nhận thông báo, và mắt xích 3 ⛔ không đếm nó
 * @param dinh giá trị nặng nhất quan sát được từ lúc bắt đầu — thứ người trực cần thấy trong lịch
 *     sử, không phải giá trị cuối cùng
 */
public record CanhBaoDangMo(
        long id, long ruleId, Instant batDau, Instant xacNhanLuc, BigDecimal dinh, Instant dinhLuc) {

    public CanhBaoDangMo {
        Objects.requireNonNull(batDau, "batDau");
        Objects.requireNonNull(dinh, "dinh");
        Objects.requireNonNull(dinhLuc, "dinhLuc");
    }

    /** Đã giữ đủ độ trễ và đã gửi thông báo — tức là một cảnh báo <b>thật</b>. */
    public boolean daXacNhan() {
        return xacNhanLuc != null;
    }
}
