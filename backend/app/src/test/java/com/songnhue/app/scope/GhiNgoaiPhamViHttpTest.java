package com.songnhue.app.scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Vế GHI của phạm vi đơn vị — T74.8 (ASVS 4.2.1) — và trùng mã với bản ghi NGOÀI phạm vi — T74.9.</b>
 *
 * <h2>Khuyết tật</h2>
 *
 * Bộ lọc tầng 3 chỉ canh vế <b>đọc</b>. Nơi ghi đơn vị lấy từ biểu mẫu ({@code ConstructionService} tạo/sửa,
 * {@code StationService}, {@code EmployeeService}) chỉ kiểm đơn vị <b>tồn tại</b>. Quyền đi theo vai trò, phạm vi đi
 * theo <b>đơn vị của tài khoản</b> — và {@code TECHNICIAN}, vai trò thường đặt ở Xí nghiệp, giữ
 * {@code ops:construction:create/update} lẫn {@code hyd:station:manage}. Nên một lần chọn nhầm trong ô đơn vị là
 * ghi vào dữ liệu của Xí nghiệp khác — dữ liệu chính người ấy ⛔ đọc lại được — ⛔ một dòng nhật ký bảo mật.
 *
 * <p>Trùng mã: mã công trình / mã điểm đo / mã API là duy nhất <b>toàn Công ty</b>, còn phép kiểm trùng đi qua bộ
 * lọc phạm vi ⇒ mã đã có ở đơn vị khác là vô hình với người tạo, lượt lưu rơi vào ràng buộc CSDL và người dùng
 * nhận {@code SYS-0005} <i>"Dữ liệu vừa được người khác thay đổi"</i> — một câu dẫn họ đi tìm một lượt sửa đồng
 * thời ⛔ hề có.
 *
 * <h2>Mỗi vế một đối chứng phải-thành-công (luật 7 · luật 9)</h2>
 *
 * Tạo/sửa trong chính XN-A ⇒ 201/200: ⛔ có nó thì một hệ chặn MỌI lượt ghi cũng làm vế 403 xanh.
 */
class GhiNgoaiPhamViHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T74B-";
    private static final String VAI_TRO = "T74B_GHI_PHAM_VI";
    private static final List<String> QUYEN = List.of(
            "ops:construction:view",
            "ops:construction:create",
            "ops:construction:update",
            "hyd:station:view",
            "hyd:station:manage");

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    private long rootId;
    private long xnA;
    private long xnB;
    private UUID xnAPublic;
    private UUID xnBPublic;

    private PhienHttp phienA;
    private PhienHttp.Phien a;
    private String tenA;
    private PhienHttp phienGoc;
    private PhienHttp.Phien goc;

    @BeforeEach
    void dung() {
        don();
        rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);
        String pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        xnA = themDonVi(TIEN_TO + "XN-A", rootId, pathRoot);
        xnB = themDonVi(TIEN_TO + "XN-B", rootId, pathRoot);
        xnAPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, xnA);
        xnBPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, xnB);

        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử WS-74b', 'Tạm, xoá ở cuối bài', FALSE, now())",
                VAI_TRO);
        int gan = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN ("
                        + String.join(",", java.util.Collections.nCopies(QUYEN.size(), "?")) + ")",
                java.util.stream.Stream.concat(java.util.stream.Stream.of(VAI_TRO), QUYEN.stream())
                        .toArray());
        assertThat(gan)
                .as("⚠ chống tập rỗng: mã quyền đổi tên thì gán thiếu trong im lặng")
                .isEqualTo(QUYEN.size());

        tenA = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t74b_xn_a", VAI_TRO);
        jdbc.update("UPDATE users SET org_unit_id = ? WHERE username = ?", xnA, tenA);
        String tenGoc = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t74b_goc", VAI_TRO);
        phienA = new PhienHttp(http);
        a = phienA.dangNhap(tenA);
        phienGoc = new PhienHttp(http);
        goc = phienGoc.dangNhap(tenGoc);
    }

    @AfterEach
    void donSau() {
        don();
    }

    @Test
    @DisplayName(
            "⛔⛔ Công trình: tạo vào XN-B · chuyển từ XN-A sang XN-B ⇒ 403 AUTH-3002 + dấu vết; trùng mã ngoài phạm vi ⇒ 409 OPS-2008")
    void congTrinh() {
        ResponseEntity<String> taoTrong =
                phienA.goi(a, HttpMethod.POST, "/api/v1/ops/constructions", congTrinhJson("CT-A", xnAPublic));
        assertThat(taoTrong.getStatusCode())
                .as("⚠ ĐỐI CHỨNG phải-thành-công: %s", taoTrong.getBody())
                .isEqualTo(HttpStatus.CREATED);
        String ctA = PhienHttp.giaTriJson(taoTrong.getBody(), "publicId");

        long truoc = demSuKien();
        ResponseEntity<String> taoNgoai =
                phienA.goi(a, HttpMethod.POST, "/api/v1/ops/constructions", congTrinhJson("CT-B", xnBPublic));
        assertThat(taoNgoai.getStatusCode())
                .as("⛔⛔ tạo công trình vào đơn vị ngoài phạm vi: %s", taoNgoai.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(taoNgoai.getBody()).contains("AUTH-3002");
        assertThat(dem("constructions", "CT-B")).isZero();

        ResponseEntity<String> chuyen =
                phienA.goi(a, HttpMethod.PUT, "/api/v1/ops/constructions/" + ctA, congTrinhJson("CT-A", xnBPublic));
        assertThat(chuyen.getStatusCode())
                .as("⛔⛔ chuyển công trình sang đơn vị ngoài phạm vi: %s", chuyen.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject(
                        "SELECT org_unit_id FROM constructions WHERE public_id = ?::uuid", Long.class, ctA))
                .isEqualTo(xnA);
        assertThat(demSuKien() - truoc)
                .as("⛔ mỗi lượt ghi bị chặn để lại MỘT dòng ACCESS_DENIED_SCOPE (M5.16)")
                .isEqualTo(2);

        // Trùng mã với công trình của XN-B — tài khoản gốc tạo trước, XN-A ⛔ thấy nó.
        assertThat(phienGoc.goi(goc, HttpMethod.POST, "/api/v1/ops/constructions", congTrinhJson("TRUNG", xnBPublic))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> trung =
                phienA.goi(a, HttpMethod.POST, "/api/v1/ops/constructions", congTrinhJson("TRUNG", xnAPublic));
        assertThat(trung.getStatusCode()).as("%s", trung.getBody()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(trung.getBody())
                .as("⛔ SYS-0005 *'dữ liệu vừa được người khác thay đổi'* dẫn người dùng đi tìm một lượt sửa ⛔ có")
                .contains("OPS-2008");
    }

    @Test
    @DisplayName(
            "⛔⛔ Điểm đo: tạo vào XN-B · chuyển sang XN-B ⇒ 403 AUTH-3002; trùng mã / mã API ngoài phạm vi ⇒ 409 HYD-1002")
    void diemDo() {
        ResponseEntity<String> taoTrong =
                phienA.goi(a, HttpMethod.POST, "/api/v1/hyd/stations", diemDoJson("DD-A", "F97401", xnAPublic));
        assertThat(taoTrong.getStatusCode())
                .as("⚠ ĐỐI CHỨNG phải-thành-công: %s", taoTrong.getBody())
                .isEqualTo(HttpStatus.CREATED);
        String ddA = PhienHttp.giaTriJson(taoTrong.getBody(), "id");

        long truoc = demSuKien();
        ResponseEntity<String> taoNgoai =
                phienA.goi(a, HttpMethod.POST, "/api/v1/hyd/stations", diemDoJson("DD-B", "F97402", xnBPublic));
        assertThat(taoNgoai.getStatusCode())
                .as("⛔⛔ tạo điểm đo vào đơn vị ngoài phạm vi: %s", taoNgoai.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(dem("stations", "DD-B")).isZero();

        ResponseEntity<String> chuyen =
                phienA.goi(a, HttpMethod.PUT, "/api/v1/hyd/stations/" + ddA, diemDoJson("DD-A", "F97401", xnBPublic));
        assertThat(chuyen.getStatusCode())
                .as("⛔⛔ chuyển điểm đo sang đơn vị ngoài phạm vi: %s", chuyen.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT org_unit_id FROM stations WHERE public_id = ?::uuid", Long.class, ddA))
                .isEqualTo(xnA);
        assertThat(demSuKien() - truoc).isEqualTo(2);

        // Trùng mã và trùng mã API với điểm đo của XN-B.
        Long nguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        jdbc.update(
                "INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, org_unit_id, created_at)"
                        + " VALUES (?, 'Điểm đo của B', 'F97409', ?, 'MN_SONG', TRUE, ?, now())",
                TIEN_TO + "TRUNG",
                nguon,
                xnB);
        ResponseEntity<String> trungMa =
                phienA.goi(a, HttpMethod.POST, "/api/v1/hyd/stations", diemDoJson("TRUNG", "F97408", xnAPublic));
        assertThat(trungMa.getStatusCode()).as("%s", trungMa.getBody()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(trungMa.getBody()).contains("HYD-1002");
        ResponseEntity<String> trungApi =
                phienA.goi(a, HttpMethod.POST, "/api/v1/hyd/stations", diemDoJson("KHAC", "F97409", xnAPublic));
        assertThat(trungApi.getStatusCode()).as("%s", trungApi.getBody()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(trungApi.getBody()).contains("HYD-1002");
    }

    @Test
    @DisplayName("⛔⛔ Nhập trạm bơm: mã SINH RA phải nhảy qua mã đang bị Xí nghiệp KHÁC giữ (T74.15)")
    void nhapTramBomKhongCapMaTrung() {
        // Mã mà bộ sinh sẽ đề nghị cho XN-A — nhưng XN-B đang giữ nó, và XN-A ⛔ nhìn thấy nó.
        String maBiChiem = "TB-" + TIEN_TO + "XN-A-001";
        assertThat(phienGoc.goi(
                                goc,
                                HttpMethod.POST,
                                "/api/v1/ops/constructions",
                                congTrinhJsonMa(maBiChiem, xnBPublic))
                        .getStatusCode())
                .as("⚠ TIỀN ĐỀ: mã bị chiếm phải được tạo THẬT, ⛔ thì bài xanh vì lý do sai (luật 7)")
                .isEqualTo(HttpStatus.CREATED);

        String csv =
                """
                ma_cong_trinh,ten_cong_trinh,ma_don_vi,nguon_tuoi_huong_tieu,dia_diem,ly_trinh,so_may,q_mot_may_m3h
                ,Trạm bơm kiểm thử T74.15,%s,,,,2,1000
                """
                        .formatted(TIEN_TO + "XN-A");
        ResponseEntity<String> nhap = phienA.dangTep(
                a, "/api/v1/ops/may-bom/nhom-may/nhap", csv.getBytes(StandardCharsets.UTF_8), "tram.csv");

        assertThat(nhap.getStatusCode())
                .as(
                        "⛔⛔ mã sinh ra trùng mã của XN-B ⇒ OPS-2008 về một trạm người nhập ⛔ nhìn thấy được: %s",
                        nhap.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(nhap.getBody()).doesNotContain("OPS-2008");
        assertThat(jdbc.queryForObject(
                        "SELECT code FROM constructions WHERE org_unit_id = ? AND deleted_at IS NULL "
                                + "ORDER BY id DESC LIMIT 1",
                        String.class,
                        xnA))
                .as("⛔ NHẢY QUA mã đã bị chiếm, ⛔ phải cấp lại nó rồi để ràng buộc CSDL chặn")
                .isEqualTo("TB-" + TIEN_TO + "XN-A-002");
    }

    // ---- Trợ giúp -----------------------------------------------------------

    private static String congTrinhJson(String hau, UUID donVi) {
        return congTrinhJsonMa(TIEN_TO + hau, donVi);
    }

    private static String congTrinhJsonMa(String ma, UUID donVi) {
        return """
                {"code":"%s","name":"Công trình kiểm thử %s","constructionType":"TRAM_BOM",
                 "orgUnitId":"%s","managementLevel":"XI_NGHIEP"}"""
                .formatted(ma, ma, donVi);
    }

    private String diemDoJson(String hau, String maApi, UUID donVi) {
        return """
                {"code":"%s","name":"Điểm đo kiểm thử %s","apiCode":"%s","apiSourceId":"%s",
                 "positionRole":"MN_SONG","orgUnitId":"%s","measurementTypeIds":["%s"]}"""
                .formatted(
                        TIEN_TO + hau,
                        hau,
                        maApi,
                        jdbc.queryForObject(
                                "SELECT public_id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1",
                                UUID.class),
                        donVi,
                        jdbc.queryForObject(
                                "SELECT public_id FROM measurement_types WHERE code = 'MUC_NUOC'", UUID.class));
    }

    private long demSuKien() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM security_events WHERE event_type = 'ACCESS_DENIED_SCOPE' AND username = ?",
                Long.class,
                tenA);
    }

    private int dem(String bang, String hau) {
        return jdbc.queryForObject("SELECT count(*) FROM " + bang + " WHERE code = ?", Integer.class, TIEN_TO + hau);
    }

    private long themDonVi(String ma, long chaId, String pathCha) {
        Long id = jdbc.queryForObject(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/0/', 0, 0, now()) RETURNING id",
                Long.class,
                ma,
                "Đơn vị kiểm thử " + ma,
                chaId);
        String path = pathCha + id + "/";
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", path, path.split("/").length - 1, id);
        return id;
    }

    /** Dọn theo thứ tự khoá ngoại. ⚠ {@code security_events} cố ý ⛔ dọn (vai trò ứng dụng ⛔ có DELETE, T2.7). */
    private void don() {
        jdbc.update("UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE parent_id IS NULL) "
                + "WHERE username LIKE 'kiemtra_t74b%'");
        jdbc.update(
                "DELETE FROM station_measurement_types WHERE station_id IN (SELECT id FROM stations WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM stations WHERE code LIKE ?", TIEN_TO + "%");
        // ⚠ Mã do bộ nhập SINH mang tiền tố `TB-<mã đơn vị>-` nên ⛔ khớp TIEN_TO — thiếu dòng này thì
        //    trạm rò sang lớp sau và `HydroCatalogueSeedTest` đỏ ở một chỗ vô can (T48.8).
        jdbc.update(
                "DELETE FROM nhom_may_bom WHERE construction_id IN (SELECT id FROM constructions WHERE code LIKE ?)",
                "TB-" + TIEN_TO + "%");
        jdbc.update("DELETE FROM constructions WHERE code LIKE ? OR code LIKE ?", TIEN_TO + "%", "TB-" + TIEN_TO + "%");
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO);
        for (String bang : List.of("jobs", "attachments")) {
            jdbc.update(
                    "DELETE FROM " + bang + " WHERE org_unit_id IN (SELECT id FROM org_units WHERE code LIKE ?)",
                    TIEN_TO + "%");
        }
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE code LIKE ?", Long.class, TIEN_TO + "%"))
                .as("dọn hỏng thì HydroCatalogueSeedTest (đúng 19 điểm đo) đỏ ở một lớp KHÁC")
                .isZero();
    }
}
