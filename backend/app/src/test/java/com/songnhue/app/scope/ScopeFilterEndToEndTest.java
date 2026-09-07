package com.songnhue.app.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;

/**
 * ⚠ <b>Tầng 3 phân quyền, kiểm chứng đầu-cuối</b> — trả nợ WS-5/T5.11 (sổ nợ mục 7),
 * <b>đóng Definition of Done mục 7</b>.
 *
 * <p>WS-5 dựng đủ cơ chế: {@code @FilterDef} trên {@code ScopedEntity}, {@code ScopeFilterAspect}
 * bật bộ lọc quanh mỗi transaction, mã lỗi {@code AUTH-3002} trong danh mục. Nhưng cả ba thứ đó chưa
 * bao giờ <b>chạy cùng nhau</b>, vì Phase 0 không có entity nào thuộc phạm vi đơn vị — hồ sơ công
 * trình, lịch sử sửa chữa, hồ sơ nhân viên đều thuộc Phase 1. Cơ chế phân quyền quan trọng nhất của
 * hệ thống đang ở trạng thái "chắc là chạy".
 *
 * <p>Bài kiểm này dựng entity đầu tiên ấy (bảng chỉ có trong {@code src/test}) và kiểm ba điều:
 *
 * <ol>
 *   <li>Truy vấn danh sách <b>chỉ</b> trả về bản ghi của đơn vị mình.
 *   <li>Người ở đơn vị cha thấy cả dữ liệu của đơn vị con — vì lọc theo <i>materialized path</i>, chứ
 *       không phải so bằng {@code org_unit_id}.
 *   <li>Tra bản ghi của đơn vị khác trả {@code AUTH-3002}, <b>không</b> phải 404 — nghĩa là lần chạm
 *       đó có để lại dấu vết.
 * </ol>
 *
 * <p>Nếu chỉ kiểm điều 1, một bộ lọc viết sai kiểu {@code WHERE 1 = 0} cũng qua. Điều 2 là thứ phân
 * biệt "có lọc" với "lọc đúng".
 */
@Import(ScopeFilterEndToEndTest.ScopedRecordTestConfig.class)
class ScopeFilterEndToEndTest extends IntegrationTestBase {

    /** Chỉ để dựng bean thăm dò; entity đã được {@code @EntityScan("com.songnhue")} nhận sẵn. */
    @TestConfiguration
    static class ScopedRecordTestConfig {

        @Bean
        ScopedRecordProbe scopedRecordProbe(ScopeGuard scopeGuard) {
            return new ScopedRecordProbe(scopeGuard);
        }
    }

    @Autowired
    private ScopedRecordProbe probe;

    @Autowired
    private JdbcTemplate jdbc;

    private long xiNghiepAId;
    private long xiNghiepBId;
    private long toDoiA1Id;
    private String pathA;
    private String pathB;
    private String pathRoot;

    @BeforeEach
    void setUpTree() {
        cleanUp();
        pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        Long rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);

        xiNghiepAId = insertUnit("T10-XN-A", "Xí nghiệp A (test)", rootId, pathRoot);
        xiNghiepBId = insertUnit("T10-XN-B", "Xí nghiệp B (test)", rootId, pathRoot);
        pathA = pathRoot + xiNghiepAId + "/";
        pathB = pathRoot + xiNghiepBId + "/";
        toDoiA1Id = insertUnit("T10-TO-A1", "Tổ đội A1 (test)", xiNghiepAId, pathA);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        cleanUp();
    }

    @Test
    @DisplayName("Danh sách chỉ trả về bản ghi của đơn vị mình")
    void listIsScopedToTheUsersUnit() {
        asSystem(() -> {
            probe.create("Hồ sơ của A", xiNghiepAId);
            probe.create("Hồ sơ của B", xiNghiepBId);
        });

        AuthContext.set(userAt(xiNghiepAId, pathA));
        assertThat(probe.listTitles()).containsExactly("Hồ sơ của A");

        AuthContext.set(userAt(xiNghiepBId, pathB));
        assertThat(probe.listTitles()).containsExactly("Hồ sơ của B");
    }

    @Test
    @DisplayName("Cấp trên thấy dữ liệu cấp dưới — lọc theo materialized path, không phải org_unit_id")
    void parentUnitSeesChildData() {
        asSystem(() -> {
            probe.create("Hồ sơ tổ đội A1", toDoiA1Id);
            probe.create("Hồ sơ của B", xiNghiepBId);
        });

        // Trưởng Xí nghiệp A phải thấy dữ liệu của Tổ đội A1 trực thuộc.
        AuthContext.set(userAt(xiNghiepAId, pathA));
        assertThat(probe.listTitles()).containsExactly("Hồ sơ tổ đội A1");

        // Người ở nút gốc thấy tất cả — không cần cờ "bỏ qua phạm vi" nào, và đó là chủ ý:
        // cờ như vậy chính là thứ hay bị bật nhầm rồi không ai để ý (conventions.md §4.2).
        AuthContext.set(userAt(1L, pathRoot));
        assertThat(probe.listTitles()).containsExactly("Hồ sơ của B", "Hồ sơ tổ đội A1");
    }

    @Test
    @DisplayName("⚠ Tra bản ghi của đơn vị khác → AUTH-3002, không phải 404")
    void outOfScopeLookupIsForbiddenNotMissing() {
        UUID recordOfB = asSystemResult(
                () -> probe.create("Hồ sơ mật của B", xiNghiepBId).getPublicId());

        AuthContext.set(userAt(xiNghiepAId, pathA));

        assertThatThrownBy(() -> probe.get(recordOfB))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(e -> ((PermissionDeniedException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_3002);
    }

    @Test
    @DisplayName("Định danh không tồn tại vẫn là 404 — không được biến mọi thứ thành 403")
    void unknownIdStaysNotFound() {
        AuthContext.set(userAt(xiNghiepAId, pathA));

        assertThatThrownBy(() -> probe.get(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("⚠ Chạm dữ liệu ngoài phạm vi để lại dấu vết trong security_events")
    void outOfScopeAccessIsRecorded() {
        UUID recordOfB = asSystemResult(
                () -> probe.create("Hồ sơ mật của B", xiNghiepBId).getPublicId());

        AuthContext.set(userAt(xiNghiepAId, pathA));
        assertThatThrownBy(() -> probe.get(recordOfB)).isInstanceOf(PermissionDeniedException.class);

        Integer events = jdbc.queryForObject(
                // `detail` là jsonb — phải ép sang text mới LIKE được
                "SELECT count(*) FROM security_events WHERE event_type = 'ACCESS_DENIED_SCOPE' "
                        + "AND detail::text LIKE ?",
                Integer.class,
                "%" + recordOfB + "%");
        assertThat(events)
                .as("một người dò public_id để tìm hồ sơ đơn vị khác trông y hệt người gõ nhầm — "
                        + "không ghi lại thì không ai phát hiện")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Bộ lọc được bật lại sau khi ScopeGuard tra cứu với bộ lọc tắt")
    void filterIsRestoredAfterTheGuardPeeks() {
        UUID recordOfB = asSystemResult(
                () -> probe.create("Hồ sơ mật của B", xiNghiepBId).getPublicId());
        asSystem(() -> probe.create("Hồ sơ của A", xiNghiepAId));

        AuthContext.set(userAt(xiNghiepAId, pathA));
        assertThatThrownBy(() -> probe.get(recordOfB)).isInstanceOf(PermissionDeniedException.class);

        // Quên bật lại bộ lọc thì truy vấn kế tiếp nhìn thấy toàn bộ dữ liệu — lớp sinh ra để bảo vệ
        // phạm vi lại trở thành chỗ chọc thủng nó, và không có lỗi nào báo ra.
        assertThat(probe.listTitles()).containsExactly("Hồ sơ của A");
    }

    // -------------------------------------------------------------------------

    /** Không có người đăng nhập → bộ lọc không bật, dùng để dựng dữ liệu ở cả hai đơn vị. */
    private void asSystem(Runnable action) {
        AuthContext.clear();
        action.run();
    }

    private <T> T asSystemResult(java.util.function.Supplier<T> action) {
        AuthContext.clear();
        return action.get();
    }

    private AuthenticatedUser userAt(long orgUnitId, String orgUnitPath) {
        return new AuthenticatedUser(
                999L,
                UUID.randomUUID(),
                "t10-probe",
                "Người kiểm thử",
                orgUnitId,
                orgUnitPath,
                Set.of("XN_MANAGER"),
                Set.of(),
                false,
                UUID.randomUUID(),
                UUID.randomUUID());
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
        jdbc.update("DELETE FROM test_scoped_records");
        jdbc.update("DELETE FROM org_units WHERE code LIKE 'T10-%'");
        // ⚠ Cố ý KHÔNG dọn `security_events`: vai trò `songnhue_app` không có DELETE trên bảng đó
        // (WS-2/T2.7) — thử dọn là nhận "permission denied for table security_events". Đúng như
        // thiết kế, và bản thân điều đó cũng là một bằng chứng nhỏ rằng việc tách vai trò ở tầng DB
        // đang có hiệu lực. Bài kiểm đếm theo public_id của bản ghi nên dữ liệu sót không ảnh hưởng.
    }
}
