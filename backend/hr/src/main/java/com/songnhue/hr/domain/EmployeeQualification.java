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
 * Một mục lý lịch/chuyên môn của CBNV — CN-04.3.
 *
 * <h2>⚠ {@link BaseEntity} chứ ⛔ không {@code ScopedEntity} — và đó là chủ ý</h2>
 *
 * <p>Bảng này ⛔ không có {@code org_unit_id}, nên bộ lọc Hibernate ⛔ không áp cho nó. Phạm vi đơn
 * vị đi <b>qua nhân viên</b>: mọi lời gọi ở {@code LyLichService} bắt đầu bằng
 * {@code ScopeGuard.require(...)} trên hồ sơ CBNV, và một hàng ⛔ không thể tồn tại mà thiếu
 * {@code employee_id} ({@code NOT NULL} + khoá ngoại).
 *
 * <p>⛔ Đây là <b>cùng khuôn</b> {@link EmployeeSensitive} và tài liệu công trình đang dùng, ⛔ không
 * phải một ngoại lệ mới. Thêm cột phạm vi thứ hai cho một bản ghi con là dựng hai nguồn sự thật cho
 * cùng một câu hỏi — và chúng sẽ lệch vào đúng ngày một nhân viên được điều động sang đơn vị khác.
 *
 * <h2>{@code expiresOn} rỗng là hợp lệ</h2>
 *
 * <p>Bằng đại học ⛔ không hết hiệu lực. ⛔ Đừng điền một ngày xa để né {@code null} — nó biến
 * "vĩnh viễn" thành một ngày sẽ tới, và chuông M4.9 sẽ kêu vào năm 2099 (quy tắc 3).
 */
@Entity
@Table(name = "employee_qualifications")
@Audited(module = "hr", entityType = "Lý lịch & chuyên môn CBNV")
public class EmployeeQualification extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private QualificationKind kind;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "grade", length = 100)
    private String grade;

    @Column(name = "major", length = 255)
    private String major;

    @Column(name = "institution", length = 255)
    private String institution;

    @Column(name = "certificate_no", length = 100)
    private String certificateNo;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    /** {@code null} = ⛔ không hết hiệu lực — xem javadoc lớp. */
    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    protected EmployeeQualification() {}

    public EmployeeQualification(Long employeeId, QualificationKind kind, String name) {
        this.employeeId = employeeId;
        this.kind = kind;
        this.name = name;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public QualificationKind getKind() {
        return kind;
    }

    public void setKind(QualificationKind kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getCertificateNo() {
        return certificateNo;
    }

    public void setCertificateNo(String certificateNo) {
        this.certificateNo = certificateNo;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public void setIssuedOn(LocalDate issuedOn) {
        this.issuedOn = issuedOn;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(LocalDate expiresOn) {
        this.expiresOn = expiresOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
