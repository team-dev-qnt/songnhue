package com.songnhue.hr.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.hr.domain.Employee;

/**
 * Hồ sơ của <b>chính người đang đăng nhập</b> — CN-04.7 vế hai, T51.8.
 *
 * <h2>⛔⛔ Lớp này ⛔ không nhận một định danh nào từ request — và đó là toàn bộ thiết kế</h2>
 *
 * <p>Mọi đường vào MOD-04 khác đều có dạng {@code /employees/{publicId}/…}, nên mỗi đường phải tự
 * chứng minh hai chuyện: người gọi <b>có quyền</b> ({@code @RequirePermission}) và bản ghi
 * <b>trong phạm vi</b> ({@code ScopeGuard}). Đường này ⛔ không có {@code publicId} nào cả: hồ sơ
 * suy từ {@code users.employee_id} nằm trong token. ⇒ IDOR ở đây là một trạng thái <b>⛔ không biểu
 * diễn được</b>, ⛔ không phải một phép kiểm mà người viết sau phải nhớ thêm vào.
 *
 * <p>Đó cũng là lý do endpoint tương ứng ⛔ <b>không</b> mang {@code @RequirePermission} nào: quyền
 * ở đây <b>chính là danh tính</b>. Gác thêm {@code hr:employee:view} sẽ chặn đúng đối tượng nó phục
 * vụ — một cán bộ vai trò VIEWER ⛔ không có quyền ấy, mà đặc tả thì nói <i>"chính nhân viên đó"</i>.
 *
 * <h2>⚠ Vì sao nửa GHI ⛔ không ở đây</h2>
 *
 * <p>Tự <b>đọc</b> lương và số tài khoản của mình là quyền theo NĐ 13/2023. Tự <b>sửa</b> thì ⛔
 * không — {@code EmployeeSensitiveService.luu} vẫn đòi {@code hr:employee:view-sensitive}. Một lớp
 * "hồ sơ của tôi" có cả hai vế là một đường để nhân viên tự nâng hệ số lương của mình.
 */
@Service
public class HoSoCuaToiService {

    private final EmployeeService employees;
    private final EmployeeSensitiveService sensitive;

    public HoSoCuaToiService(EmployeeService employees, EmployeeSensitiveService sensitive) {
        this.employees = employees;
        this.sensitive = sensitive;
    }

    /** Hồ sơ CBNV liên kết với tài khoản đang đăng nhập, hoặc rỗng khi chưa liên kết. */
    @Transactional(readOnly = true)
    public Optional<Employee> hoSo() {
        return AuthContext.current().map(AuthenticatedUser::employeeId).flatMap(employees::cuaChinhMinh);
    }

    /**
     * Trường 🔒 của chính mình — <b>để lại một dòng {@code security_events}</b> như mọi lượt đọc khác.
     *
     * <p>⚠ Nhận {@link Employee} đã lấy được chứ ⛔ không tự tra lại: nơi gọi vừa lấy nó ở
     * {@link #hoSo()}, và tra hai lần là mở hai đường vào cùng một bảng nhạy cảm.
     */
    @Transactional
    public EmployeeSensitiveForm truongBaoMat(Employee hoSo) {
        return sensitive.docCuaChinhMinh(hoSo);
    }
}
