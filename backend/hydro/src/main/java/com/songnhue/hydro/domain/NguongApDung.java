package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Một dòng {@code alert_rules} đã <b>làm phẳng</b>, sẵn sàng cho {@link DanhGiaNguong} — WS-33.
 *
 * <h2>⚠ Vì sao không dùng thẳng entity {@link AlertRule} trên đường nóng</h2>
 *
 * <p>Đường đánh giá chạy <b>bên trong</b> giao dịch ghi số đo: 28 điểm đo × 2 phút/lần × suốt đời hệ
 * thống. Mang {@code AlertRule} vào đó là mang theo ba {@code @ManyToOne} lười — và
 * {@code spring.jpa.open-in-view: false} đã cho thấy chuyện gì xảy ra khi một proxy lười bị đọc
 * ngoài giao dịch: {@code GET /hyd/stations} trả <b>500 suốt bốn ngày</b> từ WS-28, không bài kiểm
 * nào thấy vì tất cả đều gọi thẳng service (luật 5).
 *
 * <p>Ở đây không có proxy nào: một câu {@code SELECT} phẳng của {@code AlertRuleQueryRepository} đổ
 * thẳng vào record này. Cùng lý do chịu lực với {@code PollerRepository} và
 * {@code HydroTimeSeriesWriter}.
 *
 * @param ruleId khoá nội bộ của dòng {@code alert_rules}
 * @param alertLevelId mức cảnh báo, ⭐ <b>sao chép</b> sang {@code alert_events} lúc bắn — một quy
 *     tắc bị sửa mức sau đó ⛔ không được làm lịch sử cảnh báo kể lại chuyện khác
 * @param severityRank hạng nặng nhẹ; lớn hơn là nặng hơn. Dùng để chọn mức khi một số đo vượt nhiều
 *     ngưỡng cùng lúc
 * @param treTrongPhut điều kiện phải giữ được bấy nhiêu phút mới thành cảnh báo thật; 0 = xác nhận
 *     ngay
 */
public record NguongApDung(
        long ruleId,
        long stationId,
        long measurementTypeId,
        long alertLevelId,
        int severityRank,
        AlertConditionType loai,
        BigDecimal nguong,
        BigDecimal nguongCao,
        int treTrongPhut) {

    public NguongApDung {
        Objects.requireNonNull(loai, "loai");
        Objects.requireNonNull(nguong, "nguong");
        if (treTrongPhut < 0) {
            throw new IllegalArgumentException("Độ trễ xác nhận không được âm: " + treTrongPhut);
        }
    }

    /**
     * ⭐ Bất biến của điều kiện được kiểm ở <b>hàm dựng</b> của {@link DieuKienNguong}, không ở đây.
     *
     * <p>Nghĩa là một dòng {@code alert_rules} hỏng — {@code OUT_OF_RANGE} thiếu cận trên chẳng hạn
     * — nổ với một câu đọc được ngay tại điểm dựng, ⛔ không đi tiếp vào phép so rồi ném
     * {@code NullPointerException} ở một dòng cách chỗ sai ba lớp (quy tắc 16 áp cho mã, không chỉ
     * cho số liệu: <i>ràng buộc ép ở hàm dựng, không ở lời dặn</i>).
     */
    public DieuKienNguong dieuKien() {
        return new DieuKienNguong(loai, nguong, nguongCao);
    }
}
