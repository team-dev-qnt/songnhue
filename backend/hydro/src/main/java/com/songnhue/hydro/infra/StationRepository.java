package com.songnhue.hydro.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
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

    /** ⚠ Nạp kèm loại chỉ số — xem javadoc của {@link #findByDeletedAtIsNullOrderByCodeAsc()}. */
    @EntityGraph(attributePaths = "measurementTypes")
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

    /**
     * ⭐⭐ {@code @EntityGraph} nạp kèm {@code measurementTypes} — <b>vá 03/09/2026</b>.
     *
     * <p>⛔⛔ Thiếu nó thì <b>mọi lượt {@code GET} điểm đo qua HTTP trả 500</b>, và nó đã như vậy từ
     * WS-28. {@code spring.jpa.open-in-view = false} (application.yml:97, cố ý), nên phiên Hibernate
     * đóng ngay khi {@code StationService.get}/{@code list} trả về — còn
     * {@code StationController.toView} thì đọc {@code getMeasurementTypes()} <i>sau</i> đó và nhận
     * {@code LazyInitializationException}.
     *
     * <h2>Vì sao không ai thấy suốt bốn ngày</h2>
     *
     * <p>WS-28 đóng với {@code StationScopeTest} và {@code ApiSourceServiceTest} — <b>cả hai gọi
     * thẳng service</b>, tức là chạy <i>bên trong</i> giao dịch của bài kiểm, nơi phiên còn sống.
     * Đây đúng luật 5 mà dự án đã trả giá hai lần: <i>bài kiểm gọi thẳng service không đi cùng đường
     * với production</i>. Và đường {@code POST} thì <b>vẫn chạy</b> — entity vừa dựng mang một
     * {@code Set} thường, ⛔ không phải proxy — nên một lượt thử tay "thêm điểm đo xong thấy 201" cho
     * cảm giác màn hình hoạt động.
     *
     * <p>⇒ Khuyết tật lộ ra ở lượt {@code GET} <b>đầu tiên qua HTTP</b> của cả module, và lượt ấy là
     * {@code StationConstructionLinkHttpTest} của T28.19 — một bài kiểm viết cho việc khác.
     */
    @EntityGraph(attributePaths = "measurementTypes")
    List<Station> findByDeletedAtIsNullOrderByCodeAsc();

    /** Màn hình "Điểm đo chưa gán đơn vị" — hệ quả của OI-05 (T28.9). */
    /** ⚠ Cũng đi qua {@code toView} nên cũng cần nạp kèm — cùng lý do với hai câu trên. */
    @EntityGraph(attributePaths = "measurementTypes")
    List<Station> findByOrgUnitIdIsNullAndDeletedAtIsNullOrderByCodeAsc();

    List<Station> findByApiSourceIdAndDeletedAtIsNullOrderByCodeAsc(Long apiSourceId);
}
