package com.songnhue.hr.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.hr.domain.Position;
import com.songnhue.hr.infra.EmployeeRepository;
import com.songnhue.hr.infra.PositionRepository;

/**
 * Danh mục chức vụ — CN-04.2.
 *
 * <h2>⛔ Vì sao service này ⛔ KHÔNG bọc {@code ScopeGuard}</h2>
 *
 * <p>{@link Position} kế thừa {@code BaseEntity}, ⛔ không {@code ScopedEntity}: chức vụ dùng chung
 * toàn Công ty. Bọc {@code ScopeGuard} vào đây sẽ ném {@code AUTH-3002} cho một danh mục mà mọi
 * người đều phải đọc được. Ghi lý do ra thay vì để người đọc sau tự đoán — tiền lệ:
 * {@code OperationStatusCodeService:149}.
 *
 * <p>⚠ Nhưng "⛔ không lọc phạm vi" ⛔ không có nghĩa "ai cũng sửa được": tầng 2
 * ({@code @RequirePermission}) vẫn gác, và chỉ ADMIN_HR / ADMIN / SUPER_ADMIN có
 * {@code hr:employee:create|update|delete}.
 */
@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    private final PositionRepository positions;
    private final EmployeeRepository employees;

    public PositionService(PositionRepository positions, EmployeeRepository employees) {
        this.positions = positions;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public List<Position> list() {
        return positions.findByDeletedAtIsNullOrderBySortOrderAscNameAsc();
    }

    @Transactional
    public Position create(PositionForm form) {
        String ma = chuanHoaMa(form.code());
        if (positions.existsByCodeAndDeletedAtIsNull(ma)) {
            throw new ConflictException(ErrorCode.HR_1002, ma);
        }
        Position chucVu = new Position();
        chucVu.setCode(ma);
        apDung(chucVu, form);
        log.info("Thêm chức vụ {}", ma);
        return positions.save(chucVu);
    }

    @Transactional
    public Position update(UUID publicId, PositionForm form) {
        Position chucVu = tim(publicId);
        String ma = chuanHoaMa(form.code());
        if (positions.existsByCodeAndDeletedAtIsNullAndIdNot(ma, chucVu.getId())) {
            throw new ConflictException(ErrorCode.HR_1002, ma);
        }
        chucVu.setCode(ma);
        apDung(chucVu, form);
        return positions.save(chucVu);
    }

    /**
     * Xoá mềm — ⛔ chỉ khi ⛔ không hồ sơ nào còn giữ chức vụ này.
     *
     * <p>⛔⛔ Đây là vế thứ hai của T40.26: xoá thẳng rồi để cột {@code position_id} của hồ sơ tự về
     * rỗng thì màn hình hồ sơ ⛔ <b>không lộ ra gì</b> — chức vụ chỉ đơn giản biến mất khỏi từng ấy
     * hồ sơ, ⛔ không một dòng cảnh báo. Hỏi trước khi xoá, ⛔ không dọn sau khi xoá.
     */
    @Transactional
    public void delete(UUID publicId) {
        Position chucVu = tim(publicId);
        long dangGiu = employees.countByPositionIdAndDeletedAtIsNull(chucVu.getId());
        if (dangGiu > 0) {
            throw new ValidationException(ErrorCode.HR_2002, dangGiu);
        }
        chucVu.markDeleted(Instant.now());
        positions.save(chucVu);
        log.info("Xoá mềm chức vụ {}", chucVu.getCode());
    }

    private Position tim(UUID publicId) {
        return positions
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private static void apDung(Position chucVu, PositionForm form) {
        chucVu.setName(batBuoc(form.name()));
        chucVu.setPositionGroup(rutGon(form.positionGroup()));
        chucVu.setDescription(rutGon(form.description()));
        chucVu.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        chucVu.setActive(form.active() == null || form.active());
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

    /** Rỗng và "chưa đặt" là hai chuyện khác nhau ở tầng dây, nhưng ở CSDL thì cùng là {@code NULL}. */
    private static String rutGon(String giaTri) {
        return giaTri == null || giaTri.isBlank() ? null : giaTri.trim();
    }
}
