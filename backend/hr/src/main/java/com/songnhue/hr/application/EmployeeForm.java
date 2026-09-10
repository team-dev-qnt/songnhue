package com.songnhue.hr.application;

import java.time.LocalDate;
import java.util.UUID;

import com.songnhue.hr.domain.ContractType;
import com.songnhue.hr.domain.EmploymentStatus;
import com.songnhue.hr.domain.Gender;
import com.songnhue.hr.domain.MaritalStatus;

/**
 * Dữ liệu tạo/sửa một hồ sơ CBNV — CN-04.2. ⛔ KHÔNG chứa trường 🔒.
 *
 * <h2>⛔⛔ Đây là một biểu mẫu THAY TOÀN PHẦN — và §11.19 nói điều đó nghĩa là gì</h2>
 *
 * <p>Mọi trường {@code null} ở đây sẽ được ghi thành {@code null} vào hồ sơ. Đó là hành vi ĐÚNG cho
 * một biểu mẫu web (người dùng xoá ô là muốn xoá giá trị), nhưng nó là hành vi <b>sai</b> nếu nơi
 * gọi dựng {@code EmployeeForm} mà quên một trường. Ngày 09/09 một {@code PUT} thiếu trường đã xoá
 * trắng tuyến sông/lý trình của 19 điểm đo, và nó vô hình suốt hai tuần vì mọi ô vốn đã rỗng.
 *
 * <p>⇒ Bảo đảm ⛔ không nằm ở một lời dặn: {@code HoSoNhanSuHttpTest.suaHoSoKhongXoaTruongKhongGui}
 * gửi <b>nguyên văn</b> thân của {@code GET} rồi khẳng định từng trường còn nguyên.
 *
 * @param orgUnitPublicId đơn vị phụ trách — ⛔ BẮT BUỘC. Một hồ sơ ⛔ không thuộc đơn vị nào là một
 *     hồ sơ ⛔ không ai chịu trách nhiệm và MỌI người đọc được (bộ lọc phạm vi ⛔ không cắt được nó)
 */
public record EmployeeForm(
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
        UUID orgUnitPublicId,
        UUID positionPublicId,
        String jobTitle,
        LocalDate hiredAt,
        ContractType contractType,
        LocalDate contractSignedAt,
        LocalDate contractExpiresAt,
        EmploymentStatus status,
        LocalDate terminatedAt,
        String terminationReason) {}
