package com.songnhue.hydro.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Ngưỡng cảnh báo cho một bộ ba <b>(điểm đo × loại chỉ số × mức)</b> — <b>T33.2</b>.
 *
 * <h2>Lịch sử sửa ngưỡng đi qua {@code audit_logs}, ⛔ không có bảng lịch sử riêng</h2>
 *
 * <p>{@code @Audited} ở đây là <b>toàn bộ</b> cơ chế lịch sử. Dựng thêm một bảng
 * {@code alert_rule_history} là dựng kho lịch sử thứ hai cho cùng một loại thay đổi, và hai kho là
 * hai kho sẽ lệch nhau — chỉ là chưa biết lúc nào.
 *
 * <h2>⚠ {@code delayMinutes} — vì sao một ngưỡng cần một đồng hồ</h2>
 *
 * <p>Nguồn {@code bhh40} trả một khung 10 phút mỗi lượt, và cảm biến có thật thì thỉnh thoảng nhảy
 * một giá trị rồi về ngay. Bắn cảnh báo ở lượt vượt đầu tiên nghĩa là mỗi cú nhiễu là một email tới
 * Ban điều hành. Vài tuần sau không ai đọc thông báo nữa — <i>và lúc đó cảnh báo sự cố thật chết
 * theo</i> (§10.10, chính là lý do {@code NotifyRequest} tách {@code alert} khỏi {@code targeted}).
 *
 * <p>⛔ Đồng hồ ấy ⛔ <b>không</b> chạy trong bộ nhớ: mốc bắt đầu vượt nằm ở
 * {@code alert_events.started_at}, mốc xác nhận ở {@code confirmed_at}. Xem khối hysteresis của
 * migration {@code V202609031055}.
 *
 * <h2>⛔ Đây KHÔNG phải quy tắc "nghi ngờ" của WS-32</h2>
 *
 * <p>Hai thứ dễ lẫn vì cùng là một con số so với một giá trị đo:
 *
 * <ul>
 *   <li>{@link QuyTacNghiNgo} (WS-32) hỏi <i>"cảm biến có đang hỏng không"</i> — khoảng vật lý. Kết
 *       quả là {@code quality}, ghi vào chính dòng số đo.
 *   <li>Lớp này hỏi <i>"tình hình có đáng báo động không"</i> — ngưỡng nghiệp vụ. Kết quả là một
 *       {@code alert_event}, và nó chỉ chạy trên số đo đã {@code HOP_LE}.
 * </ul>
 *
 * <p>⛔ Một số đo {@code NGHI_NGO} ⛔ không bao giờ sinh cảnh báo (quy tắc 14) — đó là bẫy sai số
 * liệu dễ mắc nhất của dự án.
 */
@Entity
@Table(name = "alert_rules")
@Audited(module = "hyd", entityType = "Ngưỡng cảnh báo")
public class AlertRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "measurement_type_id", nullable = false)
    private MeasurementType measurementType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_level_id", nullable = false)
    private AlertLevel alertLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 20)
    private AlertConditionType conditionType;

    @Column(name = "threshold_value", nullable = false, precision = 12, scale = 3)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_value_high", precision = 12, scale = 3)
    private BigDecimal thresholdValueHigh;

    @Column(name = "delay_minutes", nullable = false)
    private Integer delayMinutes = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "note", length = 500)
    private String note;

    protected AlertRule() {}

    public AlertRule(
            Station station,
            MeasurementType measurementType,
            AlertLevel alertLevel,
            AlertConditionType conditionType,
            BigDecimal thresholdValue) {
        this.station = station;
        this.measurementType = measurementType;
        this.alertLevel = alertLevel;
        this.conditionType = conditionType;
        this.thresholdValue = thresholdValue;
    }

    /**
     * Điều kiện thuần để {@link DanhGiaNguong} chạy.
     *
     * <p>⭐ Đây là chỗ entity (có JPA, có giao dịch, có proxy lười) đưa bài toán sang tầng
     * <b>domain thuần</b>. {@link DieuKienNguong} kiểm bất biến ở hàm dựng, nên một dòng
     * {@code alert_rules} hỏng — {@code OUT_OF_RANGE} mà thiếu cận trên chẳng hạn — nổ ở đây với một
     * câu đọc được, ⛔ không đi tiếp vào phép so rồi ném {@code NullPointerException} ở một dòng
     * cách chỗ sai ba lớp.
     */
    public DieuKienNguong dieuKien() {
        return new DieuKienNguong(conditionType, thresholdValue, thresholdValueHigh);
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public MeasurementType getMeasurementType() {
        return measurementType;
    }

    public void setMeasurementType(MeasurementType measurementType) {
        this.measurementType = measurementType;
    }

    public AlertLevel getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(AlertLevel alertLevel) {
        this.alertLevel = alertLevel;
    }

    public AlertConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(AlertConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(BigDecimal thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public BigDecimal getThresholdValueHigh() {
        return thresholdValueHigh;
    }

    public void setThresholdValueHigh(BigDecimal thresholdValueHigh) {
        this.thresholdValueHigh = thresholdValueHigh;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Integer delayMinutes) {
        this.delayMinutes = delayMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
