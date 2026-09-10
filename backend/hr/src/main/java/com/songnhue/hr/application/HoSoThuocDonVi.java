package com.songnhue.hr.application;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.OrgUnitUsagePort;
import com.songnhue.hr.infra.EmployeeRepository;

/**
 * Phần khai của {@code hr} cho {@link OrgUnitUsagePort} — CN-04.1.
 *
 * <p>{@code employees.org_unit_id} là {@code NOT NULL}, nên <b>mọi</b> hồ sơ đều thuộc một đơn vị.
 * Đây là một trong <b>hai</b> thứ mà {@code function-spec.md:616} nêu đích danh
 * (<i>"⛔ không còn <b>nhân viên</b>/công trình liên kết"</i>).
 *
 * <p>⚠ Đếm cả hồ sơ của người <b>đã nghỉ việc</b>, có chủ đích: hồ sơ vẫn là hồ sơ, vẫn trỏ vào
 * đơn vị, và báo cáo <i>"biến động nhân sự theo phòng ban"</i> vẫn đọc chúng. Bỏ qua họ là để một
 * lượt giải thể làm hỏng đúng báo cáo lịch sử.
 *
 * <p>⚠ Trả <b>một dòng tổng hợp</b> kèm số lượng, ⛔ không liệt kê tên — vừa để câu lỗi đọc được,
 * vừa vì họ tên CBNV là dữ liệu cá nhân và câu lỗi này hiện cho người có {@code adm:org-unit:manage}
 * (⛔ không nhất thiết có {@code hr:employee:view}).
 */
@Component
public class HoSoThuocDonVi implements OrgUnitUsagePort {

    private final EmployeeRepository employees;

    public HoSoThuocDonVi(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> dangThuocDonVi(Long orgUnitId) {
        long so = employees.countByOrgUnitIdAndDeletedAtIsNull(orgUnitId);
        return so == 0 ? List.of() : List.of("%d hồ sơ cán bộ nhân viên".formatted(so));
    }
}
