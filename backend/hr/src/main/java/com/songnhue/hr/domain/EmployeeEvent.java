package com.songnhue.hr.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một sự kiện trên timeline công tác — CN-04.4.
 *
 * <h2>⭐ *"⛔ Không ghi đè mất dấu vết cũ"* được bảo đảm bằng HÌNH DẠNG, ⛔ không bằng lời dặn</h2>
 *
 * <p>SRS §3.4.3 đòi điều đó. Nó đúng ở đây vì <b>mỗi sự kiện là MỘT HÀNG</b>: một lượt điều động ⛔
 * không có chỗ nào để ghi đè lượt trước, kể cả khi ai đó muốn. So sánh với cách làm sai kinh điển —
 * một cột {@code current_position} bị {@code UPDATE} mỗi lần bổ nhiệm — thì lịch sử biến mất mà ⛔
 * không một dòng lỗi.
 *
 * <p>⚠ Sửa một hàng vẫn <b>được phép</b> (gõ nhầm số quyết định là chuyện có thật) và đi qua
 * {@link Audited} như mọi entity nghiệp vụ: giá trị cũ/mới vào {@code audit_logs}, giữ 5 năm.
 *
 * <h2>⛔ Ba mốc thời gian khác nhau, đừng gộp</h2>
 *
 * <ul>
 *   <li>{@link #effectiveOn} — ngày quyết định <b>có hiệu lực</b>. Đây là trục của timeline.
 *   <li>{@link #decisionDate} — ngày <b>ký</b> văn bản.
 *   <li>{@code createdAt} — ngày <b>nhập liệu</b>.
 * </ul>
 *
 * <p>Một quyết định ký tháng 3 có hiệu lực từ tháng 1 phải nằm ở <b>tháng 1</b> trên timeline. Gộp
 * ba mốc làm một là làm sai thứ tự lịch sử công tác của một con người, im lặng.
 */
@Entity
@Table(name = "employee_events")
@Audited(module = "hr", entityType = "Sự kiện công tác CBNV")
public class EmployeeEvent extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EmployeeEventType eventType;

    /** Trục timeline — xem javadoc lớp. */
    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    @Column(name = "decision_no", length = 100)
    private String decisionNo;

    @Column(name = "decision_date")
    private LocalDate decisionDate;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    protected EmployeeEvent() {}

    public EmployeeEvent(Long employeeId, EmployeeEventType eventType, LocalDate effectiveOn, String title) {
        this.employeeId = employeeId;
        this.eventType = eventType;
        this.effectiveOn = effectiveOn;
        this.title = title;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public EmployeeEventType getEventType() {
        return eventType;
    }

    public void setEventType(EmployeeEventType eventType) {
        this.eventType = eventType;
    }

    public LocalDate getEffectiveOn() {
        return effectiveOn;
    }

    public void setEffectiveOn(LocalDate effectiveOn) {
        this.effectiveOn = effectiveOn;
    }

    public String getDecisionNo() {
        return decisionNo;
    }

    public void setDecisionNo(String decisionNo) {
        this.decisionNo = decisionNo;
    }

    public LocalDate getDecisionDate() {
        return decisionDate;
    }

    public void setDecisionDate(LocalDate decisionDate) {
        this.decisionDate = decisionDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
