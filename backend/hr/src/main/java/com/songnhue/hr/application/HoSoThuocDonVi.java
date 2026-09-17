package com.songnhue.hr.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.OrgUnitUsagePort;
import com.songnhue.hr.infra.EmployeeRepository;
import com.songnhue.hr.infra.LeaveRequestRepository;

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
 * <p>⚠ Lớp này khai <b>hai</b> thứ của {@code hr}: hồ sơ CBNV và <b>đơn nghỉ phép đang chờ</b>.
 * Vế thứ hai thêm 14/09 (WS-57) — giải thể một đơn vị còn đơn chờ là xoá mất <b>người duyệt</b> của
 * nó, và người lao động ở lại với một đơn ⛔ không ai quyết được nữa.
 *
 * <p>⚠ Trả <b>một dòng tổng hợp</b> kèm số lượng, ⛔ không liệt kê tên — vừa để câu lỗi đọc được,
 * vừa vì họ tên CBNV là dữ liệu cá nhân và câu lỗi này hiện cho người có {@code adm:org-unit:manage}
 * (⛔ không nhất thiết có {@code hr:employee:view}).
 */
@Component
public class HoSoThuocDonVi implements OrgUnitUsagePort {

    private final EmployeeRepository employees;
    private final LeaveRequestRepository donNghi;

    public HoSoThuocDonVi(EmployeeRepository employees, LeaveRequestRepository donNghi) {
        this.employees = employees;
        this.donNghi = donNghi;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> dangThuocDonVi(Long orgUnitId) {
        List<String> ly = new ArrayList<>(2);
        long soHoSo = employees.countByOrgUnitIdAndDeletedAtIsNull(orgUnitId);
        if (soHoSo > 0) {
            ly.add("%d hồ sơ cán bộ nhân viên".formatted(soHoSo));
        }
        // ⭐ Vế này thêm 14/09 vì `SoDonViThamChieuTest` — bộ canh dựng ở WS-56 — đỏ ngay lượt chạy
        //   đầu sau khi `leave_requests.org_unit_id` ra đời: **cột khoá ngoại thứ 14**, đúng thứ nó
        //   sinh ra để bắt. Lần thứ CHÍN một bộ canh của dự án bắt chính người vừa viết mã.
        long soDon = donNghi.demDonConChoCuaDonVi(orgUnitId);
        if (soDon > 0) {
            ly.add("%d đơn nghỉ phép đang chờ duyệt".formatted(soDon));
        }
        return ly;
    }
}
