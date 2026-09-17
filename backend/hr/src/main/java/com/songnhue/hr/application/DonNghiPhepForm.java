package com.songnhue.hr.application;

import java.time.LocalDate;
import java.util.UUID;

import com.songnhue.hr.domain.LeaveType;

/**
 * Dữ liệu nộp một đơn nghỉ phép — CN-04.9.
 *
 * @param employeePublicId hồ sơ xin nghỉ. {@code null} = <b>chính người đang đăng nhập</b>, suy từ
 *     {@code users.employee_id} (T51.8). Khác {@code null} là <b>nộp hộ</b> (chốt C3) và đòi quyền
 *     {@code hr:leave:view-all} — xem {@code DonNghiPhepService.nop}
 */
public record DonNghiPhepForm(
        UUID employeePublicId, LeaveType leaveType, LocalDate fromDate, LocalDate toDate, String reason) {}
