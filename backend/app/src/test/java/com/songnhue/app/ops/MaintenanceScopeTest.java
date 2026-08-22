package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.persistence.ScopedEntity;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.operations.application.ConstructionForm;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.application.MaintenanceFilter;
import com.songnhue.operations.application.MaintenanceLogForm;
import com.songnhue.operations.application.MaintenanceLogService;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.IncidentSeverity;
import com.songnhue.operations.domain.MaintenanceLog;
import com.songnhue.operations.domain.MaintenanceState;
import com.songnhue.operations.domain.MaintenanceType;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;

/**
 * Phạm vi đơn vị của lịch sử sửa chữa, và <b>bẫy bàn giao công trình</b> — T18.2 + T18.11.
 *
 * <h2>⭐⭐ Bài kiểm quan trọng nhất ở đây là {@link #statusSurvivesAConstructionHandover()}</h2>
 *
 * T18.2 chốt rằng bản ghi giữ {@code org_unit_id} tại thời điểm <i>phát sinh</i>, không đi theo công
 * trình khi công trình được bàn giao. Đó là điều đúng cho một hồ sơ lịch sử, nhưng nó tạo ra một
 * khoảng thời gian mà <b>bản ghi và công trình thuộc hai đơn vị khác nhau</b>. Nếu phép đếm "còn sự
 * cố nào đang mở không" đi qua bộ lọc phạm vi thì trong khoảng đó nó trả 0, cờ đỏ tắt, và <b>không
 * một dòng lỗi nào</b> — đúng hình dạng lỗi mà dự án đã trả giá nhiều lần.
 */
class MaintenanceScopeTest extends IntegrationTestBase {

    @Autowired
    private ConstructionService constructions;

    @Autowired
    private MaintenanceLogService logs;

    @Autowired
    private JdbcTemplate jdbc;

    private long rootId;
    private String pathRoot;
    private long xnAId;
    private long xnBId;
    private UUID xnAPublicId;
    private UUID xnBPublicId;
    private String pathA;
    private String pathB;

    @BeforeEach
    void setUp() {
        cleanUp();
        pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);

        xnAId = insertUnit("T18-XN-A", "Xí nghiệp A (T18)", rootId, pathRoot);
        xnBId = insertUnit("T18-XN-B", "Xí nghiệp B (T18)", rootId, pathRoot);
        pathA = pathRoot + xnAId + "/";
        pathB = pathRoot + xnBId + "/";
        xnAPublicId = publicIdOf(xnAId);
        xnBPublicId = publicIdOf(xnBId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        cleanUp();
    }

    // === Cấu trúc ============================================================

    @Test
    @DisplayName("⭐ MaintenanceLog thuộc phạm vi đơn vị và có @Filter — canh cấu trúc, không canh văn bản")
    void theEntityIsScopedAndFiltered() {
        assertThat(ScopedEntity.class.isAssignableFrom(MaintenanceLog.class)).isTrue();
        assertThat(MaintenanceLog.class.getAnnotation(org.hibernate.annotations.Filter.class))
                .as("⛔ Thiếu @Filter thì bộ lọc TỒN TẠI nhưng không áp cho entity này — mọi Xí nghiệp "
                        + "đọc được chi phí sửa chữa của nhau, màn hình vẫn đầy đủ, không lỗi nào")
                .isNotNull();
    }

    @Test
    @DisplayName("⛔ Không có setter công khai nào ghi thẳng trạng thái xử lý")
    void thereIsNoPublicWritePathForTheWorkflowState() {
        assertThat(MaintenanceLog.class.getMethods())
                .as("quy tắc 4: đổi trạng thái chỉ qua Workflow engine. applyState là đường duy nhất, "
                        + "và luật ArchUnit canh ai được gọi nó")
                .noneMatch(m -> m.getName().equals("setStatus"));
    }

    @Test
    @DisplayName("⭐ Tập trạng thái trong mã khớp CHECK của CSDL — chỗ phải nhớ hai nơi cần một phép kiểm nhớ hộ")
    void theStateVocabularyMatchesTheDatabaseConstraint() {
        String check = jdbc.queryForObject(
                """
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'ck_maintenance_logs_status'
                """,
                String.class);

        assertThat(check).isNotNull();
        for (String trangThai : MaintenanceState.TAT_CA) {
            assertThat(check)
                    .as("trạng thái '%s' có trong mã Java mà không có trong ràng buộc CSDL", trangThai)
                    .contains("'" + trangThai + "'");
        }
        // Chiều ngược lại: CSDL không được có trạng thái nào mã không biết, nếu không thì
        // MaintenanceState.dangMo() sẽ trả lời sai cho nó mà không ai hay.
        assertThat(check.split("'").length / 2)
                .as("CSDL cho phép nhiều trạng thái hơn mã Java biết tới")
                .isEqualTo(MaintenanceState.TAT_CA.size());
    }

    @Test
    @DisplayName("Hai quy trình xử lý đều được seed và đều có ba trạng thái")
    void bothWorkflowsAreSeeded() {
        for (String loai : List.of(MaintenanceLog.WORKFLOW_WORK, MaintenanceLog.WORKFLOW_INCIDENT)) {
            Integer soBuoc = jdbc.queryForObject(
                    """
                    SELECT count(*) FROM workflow_transitions t
                    JOIN workflow_definitions d ON d.id = t.definition_id
                    WHERE d.entity_type = ?
                    """,
                    Integer.class,
                    loai);
            assertThat(soBuoc).as("quy trình %s chưa được seed", loai).isPositive();
        }

        // ⭐ Khác biệt DUY NHẤT đáng kể giữa hai quy trình — và cũng là lý do có hai quy trình.
        String quyenDongSuCo = jdbc.queryForObject(
                """
                SELECT t.required_permission FROM workflow_transitions t
                JOIN workflow_definitions d ON d.id = t.definition_id
                WHERE d.entity_type = ? AND t.from_state = 'DANG_XU_LY' AND t.to_state = 'DA_XU_LY'
                """,
                String.class,
                MaintenanceLog.WORKFLOW_INCIDENT);
        assertThat(quyenDongSuCo)
                .as("ma trận §6: 'Đóng bản ghi sự cố' — Kỹ thuật ✘. Không có dòng này thì quyền "
                        + "ops:maintenance:close-incident là một quyền chưa ai đọc")
                .isEqualTo("ops:maintenance:close-incident");
    }

    // === Phạm vi đơn vị ======================================================

    @Test
    @DisplayName("Người của Xí nghiệp A không thấy bản ghi của B; cấp Công ty thấy cả hai")
    void logsAreScopedToTheOwningUnit() {
        heThong(() -> {
            ghiBaoTri(taoCongTrinh("T18-A-001", xnAPublicId), "Bảo trì của A");
            ghiBaoTri(taoCongTrinh("T18-B-001", xnBPublicId), "Bảo trì của B");
        });

        AuthContext.set(nguoiDungTai(xnAId, pathA));
        assertThat(noiDungBanGhi()).containsExactly("Bảo trì của A");

        AuthContext.set(nguoiDungTai(xnBId, pathB));
        assertThat(noiDungBanGhi()).containsExactly("Bảo trì của B");

        AuthContext.set(nguoiDungTai(rootId, pathRoot));
        assertThat(noiDungBanGhi()).containsExactlyInAnyOrder("Bảo trì của A", "Bảo trì của B");
    }

    @Test
    @DisplayName("⚠ Hỏi bản ghi của đơn vị khác → AUTH-3002, không phải 404 im lặng")
    void outOfScopeLookupIsForbidden() {
        UUID cuaB = heThongTraVe(() -> ghiBaoTri(taoCongTrinh("T18-B-002", xnBPublicId), "Chi phí của B"));

        AuthContext.set(nguoiDungTai(xnAId, pathA));

        assertThatThrownBy(() -> logs.get(cuaB)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("⚠ Tổng chi phí cũng bị lọc — con số của A không gồm chi phí của B")
    void costSummaryIsScopedToo() {
        heThong(() -> {
            ghiBaoTriCoChiPhi(taoCongTrinh("T18-A-010", xnAPublicId), "A tiêu", new BigDecimal("1000000.00"));
            ghiBaoTriCoChiPhi(taoCongTrinh("T18-B-010", xnBPublicId), "B tiêu", new BigDecimal("9000000.00"));
        });

        AuthContext.set(nguoiDungTai(xnAId, pathA));
        assertThat(logs.costSummary(locRong()).total())
                .as("một bảng quyết toán gộp nhầm chi phí của Xí nghiệp khác là sai số liệu, không phải "
                        + "rò rỉ thông tin — và nó đi thẳng vào BC-09")
                .isEqualByComparingTo("1000000.00");
    }

    // === ⭐⭐ Bẫy bàn giao công trình =========================================

    @Test
    @DisplayName("⭐⭐ Bàn giao công trình sang Xí nghiệp khác — cờ sự cố VẪN đúng, không âm thầm tắt")
    void statusSurvivesAConstructionHandover() {
        UUID congTrinh =
                heThongTraVe(() -> taoCongTrinh("T18-A-020", xnAPublicId).getPublicId());
        heThong(() -> ghiSuCo(heThongTraVe(() -> constructions.get(congTrinh)), "Sự cố phát sinh khi còn thuộc A"));

        assertThat(heThongTraVe(() -> constructions.get(congTrinh).getOperationalStatus()))
                .isEqualTo(OperationalStatus.SU_CO);

        // Bàn giao: hồ sơ công trình sang B, còn BẢN GHI vẫn mang đơn vị A (T18.2).
        heThong(() -> constructions.update(congTrinh, hoSo("T18-A-020", "Trạm bơm bàn giao", xnBPublicId)));

        Long donViBanGhi = jdbc.queryForObject(
                "SELECT org_unit_id FROM maintenance_logs WHERE content = ?",
                Long.class,
                "Sự cố phát sinh khi còn thuộc A");
        assertThat(donViBanGhi)
                .as("T18.2 — hồ sơ lịch sử giữ đơn vị lúc phát sinh; chi phí năm ngoái thuộc về Xí "
                        + "nghiệp đã bỏ tiền ra")
                .isEqualTo(xnAId);

        // ⛔ Đây là chỗ vỡ nếu phép đếm đi qua bộ lọc phạm vi: người của B không "thấy" bản ghi của
        //    A, nên lượt tính lại tiếp theo sẽ kết luận công trình không còn sự cố nào.
        AuthContext.set(nguoiDungTai(xnBId, pathB));
        assertThat(constructions.get(congTrinh).getOperationalStatus())
                .as("trạng thái là sự thật về CÔNG TRÌNH, không phải về người đang nhìn")
                .isEqualTo(OperationalStatus.SU_CO);

        // Và một lượt ghi bất kỳ của người B cũng không được làm cờ đỏ tắt.
        heThong(() -> constructions.update(congTrinh, hoSo("T18-A-020", "Trạm bơm bàn giao lần 2", xnBPublicId)));
        assertThat(heThongTraVe(() -> constructions.get(congTrinh).getOperationalStatus()))
                .isEqualTo(OperationalStatus.SU_CO);
    }

    // -------------------------------------------------------------------------

    private List<String> noiDungBanGhi() {
        return logs.search(locRong(), PageRequest.of(0, 50)).stream()
                .map(MaintenanceLog::getContent)
                .filter(noiDung -> noiDung.contains("của A") || noiDung.contains("của B"))
                .sorted()
                .toList();
    }

    private static MaintenanceFilter locRong() {
        return new MaintenanceFilter(null, null, null, null, null, null, null, null);
    }

    private Construction taoCongTrinh(String ma, UUID donVi) {
        return constructions.create(hoSo(ma, "Công trình " + ma, donVi));
    }

    private UUID ghiBaoTri(Construction ct, String noiDung) {
        return logs.create(banGhi(ct, MaintenanceType.BAO_TRI_DINH_KY, null, noiDung, null))
                .getPublicId();
    }

    private void ghiBaoTriCoChiPhi(Construction ct, String noiDung, BigDecimal chiPhi) {
        logs.create(banGhi(ct, MaintenanceType.BAO_TRI_DINH_KY, null, noiDung, chiPhi));
    }

    private void ghiSuCo(Construction ct, String noiDung) {
        logs.create(banGhi(ct, MaintenanceType.KHAC_PHUC_SU_CO, IncidentSeverity.CAO, noiDung, null));
    }

    private MaintenanceLogForm banGhi(
            Construction ct, MaintenanceType loai, IncidentSeverity mucDo, String noiDung, BigDecimal chiPhi) {
        return new MaintenanceLogForm(
                ct.getPublicId(),
                loai,
                mucDo,
                null,
                LocalDate.of(2026, 8, 1),
                null,
                noiDung,
                null,
                null,
                "Tổ kỹ thuật",
                chiPhi,
                null,
                null,
                null,
                null,
                null);
    }

    private static ConstructionForm hoSo(String ma, String ten, UUID donViPublicId) {
        return new ConstructionForm(
                ma,
                ten,
                ConstructionType.TRAM_BOM,
                null,
                donViPublicId,
                ManagementLevel.XI_NGHIEP,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * Không có người đăng nhập → bộ lọc không bật.
     *
     * <p>⚠ Người phụ trách bản ghi lấy từ {@code AuthContext}, nên ở chế độ "hệ thống" nó rỗng và
     * {@code MaintenanceLogService} sẽ từ chối. Vì vậy các hàm dựng dữ liệu bên dưới đặt một người
     * dùng cấp Công ty thay vì xoá sạch ngữ cảnh.
     */
    private void heThong(Runnable hanhDong) {
        AuthContext.set(nguoiDungTai(rootId, pathRoot));
        hanhDong.run();
    }

    private <T> T heThongTraVe(java.util.function.Supplier<T> hanhDong) {
        AuthContext.set(nguoiDungTai(rootId, pathRoot));
        return hanhDong.get();
    }

    private AuthenticatedUser nguoiDungTai(long orgUnitId, String orgUnitPath) {
        return new AuthenticatedUser(
                nguoiDungKiemThuId(),
                UUID.randomUUID(),
                "t18-probe",
                "Người kiểm thử WS-18",
                orgUnitId,
                orgUnitPath,
                Set.of("XN_MANAGER"),
                Set.of("ops:maintenance:update"),
                false,
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    /** Khoá ngoại {@code assignee_user_id} là NOT NULL — mượn một tài khoản seed có sẵn. */
    private Long nguoiDungKiemThuId() {
        return jdbc.queryForObject("SELECT id FROM users ORDER BY id LIMIT 1", Long.class);
    }

    private UUID publicIdOf(long orgUnitId) {
        return jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, orgUnitId);
    }

    private long insertUnit(String code, String name, Long parentId, String parentPath) {
        Long id = jdbc.queryForObject(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/0/', 0, 0, now()) RETURNING id",
                Long.class,
                code,
                name,
                parentId);
        String path = parentPath + id + "/";
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", path, path.split("/").length - 1, id);
        return id;
    }

    private void cleanUp() {
        jdbc.update(
                """
                DELETE FROM maintenance_logs WHERE construction_id IN
                    (SELECT id FROM constructions WHERE code LIKE 'T18-%')
                """);
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T18-%'");
        jdbc.update("DELETE FROM org_units WHERE code LIKE 'T18-%'");
    }
}
