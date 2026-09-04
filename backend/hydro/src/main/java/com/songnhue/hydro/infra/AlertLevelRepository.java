package com.songnhue.hydro.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.hydro.domain.AlertLevel;

/** Danh mục mức cảnh báo (T33.1) — CRUD thuần, khối lượng nhỏ, ⛔ không nằm trên đường nóng. */
public interface AlertLevelRepository extends JpaRepository<AlertLevel, Long> {

    Optional<AlertLevel> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /** ⚠ Sắp theo hạng nặng nhẹ, ⛔ không theo mã: người đọc cần thấy thang, không thấy bảng chữ cái. */
    List<AlertLevel> findByDeletedAtIsNullOrderBySeverityRankAsc();

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsBySeverityRankAndDeletedAtIsNull(Integer severityRank);
}
