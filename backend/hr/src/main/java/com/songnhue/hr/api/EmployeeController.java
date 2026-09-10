package com.songnhue.hr.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.hr.application.EmployeeFilter;
import com.songnhue.hr.application.EmployeeForm;
import com.songnhue.hr.application.EmployeeService;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.EmploymentStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Hồ sơ cán bộ nhân viên — {@code /api/v1/hr/employees/**} (CN-04.2, CN-04.7).
 *
 * <h2>⛔⛔ ⛔ Không endpoint nào ở đây trả một trường 🔒</h2>
 *
 * <p>CCCD, ngày/nơi cấp, lương, hệ số, số tài khoản, MST, số BHXH đi qua
 * {@link EmployeeSensitiveController} — đường riêng, quyền riêng
 * ({@code hr:employee:view-sensitive}), và mỗi lượt đọc để lại một dòng {@code security_events}.
 *
 * <p>Cách chặn ⛔ <b>không</b> phải là null hoá trường theo quyền bên trong endpoint chi tiết: một
 * nhánh {@code if} như thế chỉ cần sai một lần là lộ toàn bộ, và nó ⛔ không để lại dấu vết nào để
 * bộ canh nhìn thấy. Cách chặn là <b>hai DTO khác nhau</b>, và một bài kiểm đọc
 * {@code getRecordComponents()} khẳng định {@code EmployeeDetail} ⛔ không mang tên trường nào thuộc
 * nhóm 🔒.
 *
 * <h2>Phạm vi đơn vị</h2>
 *
 * <p>Quản lý cấp Xí nghiệp chỉ thấy hồ sơ đơn vị mình và các Tổ đội trực thuộc (SRS M4.13) — bộ lọc
 * Hibernate ở tầng 3 lo việc đó cho <b>mọi</b> truy vấn, kể cả truy vấn viết sau này. Truy cập một
 * hồ sơ ngoài phạm vi trả {@code AUTH-3002} <b>và ghi {@code security_events}</b>, đúng câu đặc tả:
 * <i>"từ chối + ghi log"</i>.
 */
@RestController
@RequestMapping("/api/v1/hr/employees")
@Tag(name = "04-hr · Hồ sơ CBNV", description = "Hồ sơ cán bộ nhân viên — trường 🔒 nằm ở đường riêng")
public class EmployeeController {

    private final EmployeeService employees;
    private final HoSoMapper mapper;

    public EmployeeController(EmployeeService employees, HoSoMapper mapper) {
        this.employees = employees;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Danh sách hồ sơ — lọc, tìm kiếm bỏ dấu, phân trang")
    @RequirePermission("hr:employee:view")
    public Page<HrDtos.EmployeeRow> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(required = false) UUID positionId,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {

        Pageable pageable = PageUtils.toPageable(page, size, sort, EmployeeService.SAP_XEP_CHO_PHEP);
        Page<Employee> ketQua = employees.search(new EmployeeFilter(q, orgUnitId, positionId, status), pageable);

        // Nạp tên đơn vị/chức vụ MỘT LẦN cho cả trang — tra từng dòng thì một trang 100 hồ sơ thành
        // 200 lượt truy vấn, kiểu N+1 ⛔ không gây lỗi nào, chỉ làm màn hình chậm dần theo dữ liệu.
        List<Employee> trang = ketQua.getContent();
        Map<Long, String> tenDonVi = employees.tenDonVi(trang);
        Map<Long, String> tenChucVu = employees.tenChucVu(trang);

        return ketQua.map(e -> HrDtos.EmployeeRow.of(
                e,
                e.getOrgUnitId() == null ? null : tenDonVi.get(e.getOrgUnitId()),
                e.getPositionId() == null ? null : tenChucVu.get(e.getPositionId())));
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Chi tiết hồ sơ — KHÔNG kèm trường 🔒, chỉ kèm cờ ô nào đã có dữ liệu")
    @RequirePermission("hr:employee:view")
    public HrDtos.EmployeeDetail get(@PathVariable UUID publicId) {
        return toDetail(employees.get(publicId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm hồ sơ CBNV")
    @RequirePermission("hr:employee:create")
    public HrDtos.EmployeeDetail create(@Valid @RequestBody HrDtos.EmployeeRequest request) {
        return toDetail(employees.create(toForm(request)));
    }

    /**
     * Sửa hồ sơ — <b>thay toàn phần</b>.
     *
     * <p>⛔⛔ Mọi trường vắng trong thân JSON sẽ được ghi thành {@code null}. Đó là hành vi đúng cho
     * một biểu mẫu, và là hành vi <b>tai hại</b> cho một lượt gọi API dựng tay: ngày 09/09 một
     * {@code PUT} thiếu trường đã xoá trắng tuyến sông/lý trình của 19 điểm đo và vô hình suốt hai
     * tuần (§11.19). Bảo đảm ⛔ không nằm ở lời dặn này —
     * {@code HoSoNhanSuHttpTest.suaHoSoKhongXoaTruongKhongGui} gửi nguyên văn thân của {@code GET}
     * rồi khẳng định từng trường còn nguyên.
     */
    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa hồ sơ — thay toàn phần, TRỪ mã nhân viên (không đổi được)")
    @RequirePermission("hr:employee:update")
    public HrDtos.EmployeeDetail update(
            @PathVariable UUID publicId, @Valid @RequestBody HrDtos.EmployeeRequest request) {
        return toDetail(employees.update(publicId, toForm(request)));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm hồ sơ")
    @RequirePermission("hr:employee:delete")
    public void delete(@PathVariable UUID publicId) {
        employees.delete(publicId);
    }

    /**
     * ⚠ Thân phép ánh xạ 27 trường nay ở {@link HoSoMapper} — <b>một</b> nơi cho cả màn hình quản
     * trị lẫn màn hình <i>Hồ sơ của tôi</i> (T51.8). Chép nó là luật 14 ở chỗ đắt nhất.
     */
    private HrDtos.EmployeeDetail toDetail(Employee e) {
        return mapper.chiTiet(e);
    }

    private static EmployeeForm toForm(HrDtos.EmployeeRequest r) {
        return new EmployeeForm(
                r.code(),
                r.fullName(),
                r.dateOfBirth(),
                r.gender(),
                r.educationLevel(),
                r.ethnicity(),
                r.hometown(),
                r.address(),
                r.phone(),
                r.workEmail(),
                r.personalEmail(),
                r.maritalStatus(),
                r.emergencyContactName(),
                r.emergencyContactPhone(),
                r.orgUnitId(),
                r.positionId(),
                r.jobTitle(),
                r.hiredAt(),
                r.contractType(),
                r.contractSignedAt(),
                r.contractExpiresAt(),
                r.status(),
                r.terminatedAt(),
                r.terminationReason());
    }
}
