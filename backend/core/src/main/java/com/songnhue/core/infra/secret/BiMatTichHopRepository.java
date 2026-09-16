package com.songnhue.core.infra.secret;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.secret.BiMatTichHop;
import com.songnhue.core.spi.LoaiBiMat;

@Repository
public interface BiMatTichHopRepository extends JpaRepository<BiMatTichHop, Long> {

    Optional<BiMatTichHop> findByLoai(LoaiBiMat loai);
}
