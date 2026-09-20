package com.songnhue.app.scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

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
 * <b>Tra cứu theo {@code publicId} của entity phạm vi: ngoài phạm vi ⇒ 403 {@code AUTH-3002} + dấu vết — T73.1.</b>
 *
 * <h2>Khuyết tật</h2>
 *
 * Sáu đường tra cứu (5 ở {@code hydro}, 1 ở {@code hr}) gọi thẳng
 * {@code findByPublicIdAndDeletedAtIsNull(...).orElseThrow(SYS_0004)}. Bộ lọc phạm vi vẫn bật (cả sáu nằm trong
 * {@code @Transactional}), nên dữ liệu ⛔ lọt — nhưng bản ghi của đơn vị khác trả <b>404</b> và ⛔ để lại dòng
 * {@code security_events} nào. Một người dò {@code publicId} của đơn vị khác trông y hệt một người gõ nhầm đường
 * dẫn (M5.16); {@code StationService.get} và các service anh em đã đi qua {@code ScopeGuard} từ WS-28.
 *
 * <h2>Hai vế cho mỗi đường — vế thứ hai là thứ cho phép tin vế thứ nhất (luật 9)</h2>
 *
 * <ol>
 *   <li>bản ghi của Xí nghiệp B ⇒ 403 + {@code AUTH-3002};
 *   <li>một UUID ⛔ tồn tại ⇒ 404 + {@code SYS-0004}. Vế này chứng minh người dùng ĐÃ QUA kiểm quyền và lượt tra
 *       cứu thật sự chạy tới — ⛔ có nó thì một 403 vì thiếu quyền cũng làm vế 1 xanh.
 * </ol>
 *
 * Cộng một đối chứng phải-thành-công (điểm đo của chính Xí nghiệp A ⇒ 200): ⛔ có nó thì một hệ từ chối TẤT CẢ
 * cũng làm mọi vế xanh (luật 7). Qua HTTP chứ ⛔ gọi service: bộ lọc bật quanh {@code @Transactional}, và thứ tự
 * aspect quanh giao dịch chỉ bài HTTP thấy (T51.16).
 */
class TraCuuNgoaiPhamViHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T73PV-";
    private static final String VAI_TRO = "T73_PHAM_VI";
    private static final List<String> QUYEN = List.of(
            "hyd:report:view",
            "hyd:measurement:create",
            "hyd:measurement:review",
            "hyd:threshold:manage",
            "hr:leave:request");
    private static final String MOC = "2026-09-01T00:10:00Z";

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
    private UUID diemDoA;
    private UUID diemDoB;
    private UUID donNghiB;
    private String maLoai;

    /** Một đường tra cứu: tên để đọc khi đỏ · lời gọi theo một định danh · loại entity ScopeGuard ghi vào sự kiện. */
    private record Duong(String ten, Function<UUID, ResponseEntity<String>> goi, String entity) {}

    @BeforeEach
    void dung() {
        don();
        rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);
        String pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        xnA = themDonVi(TIEN_TO + "XN-A", rootId, pathRoot);
        xnB = themDonVi(TIEN_TO + "XN-B", rootId, pathRoot);

        Long nguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        diemDoA = themDiemDo(TIEN_TO + "A", "F97301", nguon, xnA);
        diemDoB = themDiemDo(TIEN_TO + "B", "F97302", nguon, xnB);
        maLoai = jdbc.queryForObject(
                "SELECT code FROM measurement_types WHERE deleted_at IS NULL ORDER BY id LIMIT 1", String.class);

        Long nhanVienB = jdbc.queryForObject(
                "INSERT INTO employees (code, full_name, org_unit_id) VALUES (?, 'Nhân viên của B (T73)', ?) RETURNING id",
                Long.class,
                TIEN_TO + "NV-B",
                xnB);
        donNghiB = jdbc.queryForObject(
                """
                INSERT INTO leave_requests (employee_id, org_unit_id, leave_type, from_date, to_date, working_days,
                                            requester_user_id, state)
                VALUES (?, ?, 'PHEP_NAM', DATE '2026-10-05', DATE '2026-10-05', 1,
                        (SELECT id FROM users ORDER BY id LIMIT 1), 'CHO_DUYET')
                RETURNING public_id
                """,
                UUID.class,
                nhanVienB,
                xnB);
    }

    @AfterEach
    void donSau() {
        don();
    }

    @Test
    @DisplayName("⛔⛔ Sáu đường tra cứu theo publicId: đơn vị khác ⇒ 403 AUTH-3002 + security_events; UUID lạ ⇒ 404")
    void ngoaiPhamViLaTuChoiCoDauVet() {
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử T73', 'Tạm, xoá ở cuối bài', FALSE, now())",
                VAI_TRO);
        int soQuyen = 0;
        for (String q : QUYEN) {
            soQuyen += jdbc.update(
                    "INSERT INTO role_permissions (role_id, permission_id) "
                            + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = ?",
                    VAI_TRO,
                    q);
        }
        assertThat(soQuyen)
                .as("chống tập rỗng: mã quyền đổi tên thì vế 404 dưới đây đỏ vì 403-thiếu-quyền, ⛔ vì phạm vi")
                .isEqualTo(QUYEN.size());

        PhienHttp phienHttp = new PhienHttp(http);
        phienHttp.doiIp();
        String ten = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t73_pham_vi", VAI_TRO);
        // ⛔⛔ Câu làm bài này KHẢ THI: `taoNguoiDung` đặt người dùng ở nút gốc, mà nút gốc thấy TẤT CẢ.
        assertThat(jdbc.update("UPDATE users SET org_unit_id = ? WHERE username = ?", xnA, ten))
                .isEqualTo(1);
        try {
            PhienHttp.Phien phien = phienHttp.dangNhap(ten);

            List<Duong> duong = List.of(
                    new Duong(
                            "báo cáo chi tiết",
                            id -> phienHttp.get(
                                    phien,
                                    "/api/v1/hyd/bao-cao/chi-tiet?stationPublicId=" + id + "&maLoaiChiSo=" + maLoai
                                            + "&tuNgay=2026-09-01&denNgay=2026-09-02"),
                            "Station"),
                    new Duong(
                            "báo cáo đồng bộ",
                            id -> phienHttp.get(
                                    phien,
                                    "/api/v1/hyd/bao-cao/dong-bo?tuNgay=2026-09-01&denNgay=2026-09-02&stationPublicId="
                                            + id),
                            "Station"),
                    new Duong(
                            "nhập tay số đo",
                            id -> phienHttp.goi(
                                    phien,
                                    HttpMethod.POST,
                                    "/api/v1/hyd/so-do/nhap-tay",
                                    "{\"diemDoId\":\"%s\",\"maLoaiChiSo\":\"%s\",\"mocDo\":\"%s\",\"giaTri\":1.0}"
                                            .formatted(id, maLoai, MOC)),
                            "Station"),
                    new Duong(
                            "nút duyệt số đo nghi ngờ",
                            id -> phienHttp.get(
                                    phien,
                                    "/api/v1/hyd/so-do/thao-tac?diemDoId=" + id + "&maLoaiChiSo=" + maLoai + "&mocDo="
                                            + MOC),
                            "Station"),
                    new Duong(
                            "thêm ngưỡng cảnh báo",
                            id -> phienHttp.goi(
                                    phien,
                                    HttpMethod.POST,
                                    "/api/v1/hyd/alert-rules",
                                    ("{\"stationId\":\"%s\",\"measurementTypeCode\":\"%s\",\"alertLevelId\":\"%s\","
                                                    + "\"conditionType\":\"GT\",\"thresholdValue\":1}")
                                            .formatted(id, maLoai, UUID.randomUUID())),
                            "Station"),
                    new Duong(
                            "nút của đơn nghỉ phép",
                            id -> phienHttp.get(phien, "/api/v1/hr/nghi-phep/" + id + "/hanh-dong"),
                            "LeaveRequest"));

            ResponseEntity<String> doiChung = duong.get(0).goi().apply(diemDoA);
            assertThat(doiChung.getStatusCode())
                    .as("⚠ ĐỐI CHỨNG phải-thành-công — điểm đo của CHÍNH Xí nghiệp A: %s", doiChung.getBody())
                    .isEqualTo(HttpStatus.OK);

            for (Duong d : duong) {
                UUID ngoai = "LeaveRequest".equals(d.entity()) ? donNghiB : diemDoB;
                ResponseEntity<String> b = d.goi().apply(ngoai);
                assertThat(b.getStatusCode())
                        .as("[%s] bản ghi của Xí nghiệp B — 404 là nói dối rằng nó ⛔ tồn tại: %s", d.ten(), b.getBody())
                        .isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(b.getBody()).as("[%s]", d.ten()).contains("AUTH-3002");

                ResponseEntity<String> la = d.goi().apply(UUID.randomUUID());
                assertThat(la.getStatusCode())
                        .as(
                                "[%s] UUID ⛔ tồn tại ⇒ 404 — ⛔ phải 404 thì người dùng chưa qua được kiểm quyền: %s",
                                d.ten(), la.getBody())
                        .isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(la.getBody()).as("[%s]", d.ten()).contains("SYS-0004");
            }

            assertThat(demSuKien(diemDoB, "Station"))
                    .as("mỗi lượt chạm điểm đo của đơn vị khác phải để lại MỘT dòng ACCESS_DENIED_SCOPE (M5.16)")
                    .isEqualTo(5);
            assertThat(demSuKien(donNghiB, "LeaveRequest")).isEqualTo(1);
        } finally {
            // ⛔ Trả người dùng về nút gốc TRƯỚC khi dọn cây đơn vị (users_org_unit_id_fkey — bài học
            //    HoSoNhanSuPhamViTest), và dọn vai trò tạm (RbacMatrixTest đọc toàn bảng roles).
            jdbc.update("UPDATE users SET org_unit_id = ? WHERE org_unit_id IN (?, ?)", rootId, xnA, xnB);
            donVaiTro();
        }
    }

    // ── hạ tầng ──────────────────────────────────────────────────────────────

    private int demSuKien(UUID publicId, String entity) {
        Integer so = jdbc.queryForObject(
                // `detail` là jsonb — phải ép sang text mới LIKE được.
                "SELECT count(*) FROM security_events WHERE event_type = 'ACCESS_DENIED_SCOPE' "
                        + "AND detail::text LIKE ? AND detail::text LIKE ?",
                Integer.class,
                "%" + publicId + "%",
                "%" + entity + "%");
        return so == null ? 0 : so;
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

    private UUID themDiemDo(String ma, String maApi, long nguon, long donVi) {
        return jdbc.queryForObject(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active, org_unit_id, created_at)
                VALUES (?, ?, ?, ?, 'MN_SONG', TRUE, ?, now())
                RETURNING public_id
                """,
                UUID.class,
                ma,
                "Điểm đo kiểm thử " + ma,
                maApi,
                nguon,
                donVi);
    }

    private void donVaiTro() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO);
    }

    /**
     * Dọn theo thứ tự khoá ngoại, TRƯỚC và SAU mỗi bài. ⚠ {@code security_events} cố ý ⛔ dọn: vai trò ứng dụng ⛔
     * có DELETE trên bảng ấy (T2.7); phép đếm lọc theo {@code publicId} của chính bài nên dòng sót ⛔ ảnh hưởng.
     */
    private void don() {
        jdbc.update(
                "DELETE FROM leave_requests WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update(
                "DELETE FROM hydro_readings WHERE station_id IN (SELECT id FROM stations WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM stations WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stations WHERE code LIKE ?", Long.class, TIEN_TO + "%"))
                .as("dọn hỏng thì HydroCatalogueSeedTest (đúng 19 điểm đo) đỏ ở một lớp KHÁC")
                .isZero();
    }
}
