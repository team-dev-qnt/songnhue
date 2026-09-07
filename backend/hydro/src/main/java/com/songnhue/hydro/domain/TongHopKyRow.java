package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một hàng thô của BC-05 — tổng hợp kỳ của (điểm đo × loại chỉ số). T34.5.
 *
 * <h2>⛔ Bốn ô có thể RỖNG, và rỗng ở đây là một câu trả lời, ⛔ không phải một lỗi</h2>
 *
 * <p>{@link #giaTriMin} · {@link #mocMin} · {@link #giaTriMax} · {@link #mocMax} · {@link #giaTriTb}
 * đều {@code null} khi kỳ ấy ⛔ <b>không có bản ghi hợp lệ nào</b>. Đó là trạng thái <i>bình
 * thường</i> của một điểm đo mới khai, một điểm đo đang tắt, hoặc một điểm đo mà poller chưa với
 * tới — và cả ba đều phải <b>hiện ra trong báo cáo</b>, ⛔ không được biến mất.
 *
 * <p>⚠ Vì thế truy vấn dùng {@code LEFT JOIN} với {@code quality = 'HOP_LE'} ở mệnh đề {@code ON}
 * chứ ⛔ không ở {@code WHERE}: đẩy xuống {@code WHERE} là biến nó thành {@code INNER JOIN}, và khi
 * ấy đúng những điểm đo đang có vấn đề bị <b>giấu đi</b> trong khi bảng trông sạch sẽ.
 *
 * <p>⛔ Chuyển {@code null} thành {@code 0} ở bất kỳ tầng nào là bịa số liệu (quy tắc 16). Vế bắt
 * buộc kèm lý do được ép ở <b>hàm dựng</b> của {@code HydroReportDtos.TongHopKyView}.
 *
 * @param soBanGhi tổng số bản ghi <b>hợp lệ</b> trong kỳ — {@code 0} là hợp lệ và có nghĩa
 * @param soNgayCoDuLieu số ngày có ít nhất một bản ghi hợp lệ. ⭐ Cùng với {@code soBanGhi}, nó cho
 *     người đọc biết trung bình kỳ đang dựa trên bao nhiêu quan sát — một trung bình của 12 bản ghi
 *     và một trung bình của 4320 bản ghi trông y hệt nhau nếu ⛔ không nói ra
 */
public record TongHopKyRow(
        String stationCode,
        String stationName,
        String riverName,
        String positionRole,
        String measurementTypeCode,
        String measurementTypeName,
        String unit,
        long soBanGhi,
        int soNgayCoDuLieu,
        BigDecimal giaTriMin,
        Instant mocMin,
        BigDecimal giaTriMax,
        Instant mocMax,
        BigDecimal giaTriTb) {

    /** ⛔ Kỳ ⛔ không có bản ghi hợp lệ nào — ô số liệu phải rỗng KÈM LÝ DO. */
    public boolean rong() {
        return soBanGhi == 0;
    }
}
