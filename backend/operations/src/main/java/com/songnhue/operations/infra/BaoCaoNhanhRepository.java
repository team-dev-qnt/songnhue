package com.songnhue.operations.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.operations.domain.BaoCaoNhanh;

public interface BaoCaoNhanhRepository extends JpaRepository<BaoCaoNhanh, Long> {

    Optional<BaoCaoNhanh> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Page<BaoCaoNhanh> findByDeletedAtIsNull(Pageable pageable);
}
