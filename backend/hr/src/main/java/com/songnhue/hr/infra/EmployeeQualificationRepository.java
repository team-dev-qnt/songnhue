package com.songnhue.hr.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.hr.domain.EmployeeQualification;

/**
 * Lý lịch & chuyên môn — CN-04.3.
 *
 * <p>⚠ Bảng này ⛔ không mang cột phạm vi đơn vị, nên <b>⛔ không</b> có bộ lọc Hibernate nào đứng
 * chắn ở đây. Phạm vi đi qua nhân viên và được ép ở {@code LyLichService} bằng
 * {@code ScopeGuard.require}. ⛔ Đừng gọi thẳng repository này từ controller.
 */
public interface EmployeeQualificationRepository extends JpaRepository<EmployeeQualification, Long> {

    Optional<EmployeeQualification> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    List<EmployeeQualification> findByEmployeeIdAndDeletedAtIsNullOrderByIssuedOnDescIdDesc(Long employeeId);

    long countByEmployeeIdAndDeletedAtIsNull(Long employeeId);

    /**
     * Chứng chỉ sắp/đã hết hiệu lực — <b>vế ĐỌC còn thiếu</b> của khoá
     * {@code hr.certificate.expiry-warning-days}, thứ đã nằm trong {@code settings} từ WS-4 với
     * <b>0 nơi đọc</b> (quy tắc 15).
     *
     * <p>⛔ Điều kiện là {@code expires_on <= :moc} chứ ⛔ không phải một khoảng: mục <b>đã</b> hết
     * hạn cũng phải nằm trong danh sách, tô đỏ. Chỉ lấy khoảng {@code [hôm nay, mốc]} là để rơi mất
     * đúng những mục nguy hiểm nhất — và triệu chứng là một danh sách <i>ngắn dần</i> theo thời
     * gian, thứ trông y hệt <i>"mọi thứ đang ổn"</i>.
     *
     * <p>⚠ {@code employee_id} lọc theo tập nhân viên nơi gọi đã kiểm phạm vi — ⛔ không quét cả
     * bảng rồi lọc ở Java.
     */
    @Query(
            """
            SELECT q FROM EmployeeQualification q
            WHERE q.deletedAt IS NULL
              AND q.expiresOn IS NOT NULL
              AND q.expiresOn <= :moc
              AND q.employeeId IN :employeeIds
            ORDER BY q.expiresOn ASC
            """)
    List<EmployeeQualification> sapHetHieuLuc(
            @Param("moc") LocalDate moc, @Param("employeeIds") List<Long> employeeIds);
}
