package com.songnhue.operations.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.songnhue.operations.domain.ConstructionOperationStatus;

@Repository
public interface ConstructionOperationStatusRepository extends JpaRepository<ConstructionOperationStatus, Long> {

    Optional<ConstructionOperationStatus> findFirstByConstructionIdOrderByEffectiveAtDesc(Long constructionId);

    boolean existsByOperationCodeId(Long operationCodeId);

    @Query(
            value = "SELECT cos.construction_id FROM construction_operation_status cos "
                    + "WHERE cos.operation_code_id = :codeId AND cos.id = ("
                    + "  SELECT cos2.id FROM construction_operation_status cos2 "
                    + "  WHERE cos2.construction_id = cos.construction_id "
                    + "  ORDER BY cos2.effective_at DESC LIMIT 1"
                    + ")",
            nativeQuery = true)
    java.util.List<Long> findConstructionIdsWithLatestCode(
            @org.springframework.data.repository.query.Param("codeId") Long codeId);
}
