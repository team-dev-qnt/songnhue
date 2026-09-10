package com.songnhue.hr.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.EmployeeEvent;
import com.songnhue.hr.domain.EmployeeEventType;
import com.songnhue.hr.infra.EmployeeEventRepository;
import com.songnhue.hr.infra.EmployeeRepository;

/**
 * Lịch sử công tác — timeline 10 loại sự kiện, CN-04.4.
 *
 * <p>⛔ Cùng một cửa phạm vi với {@link LyLichService}: mọi phương thức nhận {@code UUID} của <b>hồ
 * sơ</b> và bắt đầu bằng {@link ScopeGuard}. Xem javadoc lớp ấy để biết vì sao đây là cấu trúc chứ
 * ⛔ không phải một quy ước.
 *
 * <h2>⚠ Lớp này cố ý ⛔ KHÔNG tự sinh sự kiện</h2>
 *
 * <p>Nghe rất hợp lý khi cho {@code EmployeeService.update} tự ghi một sự kiện {@code DIEU_DONG}
 * mỗi lần đổi đơn vị. ⛔ Đừng. Đặc tả CN-04.4 nói *"10 loại sự kiện (<b>Admin HR tạo</b>)"* và mỗi
 * sự kiện mang <b>số quyết định</b> — một sự kiện tự sinh sẽ ⛔ không có số quyết định, tức một
 * dòng lịch sử công tác ⛔ không có căn cứ pháp lý nằm lẫn giữa những dòng có. Nặng hơn: sửa một ô
 * gõ nhầm rồi sửa lại sẽ đẻ ra hai sự kiện điều động ⛔ không có thật, và §3.4.3 nói timeline ⛔
 * không được mất dấu vết — nó cũng ⛔ không được mọc thêm dấu vết giả.
 */
@Service
public class TimelineService {

    private final EmployeeRepository employees;
    private final EmployeeEventRepository events;
    private final ScopeGuard scopeGuard;

    public TimelineService(EmployeeRepository employees, EmployeeEventRepository events, ScopeGuard scopeGuard) {
        this.employees = employees;
        this.events = events;
        this.scopeGuard = scopeGuard;
    }

    /** @param loai lọc theo loại sự kiện, {@code null} = tất cả */
    @Transactional(readOnly = true)
    public List<EmployeeEvent> timeline(UUID hoSoPublicId, EmployeeEventType loai) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        return loai == null
                ? events.findByEmployeeIdAndDeletedAtIsNullOrderByEffectiveOnDescIdDesc(hoSo.getId())
                : events.findByEmployeeIdAndEventTypeAndDeletedAtIsNullOrderByEffectiveOnDescIdDesc(hoSo.getId(), loai);
    }

    @Transactional
    public EmployeeEvent them(UUID hoSoPublicId, SuKienForm form) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        EmployeeEvent suKien = new EmployeeEvent(
                hoSo.getId(), batBuocLoai(form.eventType()), batBuocNgay(form.effectiveOn()), batBuocTieuDe(form));
        apDung(suKien, form);
        return events.save(suKien);
    }

    @Transactional
    public EmployeeEvent sua(UUID hoSoPublicId, UUID suKienPublicId, SuKienForm form) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        EmployeeEvent suKien = trongHoSo(hoSo, suKienPublicId);
        suKien.setEventType(batBuocLoai(form.eventType()));
        suKien.setEffectiveOn(batBuocNgay(form.effectiveOn()));
        suKien.setTitle(batBuocTieuDe(form));
        apDung(suKien, form);
        return events.save(suKien);
    }

    @Transactional
    public void xoa(UUID hoSoPublicId, UUID suKienPublicId) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        EmployeeEvent suKien = trongHoSo(hoSo, suKienPublicId);
        suKien.markDeleted(Instant.now());
        events.save(suKien);
    }

    // -------------------------------------------------------------------------

    private void apDung(EmployeeEvent suKien, SuKienForm form) {
        suKien.setDecisionNo(rutGon(form.decisionNo()));
        suKien.setDecisionDate(form.decisionDate());
        suKien.setDetail(rutGon(form.detail()));
    }

    private Employee trongPhamVi(UUID publicId) {
        return scopeGuard.require(employees.findByPublicIdAndDeletedAtIsNull(publicId), Employee.class, publicId);
    }

    private EmployeeEvent trongHoSo(Employee hoSo, UUID suKienPublicId) {
        return events.findByPublicIdAndDeletedAtIsNull(suKienPublicId)
                .filter(s -> s.getEmployeeId().equals(hoSo.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004, suKienPublicId));
    }

    private static EmployeeEventType batBuocLoai(EmployeeEventType loai) {
        if (loai == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("eventType", "REQUIRED", null);
        }
        return loai;
    }

    private static java.time.LocalDate batBuocNgay(java.time.LocalDate ngay) {
        if (ngay == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("effectiveOn", "REQUIRED", null);
        }
        return ngay;
    }

    private static String batBuocTieuDe(SuKienForm form) {
        String rut = rutGon(form.title());
        if (rut == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("title", "REQUIRED", null);
        }
        return rut;
    }

    private static String rutGon(String value) {
        if (value == null) {
            return null;
        }
        String rut = value.trim();
        return rut.isEmpty() ? null : rut;
    }
}
