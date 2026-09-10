package com.songnhue.hr.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hr.application.EmployeeSensitiveForm;
import com.songnhue.hr.application.EmployeeSensitiveService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Trường 🔒 của hồ sơ CBNV — {@code /api/v1/hr/employees/{publicId}/sensitive} (CN-04.2, CN-04.7).
 *
 * <h2>Vì sao là một controller RIÊNG</h2>
 *
 * <p>Gộp phần 🔒 vào {@link EmployeeController} biến "quyền xem trường nhạy cảm" thành một nhánh
 * {@code if} bên trong một endpoint mà <b>mọi</b> vai trò có {@code hr:employee:view} đều gọi được.
 * Một nhánh như thế chỉ cần sai một lần là lộ toàn bộ, và nó ⛔ không để lại hình dạng nào cho một
 * bộ canh nhìn thấy. Đường riêng + quyền riêng thì phép kiểm là <b>có/⛔ không có endpoint</b> —
 * thứ đo được.
 *
 * <h2>⛔⛔ ADMIN bị loại trừ TƯỜNG MINH khỏi quyền này</h2>
 *
 * <p>{@code V202608131007:169} cấp cho ADMIN toàn bộ danh mục quyền <b>TRỪ</b>
 * {@code hr:employee:view-sensitive}, và chú thích ngay trên đó gọi đây là <i>"ngoại lệ dễ bị bỏ
 * sót nhất khi hiểu Admin = toàn quyền"</i>. Chỉ SUPER_ADMIN và ADMIN_HR đọc được — đúng nguyên tắc
 * tối thiểu của NĐ 13/2023.
 *
 * <h2>Mỗi lượt ĐỌC để lại một dòng {@code security_events}</h2>
 *
 * <p>{@code audit_logs} chỉ sinh dòng khi có <b>thay đổi</b>. Thiếu dòng nhật ký đọc thì một người
 * có quyền mở lần lượt toàn bộ hồ sơ để chép số tài khoản ⛔ không để lại dấu vết ở bất kỳ bảng
 * nào — xem {@code SecurityEventType.HR_SENSITIVE_FIELDS_READ}.
 */
@RestController
@RequestMapping("/api/v1/hr/employees/{publicId}/sensitive")
@Tag(name = "04-hr · Trường nhạy cảm", description = "CCCD, lương, tài khoản, MST, BHXH — mỗi lượt đọc đều ghi nhật ký")
public class EmployeeSensitiveController {

    private final EmployeeSensitiveService sensitive;

    public EmployeeSensitiveController(EmployeeSensitiveService sensitive) {
        this.sensitive = sensitive;
    }

    @GetMapping
    @Operation(summary = "Đọc trường 🔒 đã giải mã — ghi một dòng security_events")
    @RequirePermission("hr:employee:view-sensitive")
    public HrDtos.SensitiveView get(@PathVariable UUID publicId) {
        EmployeeSensitiveForm form = sensitive.doc(publicId);
        return new HrDtos.SensitiveView(
                form.nationalId(),
                form.nationalIdIssuedOn(),
                form.nationalIdIssuedPlace(),
                form.baseSalary(),
                form.salaryCoefficient(),
                form.bankAccount(),
                form.taxCode(),
                form.socialInsuranceNo());
    }

    /**
     * Lưu trường 🔒 — thay toàn phần.
     *
     * <p>⚠ Đòi {@code hr:employee:view-sensitive} chứ ⛔ không {@code hr:employee:update}: ghi đè
     * một ô mà ⛔ không được phép đọc nó là một đường <b>xoá</b> dữ liệu nhạy cảm mà người xoá ⛔
     * không bao giờ nhìn thấy thứ mình vừa xoá.
     */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Lưu trường 🔒 — thay toàn phần; CCCD trùng trả HR-1003")
    @RequirePermission("hr:employee:view-sensitive")
    public void save(@PathVariable UUID publicId, @Valid @RequestBody HrDtos.SensitiveRequest request) {
        sensitive.luu(
                publicId,
                new EmployeeSensitiveForm(
                        request.nationalId(),
                        request.nationalIdIssuedOn(),
                        request.nationalIdIssuedPlace(),
                        request.baseSalary(),
                        request.salaryCoefficient(),
                        request.bankAccount(),
                        request.taxCode(),
                        request.socialInsuranceNo()));
    }
}
