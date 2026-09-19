package com.songnhue.operations.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.operations.domain.BaoCaoNhanhVanHanh;

public interface BaoCaoNhanhVanHanhRepository extends JpaRepository<BaoCaoNhanhVanHanh, Long> {

    List<BaoCaoNhanhVanHanh> findByBaoCaoIdAndDeletedAtIsNull(Long baoCaoId);
}
