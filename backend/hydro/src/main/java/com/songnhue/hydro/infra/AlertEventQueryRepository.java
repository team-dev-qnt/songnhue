package com.songnhue.hydro.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.AlertConditionType;
import com.songnhue.hydro.domain.AlertEventStatus;
import com.songnhue.hydro.domain.CanhBaoRow;

/**
 * Đường <b>ĐỌC</b> của {@code alert_events} — nửa còn lại của cặp mà {@link AlertEventWriter} mở ra.
 *
 * <p>Ba người đọc, và họ hỏi ba câu khác nhau:
 *
 * <ol>
 *   <li>{@link #congTrinhDangCanhBao(long)} — <b>mắt xích 3</b> của trạng thái công trình
 *   <li>{@link #trang} — màn hình lịch sử cảnh báo (T33.11)
 *   <li>{@link #suKienTonTai(UUID)} — {@code maintenance_logs.alert_event_public_id} kiểm trước khi
 *       ghi (T33.4)
 * </ol>
 */
@Repository
public class AlertEventQueryRepository {

    /**
     * ⭐⭐ <b>Mắt xích 3.</b> ⛔ KHÔNG lọc phạm vi đơn vị — và đó là một quyết định, không phải sơ suất.
     *
     * <p>Luật 13 / §10.35 lỗi 2: {@code ConstructionStatusService.tinh()} có 6 mắt xích, và kết quả
     * của nó được <b>ghi xuống cột {@code operational_status}</b> của {@code constructions}. Bốn mắt
     * xích còn lại (sự cố · bảo trì · mã tình hình vận hành) đều chạy bằng câu native <b>không lọc
     * phạm vi</b>. Thêm một vế lọc ở đây thì người ngoài đơn vị mở màn hình là trạng thái bị hạ
     * xuống <i>cho tất cả mọi người</i> — đúng lỗi đã trả giá một lần ở chính hàm ấy.
     *
     * <p>⭐ <i>"Công trình này có cảnh báo đang mở không"</i> là một <b>sự thật về công trình</b>,
     * không phụ thuộc ai đang nhìn.
     *
     * <p>⚠ {@code confirmed_at IS NOT NULL} là vế chịu lực thứ hai: một dòng {@code DANG_XAY_RA}
     * chưa xác nhận là một điều kiện <i>đang được theo dõi</i>, chưa ai nhận thông báo nào về nó.
     * Đếm nó vào đây là để một cú nhiễu cảm biến 2 phút lật trạng thái một công trình sang
     * {@code CANH_BAO}.
     *
     * <p>⛔ Nối qua {@code station_constructions}, ⛔ không qua một khoá ngoại xuyên module (T33.4).
     */
    private static final String SQL_CONG_TRINH_DANG_CANH_BAO =
            """
            SELECT 1
              FROM alert_events e
              JOIN station_constructions sc
                ON sc.station_id = e.station_id AND sc.deleted_at IS NULL
             WHERE sc.construction_id = ?
               AND e.status = 'DANG_XAY_RA'
               AND e.confirmed_at IS NOT NULL
             LIMIT 1
            """;

    private static final String CHON =
            """
            SELECT e.public_id, e.status, e.started_at, e.confirmed_at, e.ended_at,
                   e.trigger_value, e.peak_value, e.peak_at, e.reason, e.note, e.resolved_by,
                   s.public_id AS station_public_id, s.code AS station_code, s.name AS station_name,
                   mt.name AS mt_name, mt.unit AS mt_unit,
                   l.code AS level_code, l.name AS level_name, l.color_token,
                   r.condition_type
              FROM alert_events e
              JOIN stations s ON s.id = e.station_id
              JOIN measurement_types mt ON mt.id = e.measurement_type_id
              JOIN alert_levels l ON l.id = e.alert_level_id
              JOIN alert_rules r ON r.id = e.rule_id
            """;

    private static final RowMapper<CanhBaoRow> ANH_XA = AlertEventQueryRepository::doc;

    private final JdbcTemplate jdbc;

    public AlertEventQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean congTrinhDangCanhBao(long constructionId) {
        return !jdbc.queryForList(SQL_CONG_TRINH_DANG_CANH_BAO, Integer.class, constructionId)
                .isEmpty();
    }

    public boolean suKienTonTai(UUID publicId) {
        return khoaNoiBo(publicId).isPresent();
    }

    /**
     * Đổi {@code public_id} sang khoá nội bộ.
     *
     * <p>⚠ Một câu, hai người dùng — {@link #suKienTonTai} và đường đóng bằng tay. Tách làm hai câu
     * là mở cửa cho chúng lệch nhau (VD một câu quên vế xoá mềm, câu kia có), và luật 14 nói thẳng
     * chuyện gì xảy ra với hai bản của một sự thật.
     */
    public java.util.Optional<Long> khoaNoiBo(UUID publicId) {
        if (publicId == null) {
            return java.util.Optional.empty();
        }
        return jdbc.queryForList("SELECT id FROM alert_events WHERE public_id = ?", Long.class, publicId).stream()
                .findFirst();
    }

    /**
     * Lịch sử cảnh báo, mới nhất trước.
     *
     * @param diemDoPublicId lọc theo điểm đo, {@code null} = tất cả
     * @param dangMo {@code true} = chỉ dòng còn mở; {@code null} = tất cả
     */
    public List<CanhBaoRow> trang(UUID diemDoPublicId, Boolean dangMo, Instant tu, Instant den, int limit, int offset) {
        List<Object> thamSo = new ArrayList<>();
        String sql = CHON + dieuKien(diemDoPublicId, dangMo, tu, den, thamSo)
                + " ORDER BY e.started_at DESC, e.id DESC LIMIT ? OFFSET ?";
        thamSo.add(limit);
        thamSo.add(offset);
        return jdbc.query(sql, ANH_XA, thamSo.toArray());
    }

    public long dem(UUID diemDoPublicId, Boolean dangMo, Instant tu, Instant den) {
        List<Object> thamSo = new ArrayList<>();
        String sql = "SELECT count(*) FROM alert_events e JOIN stations s ON s.id = e.station_id"
                + dieuKien(diemDoPublicId, dangMo, tu, den, thamSo);
        Long n = jdbc.queryForObject(sql, Long.class, thamSo.toArray());
        return n == null ? 0L : n;
    }

    private static String dieuKien(UUID diemDoPublicId, Boolean dangMo, Instant tu, Instant den, List<Object> thamSo) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (diemDoPublicId != null) {
            w.append(" AND s.public_id = ?");
            thamSo.add(diemDoPublicId);
        }
        if (dangMo != null) {
            w.append(dangMo ? " AND e.status = 'DANG_XAY_RA'" : " AND e.status <> 'DANG_XAY_RA'");
        }
        if (tu != null) {
            w.append(" AND e.started_at >= ?");
            thamSo.add(Timestamp.from(tu));
        }
        if (den != null) {
            w.append(" AND e.started_at < ?");
            thamSo.add(Timestamp.from(den));
        }
        return w.toString();
    }

    private static CanhBaoRow doc(ResultSet rs, int i) throws SQLException {
        Timestamp xacNhan = rs.getTimestamp("confirmed_at");
        Timestamp ketThuc = rs.getTimestamp("ended_at");
        rs.getLong("resolved_by");
        boolean coNguoiDong = !rs.wasNull();
        return new CanhBaoRow(
                rs.getObject("public_id", UUID.class),
                rs.getObject("station_public_id", UUID.class),
                rs.getString("station_code"),
                rs.getString("station_name"),
                rs.getString("mt_name"),
                rs.getString("mt_unit"),
                rs.getString("level_code"),
                rs.getString("level_name"),
                rs.getString("color_token"),
                AlertConditionType.valueOf(rs.getString("condition_type")),
                AlertEventStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("started_at").toInstant(),
                xacNhan == null ? null : xacNhan.toInstant(),
                ketThuc == null ? null : ketThuc.toInstant(),
                rs.getBigDecimal("trigger_value"),
                rs.getBigDecimal("peak_value"),
                rs.getTimestamp("peak_at").toInstant(),
                rs.getString("reason"),
                xacNhan != null,
                coNguoiDong,
                rs.getString("note"));
    }
}
