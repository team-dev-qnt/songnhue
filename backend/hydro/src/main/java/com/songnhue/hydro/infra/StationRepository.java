package com.songnhue.hydro.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // =========================================================================
    // ⭐⭐ T28.32 — hai phép đếm CỐ Ý ĐỨNG NGOÀI bộ lọc phạm vi
    // =========================================================================

    /**
     * Số điểm đo <b>toàn hệ thống</b> đang trỏ vào một nguồn dữ liệu — ⛔ ⛔ <b>không</b> lọc phạm vi.
     *
     * <h2>⛔⛔ Vì sao một phép đếm "còn ai dùng không" ⛔ KHÔNG được chịu bộ lọc</h2>
     *
     * <p>{@link #findByApiSourceIdAndDeletedAtIsNullOrderByCodeAsc} đi qua
     * {@code Station.LOC_PHAM_VI}. Dùng nó để trả lời <i>"nguồn này còn ai dùng không"</i> thì câu
     * trả lời phụ thuộc <b>người đang hỏi</b>: người của Xí nghiệp A đếm ra 0 trong khi Xí nghiệp B
     * còn 12 điểm đo trỏ vào nguồn ấy ⇒ {@code HYD-1002} ⛔ không bao giờ bắn, nguồn bị xoá mềm, và
     * <b>12 điểm đo của đơn vị khác mất đường lấy số liệu</b> — ⛔ không lỗi nào, ⛔ không cảnh báo
     * nào, và người gây ra thì ⛔ không nhìn thấy hậu quả vì nó nằm ngoài phạm vi của họ.
     *
     * <p>⚠ Đây đúng họ với luật 13 (§10.35 lỗi 2): <i>một phép tính trộn hai nguồn khác chiều lọc
     * thì kết quả phụ thuộc ai bấm nút</i>. Ràng buộc toàn vẹn ⛔ <b>không</b> phải một câu hỏi về
     * phạm vi — <i>"đối tượng này còn được tham chiếu không"</i> có <b>một</b> câu trả lời đúng cho
     * cả hệ.
     *
     * <p>⭐ {@code nativeQuery} là <b>cơ chế</b> đi vòng: {@code @Filter} của Hibernate chỉ áp cho
     * HQL/Criteria, ⛔ không áp cho SQL thuần. ⛔ Đừng "dọn" thành câu derived cho gọn — làm vậy là
     * khôi phục nguyên vẹn khuyết tật này, và bộ test sẽ ⛔ không đỏ nếu người kiểm chỉ đăng nhập
     * bằng SUPER_ADMIN.
     */
    @Query(
            value = "SELECT count(*) FROM stations WHERE api_source_id = :apiSourceId AND deleted_at IS NULL",
            nativeQuery = true)
    long demMoiPhamViTheoNguon(@Param("apiSourceId") Long apiSourceId);

    /**
     * Số điểm đo <b>toàn hệ thống</b> đang gắn một loại chỉ số — ⛔ ⛔ <b>không</b> lọc phạm vi.
     *
     * <p>Cùng lý lẽ với {@link #demMoiPhamViTheoNguon}, và hậu quả còn im lặng hơn: xoá một loại chỉ
     * số vẫn đang được gắn thì {@code hydro_readings} của loại ấy mồ côi — số liệu <b>vẫn được
     * ghi</b>, chỉ là ⛔ không màn hình nào đọc ra nữa.
     */
    @Query(
            value =
                    """
                    SELECT count(DISTINCT s.id)
                      FROM stations s
                      JOIN station_measurement_types smt ON smt.station_id = s.id
                     WHERE smt.measurement_type_id = :typeId AND s.deleted_at IS NULL
                    """,
            nativeQuery = true)
    long demMoiPhamViTheoLoaiChiSo(@Param("typeId") Long typeId);
}
