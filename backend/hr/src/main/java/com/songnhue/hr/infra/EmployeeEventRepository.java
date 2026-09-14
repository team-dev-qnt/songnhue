package com.songnhue.hr.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.hr.domain.EmployeeEvent;
import com.songnhue.hr.domain.EmployeeEventType;

/**
 * Timeline công tác — CN-04.4.
 *
 * <p>Sắp theo {@code effectiveOn DESC} (reverse-chronological, đúng đặc tả), rồi {@code id DESC} để
 * hai sự kiện <b>cùng ngày hiệu lực</b> có thứ tự ổn định. ⛔ Thiếu vế thứ hai thì thứ tự do CSDL
 * quyết định và nó đổi giữa hai lượt tải — đúng hình dạng §11.12 (`ORDER BY` ⛔ không bao giờ so tới
 * vế hai).
 */
public interface EmployeeEventRepository extends JpaRepository<EmployeeEvent, Long> {

    Optional<EmployeeEvent> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    List<EmployeeEvent> findByEmployeeIdAndDeletedAtIsNullOrderByEffectiveOnDescIdDesc(Long employeeId);

    /**
     * Sự kiện của <b>nhiều loại</b> từ một mốc ngày — CN-04.8 (biểu đồ biến động 12 tháng).
     *
     * <p>⛔⛔ Chống N+1, và ở đây N là <b>số cán bộ</b>: bản đầu của {@code BaoCaoNhanSuService} gọi
     * {@code findByEmployeeId…} trong một vòng lặp, tức ~200 lượt truy vấn cho <b>một</b> lượt mở
     * màn hình thống kê — trên một VPS 2 nhân. Và nó ⛔ không có triệu chứng nào ngoài *"trang hơi
     * chậm"*, thứ ⛔ không ai đi đo.
     *
     * <p>⚠ Câu này đi qua bộ lọc phạm vi như mọi câu JPA khác — đúng ý: báo cáo <b>cắt</b> theo đơn
     * vị (M4.13), khác hẳn danh bạ và sơ đồ tổ chức.
     */
    @Query(
            """
            SELECT s FROM EmployeeEvent s
            WHERE s.deletedAt IS NULL
              AND s.eventType IN :loai
              AND s.effectiveOn >= :tu
            """)
    List<EmployeeEvent> theoLoaiTuNgay(
            @Param("loai") List<com.songnhue.hr.domain.EmployeeEventType> loai, @Param("tu") java.time.LocalDate tu);

    List<EmployeeEvent> findByEmployeeIdAndEventTypeAndDeletedAtIsNullOrderByEffectiveOnDescIdDesc(
            Long employeeId, EmployeeEventType eventType);
}
