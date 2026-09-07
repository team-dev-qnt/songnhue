package com.songnhue.hydro.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.hydro.domain.StationConstruction;

/** Liên kết điểm đo ↔ công trình — T28.7. */
public interface StationConstructionRepository extends JpaRepository<StationConstruction, Long> {

    Optional<StationConstruction> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    List<StationConstruction> findByStationIdAndDeletedAtIsNull(Long stationId);

    List<StationConstruction> findByStationIdInAndDeletedAtIsNull(List<Long> stationIds);

    List<StationConstruction> findByConstructionIdAndDeletedAtIsNull(Long constructionId);

    Optional<StationConstruction> findByStationIdAndPrimaryTrueAndDeletedAtIsNull(Long stationId);
}
