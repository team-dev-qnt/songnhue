package com.songnhue.hydro.infra;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.AlertConditionType;
import com.songnhue.hydro.domain.CanhBaoDangMo;
import com.songnhue.hydro.domain.NguongApDung;
import com.songnhue.hydro.domain.SoDoTruoc;

/**
 * Đường <b>ĐỌC</b> của máy cảnh báo — ba câu hỏi mà mỗi số đo hợp lệ phải trả lời (WS-33).
 *
 * <h2>Vì sao JDBC phẳng chứ không repository JPA</h2>
 *
 * <p>Cùng lý do chịu lực với {@link PollerRepository} và {@link HydroTimeSeriesWriter}: đường này
 * chạy <b>bên trong</b> giao dịch ghi số đo, 28 điểm đo × 2 phút/lần × suốt đời hệ thống. Mang
 * {@code AlertRule} (ba {@code @ManyToOne} lười) vào đó là mang theo đúng cái bẫy đã làm
 * {@code GET /hyd/stations} trả <b>500 suốt bốn ngày</b> từ WS-28 —
 * {@code spring.jpa.open-in-view: false} + một proxy đọc ngoài giao dịch.
 *
 * <p>⛔ Không phương thức nào ở đây mở giao dịch — ranh giới transaction thuộc tầng application
 * (ArchUnit canh).
 */
@Repository
public class AlertEngineRepository {

    /**
     * Ngưỡng đang có hiệu lực cho một bộ (điểm đo × loại chỉ số).
     *
     * <p>⚠ Sắp theo {@code severity_rank DESC}: một số đo có thể vượt đồng thời BĐ I, II và III, và
     * cảnh báo phải mang mức <b>nặng nhất</b> đã vượt. Thứ tự ở đây là thứ tự nơi gọi duyệt qua.
     *
     * <p>⚠ Lọc cả {@code active} của <b>mức</b>: tắt một mức cảnh báo trên màn hình danh mục phải
     * làm im mọi ngưỡng đang trỏ vào nó, ⛔ không chỉ ẩn nó khỏi ô chọn. Không có vế ấy thì nút
     * "Đang dùng" là một nút không điều khiển gì (luật 15).
     */
    private static final String SQL_NGUONG =
            """
            SELECT r.id, r.station_id, r.measurement_type_id, r.alert_level_id,
                   l.severity_rank, r.condition_type, r.threshold_value,
                   r.threshold_value_high, r.delay_minutes
              FROM alert_rules r
              JOIN alert_levels l ON l.id = r.alert_level_id
             WHERE r.station_id = ?
               AND r.measurement_type_id = ?
               AND r.active = TRUE
               AND r.deleted_at IS NULL
               AND l.active = TRUE
               AND l.deleted_at IS NULL
             ORDER BY l.severity_rank DESC
            """;

    /** Cảnh báo đang mở của một quy tắc — ⭐ chính là hysteresis, đọc từ CSDL ở mỗi lượt. */
    private static final String SQL_DANG_MO =
            """
            SELECT id, rule_id, started_at, confirmed_at, peak_value, peak_at
              FROM alert_events
             WHERE rule_id = ? AND status = 'DANG_XAY_RA'
            """;

    /**
     * Số đo <b>hợp lệ</b> gần nhất trước một mốc — chỉ {@code RATE_OF_CHANGE} cần.
     *
     * <p>⛔ {@code quality = 'HOP_LE'} là bắt buộc (quy tắc 14, và {@code QualityFilterGuardTest}
     * canh đúng câu này). Lấy một bản {@code NGHI_NGO} làm mốc so là tính tốc độ đổi từ một con số
     * mà chính hệ thống đã nói là không tin được — rồi báo động vì cái chênh do lỗi cảm biến gây ra.
     */
    private static final String SQL_SO_DO_TRUOC =
            """
            SELECT measured_at, reading_value
              FROM hydro_readings
             WHERE station_id = ?
               AND measurement_type_id = ?
               AND measured_at < ?
               AND quality = 'HOP_LE'
             ORDER BY measured_at DESC
             LIMIT 1
            """;

    private static final RowMapper<NguongApDung> ANH_XA_NGUONG = (rs, i) -> new NguongApDung(
            rs.getLong("id"),
            rs.getLong("station_id"),
            rs.getLong("measurement_type_id"),
            rs.getLong("alert_level_id"),
            rs.getInt("severity_rank"),
            AlertConditionType.valueOf(rs.getString("condition_type")),
            rs.getBigDecimal("threshold_value"),
            rs.getBigDecimal("threshold_value_high"),
            rs.getInt("delay_minutes"));

    private static final RowMapper<CanhBaoDangMo> ANH_XA_DANG_MO = (rs, i) -> {
        Timestamp xacNhan = rs.getTimestamp("confirmed_at");
        return new CanhBaoDangMo(
                rs.getLong("id"),
                rs.getLong("rule_id"),
                rs.getTimestamp("started_at").toInstant(),
                xacNhan == null ? null : xacNhan.toInstant(),
                rs.getBigDecimal("peak_value"),
                rs.getTimestamp("peak_at").toInstant());
    };

    private final JdbcTemplate jdbc;

    public AlertEngineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return ngưỡng đang hiệu lực, nặng nhất đứng đầu; rỗng = <b>điểm đo chưa cấu hình ngưỡng</b> */
    public List<NguongApDung> nguongCua(long stationId, long measurementTypeId) {
        return jdbc.query(SQL_NGUONG, ANH_XA_NGUONG, stationId, measurementTypeId);
    }

    public Optional<CanhBaoDangMo> canhBaoDangMo(long ruleId) {
        return jdbc.query(SQL_DANG_MO, ANH_XA_DANG_MO, ruleId).stream().findFirst();
    }

    public Optional<SoDoTruoc> soDoHopLeTruoc(long stationId, long measurementTypeId, Instant mocDo) {
        return jdbc
                .query(
                        SQL_SO_DO_TRUOC,
                        (rs, i) -> new SoDoTruoc(
                                rs.getTimestamp("measured_at").toInstant(), rs.getBigDecimal("reading_value")),
                        stationId,
                        measurementTypeId,
                        Timestamp.from(mocDo))
                .stream()
                .findFirst();
    }

    /**
     * Có ngưỡng nào được cấu hình cho điểm đo này không — ⛔ <b>không</b> lọc {@code active}.
     *
     * <p>⚠ Phân biệt hai câu khác nhau mà màn hình dễ trộn: <i>"chưa ai cấu hình ngưỡng"</i>
     * ({@code HYD-2003}, một khoảng trống cần Công ty điền) và <i>"đã cấu hình nhưng đang tắt"</i>
     * (một quyết định của người vận hành). Gộp hai thứ này là biến một quyết định thành một lỗi
     * thiếu dữ liệu trên danh sách nhắc việc.
     */
    public boolean coCauHinhNguong(long stationId) {
        Integer co = jdbc.queryForObject(
                "SELECT 1 FROM alert_rules WHERE station_id = ? AND deleted_at IS NULL LIMIT 1",
                Integer.class,
                stationId);
        return co != null;
    }

    /**
     * Hạng nặng nhất trong danh mục mức cảnh báo <b>đang hoạt động</b>.
     *
     * <p>⚠ Đọc từ dữ liệu, ⛔ không từ một hằng số. Thang mức là của Công ty (G9-a, chưa chốt) —
     * hôm nay có thể 3 mức, ngày mai 5. Một bảng ánh xạ cứng kiểu <i>"1 → INFO, 3 → CRITICAL"</i>
     * sai <b>lặng lẽ</b> đúng vào ngày danh mục đổi, và không lệnh nào báo.
     *
     * @return rỗng khi danh mục còn trống (G9-a chưa về) — nơi gọi phải chịu được trạng thái ấy
     */
    public Optional<Integer> hangNangNhat() {
        return jdbc
                .queryForList(
                        "SELECT max(severity_rank) AS m FROM alert_levels WHERE active = TRUE AND deleted_at IS NULL",
                        Integer.class)
                .stream()
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    /** Giá trị đỉnh mới nếu nặng hơn đỉnh cũ — ⛔ không so bằng {@code >} trần trên BigDecimal. */
    public static boolean nangHon(BigDecimal moi, BigDecimal cu, AlertConditionType loai) {
        // Với LT, "nặng hơn" nghĩa là THẤP hơn. Một hàm max() thẳng thừng ở đây sẽ ghi đỉnh của một
        // cảnh báo mực nước xuống thấp là giá trị CAO nhất — đúng ngược điều người trực cần đọc.
        int so = moi.compareTo(cu);
        return loai == AlertConditionType.LT ? so < 0 : so > 0;
    }
}
