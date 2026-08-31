package com.songnhue.hydro.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.hydro.domain.Station;

/**
 * Điểm đo — T28.7.
 *
 * <p>⚠ Mọi truy vấn ở đây chịu bộ lọc phạm vi {@code Station.LOC_PHAM_VI} do {@code ScopeFilterAspect}
 * bật (tầng 3 phân quyền). Đường đi vòng duy nhất là {@code findById}, và đó chính là lý do mọi tra
 * cứu theo {@code public_id} phải qua {@code ScopeGuard.require} — {@code conventions.md} §4.2.
 */
public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /**
     * ⭐ Đường tra cứu của poller: mã API → điểm đo.
     *
     * <p>⚠ {@code AndDeletedAtIsNull} <b>không phải cho đẹp</b>: chỉ mục duy nhất trên
     * {@code api_code} là chỉ mục MỘT PHẦN (chỉ trên bản ghi còn sống), nên xoá mềm một điểm đo rồi
     * lập lại hồ sơ với đúng mã cũ là việc hợp lệ và sẽ có hai dòng cùng mã. Thiếu điều kiện này thì
     * một ngày nào đó poller ghi số liệu vào bản ghi đã xoá.
     */
    Optional<Station> findByApiCodeAndDeletedAtIsNull(String apiCode);

    boolean existsByApiCodeAndDeletedAtIsNull(String apiCode);

    boolean existsByApiCodeAndDeletedAtIsNullAndIdNot(String apiCode, Long id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, Long id);

    List<Station> findByDeletedAtIsNullOrderByCodeAsc();

    /** Màn hình "Điểm đo chưa gán đơn vị" — hệ quả của OI-05 (T28.9). */
    List<Station> findByOrgUnitIdIsNullAndDeletedAtIsNullOrderByCodeAsc();

    List<Station> findByApiSourceIdAndDeletedAtIsNullOrderByCodeAsc(Long apiSourceId);
}
