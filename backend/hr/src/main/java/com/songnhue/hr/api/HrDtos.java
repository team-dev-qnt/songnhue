package com.songnhue.hr.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.songnhue.hr.application.EmployeeSensitiveStatus;
import com.songnhue.hr.domain.ContractType;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.EmploymentStatus;
import com.songnhue.hr.domain.Gender;
import com.songnhue.hr.domain.MaritalStatus;
import com.songnhue.hr.domain.Position;

/**
 * Kiểu dữ liệu vào/ra của API nhân sự — CN-04.2.
 *
 * <h2>⛔⛔ Bất biến của tệp này: ⛔ KHÔNG record nào ở đây mang một trường 🔒</h2>
 *
 * <p>CCCD, ngày/nơi cấp, lương, hệ số, số tài khoản, MST, số BHXH ⛔ không được xuất hiện trong
 * {@link EmployeeRow} hay {@link EmployeeDetail} — kể cả dưới dạng che một phần. Cách chặn ⛔ không
 * phải là <i>null hoá trường theo quyền</i> (một nhánh {@code if} ai cũng có thể viết sai) mà là
 * <b>DTO riêng cho từng đối tượng đọc</b>: phần nhạy cảm chỉ ra ngoài qua
 * {@code EmployeeSensitiveController}, gác bằng một quyền khác.
 *
 * <p>Bất biến ấy được canh bằng {@code HoSoNhanSuKhongLoTruongKinTest} — nó đọc
 * {@code getRecordComponents()} chứ ⛔ không đọc mã nguồn, theo khuôn
 * {@code PublicOrgDirectoryServiceTest:236-251}.
 */
public final class HrDtos {

    private HrDtos() {}

    // === Chức vụ =============================================================

    /** Tạo/sửa một chức vụ. */
    public record PositionRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 100) String positionGroup,
            @Size(max = 500) String description,
            Integer sortOrder,
            Boolean active) {}

    /** Một dòng danh mục chức vụ. */
    public record PositionView(
            UUID publicId,
            String code,
            String name,
            String positionGroup,
            String description,
            Integer sortOrder,
            Boolean active) {

        public static PositionView of(Position p) {
            return new PositionView(
                    p.getPublicId(),
                    p.getCode(),
                    p.getName(),
                    p.getPositionGroup(),
                    p.getDescription(),
                    p.getSortOrder(),
                    p.getActive());
        }
    }

    // === Hồ sơ CBNV ==========================================================

    /**
     * Tạo/sửa hồ sơ — thay toàn phần.
     *
     * <p>⚠ Tên trường ở đây là thứ {@code error.details[].field} của 422 phải trỏ tới: AntD
     * {@code Form.setFields} so đúng chuỗi, lệch một chữ là người dùng bấm Lưu rồi ⛔ không thấy gì
     * ({@code LoiTheoTruongToiDungOTest}).
     *
     * <p>⛔ ⛔ Không có {@code code} ở bản sửa — mã NV ⛔ không đổi suốt quá trình công tác (CN-04.2),
     * và {@code EmployeeService.update} cố ý bỏ qua nó thay vì tin biểu mẫu ⛔ không gửi lên.
     */
    public record EmployeeRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            @Size(max = 100) String ethnicity,
            @Size(max = 255) String hometown,
            @Size(max = 500) String address,
            @Size(max = 30) String phone,
            @Size(max = 255) String workEmail,
            @Size(max = 255) String personalEmail,
            MaritalStatus maritalStatus,
            @Size(max = 255) String emergencyContactName,
            @Size(max = 30) String emergencyContactPhone,
            @NotNull UUID orgUnitId,
            UUID positionId,
            @Size(max = 255) String jobTitle,
            LocalDate hiredAt,
            ContractType contractType,
            LocalDate contractSignedAt,
            LocalDate contractExpiresAt,
            EmploymentStatus status,
            LocalDate terminatedAt,
            @Size(max = 500) String terminationReason) {}

    /** Một dòng trên màn hình danh sách — cố ý gọn. ⛔ KHÔNG trường 🔒. */
    public record EmployeeRow(
            UUID publicId,
            String code,
            String fullName,
            String orgUnitName,
            String positionName,
            String jobTitle,
            EmploymentStatus status,
            LocalDate contractExpiresAt,
            Instant updatedAt) {

        public static EmployeeRow of(Employee e, String orgUnitName, String positionName) {
            return new EmployeeRow(
                    e.getPublicId(),
                    e.getCode(),
                    e.getFullName(),
                    orgUnitName,
                    positionName,
                    e.getJobTitle(),
                    e.getStatus(),
                    e.getContractExpiresAt(),
                    e.getUpdatedAt());
        }
    }

    /**
     * Hồ sơ đầy đủ — ⛔ KHÔNG trường 🔒.
     *
     * <p>{@code sensitive} chỉ nói <b>ô nào đã có dữ liệu</b>, ⛔ không kèm giá trị nào — cùng khuôn
     * {@code ApiSourceView.credentialDaCauHinh}. Nó có mặt để người dùng phân biệt <i>"chưa
     * nhập"</i> với <i>"⛔ không được xem"</i>; hai trạng thái ấy mà trông giống nhau thì sẽ có
     * người nhập đè lên dữ liệu đang có (T50.1 là một sự cố đúng hình dạng ấy).
     */
    public record EmployeeDetail(
            UUID publicId,
            String code,
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            String ethnicity,
            String hometown,
            String address,
            String phone,
            String workEmail,
            String personalEmail,
            MaritalStatus maritalStatus,
            String emergencyContactName,
            String emergencyContactPhone,
            UUID orgUnitId,
            String orgUnitName,
            UUID positionId,
            String positionName,
            String jobTitle,
            LocalDate hiredAt,
            ContractType contractType,
            LocalDate contractSignedAt,
            LocalDate contractExpiresAt,
            EmploymentStatus status,
            LocalDate terminatedAt,
            String terminationReason,
            EmployeeSensitiveStatus sensitive) {}

    // === Trường 🔒 ===========================================================

    /**
     * Giá trị 🔒 đã giải mã — chỉ đi qua {@code EmployeeSensitiveController}, gác bằng
     * {@code hr:employee:view-sensitive}.
     *
     * <p>⛔ Record này CỐ Ý ⛔ không nằm chung đường với {@link EmployeeDetail}: gộp chúng lại là
     * biến "quyền xem trường 🔒" thành một nhánh {@code if} bên trong một endpoint mà ai cũng gọi
     * được — và một nhánh như thế chỉ cần sai một lần là lộ toàn bộ.
     */
    public record SensitiveRequest(
            @Size(max = 20) String nationalId,
            @Size(max = 30) String nationalIdIssuedOn,
            @Size(max = 255) String nationalIdIssuedPlace,
            @Size(max = 30) String baseSalary,
            @Size(max = 20) String salaryCoefficient,
            @Size(max = 50) String bankAccount,
            @Size(max = 20) String taxCode,
            @Size(max = 30) String socialInsuranceNo) {}

    /** Cùng hình dạng {@link SensitiveRequest} — vòng khứ hồi của hộp thoại 🔒 phải khép kín. */
    public record SensitiveView(
            String nationalId,
            String nationalIdIssuedOn,
            String nationalIdIssuedPlace,
            String baseSalary,
            String salaryCoefficient,
            String bankAccount,
            String taxCode,
            String socialInsuranceNo) {}
}
