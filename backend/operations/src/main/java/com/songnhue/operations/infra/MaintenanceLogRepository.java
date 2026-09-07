package com.songnhue.operations.infra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.songnhue.operations.domain.IncidentSeverity;
import com.songnhue.operations.domain.MaintenanceLog;
import com.songnhue.operations.domain.MaintenanceType;

/**
 * Truy vấn lịch sử sửa chữa — CN-02.2.
 *
 * <h2>⚠ Hai nhóm câu, hai luật phạm vi khác nhau — đọc kỹ trước khi thêm câu mới</h2>
 *
 * <ul>
 *   <li><b>Câu HQL</b> ({@code search}, {@code sumCost}, {@code openIncidents}…) — {@link
 *       MaintenanceLog} kế thừa {@code ScopedEntity} nên {@code ScopeFilterAspect} tự áp bộ lọc
 *       theo Xí nghiệp. Đúng như vậy: người của Xí nghiệp A không được đọc chi phí sửa chữa của Xí
 *       nghiệp B.
 *   <li><b>Câu native</b> ({@code demBanGhiDangMo}) — bộ lọc Hibernate <b>không</b> áp cho native
 *       query, và ở đó điều đó là <b>yêu cầu</b>, không phải tác dụng phụ. Xem chú thích riêng bên
 *       dưới.
 * </ul>
 */
public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, Long> {

    Optional<MaintenanceLog> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /**
     * ⭐⭐ Đếm bản ghi đang mở của một công trình — <b>đầu vào của chuỗi suy ra trạng thái</b>.
     *
     * <h2>Vì sao câu này BẮT BUỘC là native</h2>
     *
     * Trạng thái công trình là một sự thật về <i>công trình</i>, không phải một sự thật về
     * <i>người đang nhìn</i>. Nếu câu này đi qua bộ lọc phạm vi thì hai người ở hai Xí nghiệp mở
     * cùng một hồ sơ sẽ tính ra hai trạng thái khác nhau, và người tính sau <b>ghi đè</b> giá trị
     * của người tính trước xuống cột {@code operational_status} — cột đó rồi sẽ mang giá trị của
     * lượt tính gần nhất, tức là của người mở màn hình gần nhất.
     *
     * <p>Tình huống này không phải giả định: T18.2 chốt rằng bản ghi giữ {@code org_unit_id} lúc
     * phát sinh, nên ngay sau một lượt bàn giao công trình sang Xí nghiệp khác, các bản ghi cũ đã
     * nằm ngoài phạm vi của chính người đang phụ trách công trình đó. Sự cố đang mở sẽ biến mất khỏi
     * phép đếm và cờ đỏ tắt — không một dòng lỗi nào.
     *
     * <p>⚠ Đây là ngoại lệ có chủ đích thứ hai trong module (cùng loại với
     * {@code ConstructionRepository.codesStartingWith}), và nó chỉ trả về <b>một con số đếm</b> của
     * đúng một công trình mà người gọi vừa được kiểm quyền — không phải một đường đọc dữ liệu vòng
     * qua phạm vi.
     *
     * @param laSuCo {@code true} đếm bản ghi khắc phục sự cố, {@code false} đếm các loại còn lại
     */
    @Query(
            value =
                    """
                    SELECT count(*) FROM maintenance_logs
                    WHERE construction_id = :congTrinhId
                      AND deleted_at IS NULL
                      AND status IN ('MOI', 'DANG_XU_LY')
                      AND (work_type = 'KHAC_PHUC_SU_CO') = :laSuCo
                    """,
            nativeQuery = true)
    long demBanGhiDangMo(@Param("congTrinhId") Long congTrinhId, @Param("laSuCo") boolean laSuCo);

    /**
     * Timeline + danh sách quản trị (T18.7).
     *
     * <p>Mọi tham số nhận {@code null} = không lọc theo tiêu chí đó, cùng lý do với
     * {@code ConstructionRepository.search}: một câu chịu được mọi tổ hợp thay vì dựng câu động.
     *
     * <p>⚠ {@code CAST(:tuKhoa AS String)} — Hibernate không suy được kiểu tham số khi so sánh với
     * {@code NULL} trần trong câu HQL có {@code LIKE}; bỏ ép kiểu thì lượt mở màn hình đầu tiên
     * (chưa gõ từ khoá) hỏng. Bẫy này đã trả giá ở WS-13 và WS-17.
     */
    // CHECKSTYLE.OFF: ParameterNumber - mỗi tham số là một ô lọc của CN-02.2; nơi gọi luôn đi qua
    // MaintenanceFilter nên phía ngoài vẫn là một tham số.
    @Query(
            """
            SELECT m FROM MaintenanceLog m
            WHERE m.deletedAt IS NULL
              AND (:congTrinhId IS NULL OR m.constructionId = :congTrinhId)
              AND (:loai IS NULL OR m.workType = :loai)
              AND (:mucDo IS NULL OR m.severity = :mucDo)
              AND (CAST(:trangThai AS String) IS NULL OR m.status = :trangThai)
              AND (:donViThucHienId IS NULL OR m.performerOrgUnitId = :donViThucHienId)
              AND (CAST(:tuNgay AS LocalDate) IS NULL OR m.startedOn >= :tuNgay)
              AND (CAST(:denNgay AS LocalDate) IS NULL OR m.startedOn <= :denNgay)
              AND (CAST(:tuKhoa AS String) IS NULL
                   OR sn_khong_dau(m.content) LIKE sn_khong_dau(CAST(:tuKhoa AS String))
                   OR sn_khong_dau(m.code) LIKE sn_khong_dau(CAST(:tuKhoa AS String)))
            """)
    Page<MaintenanceLog> search(
            @Param("congTrinhId") Long congTrinhId,
            @Param("loai") MaintenanceType loai,
            @Param("mucDo") IncidentSeverity mucDo,
            @Param("trangThai") String trangThai,
            @Param("donViThucHienId") Long donViThucHienId,
            @Param("tuNgay") LocalDate tuNgay,
            @Param("denNgay") LocalDate denNgay,
            @Param("tuKhoa") String tuKhoa,
            Pageable pageable);
    // CHECKSTYLE.ON: ParameterNumber

    /**
     * Tổng chi phí theo kỳ — <b>tính ở CSDL</b> (quy tắc 3).
     *
     * <p>⛔ Không tải danh sách về rồi cộng trong Java, và tuyệt đối không để FE cộng. Chi phí là
     * {@code NUMERIC} và {@code SUM} của PostgreSQL giữ nguyên độ chính xác thập phân; cộng ở tầng
     * hiển thị là mở đường cho {@code Number} của JavaScript, nơi 0.1 + 0.2 không bằng 0.3.
     *
     * <p>Trả {@code null} khi không có bản ghi nào — nơi gọi phải phân biệt "chưa có bản ghi" với
     * "có bản ghi nhưng chưa ai điền chi phí". Cả hai đều khác 0 đồng.
     */
    // CHECKSTYLE.OFF: ParameterNumber
    @Query(
            """
            SELECT sum(m.cost) FROM MaintenanceLog m
            WHERE m.deletedAt IS NULL
              AND (:congTrinhId IS NULL OR m.constructionId = :congTrinhId)
              AND (:loai IS NULL OR m.workType = :loai)
              AND (CAST(:tuNgay AS LocalDate) IS NULL OR m.startedOn >= :tuNgay)
              AND (CAST(:denNgay AS LocalDate) IS NULL OR m.startedOn <= :denNgay)
            """)
    BigDecimal sumCost(
            @Param("congTrinhId") Long congTrinhId,
            @Param("loai") MaintenanceType loai,
            @Param("tuNgay") LocalDate tuNgay,
            @Param("denNgay") LocalDate denNgay);

    @Query(
            """
            SELECT count(m) FROM MaintenanceLog m
            WHERE m.deletedAt IS NULL
              AND (:congTrinhId IS NULL OR m.constructionId = :congTrinhId)
              AND (:loai IS NULL OR m.workType = :loai)
              AND (CAST(:tuNgay AS LocalDate) IS NULL OR m.startedOn >= :tuNgay)
              AND (CAST(:denNgay AS LocalDate) IS NULL OR m.startedOn <= :denNgay)
            """)
    long countInPeriod(
            @Param("congTrinhId") Long congTrinhId,
            @Param("loai") MaintenanceType loai,
            @Param("tuNgay") LocalDate tuNgay,
            @Param("denNgay") LocalDate denNgay);
    // CHECKSTYLE.ON: ParameterNumber

    /**
     * "Sự cố chưa xử lý" (T18.8) — nguồn của ô KPI cùng tên trên dashboard điều hành.
     *
     * <p>Đã lọc theo phạm vi đơn vị, và đó là hành vi đúng ở đây: đây là <i>danh sách việc phải
     * làm</i> của người đang đăng nhập, khác với phép đếm dùng để suy ra trạng thái công trình.
     */
    @Query(
            """
            SELECT m FROM MaintenanceLog m
            WHERE m.deletedAt IS NULL
              AND m.workType = com.songnhue.operations.domain.MaintenanceType.KHAC_PHUC_SU_CO
              AND m.status IN ('MOI', 'DANG_XU_LY')
            ORDER BY m.severity ASC, m.startedOn ASC
            """)
    List<MaintenanceLog> openIncidents(Pageable pageable);

    /** Số sự cố chưa xử lý — ô KPI {@code incident.open}. */
    @Query(
            """
            SELECT count(m) FROM MaintenanceLog m
            WHERE m.deletedAt IS NULL
              AND m.workType = com.songnhue.operations.domain.MaintenanceType.KHAC_PHUC_SU_CO
              AND m.status IN ('MOI', 'DANG_XU_LY')
            """)
    long countOpenIncidents();

    /** Số công việc bảo trì đang thực hiện — ô KPI {@code maintenance.in-progress}. */
    @Query(
            """
            SELECT count(m) FROM MaintenanceLog m
            WHERE m.deletedAt IS NULL
              AND m.workType <> com.songnhue.operations.domain.MaintenanceType.KHAC_PHUC_SU_CO
              AND m.status IN ('MOI', 'DANG_XU_LY')
            """)
    long countOpenWork();
}
