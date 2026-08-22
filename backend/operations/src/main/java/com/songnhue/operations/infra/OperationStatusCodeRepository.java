package com.songnhue.operations.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.operations.domain.OperationStatusCode;

@Repository
public interface OperationStatusCodeRepository extends JpaRepository<OperationStatusCode, Long> {

    boolean existsByCodeAndDeletedAtIsNull(String code);

    Optional<OperationStatusCode> findByCodeAndDeletedAtIsNull(String code);
}
