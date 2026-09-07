package com.songnhue.hydro.infra;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.AlertEventStatus;

/**
 * Đường <b>GHI</b> duy nhất của {@code alert_events} — WS-33.
 *
 * <h2>⛔ Không có entity JPA cho bảng này, và đó là một quyết định</h2>
 *
 * <p>Cùng khuôn với {@code SyncLogWriter} / {@code HydroTimeSeriesWriter}: dựng một entity JPA cho
 * {@code alert_events} là mở <b>đường ghi thứ hai</b> vào một bảng mà mọi ràng buộc liên trường
 * đang được đúng một nơi tôn trọng — {@code ck_alert_events_ended_paired} (đang xảy ra ⇔ chưa có
 * {@code ended_at}), {@code ck_alert_events_resolved_paired}, và ⭐ chỉ mục một-phần
 * {@code ux_alert_events_mot_cai_dang_mo} vốn <b>là</b> cơ chế hysteresis. Hai đường ghi vào một
 * bảng có luật liên trường là đúng chỗ luật 14 gọi tên.
 *
 * <p>⛔ Lớp này ⛔ không mở giao dịch: nó luôn chạy <b>bên trong</b> giao dịch ghi số đo (T33.5), và
 * đó là điều làm cho <i>"số đo được ghi mà cảnh báo không được ghi"</i> trở thành trạng thái không
 * tồn tại được.
 */
@Repository
public class AlertEventWriter {

    /**
     * ⭐ {@code ON CONFLICT DO NOTHING} <b>không nêu đích</b> — cố ý.
     *
     * <p>Bảng có <b>hai</b> chỉ mục duy nhất có thể va: {@code (rule_id, started_at)} chống ghi
     * trùng một lượt đánh giá chạy lại, và {@code (rule_id) WHERE status='DANG_XAY_RA'} chống mở
     * cảnh báo thứ hai. Nêu đích chỉ đỡ được một trong hai; cái còn lại nổ thành
     * {@code DataIntegrityViolationException} và làm rollback <b>cả giao dịch ghi số đo</b> — tức
     * mất luôn số đo, vì một va chạm vốn vô hại.
     *
     * <p>⚠ Hệ quả bắt buộc nhớ: câu này có thể trả <b>0 dòng</b>, và đó là kết quả bình thường.
     * Nơi gọi ⛔ không được coi {@code Optional.empty()} là lỗi.
     */
    private static final String SQL_MO =
            """
            INSERT INTO alert_events (
                rule_id, station_id, measurement_type_id, alert_level_id,
                started_at, confirmed_at, status,
                trigger_value, peak_value, peak_at, reason)
            VALUES (?, ?, ?, ?, ?, ?, 'DANG_XAY_RA', ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            RETURNING id
            """;

    /**
     * Nâng đỉnh — ⚠ <b>chỉ khi thật sự nặng hơn</b>, và "nặng hơn" phụ thuộc chiều của điều kiện.
     *
     * <p>Nơi gọi đã so bằng {@link AlertEngineRepository#nangHon}; câu này chỉ ghi. Tách đôi như vậy
     * vì phép so <i>"với LT thì thấp hơn là nặng hơn"</i> là một luật <b>nghiệp vụ</b>, và luật
     * nghiệp vụ nằm trong một câu SQL là luật không ai kiểm được bằng một bài kiểm đơn vị.
     */
    private static final String SQL_DINH = "UPDATE alert_events SET peak_value = ?, peak_at = ? WHERE id = ?";

    private static final String SQL_XAC_NHAN =
            "UPDATE alert_events SET confirmed_at = ? WHERE id = ? AND confirmed_at IS NULL";

    private static final String SQL_DONG =
            """
            UPDATE alert_events
               SET status = ?, ended_at = ?, resolved_by = ?, resolved_at = ?, note = COALESCE(?, note)
             WHERE id = ? AND status = 'DANG_XAY_RA'
            """;

    private final JdbcTemplate jdbc;

    public AlertEventWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param xacNhanLuc mốc xác nhận, hoặc {@code null} khi quy tắc có {@code delay_minutes > 0} —
     *     dòng ra đời ở trạng thái <i>đang theo dõi</i>, ⛔ chưa gửi thông báo nào
     * @return khoá dòng vừa mở; <b>rỗng</b> khi một dòng đã chiếm chỗ (xem {@link #SQL_MO})
     */
    public Optional<Long> mo(
            long ruleId,
            long stationId,
            long measurementTypeId,
            long alertLevelId,
            Instant batDau,
            Instant xacNhanLuc,
            BigDecimal giaTri,
            String lyDo) {
        List<Long> id = jdbc.query(
                SQL_MO,
                (rs, i) -> rs.getLong("id"),
                ruleId,
                stationId,
                measurementTypeId,
                alertLevelId,
                Timestamp.from(batDau),
                xacNhanLuc == null ? null : Timestamp.from(xacNhanLuc),
                giaTri,
                giaTri,
                Timestamp.from(batDau),
                lyDo);
        return id.stream().findFirst();
    }

    public void napDinh(long eventId, BigDecimal dinh, Instant dinhLuc) {
        jdbc.update(SQL_DINH, dinh, Timestamp.from(dinhLuc), eventId);
    }

    /** @return {@code true} nếu lượt gọi này là lượt xác nhận — nơi gọi gửi thông báo đúng một lần */
    public boolean xacNhan(long eventId, Instant luc) {
        return jdbc.update(SQL_XAC_NHAN, Timestamp.from(luc), eventId) == 1;
    }

    /**
     * @param status ⛔ {@code DANG_XAY_RA} không hợp lệ ở đây — đóng nghĩa là rời khỏi trạng thái ấy
     * @param boi {@code null} = máy tự đóng vì giá trị về dưới ngưỡng; có giá trị = người trực bấm
     * @return {@code true} nếu dòng thật sự chuyển; {@code false} nghĩa là ai đó đã đóng trước
     */
    public boolean dong(long eventId, AlertEventStatus status, Instant luc, Long boi, String ghiChu) {
        if (status == AlertEventStatus.DANG_XAY_RA) {
            throw new IllegalArgumentException("Đóng một cảnh báo thì trạng thái đích không thể là DANG_XAY_RA");
        }
        Timestamp moc = Timestamp.from(luc);
        return jdbc.update(SQL_DONG, status.name(), moc, boi, boi == null ? null : moc, ghiChu, eventId) == 1;
    }
}
