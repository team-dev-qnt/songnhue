package com.songnhue.hydro.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.hydro.domain.AlertRule;

/**
 * Ngưỡng cảnh báo — đường CRUD của màn hình cấu hình (T33.11).
 *
 * <p>⛔ <b>Không</b> phải đường mà máy cảnh báo dùng: đường nóng đọc phẳng qua
 * {@link AlertEngineRepository}. Xem javadoc lớp ấy.
 *
 * <h2>⚠ {@code @EntityGraph} trên mọi câu ĐỌC — không phải trang trí</h2>
 *
 * <p>{@code spring.jpa.open-in-view = false} (application.yml, cố ý). Ba
 * {@code @ManyToOne(LAZY)} của {@link AlertRule} mà đọc trong hàm dựng DTO — tức <b>sau</b> khi
 * giao dịch đóng — là {@code LazyInitializationException}, tức HTTP 500 ở <b>mọi</b> lượt gọi.
 * Chuyện ấy đã xảy ra thật: {@code GET /hyd/stations} trả 500 suốt bốn ngày từ WS-28 vì đúng hình
 * dạng này, và không bài kiểm nào thấy vì tất cả đều gọi thẳng service (luật 5).
 */
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    @EntityGraph(attributePaths = {"station", "measurementType", "alertLevel"})
    Optional<AlertRule> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    @EntityGraph(attributePaths = {"station", "measurementType", "alertLevel"})
    List<AlertRule> findByStationIdAndDeletedAtIsNullOrderByIdAsc(Long stationId);

    @EntityGraph(attributePaths = {"station", "measurementType", "alertLevel"})
    List<AlertRule> findByDeletedAtIsNullOrderByIdAsc();

    /** Chặn xoá một mức đang có ngưỡng trỏ vào — {@code HYD-2010}. */
    boolean existsByAlertLevelIdAndDeletedAtIsNull(Long alertLevelId);

    boolean existsByStationIdAndMeasurementTypeIdAndAlertLevelIdAndDeletedAtIsNull(
            Long stationId, Long measurementTypeId, Long alertLevelId);

    /** Điểm đo đã có ít nhất một ngưỡng — nuôi danh sách "Điểm đo chưa cấu hình ngưỡng" (T33.6). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT r.station.id FROM AlertRule r WHERE r.deletedAt IS NULL")
    List<Long> diemDoDaCauHinh();
}
