package com.songnhue.operations.infra;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.operations.domain.BaoCaoNhanhLuongMua;

public interface BaoCaoNhanhLuongMuaRepository extends JpaRepository<BaoCaoNhanhLuongMua, Long> {

    List<BaoCaoNhanhLuongMua> findByBaoCaoIdAndDeletedAtIsNull(Long baoCaoId);
}
