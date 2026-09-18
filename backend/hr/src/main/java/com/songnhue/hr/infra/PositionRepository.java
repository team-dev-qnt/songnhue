package com.songnhue.hr.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.hr.domain.Position;

/** Danh mục chức vụ — CN-04.2. Dùng chung toàn Công ty, ⛔ không lọc phạm vi đơn vị. */
public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, Long id);

    List<Position> findByDeletedAtIsNullOrderBySortOrderAscNameAsc();

    List<Position> findByIdInAndDeletedAtIsNull(List<Long> ids);
}
