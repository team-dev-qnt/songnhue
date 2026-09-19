package com.songnhue.operations.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.operations.domain.BaoCaoNhanhViTriKy;

public interface BaoCaoNhanhViTriKyRepository extends JpaRepository<BaoCaoNhanhViTriKy, Long> {

    List<BaoCaoNhanhViTriKy> findByBaoCaoIdAndDeletedAtIsNull(Long baoCaoId);
}
