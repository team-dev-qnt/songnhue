package com.songnhue.operations.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;

/**
 * Truy vấn công trình — CN-02.1, CN-02.6.
 *
 * <h2>⚠ Không câu nào ở đây tự thêm điều kiện phạm vi đơn vị</h2>
 *
 * Đó là chủ ý, không phải thiếu sót. {@link Construction} kế thừa {@code ScopedEntity} nên
 * {@code ScopeFilterAspect} bật bộ lọc Hibernate cho <i>toàn bộ</i> truy vấn trong transaction — kể
 * cả những câu viết sau này và những câu người khác viết. Tự thêm {@code WHERE org_unit_id = ?} ở
 * từng câu là quay lại đúng cách làm mà quy tắc 5 cấm: đúng cho tới lúc có người quên một chỗ, và
 * chỗ quên đó không có triệu chứng nào.
 */
public interface ConstructionRepository extends JpaRepository<Construction, Long> {

    Optional<Construction> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /** Tra theo mã — dùng ở đường nhập hàng loạt để biết dòng nào là thêm mới, dòng nào là cập nhật. */
    Optional<Construction> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    /** Kiểm trùng mã khi SỬA — bỏ qua chính bản ghi đang sửa, nếu không nó tự trùng với nó. */
    boolean existsByCodeAndDeletedAtIsNullAndIdNot(String code, Long id);

    /**
     * Tìm kiếm và lọc trên danh sách quản trị (T17.10).
     *
     * <p>Mọi tham số nhận {@code null} = không lọc theo tiêu chí đó. Viết một câu chịu được mọi tổ
     * hợp thay vì dựng câu động — câu động là nơi lỗ SQL injection hay chui vào, và cũng là nơi một
     * bộ lọc bị bỏ sót mà không ai thấy.
     *
     * <p>⚠⚠ {@code CAST(:tuKhoa AS String)} <b>không phải trang trí</b>. {@code sn_khong_dau} là hàm
     * dự án tự khai nên Hibernate không suy được kiểu tham số; truyền {@code null} trần thì nó gửi
     * xuống dạng {@code bytea} và PostgreSQL trả *"function sn_khong_dau(bytea) does not exist"* —
     * nghĩa là <b>mọi lượt mở danh sách khi chưa gõ từ khoá đều hỏng</b>. Lỗi này đã trả giá một lần
     * ở WS-13; ở đây có bài kiểm gọi search với toàn bộ bộ lọc để trống, đúng đường mà giao diện đi
     * khi mở màn hình lần đầu.
     *
     * <p>Tìm cả theo <b>mã</b> lẫn <b>tên</b>: người dùng nội bộ gọi công trình bằng mã nhiều hơn
     * bằng tên đầy đủ.
     */
    // CHECKSTYLE.OFF: ParameterNumber - gộp bộ lọc vào một record thì Spring Data không dịch được
    // sang tham số câu truy vấn, còn dựng câu động là mở đúng cánh cửa mà bản viết tay này tránh.
    // Nơi gọi luôn truyền qua ConstructionFilter nên phía ngoài vẫn là một tham số.
    @Query(
            """
            SELECT c FROM Construction c
            WHERE c.deletedAt IS NULL
              AND (CAST(:tuKhoa AS String) IS NULL
                   OR sn_khong_dau(c.name) LIKE sn_khong_dau(CAST(:tuKhoa AS String))
                   OR sn_khong_dau(c.code) LIKE sn_khong_dau(CAST(:tuKhoa AS String)))
              AND (:loai IS NULL OR c.constructionType = :loai)
              AND (:trangThai IS NULL OR c.operationalStatus = :trangThai)
              AND (:vongDoi IS NULL OR c.lifecycleState = :vongDoi)
              AND (:capQuanLy IS NULL OR c.managementLevel = :capQuanLy)
              AND (:donViId IS NULL OR c.orgUnitId = :donViId)
              AND (:cumId IS NULL OR c.clusterId = :cumId)
              AND (CAST(:tuyenSong AS String) IS NULL OR c.riverName = :tuyenSong)
              AND (:chuaSoHoa = FALSE OR c.latitude IS NULL)
            """)
    Page<Construction> search(
            @Param("tuKhoa") String tuKhoa,
            @Param("loai") ConstructionType loai,
            @Param("trangThai") OperationalStatus trangThai,
            @Param("vongDoi") LifecycleState vongDoi,
            @Param("capQuanLy") ManagementLevel capQuanLy,
            @Param("donViId") Long donViId,
            @Param("cumId") Long cumId,
            @Param("tuyenSong") String tuyenSong,
            @Param("chuaSoHoa") boolean chuaSoHoa,
            Pageable pageable);
    // CHECKSTYLE.ON: ParameterNumber

    /**
     * Điểm để vẽ lên bản đồ GIS (CN-02.4 / M2.9).
     *
     * <p>Chỉ trả công trình <b>đã số hoá toạ độ</b>. Công trình chưa có vị trí không được vẽ ở toạ độ
     * 0,0 và cũng không được bỏ qua lặng lẽ — nó nằm ở danh sách nhắc việc riêng ({@code search} với
     * {@code chuaSoHoa = true}), vì một hồ sơ thiếu vị trí mà không ai nhắc thì sẽ thiếu mãi.
     */
    @Query(
            """
            SELECT c FROM Construction c
            WHERE c.deletedAt IS NULL AND c.latitude IS NOT NULL AND c.longitude IS NOT NULL
              AND (:loai IS NULL OR c.constructionType = :loai)
            ORDER BY c.name
            """)
    List<Construction> mapPoints(@Param("loai") ConstructionType loai);

    // === Thống kê (CN-02.6) ==================================================
    // Đếm ở CSDL chứ không tải danh sách rồi đếm trong Java: quy tắc 3 — mọi giá
    // trị tính toán tính ở BE, và ở đây "BE" nghĩa là câu GROUP BY, không phải
    // một vòng lặp trên tập đã tải về.

    @Query("SELECT c.constructionType, COUNT(c) FROM Construction c WHERE c.deletedAt IS NULL"
            + " GROUP BY c.constructionType")
    List<Object[]> countByType();

    @Query("SELECT c.operationalStatus, COUNT(c) FROM Construction c WHERE c.deletedAt IS NULL"
            + " GROUP BY c.operationalStatus")
    List<Object[]> countByStatus();

    @Query("SELECT c.orgUnitId, COUNT(c) FROM Construction c WHERE c.deletedAt IS NULL GROUP BY c.orgUnitId")
    List<Object[]> countByOrgUnit();

    /**
     * Đếm theo vòng đời — nguồn của KPI "đang hoạt động / tổng số" trên dashboard (CN-02.5).
     *
     * <p>⚠ Không dùng {@code countByStatus()} thay cho việc này. Trạng thái vận hành là giá trị
     * <b>dẫn xuất</b> và sẽ có thêm bốn nguồn nữa từ WS-18 và Phase 2 (sự cố, bảo trì, ngưỡng thuỷ
     * văn, mã tình hình vận hành); còn "đang hoạt động" là một sự thật hành chính do con người
     * quyết định. Đếm nhầm cột thì con số vẫn đúng hôm nay và sai lặng lẽ vào ngày bản ghi sự cố
     * đầu tiên được nhập — công trình đang có sự cố vẫn là công trình đang hoạt động.
     */
    @Query("SELECT c.lifecycleState, COUNT(c) FROM Construction c WHERE c.deletedAt IS NULL"
            + " GROUP BY c.lifecycleState")
    List<Object[]> countByLifecycle();

    @Query("SELECT c.managementLevel, COUNT(c) FROM Construction c WHERE c.deletedAt IS NULL"
            + " GROUP BY c.managementLevel")
    List<Object[]> countByManagementLevel();

    long countByDeletedAtIsNull();

    /** Số hồ sơ chưa có vị trí GIS — con số luôn đi kèm bản đồ (M2.8). */
    long countByLatitudeIsNullAndDeletedAtIsNull();

    /** Số công trình đang gắn vào một cụm — dùng để chặn xoá cụm còn dữ liệu. */
    long countByClusterIdAndDeletedAtIsNull(Long clusterId);

    /**
     * Mã đang dùng theo một tiền tố — chỉ để gợi ý số tiếp theo (T17.3).
     *
     * <p>⚠ <b>Câu native, và đó là chủ ý</b>: {@code @Filter} của Hibernate không áp cho native
     * query, nên câu này nhìn thấy mã của <i>mọi</i> đơn vị. Đúng như vậy mới được — mã công trình
     * duy nhất trên toàn hệ thống, mà gợi ý dựa trên tập bị lọc theo đơn vị sẽ đề xuất một mã đã có
     * người khác dùng, và người nhập chỉ biết khi bấm Lưu và nhận {@code OPS-2008}.
     *
     * <p>Chỉ trả về <b>chuỗi mã</b>, không phải bản ghi. Mã không phải dữ liệu nhạy cảm, và tiền tố
     * do chính người gọi dựng từ đơn vị họ chọn — nên đây không phải một đường vòng qua phạm vi.
     */
    @Query(
            value = "SELECT code FROM constructions WHERE deleted_at IS NULL AND code LIKE :tienTo || '%'",
            nativeQuery = true)
    List<String> codesStartingWith(@Param("tienTo") String tienTo);

    /** Tuyến sông đang có công trình — nguồn cho ô lọc, thay vì bắt người dùng gõ tay. */
    @Query("SELECT DISTINCT c.riverName FROM Construction c WHERE c.deletedAt IS NULL"
            + " AND c.riverName IS NOT NULL ORDER BY c.riverName")
    List<String> distinctRivers();
}
