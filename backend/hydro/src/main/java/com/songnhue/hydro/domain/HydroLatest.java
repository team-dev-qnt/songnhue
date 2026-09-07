package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Mực nước <b>hiện tại</b> của một (điểm đo × loại chỉ số) — <b>thứ thay cache Redis</b>.
 *
 * <p>Kiến trúc đã chốt: không Redis ở v1. Dashboard, widget cổng công khai và lớp GIS đọc bảng này;
 * ⛔ không nơi nào trong số đó được quét {@code hydro_readings} (quy tắc 8 — báo cáo/dashboard đọc
 * bảng agg, không scan raw).
 *
 * <h2>⭐ Bốn cột mốc/giá trị, không phải hai — và đó là chỗ chịu lực</h2>
 *
 * <p>{@link #lastSeenAt} là bản ghi gần nhất <b>bất kể chất lượng</b>, dùng cho phát hiện mất tín
 * hiệu. {@link #validValue} là giá trị <b>HỢP LỆ</b> gần nhất và là thứ <b>duy nhất</b> được hiển
 * thị hoặc đem đi so ngưỡng.
 *
 * <p>Nếu chỉ có một cặp {@code (measured_at, value)} thì mỗi nơi đọc phải tự nhớ lọc
 * {@code quality = HOP_LE} — và <b>có nơi sẽ quên</b>, đó chính là quy tắc 14. Tách ra thì bảo đảm
 * nằm ở <i>chỗ dữ liệu đi qua</i> chứ không ở <i>nơi gọi</i> (luật 12): một widget đọc
 * {@link #validValue} không có cách nào hiện nhầm số nghi ngờ, kể cả khi người viết nó chưa từng
 * đọc quy tắc 14.
 *
 * <p>Tách ra còn giữ đúng một phân biệt nữa: một trạm chỉ trả số nghi ngờ <b>vẫn đang phát tín
 * hiệu</b>. Gộp hai vế thì nó bị báo là mất tín hiệu — sai nguyên nhân, và sai luôn người phải xử lý.
 *
 * <p>⚠ {@link #validValue} <b>NULL</b> nghĩa là chưa từng có bản ghi hợp lệ nào cho cặp này. ⛔ Nơi
 * hiển thị phải nói thẳng là chưa có, ⛔ không thay bằng số 0 — số 0 là một câu khẳng định (quy tắc
 * 16), và ở đây nó là câu khẳng định sai.
 *
 * <p>Đường GHI là một câu {@code INSERT … ON CONFLICT DO UPDATE} trong {@code HydroLatestWriter}:
 * nó phải so mốc để <b>không lùi</b> khi một lượt ingest muộn mang về bản ghi cũ hơn. Logic ấy
 * không diễn đạt được bằng JPA dirty-checking, nên entity này chỉ đọc.
 */
@Entity
@Immutable
@Table(name = "hydro_latest")
public class HydroLatest {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "measurement_type_id", nullable = false)
    private Long measurementTypeId;

    /** ⛔ Dùng cho phát hiện mất tín hiệu, ⛔ KHÔNG dùng để hiển thị giá trị. */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** Chất lượng của bản ghi gần nhất — để màn hình nói được "số mới nhất đang bị nghi ngờ". */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_quality", nullable = false, length = 20)
    private ReadingQuality lastQuality;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_source", nullable = false, length = 20)
    private ReadingSource lastSource;

    /** Mốc của bản ghi HỢP LỆ gần nhất. NULL ⇔ {@link #validValue} NULL (CSDL ép bằng CHECK). */
    @Column(name = "valid_measured_at")
    private Instant validMeasuredAt;

    /** ⭐ Cột DUY NHẤT được hiển thị và được so ngưỡng. NULL = chưa từng có bản hợp lệ. */
    @Column(name = "valid_value", precision = 12, scale = 3)
    private BigDecimal validValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public Long getStationId() {
        return stationId;
    }

    public Long getMeasurementTypeId() {
        return measurementTypeId;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public ReadingQuality getLastQuality() {
        return lastQuality;
    }

    public ReadingSource getLastSource() {
        return lastSource;
    }

    public Instant getValidMeasuredAt() {
        return validMeasuredAt;
    }

    public BigDecimal getValidValue() {
        return validValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
