package com.songnhue.hr.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.EmploymentStatus;
import com.songnhue.hr.domain.Position;
import com.songnhue.hr.infra.EmployeeRepository;
import com.songnhue.hr.infra.PositionRepository;

/**
 * Hồ sơ cán bộ nhân viên — CN-04.2, CN-04.7.
 *
 * <h2>Ba bảo đảm của lớp này, và chỗ đặt từng cái</h2>
 *
 * <ol>
 *   <li><b>Phạm vi đơn vị</b> (CN-04.7 / SRS M4.13) — bộ lọc Hibernate ở tầng 3, ⛔ không ở một câu
 *       {@code WHERE} nào trong lớp này. Đường tra theo {@code publicId} đi qua {@link ScopeGuard}
 *       để một hồ sơ thuộc đơn vị khác trả {@code AUTH-3002} <b>và ghi {@code security_events}</b>,
 *       chứ ⛔ không trả 404 lặng lẽ — đặc tả đòi <i>"từ chối + ghi log"</i>.
 *   <li><b>Trường 🔒</b> — ⛔ KHÔNG ở lớp này. {@code EmployeeSensitiveService} giữ chúng, gác bằng
 *       một quyền riêng.
 *   <li><b>Mã NV ⛔ không đổi</b> — đặc tả nói <i>"không đổi suốt quá trình công tác"</i>, nên
 *       {@link #update} ⛔ <b>không</b> nhận mã mới; nó đọc mã từ hồ sơ đang có.
 * </ol>
 */
@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    /**
     * Trường được phép sắp xếp — {@code PageUtils} ném 422 với mọi trường ngoài tập này.
     *
     * <p>⛔⛔ {@code "updatedAt"} phải có mặt vì màn hình danh sách của admin-app đặt sort mặc định
     * là {@code 'updatedAt,desc'} ngay ở {@code useState}. Thiếu nó thì màn hình trả <b>422 ở lượt
     * tải ĐẦU TIÊN</b> và trông y hệt "bảng vốn rỗng" — bẫy đã đo được ở
     * {@code ConstructionService:74-89}.
     */
    public static final List<String> SAP_XEP_CHO_PHEP =
            List.of("code", "fullName", "hiredAt", "status", "contractExpiresAt", "updatedAt", "createdAt");

    private final EmployeeRepository employees;
    private final PositionRepository positions;
    private final ScopeGuard scopeGuard;
    private final OrgUnitPort orgUnits;

    public EmployeeService(
            EmployeeRepository employees, PositionRepository positions, ScopeGuard scopeGuard, OrgUnitPort orgUnits) {
        this.employees = employees;
        this.positions = positions;
        this.scopeGuard = scopeGuard;
        this.orgUnits = orgUnits;
    }

    @Transactional(readOnly = true)
    public Page<Employee> search(EmployeeFilter filter, Pageable pageable) {
        EmployeeFilter loc = filter == null ? EmployeeFilter.rong() : filter;
        return employees.timKiem(
                loc.tuKhoaLike(),
                donViId(loc.donViPublicId()),
                chucVuId(loc.chucVuPublicId()),
                loc.trangThai(),
                pageable);
    }

    @Transactional(readOnly = true)
    public Employee get(UUID publicId) {
        return trongPhamVi(publicId);
    }

    /**
     * Hồ sơ của <b>chính người đang đăng nhập</b> — T51.8, vế thứ hai của CN-04.7.
     *
     * <h2>⛔⛔ Đường DUY NHẤT của lớp này ⛔ không đi qua {@code ScopeGuard} — và vì sao thế là đúng</h2>
     *
     * <p>Phạm vi đơn vị trả lời câu <i>"anh được xem dữ liệu của những ai"</i>. Câu ấy ⛔ không áp
     * cho chính mình: một cán bộ Xí nghiệp 5 mà hồ sơ nhân sự lại nằm ở Xí nghiệp 3 (chuyện thường
     * gặp khi tài khoản và hồ sơ nhập ở hai đợt khác nhau) sẽ ⛔ không mở nổi hồ sơ của <b>chính
     * mình</b> — một lỗi khó hiểu, ⛔ không phải một bảo đảm.
     *
     * <p>Và nới ở đây ⛔ <b>không</b> mở rộng thứ gì: tham số là {@code employeeId} lấy từ
     * {@link com.songnhue.core.common.security.AuthenticatedUser#employeeId()}, tức từ <b>token</b>,
     * ⛔ không từ một trường nào của request. ⛔ Không có id để đổi thì ⛔ không có IDOR để chặn.
     *
     * <p>⚠⚠ Nó dựa trên một tính chất của Hibernate mà một lượt đọc lướt ⛔ không thấy:
     * {@code @Filter} <b>⛔ không</b> áp cho {@code EntityManager.find()} tra theo khoá chính — chỉ
     * áp cho truy vấn. Nên {@code findById} ở đây thấy được hồ sơ ngoài phạm vi, còn một câu
     * {@code @Query} tương đương thì ⛔ không. Đó là một tính chất <b>vay mượn</b>, nên nó có bài
     * kiểm riêng khẳng định lượt tự đọc chạy được khi hai đơn vị lệch nhau — ngày Hibernate đổi
     * hành vi ấy, bài kiểm đỏ chứ ⛔ không phải người dùng.
     *
     * @return rỗng khi khoá là {@code null}, ⛔ không trỏ tới hồ sơ nào, hoặc hồ sơ đã xoá mềm
     */
    @Transactional(readOnly = true)
    public Optional<Employee> cuaChinhMinh(Long employeeId) {
        if (employeeId == null) {
            return Optional.empty();
        }
        return employees.findById(employeeId).filter(e -> e.getDeletedAt() == null);
    }

    /** Tên đơn vị / chức vụ cho một trang danh sách — tải hàng loạt, chống N+1. */
    @Transactional(readOnly = true)
    public Map<Long, String> tenDonVi(List<Employee> trang) {
        List<Long> ids = trang.stream()
                .map(Employee::getOrgUnitId)
                .filter(java.util.Objects::nonNull)
                .toList();
        return ids.isEmpty()
                ? Map.of()
                : orgUnits.findRefsByIds(ids).entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, e -> e.getValue().name()));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> tenChucVu(List<Employee> trang) {
        List<Long> ids = trang.stream()
                .map(Employee::getPositionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        return ids.isEmpty()
                ? Map.of()
                : positions.findByIdInAndDeletedAtIsNull(ids).stream()
                        .collect(Collectors.toMap(Position::getId, Position::getName));
    }

    /** {@code publicId} của một chức vụ theo khoá nội bộ — màn hình chi tiết cần nó để chọn lại ô. */
    @Transactional(readOnly = true)
    public UUID chucVuPublicId(Long positionId) {
        return positionId == null
                ? null
                : positions.findById(positionId).map(Position::getPublicId).orElse(null);
    }

    @Transactional
    public Employee create(EmployeeForm form) {
        String ma = chuanHoaMa(form.code());
        if (employees.existsByCodeAndDeletedAtIsNull(ma)) {
            throw new ConflictException(ErrorCode.HR_1001, ma);
        }
        Employee hoSo = new Employee();
        hoSo.setCode(ma);
        apDung(hoSo, form);
        log.info("Thêm hồ sơ CBNV {}", ma);
        return employees.save(hoSo);
    }

    /**
     * Sửa hồ sơ — <b>thay toàn phần</b>, ⛔ trừ mã nhân viên.
     *
     * <p>⛔⛔ Mã NV ⛔ không đổi được: đặc tả CN-04.2 viết <i>"không đổi suốt quá trình công tác"</i>,
     * và nó là thứ mọi quyết định nhân sự trên giấy tham chiếu tới. Bỏ qua {@code form.code()} một
     * cách tường minh ở đây thay vì tin biểu mẫu ⛔ không gửi nó lên.
     */
    @Transactional
    public Employee update(UUID publicId, EmployeeForm form) {
        Employee hoSo = trongPhamVi(publicId);
        apDung(hoSo, form);
        return employees.save(hoSo);
    }

    @Transactional
    public void delete(UUID publicId) {
        Employee hoSo = trongPhamVi(publicId);
        hoSo.markDeleted(Instant.now());
        employees.save(hoSo);
        log.info("Xoá mềm hồ sơ CBNV {}", hoSo.getCode());
    }

    /**
     * Tra một hồ sơ trong phạm vi đơn vị của người đăng nhập.
     *
     * <p>⛔ ⛔ Không bao giờ {@code findByPublicId(...).orElseThrow(NotFound)}: hai trạng thái
     * <i>"⛔ không tồn tại"</i> và <i>"tồn tại nhưng thuộc đơn vị khác"</i> phải phân biệt được ở
     * nhật ký bảo mật, dù cả hai đều ⛔ không trả dữ liệu ra ngoài.
     */
    private Employee trongPhamVi(UUID publicId) {
        return scopeGuard.require(employees.findByPublicIdAndDeletedAtIsNull(publicId), Employee.class, publicId);
    }

    private void apDung(Employee hoSo, EmployeeForm form) {
        hoSo.setFullName(batBuoc(form.fullName()));
        hoSo.setDateOfBirth(form.dateOfBirth());
        hoSo.setGender(form.gender());
        hoSo.setEducationLevel(form.educationLevel());
        hoSo.setEthnicity(rutGon(form.ethnicity()));
        hoSo.setHometown(rutGon(form.hometown()));
        hoSo.setAddress(rutGon(form.address()));
        hoSo.setPhone(rutGon(form.phone()));
        hoSo.setWorkEmail(rutGon(form.workEmail()));
        hoSo.setPersonalEmail(rutGon(form.personalEmail()));
        hoSo.setMaritalStatus(form.maritalStatus());
        hoSo.setEmergencyContactName(rutGon(form.emergencyContactName()));
        hoSo.setEmergencyContactPhone(rutGon(form.emergencyContactPhone()));

        hoSo.setOrgUnitId(donViBatBuoc(form.orgUnitPublicId()));
        hoSo.setPositionId(chucVuId(form.positionPublicId()));
        hoSo.setJobTitle(rutGon(form.jobTitle()));
        hoSo.setHiredAt(form.hiredAt());
        hoSo.setContractType(form.contractType());
        hoSo.setContractSignedAt(form.contractSignedAt());
        hoSo.setContractExpiresAt(form.contractExpiresAt());

        EmploymentStatus trangThai = form.status() == null ? EmploymentStatus.THU_VIEC : form.status();
        hoSo.setStatus(trangThai);
        kiemNgayNghi(trangThai, form);
        hoSo.setTerminatedAt(form.terminatedAt());
        hoSo.setTerminationReason(rutGon(form.terminationReason()));
    }

    /**
     * Cặp <i>trạng thái ↔ ngày nghỉ việc</i> phải khớp — ném 422 kèm <b>tên trường</b>.
     *
     * <p>CSDL đã có {@code ck_employees_terminated_pairs} ép hai chiều, nên nếu ⛔ không kiểm ở đây
     * thì người dùng vẫn ⛔ không ghi sai được — nhưng họ sẽ nhận một lỗi ràng buộc CSDL trần, ⛔
     * không chỉ được ô nào sai. Đây là hai lớp cho hai mục đích khác nhau, ⛔ không phải trùng lặp.
     *
     * <p>⚠ Tên trường trong {@code withDetail} phải trùng tên trường của <b>DTO request</b>, ⛔
     * không phải tên cột — {@code Form.setFields} của AntD so đúng chuỗi ấy, và lệch một chữ là
     * người dùng bấm Lưu rồi ⛔ không thấy gì (bộ canh {@code LoiTheoTruongToiDungOTest}).
     */
    private static void kiemNgayNghi(EmploymentStatus trangThai, EmployeeForm form) {
        boolean coNgayNghi = form.terminatedAt() != null;
        if (trangThai.daNghi() && !coNgayNghi) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003)
                    .withDetail("terminatedAt", "REQUIRED_WHEN_TERMINATED", null);
        }
        if (!trangThai.daNghi() && coNgayNghi) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003)
                    .withDetail("terminatedAt", "ONLY_WHEN_TERMINATED", form.terminatedAt());
        }
    }

    private Long donViBatBuoc(UUID publicId) {
        if (publicId == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("orgUnitId", "REQUIRED", null);
        }
        return orgUnits.findRef(publicId).map(OrgUnitRef::id).orElseThrow(() -> (ValidationException)
                new ValidationException(ErrorCode.SYS_0003).withDetail("orgUnitId", "NOT_FOUND", publicId));
    }

    private Long donViId(UUID publicId) {
        return publicId == null
                ? null
                : orgUnits.findRef(publicId).map(OrgUnitRef::id).orElse(null);
    }

    private Long chucVuId(UUID publicId) {
        if (publicId == null) {
            return null;
        }
        Optional<Position> chucVu = positions.findByPublicIdAndDeletedAtIsNull(publicId);
        return chucVu.map(Position::getId).orElseThrow(() -> (ValidationException)
                new ValidationException(ErrorCode.SYS_0003).withDetail("positionId", "NOT_FOUND", publicId));
    }

    private static String chuanHoaMa(String ma) {
        String rut = batBuoc(ma).toUpperCase(Locale.ROOT);
        if (rut.length() > 50) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return rut;
    }

    private static String batBuoc(String giaTri) {
        if (giaTri == null || giaTri.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return giaTri.trim();
    }

    private static String rutGon(String giaTri) {
        return giaTri == null || giaTri.isBlank() ? null : giaTri.trim();
    }
}
