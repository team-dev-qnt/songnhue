package com.songnhue.operations.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.operations.domain.ConstructionCluster;

/** Danh mục cụm công trình — T17.11. */
public interface ConstructionClusterRepository extends JpaRepository<ConstructionCluster, Long> {

    Optional<ConstructionCluster> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<ConstructionCluster> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, Long id);

    List<ConstructionCluster> findByDeletedAtIsNullOrderBySortOrderAscNameAsc();
}
