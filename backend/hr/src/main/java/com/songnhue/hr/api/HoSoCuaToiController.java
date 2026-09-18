package com.songnhue.hr.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthenticatedEndpoint;
import com.songnhue.hr.application.EmployeeSensitiveForm;
import com.songnhue.hr.application.HoSoCuaToiService;
import com.songnhue.hr.domain.Employee;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * <b>Hồ sơ của tôi</b> — {@code /api/v1/hr/ho-so-cua-toi}. CN-04.7 vế hai, T51.8.
 *
 * <h2>⛔⛔ Endpoint DUY NHẤT của MOD-04 ⛔ không mang {@code @RequirePermission} — có chủ đích</h2>
 *
 * <p>Đặc tả nói trường 🔒 dành cho <i>Admin HR <b>và chính nhân viên đó</b></i>. Vế sau ⛔ không
 * biểu diễn được bằng một mã quyền: quyền gán theo <b>vai trò</b>, còn "chính nhân viên đó" là một
 * quan hệ giữa <b>một tài khoản</b> và <b>một hàng</b>. Gác đường này bằng {@code hr:employee:view}
 * sẽ chặn đúng những người nó phục vụ — một cán bộ vai trò VIEWER ⛔ không có quyền ấy.
 *
 * <p>Thứ thay thế cho cổng quyền là một tính chất <b>cấu trúc</b>, mạnh hơn: đường này ⛔ <b>không
 * nhận một định danh nào</b>. ⛔ Không {@code @PathVariable}, ⛔ không {@code @RequestParam}, ⛔
 * không thân yêu cầu. Hồ sơ suy từ {@code users.employee_id} nằm trong token — thứ chỉ
 * {@code UserAdminService.lienKetHoSo} ghi được, chỉ với {@code adm:user:update}, và ⛔ không ghi
 * được cho chính mình ({@code ADM-2018}). ⇒ IDOR ở đây là trạng thái <b>⛔ không biểu diễn được</b>.
 *
 * <p>⚠ ⛔ <b>Không có động từ ghi.</b> Tự đọc lương/số tài khoản của mình là quyền; tự sửa thì ⛔
 * không. Thêm một {@code @PutMapping} vào lớp này là mở đường cho nhân viên tự nâng hệ số lương.
 *
 * <p>Mỗi lượt đọc vẫn để lại một dòng {@code security_events} — cùng luật với đường của Admin HR.
 */
@RestController
@RequestMapping("/api/v1/hr/ho-so-cua-toi")
@Tag(name = "04-hr · Hồ sơ của tôi", description = "Hồ sơ CBNV của chính người đang đăng nhập — chỉ đọc")
public class HoSoCuaToiController {

    private final HoSoCuaToiService hoSoCuaToi;
    private final HoSoMapper mapper;

    public HoSoCuaToiController(HoSoCuaToiService hoSoCuaToi, HoSoMapper mapper) {
        this.hoSoCuaToi = hoSoCuaToi;
        this.mapper = mapper;
    }

    /**
     * Hồ sơ + trường 🔒 của chính mình.
     *
     * <p>{@code SYS-0004} khi tài khoản chưa liên kết hồ sơ nào. Giao diện lẽ ra ⛔ không gọi tới
     * đây (mục menu chỉ hiện khi {@code /auth/me} trả {@code coHoSoNhanSu = true}), nhưng liên kết
     * có thể bị gỡ <b>giữa phiên</b> — và {@code AuthorityLoader.invalidate} làm lượt gỡ ấy có hiệu
     * lực ngay, nên nhánh này là nhánh chạy thật chứ ⛔ không phải phòng xa.
     */
    @GetMapping
    @AuthenticatedEndpoint(reason = "Hồ sơ nhân sự của chính người đang đăng nhập — danh tính CHÍNH LÀ quyền")
    @Operation(summary = "Hồ sơ CBNV của chính mình, kèm trường 🔒 — ghi một dòng security_events")
    public HoSoConDtos.HoSoCuaToiView cuaToi() {
        Employee hoSo = hoSoCuaToi.hoSo().orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        EmployeeSensitiveForm bm = hoSoCuaToi.truongBaoMat(hoSo);

        return new HoSoConDtos.HoSoCuaToiView(
                mapper.chiTiet(hoSo),
                new HrDtos.SensitiveView(
                        bm.nationalId(),
                        bm.nationalIdIssuedOn(),
                        bm.nationalIdIssuedPlace(),
                        bm.baseSalary(),
                        bm.salaryCoefficient(),
                        bm.bankAccount(),
                        bm.taxCode(),
                        bm.socialInsuranceNo()));
    }
}
