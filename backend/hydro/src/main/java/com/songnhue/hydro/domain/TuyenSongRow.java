package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một hàng thô của BC-11 — điểm đo trên một tuyến sông. T34.4.
 *
 * @param riverName ⬜ {@code null} là trạng thái <b>ĐÚNG</b> hôm nay: tuyến sông và lý trình thuộc
 *     <b>G8</b>, Công ty chưa cung cấp. ⛔ Không bịa một tuyến, ⛔ không ẩn hàng đi — điểm đo được
 *     gom vào nhóm <i>"Chưa phân tuyến"</i> và nói ra lý do.
 * @param chainageM lý trình quy ra mét, dùng để <b>sắp theo dòng chảy</b>. ⛔ Sắp theo tên là sắp
 *     theo bảng chữ cái, và một tuyến sông ⛔ không chảy theo bảng chữ cái.
 * @param validValue giá trị <b>hợp lệ</b> gần nhất ({@code hydro_latest.valid_value})
 * @param lastSeenAt bản ghi gần nhất <b>bất kể chất lượng</b> — câu trả lời cho <i>"trạm còn phát
 *     tín hiệu không"</i>. ⚠ Cố ý tách khỏi {@link #validMeasuredAt}: một trạm chỉ trả số đáng ngờ
 *     <b>vẫn đang phát</b>, và gộp hai mốc lại là dựng ra một trạm mất tín hiệu giả.
 * @param soBanGhiNgay số bản ghi hợp lệ trong ngày — {@code 0} khi ⛔ chưa có gì, và khi ấy
 *     {@link #minNgay}/{@link #maxNgay} rỗng
 */
public record TuyenSongRow(
        long stationId,
        String stationCode,
        String stationName,
        String riverName,
        String chainage,
        Integer chainageM,
        String positionRole,
        boolean active,
        String measurementTypeCode,
        String measurementTypeName,
        String unit,
        BigDecimal validValue,
        Instant validMeasuredAt,
        Instant lastSeenAt,
        BigDecimal minNgay,
        BigDecimal maxNgay,
        int soBanGhiNgay) {

    /** Nhãn nhóm — ⛔ không bao giờ rỗng, và ⛔ không bao giờ là một tên sông bịa ra. */
    public static final String CHUA_PHAN_TUYEN = "Chưa phân tuyến";

    public String nhomTuyen() {
        return riverName == null || riverName.isBlank() ? CHUA_PHAN_TUYEN : riverName;
    }
}
