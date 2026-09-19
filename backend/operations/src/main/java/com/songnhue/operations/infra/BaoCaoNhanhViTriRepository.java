package com.songnhue.operations.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.operations.domain.BaoCaoNhanhViTri;

public interface BaoCaoNhanhViTriRepository extends JpaRepository<BaoCaoNhanhViTri, Long> {

    List<BaoCaoNhanhViTri> findByDeletedAtIsNullOrderBySortOrder();

    Optional<BaoCaoNhanhViTri> findByPublicIdAndDeletedAtIsNull(UUID publicId);
}
