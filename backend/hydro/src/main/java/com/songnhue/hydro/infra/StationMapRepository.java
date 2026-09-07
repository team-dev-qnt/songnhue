package com.songnhue.hydro.infra;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Điểm đo cho <b>lớp GIS</b> — T35.1.
 *
 * <h2>⛔ Vì sao MỘT dòng / điểm đo, ⛔ không phải một dòng / (điểm đo × loại chỉ số)</h2>
 *
 * <p>BC-11 ({@code HydroReportRepository#SQL_TUYEN_SONG}) trả một dòng cho mỗi cặp — đúng cho một
 * <i>bảng số liệu</i>. Nhưng một <b>marker là một CHỖ</b>: hai loại chỉ số của cùng một điểm đo nằm
 * chồng khít lên nhau ở cùng toạ độ, và người dùng chỉ thấy cái vẽ sau cùng. ⇒ Câu này gom về mức
 * điểm đo, và popup nói rõ số đang hiện là của loại chỉ số nào.
 *
 * <h2>⚠ {@code hydro_latest} cố ý KHÔNG chịu bộ canh quy tắc 14</h2>
 *
 * <p>{@code QualityFilterGuardTest} soi {@code hydro_readings} và {@code hydro_agg_daily}, ⛔ không
 * soi {@code hydro_latest} — và đó là quyết định đã ghi ở javadoc của chính bộ canh: bảng này tách
 * sẵn {@code valid_value} (giá trị <b>HỢP LỆ</b> gần nhất) khỏi {@code last_seen_at} (mốc gần nhất
 * <i>bất kể chất lượng</i>). Nên ở đây:
 *
 * <ul>
 *   <li>số hiện trong popup lấy từ {@code valid_value} — ⛔ không bao giờ là một số nghi ngờ;
 *   <li>trạng thái tín hiệu lấy từ {@code last_seen_at} — vì câu hỏi là <i>"trạm còn phát
 *       không"</i>, và một trạm chỉ gửi số nghi ngờ thì <b>vẫn đang phát</b>.
 * </ul>
 *
 * <p>⛔ Đừng "sửa" thành cùng một cột: gộp hai câu hỏi ấy làm một là báo mất tín hiệu cho một trạm
 * đang chạy, tức huy động sai người.
 */
@Repository
public class StationMapRepository {

    /**
     * ⚠ Ba {@code LEFT JOIN LATERAL} thay vì ba lượt truy vấn: một điểm đo có nhiều loại chỉ số và
     * nhiều cảnh báo, nên gom bằng {@code GROUP BY} sẽ phải chọn hàm gộp cho từng cột và mất đi
     * <i>hàng nào đi cùng hàng nào</i>. {@code LATERAL … LIMIT 1} giữ nguyên bộ ba (giá trị · mốc ·
     * đơn vị) của <b>cùng một</b> bản ghi.
     *
     * <p>⛔ Điểm đo <b>chưa có toạ độ vẫn nằm trong kết quả</b> — T35.2. Lọc chúng ra ở SQL là làm
     * cho danh sách "chưa số hoá vị trí" phải chạy một câu thứ hai, và hai câu ấy sẽ lệch nhau. Tầng
     * dịch vụ mới là nơi tách hai nhóm, từ <b>một</b> ảnh chụp.
     */
    private static final String SQL_DIEM_DO_BAN_DO =
            """
            SELECT s.public_id, s.code, s.name, s.position_role, s.active,
                   s.latitude, s.longitude, s.river_name, s.chainage,
                   tin.gan_nhat, tin.chat_luong,
                   gt.valid_value, gt.valid_measured_at, gt.unit, gt.ten_chi_so,
                   canh_bao.color_token, canh_bao.ten_muc
              FROM stations s
              LEFT JOIN LATERAL (
                      SELECT max(l.last_seen_at) AS gan_nhat,
                             (array_agg(l.last_quality ORDER BY l.last_seen_at DESC))[1] AS chat_luong
                        FROM hydro_latest l
                       WHERE l.station_id = s.id
                   ) tin ON TRUE
              LEFT JOIN LATERAL (
                      SELECT l.valid_value, l.valid_measured_at, m.unit, m.name AS ten_chi_so
                        FROM hydro_latest l
                        JOIN measurement_types m ON m.id = l.measurement_type_id
                       WHERE l.station_id = s.id AND l.valid_value IS NOT NULL
                       ORDER BY l.valid_measured_at DESC
                       LIMIT 1
                   ) gt ON TRUE
              LEFT JOIN LATERAL (
                      SELECT al.color_token, al.name AS ten_muc
                        FROM alert_events e
                        JOIN alert_levels al ON al.id = e.alert_level_id
                       WHERE e.station_id = s.id
                         AND e.status = 'DANG_XAY_RA'
                         AND e.confirmed_at IS NOT NULL
                       ORDER BY al.severity_rank DESC
                       LIMIT 1
                   ) canh_bao ON TRUE
             WHERE s.deleted_at IS NULL
             ORDER BY s.code
            """;

    private final JdbcTemplate jdbc;

    public StationMapRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Ảnh chụp một điểm đo cho lớp bản đồ.
     *
     * @param latitude {@code null} khi chưa số hoá vị trí — <b>G8</b>, hôm nay là cả 19/19 điểm đo
     * @param chatLuong chất lượng của bản ghi gần nhất; {@code null} khi chưa từng có bản ghi nào
     * @param khoaMauCanhBao {@code color_token} của mức <b>nặng nhất</b> đang mở; {@code null} khi
     *     điểm đo không có cảnh báo nào — ⛔ khác hẳn "có cảnh báo mức nhẹ"
     */
    public record DiemDoBanDoRow(
            UUID publicId,
            String code,
            String name,
            String positionRole,
            boolean active,
            BigDecimal latitude,
            BigDecimal longitude,
            String riverName,
            String chainage,
            Instant mocGanNhat,
            String chatLuong,
            BigDecimal giaTri,
            Instant mocDo,
            String donVi,
            String tenChiSo,
            String khoaMauCanhBao,
            String tenMucCanhBao) {}

    public List<DiemDoBanDoRow> diemDoBanDo() {
        return jdbc.query(
                SQL_DIEM_DO_BAN_DO,
                (rs, i) -> new DiemDoBanDoRow(
                        rs.getObject("public_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("position_role"),
                        rs.getBoolean("active"),
                        rs.getBigDecimal("latitude"),
                        rs.getBigDecimal("longitude"),
                        rs.getString("river_name"),
                        rs.getString("chainage"),
                        moc(rs, "gan_nhat"),
                        rs.getString("chat_luong"),
                        rs.getBigDecimal("valid_value"),
                        moc(rs, "valid_measured_at"),
                        rs.getString("unit"),
                        rs.getString("ten_chi_so"),
                        rs.getString("color_token"),
                        rs.getString("ten_muc")));
    }

    /**
     * ⚠ ⛔ Không dùng {@code rs.getObject(cot, Instant.class)}.
     *
     * <p>Driver PostgreSQL ném {@code conversion to class java.time.Instant from timestamptz not
     * supported}, và {@code GlobalExceptionHandler} dịch nó thành <b>SYS-0005 / 409</b> — "dữ liệu
     * vừa bị người khác sửa" — trên một endpoint chỉ đọc. Lỗi ấy đã tốn một lượt gỡ ở WS-34; giữ
     * helper này để ⛔ không ai phải trả giá lần hai.
     */
    private static Instant moc(java.sql.ResultSet rs, String cot) throws java.sql.SQLException {
        java.sql.Timestamp t = rs.getTimestamp(cot);
        return t == null ? null : t.toInstant();
    }
}
