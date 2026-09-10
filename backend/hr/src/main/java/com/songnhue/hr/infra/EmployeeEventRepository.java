package com.songnhue.hr.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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

    List<EmployeeEvent> findByEmployeeIdAndEventTypeAndDeletedAtIsNullOrderByEffectiveOnDescIdDesc(
            Long employeeId, EmployeeEventType eventType);
}
