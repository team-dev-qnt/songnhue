package com.songnhue.operations.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.operations.domain.GisLayer;

/**
 * Lớp bản đồ GIS — CN-02.4 / M2.9.
 *
 * <p>⛔ {@code GisLayer} ⛔ <b>không</b> là {@code ScopedEntity}, nên các câu ở đây ⛔ không bị bộ lọc
 * phạm vi cắt — xem javadoc entity: một lớp nền bản đồ ⛔ không thuộc về Xí nghiệp nào.
 */
public interface GisLayerRepository extends JpaRepository<GisLayer, Long> {

    Optional<GisLayer> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /** Toàn bộ lớp còn sống, kể cả lớp đã TẮT — màn hình quản trị cần thấy đủ. */
    List<GisLayer> findByDeletedAtIsNullOrderBySortOrderAscIdAsc();

    /** Chỉ lớp ĐANG BẬT — nguồn của bảng lớp trên bản đồ. */
    List<GisLayer> findByDeletedAtIsNullAndActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    boolean existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(String name, Long id);
}
