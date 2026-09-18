package com.songnhue.hr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Trường 🔒 của hồ sơ CBNV — CN-04.2, quy tắc 10 và 13, NĐ 13/2023/NĐ-CP.
 *
 * <h2>⛔⛔ MỌI trường ở đây là BẢN MÃ, ⛔ không bao giờ là giá trị thô</h2>
 *
 * <p>Entity <b>⛔ không tự mã hoá</b> — nó nhận chuỗi {@code <key_id>:<base64>} đã mã hoá từ
 * {@code EmployeeSensitiveService}. Đặt {@code CryptoService.encrypt()} vào setter nghe tiện hơn,
 * nhưng khi ấy Hibernate nạp lại entity từ CSDL sẽ đi qua đúng setter đó và mã hoá <b>lần thứ
 * hai</b> một chuỗi vốn đã là bản mã. Tiền lệ của kho: {@code ApiSource} cũng chỉ cầm bản mã.
 *
 * <p>{@code ck_employee_sensitive_banma} ở CSDL từ chối mọi chuỗi thiếu tiền tố {@code <key_id>:} —
 * đặt bảo đảm ở chỗ <b>dữ liệu đi qua</b> chứ ⛔ không ở nơi gọi (quy tắc 12).
 *
 * <h2>⛔⛔ {@code excludeFields} liệt kê ĐỦ 9 trường — và có bộ canh ĐO chứ ⛔ không nhắc</h2>
 *
 * <p>{@code AuditEventListener} ghi <b>giá trị cũ và mới</b> vào {@code audit_logs} — bảng lưu 5 năm
 * và nhiều người xem được hơn bảng gốc. Một trường 🔒 quên khai ở đây sẽ đổ bản mã (và với
 * {@code nationalIdFingerprint} là một <b>định danh ổn định gắn với CCCD</b>) thẳng vào nhật ký,
 * ⛔ không một dòng đỏ.
 *
 * <p>⚠ Mẫu tên bí mật của {@code AuditRedactionRuleTest} (T48.1) ⛔ <b>không</b> bắt được các tên
 * này: ⛔ không trường nào mang chữ {@code password}/{@code secret}/{@code token}/{@code hash}. Đó
 * chính là khe mù mà bộ canh ấy <b>tự khai</b> ngày 10/09. Luật thứ hai — <i>bảng nào có CHECK dạng
 * bản mã thì entity của nó phải loại trừ ĐỦ các cột trong CHECK ấy</i> — sinh ra ở WS-51 để đóng
 * khe đó, và phạm vi của nó do bộ canh <b>ĐO từ migration</b>, ⛔ không do ai gõ tay (luật 28).
 */
@Entity
@Table(name = "employee_sensitive")
@Audited(
        module = "hr",
        entityType = "Trường nhạy cảm CBNV",
        excludeFields = {
            "nationalId",
            "nationalIdIssuedOn",
            "nationalIdIssuedPlace",
            "baseSalary",
            "salaryCoefficient",
            "bankAccount",
            "taxCode",
            "socialInsuranceNo",
            "nationalIdFingerprint"
        })
public class EmployeeSensitive extends BaseEntity {

    /**
     * Khoá ngoại sang {@code employees.id}. ⛔ Là {@code Long} trần chứ ⛔ không {@code @ManyToOne}:
     * nạp cả hồ sơ mỗi lần chạm trường 🔒 là mở rộng phạm vi đọc ⛔ không cần thiết, và một quan hệ
     * hai chiều sẽ khiến {@code Employee} kéo theo bảng này vào mọi lượt tuần tự hoá.
     */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "national_id")
    private String nationalId;

    @Column(name = "national_id_issued_on")
    private String nationalIdIssuedOn;

    @Column(name = "national_id_issued_place")
    private String nationalIdIssuedPlace;

    @Column(name = "base_salary")
    private String baseSalary;

    @Column(name = "salary_coefficient")
    private String salaryCoefficient;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "tax_code")
    private String taxCode;

    @Column(name = "social_insurance_no")
    private String socialInsuranceNo;

    /**
     * Vân tay HMAC của CCCD — thứ DUY NHẤT ép được {@code UNIQUE} trên một cột đã mã hoá, vì GCM
     * dùng IV ngẫu nhiên nên bản mã của cùng một số CCCD khác nhau mỗi lượt. Xem
     * {@code CryptoService.fingerprint()}.
     */
    @Column(name = "national_id_fingerprint", length = 80)
    private String nationalIdFingerprint;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getNationalIdIssuedOn() {
        return nationalIdIssuedOn;
    }

    public void setNationalIdIssuedOn(String nationalIdIssuedOn) {
        this.nationalIdIssuedOn = nationalIdIssuedOn;
    }

    public String getNationalIdIssuedPlace() {
        return nationalIdIssuedPlace;
    }

    public void setNationalIdIssuedPlace(String nationalIdIssuedPlace) {
        this.nationalIdIssuedPlace = nationalIdIssuedPlace;
    }

    public String getBaseSalary() {
        return baseSalary;
    }

    public void setBaseSalary(String baseSalary) {
        this.baseSalary = baseSalary;
    }

    public String getSalaryCoefficient() {
        return salaryCoefficient;
    }

    public void setSalaryCoefficient(String salaryCoefficient) {
        this.salaryCoefficient = salaryCoefficient;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    public String getSocialInsuranceNo() {
        return socialInsuranceNo;
    }

    public void setSocialInsuranceNo(String socialInsuranceNo) {
        this.socialInsuranceNo = socialInsuranceNo;
    }

    public String getNationalIdFingerprint() {
        return nationalIdFingerprint;
    }

    public void setNationalIdFingerprint(String nationalIdFingerprint) {
        this.nationalIdFingerprint = nationalIdFingerprint;
    }
}
