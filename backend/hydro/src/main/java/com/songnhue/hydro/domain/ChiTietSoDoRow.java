package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một bản ghi thô của BC-12 — chi tiết theo yêu cầu. T34.6.
 *
 * <h2>⭐⭐ Báo cáo DUY NHẤT được phép hiện bản ghi {@code NGHI_NGO} và {@code XOA}</h2>
 *
 * <p>Quy tắc 14 bắt mọi truy vấn báo cáo lọc {@code quality = 'HOP_LE'}. BC-12 là ngoại lệ, và nó
 * hợp lệ vì <b>nó đánh đổi bộ lọc lấy hai cột</b>: {@link #quality} và {@link #source}. Người đọc
 * biết chính xác mình đang nhìn cái gì, nên con số nghi ngờ ⛔ không thể bị nhầm thành số liệu chính
 * thức — đó mới là thứ bộ lọc kia bảo vệ.
 *
 * <p>⛔ Bỏ một trong hai cột ấy đi là biến ngoại lệ này thành đúng cái lỗi mà quy tắc 14 sinh ra để
 * chặn. Nếu ngày nào có ai rút gọn DTO của báo cáo này, hai cột đó là hai cột ⛔ <b>không</b> được
 * rút.
 *
 * @param qualityReason MÁY nói: vì sao bộ phân loại đánh dấu dòng này lúc ingest
 * @param reviewNote NGƯỜI nói: lý do khi duyệt hoặc xoá. ⚠ Hai trường khác nguồn gốc, ⛔ đừng gộp —
 *     một cái là chẩn đoán tự động, một cái là quyết định có người chịu trách nhiệm
 */
public record ChiTietSoDoRow(
        Instant measuredAt,
        BigDecimal readingValue,
        String quality,
        String qualityReason,
        String source,
        String note,
        String reviewNote) {}
