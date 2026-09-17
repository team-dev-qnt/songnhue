package com.songnhue.hr.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một ngày nghỉ lễ — CN-04.9, danh mục do <b>Công ty vận hành</b> (quy tắc 16).
 *
 * <p>⛔⛔ ⛔ <b>Không</b> phải enum, và ⛔ <b>không</b> có bộ seed. Lễ Việt Nam phụ thuộc <b>âm
 * lịch</b> (Tết, Giỗ Tổ) và quyết định nghỉ bù hằng năm của Chính phủ — ⛔ không suy ra được bằng
 * công thức, và CLAUDE.md cấm seed dữ liệu ⛔ không có nguồn. Khối {@code DO $$} của
 * {@code V202609141079} <b>ném</b> nếu bảng này ⛔ không rỗng lúc migrate.
 *
 * <p>⛔ {@link BaseEntity} chứ ⛔ không {@code ScopedEntity}: ngày lễ áp cho toàn Công ty. Cắt nó
 * theo đơn vị là dựng một luật nhân sự ⛔ không ai duyệt — và một đơn vị "⛔ không thấy" ngày lễ sẽ
 * trừ nhầm phép năm của người lao động.
 */
@Entity
@Table(name = "holidays")
@Audited(module = "hr", entityType = "Holiday")
public class Holiday extends BaseEntity {

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "note", length = 500)
    private String note;

    protected Holiday() {}

    public Holiday(LocalDate holidayDate, String name) {
        this.holidayDate = holidayDate;
        this.name = name;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
