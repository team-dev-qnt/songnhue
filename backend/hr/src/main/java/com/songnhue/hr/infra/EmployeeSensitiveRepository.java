package com.songnhue.hr.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.songnhue.hr.domain.EmployeeSensitive;

/**
 * Trường 🔒 của hồ sơ CBNV.
 *
 * <p>⛔⛔ Cố ý ⛔ <b>không</b> có phương thức nào tra theo {@code publicId}: bảng này ⛔ không có
 * đường vào riêng từ API. Mọi lượt đọc bắt đầu từ một {@code Employee} đã qua
 * {@code ScopeGuard.require(...)} — nếu ⛔ không thì phạm vi đơn vị của tầng 3 bị đi vòng đúng ở
 * bảng nhạy cảm nhất.
 */
public interface EmployeeSensitiveRepository extends JpaRepository<EmployeeSensitive, Long> {

    Optional<EmployeeSensitive> findByEmployeeIdAndDeletedAtIsNull(Long employeeId);

    Optional<EmployeeSensitive> findByNationalIdFingerprintAndDeletedAtIsNull(String fingerprint);

    /**
     * Mọi {@code key_id} đang có mặt ở cột vân tay — dùng cho một khẳng định thường trực.
     *
     * <p>⚠ Vân tay <b>phụ thuộc khoá</b>: sau một lượt xoay khoá mà ⛔ không tính lại cột này, cùng
     * một số CCCD cho hai vân tay khác nhau và phép chống trùng câm lặng. Tập này có nhiều hơn một
     * phần tử tức là chuyện đó đã xảy ra.
     */
    @Query(
            """
            SELECT DISTINCT substring(e.nationalIdFingerprint, 1, locate(':', e.nationalIdFingerprint) - 1)
            FROM EmployeeSensitive e
            WHERE e.deletedAt IS NULL AND e.nationalIdFingerprint IS NOT NULL
            """)
    List<String> cacKhoaDangDungOVanTay();
}
