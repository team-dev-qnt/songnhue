package com.songnhue.hr.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * Hồ sơ cán bộ nhân viên — CN-04.2. Entity nghiệp vụ <b>đầu tiên</b> của MOD-04.
 *
 * <h2>⛔⛔ Trường 🔒 ⛔ KHÔNG nằm ở lớp này</h2>
 *
 * <p>CCCD, ngày/nơi cấp, lương, hệ số, số tài khoản, MST, số BHXH ở {@link EmployeeSensitive} —
 * bảng riêng, mã hoá AES-256-GCM, khoá ngoài CSDL (quy tắc 10, NĐ 13/2023). ⛔ Đừng "tiện tay" đưa
 * một trường 🔒 vào đây rồi dựa vào {@code @Audited(excludeFields)}: {@code excludeFields} chỉ chặn
 * <b>nhật ký</b>, nó ⛔ không mã hoá gì cả và ⛔ không ngăn một DTO trả trường ấy ra API.
 *
 * <h2>⚠ {@code @Filter} bắt buộc — thiếu nó là rò rỉ im lặng</h2>
 *
 * <p>{@code @FilterDef} ở {@link ScopedEntity} mới chỉ <i>định nghĩa</i> bộ lọc. Thiếu dòng
 * {@code @Filter} thì bộ lọc tồn tại mà ⛔ không áp cho entity này: mọi Xí nghiệp đọc được hồ sơ
 * nhân sự của nhau, màn hình đầy đủ, ⛔ không một dòng lỗi. {@code SilentFailureRuleTest} canh đúng
 * chỗ đó.
 *
 * <h2>"Bản không dấu auto" là một CHỈ MỤC, ⛔ không phải một cột</h2>
 *
 * <p>Đặc tả viết <i>"Họ tên (+ bản không dấu auto)"</i>. Nó được hiện thực bằng chỉ mục hàm
 * {@code ix_employees_ten_khong_dau} trên {@code sn_khong_dau(full_name)}. Một cột thứ hai chứa cùng
 * một sự thật sẽ lệch đúng vào lần có đường ghi quên cập nhật nó — luật 27, hình dạng đã trả giá
 * sáu lần trong dự án này.
 */
@Entity
@Table(name = "employees")
@Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)
@Audited(module = "hr", entityType = "Hồ sơ nhân viên")
public class Employee extends ScopedEntity {

    // === Định danh và thông tin cá nhân ======================================

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "ethnicity", length = 100)
    private String ethnicity;

    @Column(name = "hometown", length = 255)
    private String hometown;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "work_email", length = 255)
    private String workEmail;

    @Column(name = "personal_email", length = 255)
    private String personalEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    @Column(name = "emergency_contact_name", length = 255)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    // === Thông tin công tác ==================================================
    // org_unit_id nằm ở ScopedEntity — ⛔ KHÔNG khai lại ở đây.

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "job_title", length = 255)
    private String jobTitle;

    @Column(name = "hired_at")
    private LocalDate hiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", length = 30)
    private ContractType contractType;

    @Column(name = "contract_signed_at")
    private LocalDate contractSignedAt;

    @Column(name = "contract_expires_at")
    private LocalDate contractExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmploymentStatus status = EmploymentStatus.THU_VIEC;

    @Column(name = "terminated_at")
    private LocalDate terminatedAt;

    @Column(name = "termination_reason", length = 500)
    private String terminationReason;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getEthnicity() {
        return ethnicity;
    }

    public void setEthnicity(String ethnicity) {
        this.ethnicity = ethnicity;
    }

    public String getHometown() {
        return hometown;
    }

    public void setHometown(String hometown) {
        this.hometown = hometown;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getWorkEmail() {
        return workEmail;
    }

    public void setWorkEmail(String workEmail) {
        this.workEmail = workEmail;
    }

    public String getPersonalEmail() {
        return personalEmail;
    }

    public void setPersonalEmail(String personalEmail) {
        this.personalEmail = personalEmail;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public LocalDate getHiredAt() {
        return hiredAt;
    }

    public void setHiredAt(LocalDate hiredAt) {
        this.hiredAt = hiredAt;
    }

    public ContractType getContractType() {
        return contractType;
    }

    public void setContractType(ContractType contractType) {
        this.contractType = contractType;
    }

    public LocalDate getContractSignedAt() {
        return contractSignedAt;
    }

    public void setContractSignedAt(LocalDate contractSignedAt) {
        this.contractSignedAt = contractSignedAt;
    }

    public LocalDate getContractExpiresAt() {
        return contractExpiresAt;
    }

    public void setContractExpiresAt(LocalDate contractExpiresAt) {
        this.contractExpiresAt = contractExpiresAt;
    }

    public EmploymentStatus getStatus() {
        return status;
    }

    public void setStatus(EmploymentStatus status) {
        this.status = status;
    }

    public LocalDate getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(LocalDate terminatedAt) {
        this.terminatedAt = terminatedAt;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }
}
