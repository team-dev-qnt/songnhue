package com.songnhue.hr.application;

/**
 * Trường 🔒 của một hồ sơ, ở dạng <b>GIÁ TRỊ THÔ</b> — CN-04.2.
 *
 * <p>⛔⛔ Record này là thứ DUY NHẤT trong module {@code hr} được phép cầm giá trị thô của một
 * trường 🔒, và nó chỉ sống trong đúng một lượt gọi: {@code EmployeeSensitiveService} mã hoá ngay
 * rồi vứt. ⛔ Cấm log nó, cấm đưa vào {@code jobs.payload}, cấm trả ra API.
 *
 * <p>⚠ {@code null} ở đây nghĩa là <b>xoá giá trị</b>, ⛔ không phải "giữ nguyên" — cùng ngữ nghĩa
 * thay-toàn-phần với {@link EmployeeForm}, và cũng cùng cái bẫy §11.19. Bài kiểm
 * {@code HoSoNhanSuMaHoaTest} khẳng định vòng khứ hồi của hộp thoại 🔒 ⛔ không đánh rơi trường nào.
 */
public record EmployeeSensitiveForm(
        String nationalId,
        String nationalIdIssuedOn,
        String nationalIdIssuedPlace,
        String baseSalary,
        String salaryCoefficient,
        String bankAccount,
        String taxCode,
        String socialInsuranceNo) {}
