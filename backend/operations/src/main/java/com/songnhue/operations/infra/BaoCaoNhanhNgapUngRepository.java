package com.songnhue.operations.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.operations.domain.BaoCaoNhanhNgapUng;

public interface BaoCaoNhanhNgapUngRepository extends JpaRepository<BaoCaoNhanhNgapUng, Long> {

    List<BaoCaoNhanhNgapUng> findByBaoCaoIdAndDeletedAtIsNull(Long baoCaoId);
}
