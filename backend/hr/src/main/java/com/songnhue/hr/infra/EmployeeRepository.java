package com.songnhue.hr.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.EmploymentStatus;

/**
 * Hồ sơ CBNV — CN-04.2.
 *
 * <h2>⛔ ⛔ Không repository nào ở đây tự viết {@code WHERE org_unit_id = ?}</h2>
 *
 * <p>Lọc phạm vi là việc của bộ lọc Hibernate bật bởi {@code ScopeFilterAspect} (quy tắc 5). Tự
 * thêm điều kiện ở đây có hai cái giá: nó chỉ đúng ở <b>câu truy vấn ấy</b> (câu kế tiếp lại quên),
 * và nó so bằng {@code id} nên quản lý Xí nghiệp ⛔ không thấy được các Tổ đội trực thuộc — trong
 * khi bộ lọc chuẩn so bằng <i>materialized path</i>.
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, Long id);

    long countByPositionIdAndDeletedAtIsNull(Long positionId);

    /**
     * Danh sách có lọc — mọi tham số {@code null} nghĩa là "⛔ không lọc theo tiêu chí này".
     *
     * <p>⚠ Truy vấn <b>tĩnh</b> chứ ⛔ không {@code Specification}: mỗi nhánh động là một đường mà ⛔
     * không ai đọc lại được toàn bộ, và câu động cũng là chỗ một bộ lọc bị bỏ sót mà ⛔ không ai thấy.
     *
     * <p>⛔⛔ <b>Gọi {@code sn_khong_dau(...)} THẲNG, ⛔ không qua {@code FUNCTION('sn_khong_dau', …)}</b>
     * — bản đầu của tôi viết dạng {@code FUNCTION(...)} và ứng dụng <b>⛔ không khởi động được</b>:
     * <i>"Operand of 'like' is of type 'java.lang.Object' which is not a string"</i>. Hibernate ⛔
     * không đoán kiểu trả về của một hàm lạ; {@code CoreFunctionContributor} đã đăng ký hàm này kèm
     * kiểu {@code STRING}, nhưng chỉ khi gọi bằng <b>tên đã đăng ký</b>. ⭐ Hỏng theo hướng TỐT:
     * Spring Data biên dịch mọi {@code @Query} lúc dựng bean, nên ⛔ không bản build nào lên được
     * với câu truy vấn hỏng — nhưng thông báo hiện ra dưới dạng <i>"⛔ không tạo được bean"</i> và
     * rất dễ bị đọc nhầm thành lỗi cấu hình Spring ({@code CoreFunctionContributor} javadoc).
     *
     * <p>⚠⚠ {@code CAST(:tuKhoa AS String)} ⛔ <b>không phải trang trí</b>. Truyền {@code null} trần
     * thì Hibernate gửi xuống dạng {@code bytea} và PostgreSQL trả <i>"function sn_khong_dau(bytea)
     * does not exist"</i> ⇒ <b>mọi lượt tìm kiếm để trống ô từ khoá đều hỏng</b>. Khuyết tật ấy đã
     * có thật ở {@code ArticleRepository} từ WS-13 và nằm im tới khi cổng công khai đi qua.
     *
     * <p>⭐ Bỏ dấu ở <b>cả hai vế</b> bằng cùng một hàm SQL — ⛔ không normalize ở Java rồi so với
     * một bản normalize ở SQL: hai cách bỏ dấu là hai nguồn sự thật cho cùng một phép so, và chúng
     * sẽ lệch đúng vào ngày ai đó sửa một bên (luật 14).
     */
    @Query(
            """
            SELECT e FROM Employee e
            WHERE e.deletedAt IS NULL
              AND (CAST(:tuKhoa AS String) IS NULL
                   OR sn_khong_dau(e.fullName) LIKE sn_khong_dau(CAST(:tuKhoa AS String))
                   OR sn_khong_dau(e.code) LIKE sn_khong_dau(CAST(:tuKhoa AS String)))
              AND (:orgUnitId IS NULL OR e.orgUnitId = :orgUnitId)
              AND (:positionId IS NULL OR e.positionId = :positionId)
              AND (:trangThai IS NULL OR e.status = :trangThai)
            """)
    Page<Employee> timKiem(
            @Param("tuKhoa") String tuKhoa,
            @Param("orgUnitId") Long orgUnitId,
            @Param("positionId") Long positionId,
            @Param("trangThai") EmploymentStatus trangThai,
            Pageable pageable);

    /**
     * Hợp đồng sắp/đã hết hạn — <b>vế ĐỌC còn thiếu</b> của {@code hr.contract.expiry-warning-days}
     * (M4.9). Khoá ấy nằm trong {@code settings} từ WS-4 với <b>0 nơi đọc</b> cho tới WS-53.
     *
     * <p>⛔ Chỉ người <b>còn làm việc</b>: hợp đồng của một người đã nghỉ việc hết hạn là chuyện
     * bình thường, và để nó trong danh sách cảnh báo là dạy người dùng bỏ qua danh sách ấy.
     *
     * <p>⚠ Đi qua bộ lọc phạm vi của {@link Employee} như mọi truy vấn khác — quản lý Xí nghiệp chỉ
     * thấy hợp đồng đơn vị mình (CN-04.7 / M4.13).
     */
    @Query(
            """
            SELECT e FROM Employee e
            WHERE e.deletedAt IS NULL
              AND e.contractExpiresAt IS NOT NULL
              AND e.contractExpiresAt <= :moc
              AND e.status NOT IN (com.songnhue.hr.domain.EmploymentStatus.NGHI_VIEC,
                                   com.songnhue.hr.domain.EmploymentStatus.NGHI_HUU)
            ORDER BY e.contractExpiresAt ASC
            """)
    List<Employee> hopDongSapHetHan(@Param("moc") java.time.LocalDate moc);

    /** Mọi hồ sơ trong phạm vi người gọi — nền cho phép quét chứng chỉ sắp hết hiệu lực. */
    List<Employee> findByDeletedAtIsNull();
}
