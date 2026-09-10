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
import com.songnhue.hr.domain.EmployeeQualification;
import com.songnhue.hr.domain.QualificationKind;
import com.songnhue.hr.infra.EmployeeQualificationRepository;
import com.songnhue.hr.infra.EmployeeRepository;

/**
 * Lý lịch & chuyên môn của một CBNV — CN-04.3.
 *
 * <h2>⛔⛔ Phạm vi đơn vị vào bằng MỘT cửa, và cửa ấy là {@link #trongPhamVi}</h2>
 *
 * <p>{@code employee_qualifications} ⛔ không có cột phạm vi, nên bộ lọc Hibernate ⛔ không đứng
 * chắn ở đây. Mọi phương thức công khai của lớp này <b>bắt đầu</b> bằng việc tra hồ sơ CBNV qua
 * {@link ScopeGuard} — tức là quản lý Xí nghiệp A hỏi một mục lý lịch của Xí nghiệp B thì dừng ở
 * bước đầu tiên với {@code AUTH-3002}, ⛔ không phải ở một câu {@code WHERE} ai đó phải nhớ viết.
 *
 * <p>⚠ Điều này là <b>cấu trúc</b>, ⛔ không phải một quy ước: các hàm nhận
 * {@code UUID hoSoPublicId} chứ ⛔ không nhận {@code Long employeeId}, nên ⛔ không có cách nào gọi
 * chúng mà bỏ qua phép kiểm. Cùng khuôn {@code EmployeeSensitiveService.tinhTrang(Employee)} của
 * WS-51.
 *
 * <h2>⚠ Mục lý lịch tra theo {@code publicId} vẫn phải kiểm nó THUỘC hồ sơ nào</h2>
 *
 * <p>Một {@code publicId} hợp lệ của Xí nghiệp B đi kèm {@code hoSoPublicId} của Xí nghiệp A sẽ qua
 * được vế đầu. {@link #trongHoSo} đóng vế thứ hai — ⛔ không có nó thì đây là một IDOR hoàn chỉnh.
 */
@Service
public class LyLichService {

    private final EmployeeRepository employees;
    private final EmployeeQualificationRepository qualifications;
    private final ScopeGuard scopeGuard;

    public LyLichService(
            EmployeeRepository employees, EmployeeQualificationRepository qualifications, ScopeGuard scopeGuard) {
        this.employees = employees;
        this.qualifications = qualifications;
        this.scopeGuard = scopeGuard;
    }

    @Transactional(readOnly = true)
    public List<EmployeeQualification> danhSach(UUID hoSoPublicId) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        return qualifications.findByEmployeeIdAndDeletedAtIsNullOrderByIssuedOnDescIdDesc(hoSo.getId());
    }

    @Transactional
    public EmployeeQualification them(UUID hoSoPublicId, LyLichForm form) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        EmployeeQualification muc =
                new EmployeeQualification(hoSo.getId(), batBuocLoai(form.kind()), batBuocTen(form.name()));
        apDung(muc, form);
        return qualifications.save(muc);
    }

    @Transactional
    public EmployeeQualification sua(UUID hoSoPublicId, UUID mucPublicId, LyLichForm form) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        EmployeeQualification muc = trongHoSo(hoSo, mucPublicId);
        muc.setKind(batBuocLoai(form.kind()));
        muc.setName(batBuocTen(form.name()));
        apDung(muc, form);
        return qualifications.save(muc);
    }

    @Transactional
    public void xoa(UUID hoSoPublicId, UUID mucPublicId) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        EmployeeQualification muc = trongHoSo(hoSo, mucPublicId);
        muc.markDeleted(Instant.now());
        qualifications.save(muc);
    }

    // -------------------------------------------------------------------------

    private void apDung(EmployeeQualification muc, LyLichForm form) {
        muc.setGrade(rutGon(form.grade()));
        muc.setMajor(rutGon(form.major()));
        muc.setInstitution(rutGon(form.institution()));
        muc.setCertificateNo(rutGon(form.certificateNo()));
        muc.setIssuedOn(form.issuedOn());
        muc.setExpiresOn(form.expiresOn());
        muc.setNote(rutGon(form.note()));
        kiemNgay(form);
    }

    /**
     * ⚠ Tên trường trong thông báo lỗi phải trùng tên trường của <b>DTO request</b>, ⛔ không phải
     * tên cột CSDL: {@code Form.setFields} của AntD tra theo tên ấy và <b>im lặng bỏ qua</b> khi ⛔
     * không khớp — người dùng thấy một lượt lưu hỏng mà ⛔ không ô nào đỏ (bài học WS-51).
     */
    private void kiemNgay(LyLichForm form) {
        if (form.issuedOn() != null
                && form.expiresOn() != null
                && form.expiresOn().isBefore(form.issuedOn())) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003)
                    .withDetail("expiresOn", "BEFORE_ISSUED", form.expiresOn());
        }
    }

    private Employee trongPhamVi(UUID publicId) {
        return scopeGuard.require(employees.findByPublicIdAndDeletedAtIsNull(publicId), Employee.class, publicId);
    }

    /**
     * ⛔ Vế thứ hai của phép kiểm — xem javadoc lớp. Trả {@code SYS-0004} chứ ⛔ không
     * {@code AUTH-3002}: mục ⛔ không thuộc hồ sơ này thì với người gọi nó <b>⛔ không tồn tại</b>,
     * và phân biệt hai trạng thái ấy là nói cho người hỏi biết UUID nào có thật.
     */
    private EmployeeQualification trongHoSo(Employee hoSo, UUID mucPublicId) {
        return qualifications
                .findByPublicIdAndDeletedAtIsNull(mucPublicId)
                .filter(m -> m.getEmployeeId().equals(hoSo.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004, mucPublicId));
    }

    private static QualificationKind batBuocLoai(QualificationKind kind) {
        if (kind == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("kind", "REQUIRED", null);
        }
        return kind;
    }

    private static String batBuocTen(String ten) {
        String rut = rutGon(ten);
        if (rut == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("name", "REQUIRED", null);
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
