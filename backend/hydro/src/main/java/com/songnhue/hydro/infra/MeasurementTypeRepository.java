package com.songnhue.hydro.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.hydro.domain.MeasurementType;

/** Danh mục loại chỉ số quan trắc — T28.7. */
public interface MeasurementTypeRepository extends JpaRepository<MeasurementType, Long> {

    Optional<MeasurementType> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<MeasurementType> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, Long id);

    List<MeasurementType> findByDeletedAtIsNullOrderBySortOrderAscNameAsc();
}
