package com.songnhue.hydro.infra;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Mực nước tại một thời điểm, theo lô mã API — Bảng 3 Báo cáo nhanh.
 *
 * <h2>⭐ Cửa sổ nhìn lại 24 giờ là CỐ Ý</h2>
 *
 * <p>(1) {@code hydro_readings} phân mảnh theo {@code measured_at}; một cận dưới cho phép cắt tỉa
 * phân mảnh thay vì quét cả lịch sử. (2) Một số đo 5 ngày tuổi in vào cột *"hồi 16h hôm nay"* là
 * một câu SAI — trạm mất tín hiệu thì ô phải TRỐNG (quy tắc 16), ⛔ phải số cũ.
 */
@Repository
public class HydroSnapshotRepository {

    /** Giờ nhìn lại tối đa trước thời điểm hỏi. */
    static final int GIO_NHIN_LAI = 24;

    static final String SQL_MUC_NUOC_TAI_THOI_DIEM =
            """
            SELECT DISTINCT ON (s.api_code) s.api_code, r.measured_at, r.reading_value
              FROM hydro_readings r
              JOIN stations s ON s.id = r.station_id AND s.deleted_at IS NULL
              JOIN measurement_types mt ON mt.id = r.measurement_type_id AND mt.code = 'MUC_NUOC'
             WHERE s.api_code = ANY (?)
               AND r.measured_at <= ?
               AND r.measured_at > ?
               AND r.quality = 'HOP_LE'
             ORDER BY s.api_code, r.measured_at DESC
            """;

    public record Dong(BigDecimal giaTriM, Instant mocDo) {}

    private final JdbcTemplate jdbc;

    public HydroSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Mã API → số đo gần nhất trong {@code (thoiDiem − 24h, thoiDiem]}. Mã ⛔ có số đo thì vắng khỏi map. */
    public Map<String, Dong> ganNhatTruoc(List<String> apiCodes, Instant thoiDiem) {
        Map<String, Dong> ket = new HashMap<>();
        if (apiCodes.isEmpty()) {
            return ket;
        }
        jdbc.query(
                con -> {
                    var ps = con.prepareStatement(SQL_MUC_NUOC_TAI_THOI_DIEM);
                    ps.setArray(1, con.createArrayOf("varchar", apiCodes.toArray()));
                    ps.setTimestamp(2, Timestamp.from(thoiDiem));
                    ps.setTimestamp(3, Timestamp.from(thoiDiem.minusSeconds(GIO_NHIN_LAI * 3600L)));
                    return ps;
                },
                rs -> {
                    ket.put(
                            rs.getString("api_code"),
                            new Dong(
                                    rs.getBigDecimal("reading_value"),
                                    rs.getObject("measured_at", OffsetDateTime.class)
                                            .toInstant()));
                });
        return ket;
    }
}
