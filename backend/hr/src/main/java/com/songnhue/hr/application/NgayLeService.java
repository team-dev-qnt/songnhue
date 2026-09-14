package com.songnhue.hr.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.hr.domain.Holiday;
import com.songnhue.hr.infra.HolidayRepository;

/**
 * Danh mục ngày nghỉ lễ — CN-04.9, <b>dữ liệu do Công ty vận hành</b> (quy tắc 16).
 *
 * <p>⛔⛔ Bảng giao đi <b>RỖNG</b>, và khối {@code DO $$} của {@code V202609141079} ném nếu ⛔ không
 * — lễ Việt Nam theo âm lịch, ⛔ không suy ra được bằng công thức, và CLAUDE.md cấm seed dữ liệu ⛔
 * không có nguồn. ⚠ Hệ quả phải xử lý chứ ⛔ không giấu: bảng rỗng làm phép đếm ngày công trừ nhầm
 * cả tuần Tết vào phép năm — xem {@link DemNgayCongService}.
 */
@Service
public class NgayLeService {

    private final HolidayRepository holidays;

    public NgayLeService(HolidayRepository holidays) {
        this.holidays = holidays;
    }

    @Transactional(readOnly = true)
    public List<Holiday> danhSach() {
        return holidays.findByDeletedAtIsNullOrderByHolidayDateAsc();
    }

    @Transactional
    public Holiday tao(LocalDate ngay, String ten, String ghiChu) {
        kiemTrung(ngay, null);
        Holiday moi = new Holiday(ngay, ten);
        moi.setNote(ghiChu);
        return holidays.save(moi);
    }

    @Transactional
    public Holiday sua(UUID publicId, LocalDate ngay, String ten, String ghiChu) {
        Holiday ban = require(publicId);
        kiemTrung(ngay, ban.getId());
        ban.setHolidayDate(ngay);
        ban.setName(ten);
        ban.setNote(ghiChu);
        return holidays.save(ban);
    }

    @Transactional
    public void xoa(UUID publicId) {
        Holiday ban = require(publicId);
        // ⚠ Xoá MỀM. Đơn nghỉ đã nộp giữ nguyên `working_days` đã đếm — xem javadoc
        //   `LeaveRequest.workingDays`: con số ấy đóng băng tại thời điểm quyết định, nên gỡ một
        //   ngày lễ ⛔ không làm đơn cũ đổi số ngày. Đó là điều ĐÚNG.
        ban.markDeleted(Instant.now());
        holidays.save(ban);
    }

    private void kiemTrung(LocalDate ngay, Long idHienTai) {
        holidays.findByHolidayDateAndDeletedAtIsNull(ngay).stream()
                .filter(h -> !h.getId().equals(idHienTai))
                .findFirst()
                .ifPresent(h -> {
                    throw new ConflictException(ErrorCode.HR_2008, ngay);
                });
    }

    private Holiday require(UUID publicId) {
        return holidays.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }
}
