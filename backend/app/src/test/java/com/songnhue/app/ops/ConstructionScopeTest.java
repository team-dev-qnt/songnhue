package com.songnhue.app.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.persistence.ScopedEntity;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.operations.application.ConstructionFilter;
import com.songnhue.operations.application.ConstructionForm;
import com.songnhue.operations.application.ConstructionService;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;

/**
 * ⭐⭐ <b>Tầng 3 phân quyền chạy trên entity nghiệp vụ THẬT</b> — T17.2 + T17.12, trả nợ #57 (T12.9).
 *
 * <h2>Vì sao cần bài kiểm này khi đã có {@code ScopeFilterEndToEndTest}</h2>
 *
 * Bài kiểm ở WS-10 chạy trên {@code ScopedRecord} — một entity <b>dựng riêng cho bài kiểm</b>, với
 * bảng chỉ tồn tại trong {@code src/test}. Nó chứng minh cơ chế đúng, nhưng không chứng minh rằng
 * <i>bảng nghiệp vụ đầu tiên</i> khai đúng: {@code @Filter} có thể bị quên, điều kiện lọc có thể bị
 * chép sai, và cả hai lỗi đó <b>không có triệu chứng nào</b> — màn hình vẫn đầy dữ liệu, chỉ là đầy
 * cả dữ liệu của Xí nghiệp khác.
 *
 * <p>Suốt Phase 0, log lúc khởi động của {@code ScopeFilterAspect} in <i>"Chưa có entity nào thuộc
 * phạm vi đơn vị — bỏ qua lọc tầng 3"</i>. Từ {@link Construction} trở đi câu đó phải biến mất trong
 * ứng dụng thật, và {@link #productionHasAtLeastOneScopedEntity()} là thứ giữ điều đó.
 *
 * <h2>Ba nhánh bắt buộc (kiểm chứng của WS-17)</h2>
 *
 * <ol>
 *   <li>Người của Xí nghiệp A chỉ thấy công trình của A.
 *   <li>Người cấp Công ty thấy cả hai — lọc theo <i>materialized path</i> chứ không so
 *       {@code org_unit_id}. Thiếu nhánh này thì một bộ lọc hỏng kiểu {@code WHERE 1=0} vẫn xanh.
 *   <li>Hỏi hồ sơ của đơn vị khác → {@code AUTH-3002} <b>và</b> một dòng {@code security_events},
 *       không phải 404 im lặng.
 * </ol>
 */
class ConstructionScopeTest extends IntegrationTestBase {

    @Autowired
    private ConstructionService constructions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long xnAId;
    private long xnBId;
    private long toDoiA1Id;
    private UUID xnAPublicId;
    private UUID xnBPublicId;
    private UUID toDoiA1PublicId;
    private String pathA;
    private String pathB;
    private String pathRoot;
    private long rootId;

    @BeforeEach
    void setUp() {
        cleanUp();
        pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);

        xnAId = insertUnit("T17-XN-A", "Xí nghiệp A (T17)", rootId, pathRoot);
        xnBId = insertUnit("T17-XN-B", "Xí nghiệp B (T17)", rootId, pathRoot);
        pathA = pathRoot + xnAId + "/";
        pathB = pathRoot + xnBId + "/";
        toDoiA1Id = insertUnit("T17-TO-A1", "Tổ đội A1 (T17)", xnAId, pathA);

        xnAPublicId = publicIdOf(xnAId);
        xnBPublicId = publicIdOf(xnBId);
        toDoiA1PublicId = publicIdOf(toDoiA1Id);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        cleanUp();
    }

    @Test
    @DisplayName("⭐ Bộ lọc phạm vi ĐÃ được đăng ký — có entity nghiệp vụ kế thừa ScopedEntity")
    void scopeFilterIsRegisteredNow() {
        Set<String> filters = entityManagerFactory.unwrap(SessionFactory.class).getDefinedFilterNames();

        assertThat(filters)
                .as("Hibernate chỉ xử lý @FilterDef trên MappedSuperclass khi có entity kế thừa nó. "
                        + "Bộ lọc vắng mặt nghĩa là tầng 3 không chạy, và không lỗi nào báo ra")
                .contains(ScopedEntity.ORG_UNIT_FILTER);
    }

    @Test
    @DisplayName("⭐ Có ít nhất một entity PRODUCTION thuộc phạm vi đơn vị — không tính lớp của test")
    void productionHasAtLeastOneScopedEntity() {
        // Nếu chỉ có `ScopedRecord` của src/test kế thừa ScopedEntity thì bài kiểm trên vẫn xanh
        // trong khi ứng dụng thật chạy KHÔNG có lọc tầng 3. Đó đúng là trạng thái suốt Phase 0.
        assertThat(ScopedEntity.class.isAssignableFrom(Construction.class))
                .as("Construction là entity nghiệp vụ đầu tiên thuộc phạm vi đơn vị")
                .isTrue();
        assertThat(Construction.class.getAnnotation(org.hibernate.annotations.Filter.class))
                .as("⛔ Thiếu @Filter thì bộ lọc TỒN TẠI nhưng không áp cho entity này — "
                        + "mọi Xí nghiệp đọc được dữ liệu của nhau, không một dòng lỗi nào")
                .isNotNull();
    }

    @Test
    @DisplayName("Nhánh 1 — người của Xí nghiệp A chỉ thấy công trình của A")
    void listIsScopedToOwnUnit() {
        heThong(() -> {
            constructions.create(hoSo("T17-A-001", "Trạm bơm A", xnAPublicId));
            constructions.create(hoSo("T17-B-001", "Trạm bơm B", xnBPublicId));
        });

        AuthContext.set(nguoiDungTai(xnAId, pathA));
        assertThat(maCongTrinh()).containsExactly("T17-A-001");

        AuthContext.set(nguoiDungTai(xnBId, pathB));
        assertThat(maCongTrinh()).containsExactly("T17-B-001");
    }

    @Test
    @DisplayName("Nhánh 2 — cấp trên thấy cấp dưới; cấp Công ty thấy cả hai Xí nghiệp")
    void parentUnitSeesChildData() {
        heThong(() -> {
            constructions.create(hoSo("T17-A1-001", "Cống tổ đội A1", toDoiA1PublicId));
            constructions.create(hoSo("T17-B-002", "Cống B", xnBPublicId));
        });

        // Trưởng Xí nghiệp A thấy hồ sơ của Tổ đội A1 trực thuộc — nếu lọc bằng org_unit_id thì không.
        AuthContext.set(nguoiDungTai(xnAId, pathA));
        assertThat(maCongTrinh()).containsExactly("T17-A1-001");

        AuthContext.set(nguoiDungTai(rootId, pathRoot));
        assertThat(maCongTrinh()).containsExactlyInAnyOrder("T17-A1-001", "T17-B-002");
    }

    @Test
    @DisplayName("⚠ Nhánh 3 — hỏi hồ sơ của đơn vị khác → AUTH-3002 kèm dấu vết, không phải 404")
    void outOfScopeLookupIsForbiddenAndRecorded() {
        UUID cuaB = heThongTraVe(() -> constructions
                .create(hoSo("T17-B-003", "Trạm bơm mật của B", xnBPublicId))
                .getPublicId());

        AuthContext.set(nguoiDungTai(xnAId, pathA));

        assertThatThrownBy(() -> constructions.get(cuaB))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(e -> ((PermissionDeniedException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_3002);

        Integer suKien = jdbc.queryForObject(
                "SELECT count(*) FROM security_events WHERE event_type = 'ACCESS_DENIED_SCOPE' "
                        + "AND detail::text LIKE ?",
                Integer.class,
                "%" + cuaB + "%");
        assertThat(suKien)
                .as("người dò public_id để tìm hồ sơ đơn vị khác trông y hệt người gõ nhầm đường dẫn")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Định danh không tồn tại vẫn là 404 — không biến mọi thứ thành 403")
    void unknownIdStaysNotFound() {
        AuthContext.set(nguoiDungTai(xnAId, pathA));
        assertThatThrownBy(() -> constructions.get(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("⚠ Sửa hồ sơ ngoài phạm vi cũng bị chặn — không chỉ đường ĐỌC được bảo vệ")
    void updateOutOfScopeIsBlocked() {
        UUID cuaB = heThongTraVe(() -> constructions
                .create(hoSo("T17-B-004", "Cống của B", xnBPublicId))
                .getPublicId());

        AuthContext.set(nguoiDungTai(xnAId, pathA));

        // Kiểm cả đường GHI vì bộ lọc và ScopeGuard là hai cơ chế khác nhau: bộ lọc làm bản ghi
        // "không có trong kết quả", còn chuyện hàm ghi có tra qua ScopeGuard hay không là lựa chọn
        // của người viết service. Quên một chỗ là sửa được hồ sơ của đơn vị khác.
        assertThatThrownBy(() -> constructions.update(cuaB, hoSo("T17-B-004", "Bị chiếm", xnAPublicId)))
                .isInstanceOf(PermissionDeniedException.class);

        assertThatThrownBy(() -> constructions.changeLifecycle(cuaB, LifecycleState.DA_THANH_LY, "thử"))
                .isInstanceOf(PermissionDeniedException.class);

        assertThatThrownBy(() -> constructions.delete(cuaB)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("⚠ Thống kê cũng bị lọc theo phạm vi — con số của A không gồm công trình của B")
    void statisticsAreScopedToo() {
        heThong(() -> {
            constructions.create(hoSo("T17-A-010", "Trạm bơm A10", xnAPublicId));
            constructions.create(hoSo("T17-B-010", "Trạm bơm B10", xnBPublicId));
            constructions.create(hoSo("T17-B-011", "Trạm bơm B11", xnBPublicId));
        });

        AuthContext.set(nguoiDungTai(xnAId, pathA));
        assertThat(maCongTrinh()).hasSize(1);

        AuthContext.set(nguoiDungTai(rootId, pathRoot));
        assertThat(maCongTrinh()).hasSize(3);
    }

    // -------------------------------------------------------------------------

    private java.util.List<String> maCongTrinh() {
        return constructions.search(ConstructionFilter.rong(), PageRequest.of(0, 50)).stream()
                .map(Construction::getCode)
                .filter(ma -> ma.startsWith("T17-"))
                .sorted()
                .toList();
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
                new BigDecimal("20.980000"),
                new BigDecimal("105.780000"),
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

    /** Không có người đăng nhập → bộ lọc không bật; dùng để dựng dữ liệu ở cả hai đơn vị. */
    private void heThong(Runnable hanhDong) {
        AuthContext.clear();
        hanhDong.run();
    }

    private <T> T heThongTraVe(java.util.function.Supplier<T> hanhDong) {
        AuthContext.clear();
        return hanhDong.get();
    }

    private AuthenticatedUser nguoiDungTai(long orgUnitId, String orgUnitPath) {
        return new AuthenticatedUser(
                998L,
                UUID.randomUUID(),
                "t17-probe",
                "Người kiểm thử WS-17",
                orgUnitId,
                orgUnitPath,
                Set.of("XN_MANAGER"),
                Set.of(),
                false,
                UUID.randomUUID(),
                UUID.randomUUID());
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
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T17-%'");
        jdbc.update("DELETE FROM construction_clusters WHERE code LIKE 'T17-%'");
        jdbc.update("DELETE FROM org_units WHERE code LIKE 'T17-%'");
    }
}
