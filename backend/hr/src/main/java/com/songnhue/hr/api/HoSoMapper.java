package com.songnhue.hr.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.hr.application.EmployeeSensitiveService;
import com.songnhue.hr.application.EmployeeService;
import com.songnhue.hr.domain.Employee;

/**
 * Dựng {@link HrDtos.EmployeeDetail} từ một {@link Employee} — <b>một</b> nơi, ⛔ không hai.
 *
 * <h2>Vì sao tách ra khỏi {@code EmployeeController}</h2>
 *
 * <p>Phép ánh xạ này có <b>27 trường</b> và nay có <b>hai</b> nơi cần nó: màn hình quản trị hồ sơ
 * ({@code EmployeeController}) và màn hình <i>Hồ sơ của tôi</i> ({@code HoSoCuaToiController},
 * T51.8). Chép nó sang nơi thứ hai là đặt một luật 14 vào chỗ đắt nhất — thêm một trường vào hồ sơ
 * CBNV rồi quên một bản chép, và triệu chứng là <b>một ô trống trên đúng màn hình của người bị mất
 * dữ liệu</b>, ⛔ không một dòng lỗi nào.
 *
 * <p>⚠ Lớp này ⛔ <b>không</b> quyết định ai được đọc gì. Nó nhận một {@link Employee} <b>đã lấy
 * được</b> — hai nơi gọi tự chịu trách nhiệm phần phạm vi và quyền, mỗi nơi một cách khác nhau và
 * có chủ đích: một nơi qua {@code ScopeGuard}, một nơi qua chính token.
 */
@Component
class HoSoMapper {

    private final EmployeeService employees;
    private final EmployeeSensitiveService sensitive;
    private final OrgUnitPort orgUnits;

    HoSoMapper(EmployeeService employees, EmployeeSensitiveService sensitive, OrgUnitPort orgUnits) {
        this.employees = employees;
        this.sensitive = sensitive;
        this.orgUnits = orgUnits;
    }

    HrDtos.EmployeeDetail chiTiet(Employee e) {
        String tenDonVi = e.getOrgUnitId() == null
                ? null
                : orgUnits.findRefById(e.getOrgUnitId()).map(r -> r.name()).orElse(null);
        UUID donViPublicId = e.getOrgUnitId() == null
                ? null
                : orgUnits.findRefById(e.getOrgUnitId()).map(r -> r.publicId()).orElse(null);
        Map<Long, String> chucVu = employees.tenChucVu(List.of(e));

        return new HrDtos.EmployeeDetail(
                e.getPublicId(),
                e.getCode(),
                e.getFullName(),
                e.getDateOfBirth(),
                e.getGender(),
                e.getEducationLevel(),
                e.getEthnicity(),
                e.getHometown(),
                e.getAddress(),
                e.getPhone(),
                e.getWorkEmail(),
                e.getPersonalEmail(),
                e.getMaritalStatus(),
                e.getEmergencyContactName(),
                e.getEmergencyContactPhone(),
                donViPublicId,
                tenDonVi,
                e.getPositionId() == null ? null : employees.chucVuPublicId(e.getPositionId()),
                e.getPositionId() == null ? null : chucVu.get(e.getPositionId()),
                e.getJobTitle(),
                e.getHiredAt(),
                e.getContractType(),
                e.getContractSignedAt(),
                e.getContractExpiresAt(),
                e.getStatus(),
                e.getTerminatedAt(),
                e.getTerminationReason(),
                sensitive.tinhTrang(e));
    }
}
