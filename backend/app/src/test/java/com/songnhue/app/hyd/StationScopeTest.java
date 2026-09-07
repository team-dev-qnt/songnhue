package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.persistence.ScopedEntity;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.hydro.application.StationService;
import com.songnhue.hydro.domain.Station;

/**
 * ⭐⭐ Bộ lọc phạm vi của điểm đo — nhánh {@code org_unit_id IS NULL} mà không entity nào khác có.
 *
 * <h2>Vì sao nhánh này phải có bài kiểm riêng</h2>
 *
 * <p>{@link Station} là {@code ScopedEntity} duy nhất của dự án cho phép {@code org_unit_id} rỗng
 * (OI-05 chưa chốt 7 hay 8 Xí nghiệp). Điều kiện lọc chuẩn là
 * {@code org_unit_id IN (SELECT …)}, mà trong SQL {@code NULL IN (…)} cho ra {@code NULL} chứ không
 * phải {@code TRUE} — nên nếu {@link Station#LOC_PHAM_VI} bị "dọn lại cho giống các entity khác",
 * <b>cả 19 điểm đo biến mất với tất cả mọi người</b>, kể cả SUPER_ADMIN ở path gốc. Màn hình rỗng,
 * không một dòng lỗi, và triệu chứng giống hệt "chưa seed".
 *
 * <p>⚠ Nhưng nới bộ lọc thì phải chứng minh nó <b>chỉ</b> nới đúng chỗ đó. Bài kiểm vì vậy khẳng
 * định hai trạng thái kề nhau: điểm đo chưa gán đơn vị <i>thấy được</i>, điểm đo của Xí nghiệp khác
 * <i>không thấy</i>. Chỉ khẳng định vế đầu thì một bộ lọc bị tắt hẳn cũng xanh.
 */
class StationScopeTest extends IntegrationTestBase {

    @Autowired
    private StationService stations;

    @Autowired
    private JdbcTemplate jdbc;

    private long xnAId;
    private long xnBId;
    private long rootId;
    private String pathA;
    private String pathB;
    private String pathRoot;

    @BeforeEach
    void setUp() {
        cleanUp();
        pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);
        xnAId = insertUnit("T28-XN-A", "Xí nghiệp A (T28)", rootId, pathRoot);
        xnBId = insertUnit("T28-XN-B", "Xí nghiệp B (T28)", rootId, pathRoot);
        pathA = pathRoot + xnAId + "/";
        pathB = pathRoot + xnBId + "/";

        insertStation("T28-DO-A", "F99001", xnAId);
        insertStation("T28-DO-B", "F99002", xnBId);
        insertStation("T28-DO-NULL", "F99003", null);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        cleanUp();
    }

    @Test
    @DisplayName("⭐ Station khai @Filter — thiếu nó thì bộ lọc tồn tại nhưng không áp cho điểm đo")
    void stationCoFilter() {
        assertThat(ScopedEntity.class.isAssignableFrom(Station.class)).isTrue();
        assertThat(Station.class.getAnnotation(org.hibernate.annotations.Filter.class))
                .as("⛔ @FilterDef ở lớp cha mới chỉ ĐỊNH NGHĨA bộ lọc; thiếu @Filter thì mọi Xí "
                        + "nghiệp đọc được điểm đo của nhau, màn hình đầy đủ, không dòng lỗi nào")
                .isNotNull();
        assertThat(Station.LOC_PHAM_VI)
                .as("⛔ vế IS NULL là thứ giữ cho 19 điểm đo chưa gán đơn vị không biến mất")
                .contains("org_unit_id IS NULL")
                .contains(ScopedEntity.ORG_UNIT_FILTER_CONDITION);
    }

    @Test
    @DisplayName("⭐⭐ Người của XN A thấy điểm đo của A và điểm đo CHƯA GÁN, nhưng KHÔNG thấy của B")
    void thayDiemDoChuaGanNhungKhongThayCuaDonViKhac() {
        AuthContext.set(nguoiDungTai(xnAId, pathA));

        List<String> thayDuoc = maDiemDo();

        assertThat(thayDuoc)
                .as("điểm đo chưa gán đơn vị không phải dữ liệu của Xí nghiệp khác — nó là dữ liệu "
                        + "chưa thuộc về ai, và giấu nó đi là giấu luôn việc cần làm")
                .contains("T28-DO-NULL");
        assertThat(thayDuoc).contains("T28-DO-A");
        assertThat(thayDuoc)
                .as("⛔ nếu vế IS NULL bị viết thành 'OR TRUE' thì dòng này lọt qua và không ai thấy")
                .doesNotContain("T28-DO-B");
    }

    @Test
    @DisplayName("Người của XN B thấy đúng phần đối xứng — bộ lọc không phải luôn trả cùng một tập")
    void docLapTheoTungDonVi() {
        AuthContext.set(nguoiDungTai(xnBId, pathB));

        assertThat(maDiemDo()).contains("T28-DO-B", "T28-DO-NULL").doesNotContain("T28-DO-A");
    }

    @Test
    @DisplayName("Cấp Công ty thấy cả ba — kể cả điểm đo chưa gán đơn vị")
    void capCongTyThayTatCa() {
        AuthContext.set(nguoiDungTai(rootId, pathRoot));

        assertThat(maDiemDo()).contains("T28-DO-A", "T28-DO-B", "T28-DO-NULL");
    }

    /**
     * Màn hình T28.9 — và nó cũng chịu bộ lọc phạm vi.
     *
     * <p>Danh sách "chưa gán đơn vị" là việc cần làm chung, nhưng nó vẫn đi qua cùng một bộ lọc; ở
     * đây điều đó vô hại vì mọi dòng trong danh sách đều có {@code org_unit_id IS NULL}.
     */
    @Test
    @DisplayName("Danh sách 'chưa gán đơn vị' chỉ chứa dòng chưa gán, không lẫn dòng đã gán")
    void danhSachChuaGanDonVi() {
        AuthContext.set(nguoiDungTai(xnAId, pathA));

        // ⚠ T28.30 — endpoint `/chua-gan-don-vi` đã GỠ 04/09. Câu hỏi ấy nay đọc từ CỜ
        //   `Station.chuaGanDonVi()` đi kèm mỗi dòng của `list()`, và đó là đúng đường mà
        //   `StationsPage` đi. Kiểm qua đường màn hình thật, ⛔ không qua một API không ai gọi.
        List<String> chuaGan = stations.list().stream()
                .filter(Station::chuaGanDonVi)
                .map(Station::getCode)
                .filter(ma -> ma.startsWith("T28-"))
                .toList();

        assertThat(chuaGan).containsExactly("T28-DO-NULL");
    }

    // -------------------------------------------------------------------------

    private List<String> maDiemDo() {
        return stations.list().stream()
                .map(Station::getCode)
                .filter(ma -> ma.startsWith("T28-"))
                .sorted()
                .toList();
    }

    private AuthenticatedUser nguoiDungTai(long orgUnitId, String orgUnitPath) {
        return new AuthenticatedUser(
                997L,
                UUID.randomUUID(),
                "t28-probe",
                "Người kiểm thử WS-28",
                orgUnitId,
                orgUnitPath,
                Set.of("XN_MANAGER"),
                Set.of(),
                false,
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private void insertStation(String code, String apiCode, Long orgUnitId) {
        Long nguonId = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE code = 'BHH40' AND deleted_at IS NULL", Long.class);
        jdbc.update(
                "INSERT INTO stations (code, name, api_code, api_source_id, position_role, org_unit_id, created_at) "
                        + "VALUES (?, ?, ?, ?, 'MN_SONG', ?, now())",
                code,
                code,
                apiCode,
                nguonId,
                orgUnitId);
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
        jdbc.update("DELETE FROM stations WHERE code LIKE 'T28-%'");
        jdbc.update("DELETE FROM org_units WHERE code LIKE 'T28-%'");
    }
}
