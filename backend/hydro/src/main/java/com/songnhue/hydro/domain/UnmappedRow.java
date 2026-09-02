package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Một số đo mang mã nguồn <b>chưa khai thành điểm đo</b> — giữ lại nguyên trạng.
 *
 * <p>⚠ {@link #rawValue} và {@link #rawUnit} là giá trị <b>nguyên văn nguồn, CHƯA quy đổi</b>. Chưa
 * biết mã này là loại chỉ số gì thì cũng chưa biết quy đổi về đâu — quy đổi bây giờ là <i>đoán</i>,
 * và đoán ở MOD-03 đã sai 1/4 mã một lần rồi.
 *
 * @param apiCode mã nguồn trả về, ví dụ {@code F01613}
 * @param apiSourceId nguồn đã trả mã này — hai nguồn khác nhau có thể trùng mã
 * @param measuredAt mốc nguồn đo
 * @param rawValue giá trị nguyên văn, chưa quy đổi
 * @param rawUnit đơn vị do adapter khai cho nguồn ấy ({@code cm} với BHH40)
 * @param rawLogId truy ngược về nguyên văn response
 */
public record UnmappedRow(
        String apiCode, Long apiSourceId, Instant measuredAt, BigDecimal rawValue, String rawUnit, Long rawLogId) {

    public UnmappedRow {
        Objects.requireNonNull(apiCode, "apiCode");
        Objects.requireNonNull(apiSourceId, "apiSourceId");
        Objects.requireNonNull(measuredAt, "measuredAt");
        Objects.requireNonNull(rawValue, "rawValue");
        if (rawUnit == null || rawUnit.isBlank()) {
            // ⛔ Đơn vị rỗng biến bảng này thành một cột số vô nghĩa: sau này không ai biết 213 là
            // cm hay m. Bắt ở hàm dựng vì lúc phát hiện ra thì đã quá muộn để hỏi lại nguồn.
            throw new IllegalArgumentException("rawUnit không được rỗng — số không đơn vị là số vô nghĩa");
        }
    }
}
