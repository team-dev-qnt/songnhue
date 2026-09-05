package com.songnhue.hydro.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.hydro.domain.ApiSource;

/** Nguồn dữ liệu bên thứ ba — T28.7. */
public interface ApiSourceRepository extends JpaRepository<ApiSource, Long> {

    Optional<ApiSource> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<ApiSource> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, Long id);

    List<ApiSource> findByDeletedAtIsNullOrderByCodeAsc();
}
