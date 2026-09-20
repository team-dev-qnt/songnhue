package com.songnhue.app.org;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.notification.RecipientResolver;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.hydro.application.NguongAlertService;
import com.songnhue.hydro.domain.ReadingQuality;

/**
 * <b>Trưởng / phó đơn vị phải có ĐƯỜNG GHI — H24 (G11).</b>
 *
 * <h2>Khuyết tật</h2>
 *
 * <p>{@code org_units.head_user_id} và {@code deputy_user_id} có từ {@code V202608131002} (13/08),
 * có getter/setter trên {@link com.songnhue.core.domain.org.OrgUnit}, và có <b>một người đọc thật</b>:
 * {@code OrgUnitRepository.findActiveHeadAndDeputyUserIds} — nguồn người nhận cảnh báo ngưỡng của
 * G11. Đo 20/09/2026: {@code setHeadUserId} / {@code setDeputyUserId} có <b>0 lời gọi</b> trong mã
 * sản phẩm; hai cột ấy chỉ được ghi bằng {@code UPDATE} thô trong <i>đồ gá kiểm thử</i>.
 *
 * <p>⇒ Nửa cặp đọc–ghi (quy tắc 27) ở dạng đắt nhất: cơ chế <b>đúng</b>, có người đọc, có bài kiểm
 * xanh — và nhánh ấy trả về <b>tập rỗng vĩnh viễn</b> vì ⛔ ai điền được dữ liệu. Javadoc của
 * {@code AlertNotifier} đã tự khai điều này từ 02/09 và dự đoán đúng hậu quả: <i>"mọi cảnh báo ở đây
 * tới ĐÚNG 0 người, trong khi bảng notifications vẫn có dòng và mọi bài kiểm verify(notify) vẫn
 * xanh"</i>. Một chú thích ⛔ phải một cổng kiểm.
 *
 * <h2>Vế CHỊU LỰC là {@link #canhBaoNguongToiTruongDonVi()}</h2>
 *
 * <p>Ba bài đầu chỉ chứng minh <i>ghi được và đọc lại được</i>. Bài thứ tư đi <b>trọn vòng</b>
 * (quy tắc 27): đặt trưởng đơn vị <b>qua API</b> → một số đo vượt ngưỡng ở điểm đo của chính đơn vị
 * ấy → {@code notification_recipients} phải <b>chứa</b> người ấy. ⛔ Có vế này thì một bản vá chỉ
 * thêm hai ô vào biểu mẫu cũng làm ba bài đầu xanh, còn cảnh báo vẫn tới 0 người.
 *
 * <p>⚠ Nhóm <i>Ban điều hành</i> cố ý giữ <b>RỖNG</b> suốt lớp này: nó là nhánh dự phòng của cùng
 * phép suy người nhận, nên một người trong đó sẽ làm bài thứ tư xanh vì lý do sai (luật 9).
 */
class TruongPhoDonViHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T76-";
    private static final String MA_DIEM_DO = TIEN_TO + "DD";
    private static final String MA_API = "F97611";
    private static final String MA_MUC = TIEN_TO + "MUC";

    /**
     * ⚠ Vai trò RIÊNG, ⛔ dùng {@code SUPER_ADMIN}: ba vai trò quản trị nằm trong
     * {@code AuthenticatedUser.TWO_FACTOR_REQUIRED_ROLES} nên lượt đăng nhập dừng ở
     * {@code TWO_FACTOR_ENROLL_REQUIRED} — bài kiểm sẽ đỏ ở ĐỒ GÁ chứ ⛔ ở thứ nó canh.
     */
    private static final String VAI_TRO = "T76_QUAN_TRI_DON_VI";

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private NguongAlertService nguongAlert;

    @Autowired
    private SettingService thamSo;

    private PhienHttp phien;
    private PhienHttp.Phien admin;
    private long donViId;
    private UUID donViPublic;
    private UUID truongPublic;
    private long truongId;
    private UUID phoPublic;

    @BeforeEach
    void dung() {
        don();
        Long goc = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);
        String pathGoc = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        donViId = themDonVi(TIEN_TO + "XN", goc, pathGoc);
        donViPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, donViId);

        Object[] truong = themNguoi(TIEN_TO + "truong", "ACTIVE");
        truongId = (Long) truong[0];
        truongPublic = (UUID) truong[1];
        phoPublic = (UUID) themNguoi(TIEN_TO + "pho", "ACTIVE")[1];

        taoVaiTro();
        phien = new PhienHttp(http);
        admin = phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, TIEN_TO + "admin", VAI_TRO));
    }

    @AfterEach
    void donSau() {
        don();
    }

    @Test
    @DisplayName("⛔⛔ Đặt trưởng/phó đơn vị QUA API rồi đọc lại — hai cột ấy trước nay ⛔ có đường ghi nào")
    void datVaDocLaiQuaApi() {
        ResponseEntity<String> sua = phien.goi(
                admin, HttpMethod.PUT, "/api/v1/org-units/" + donViPublic, donViJson(truongPublic, phoPublic));
        assertThat(sua.getStatusCode()).as("%s", sua.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject("SELECT head_user_id FROM org_units WHERE id = ?", Long.class, donViId))
                .as("⛔⛔ cột NGƯỜI ĐỌC của cảnh báo G11 — ⛔ ghi được thì nhánh ấy rỗng vĩnh viễn")
                .isEqualTo(truongId);

        ResponseEntity<String> doc = phien.get(admin, "/api/v1/org-units/" + donViPublic);
        assertThat(doc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(PhienHttp.giaTriJson(doc.getBody(), "headUserPublicId"))
                .as("vòng khứ hồi (T47.17): ghi rồi đọc lại phải ra ĐÚNG người, ⛔ phải rỗng")
                .isEqualTo(truongPublic.toString());
        assertThat(PhienHttp.giaTriJson(doc.getBody(), "deputyUserPublicId")).isEqualTo(phoPublic.toString());

        // Bỏ trống = GỠ chức danh. Thiếu vế này thì một lượt sửa tên đơn vị sẽ âm thầm giữ lại
        // người cũ, và ⛔ có cách nào để một đơn vị "chưa có trưởng" (quy tắc 16).
        ResponseEntity<String> go =
                phien.goi(admin, HttpMethod.PUT, "/api/v1/org-units/" + donViPublic, donViJson(null, null));
        assertThat(go.getStatusCode()).as("%s", go.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT head_user_id FROM org_units WHERE id = ?", Long.class, donViId))
                .isNull();
    }

    @Test
    @DisplayName("⚠ Tài khoản đã KHOÁ ⛔ được làm trưởng đơn vị — người nhận cảnh báo phải là người còn đăng nhập được")
    void taiKhoanKhoaBiTuChoi() {
        UUID khoa = (UUID) themNguoi(TIEN_TO + "khoa", "LOCKED")[1];

        ResponseEntity<String> sua =
                phien.goi(admin, HttpMethod.PUT, "/api/v1/org-units/" + donViPublic, donViJson(khoa, null));

        assertThat(sua.getStatusCode()).as("%s", sua.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sua.getBody()).contains("ADM-2026");
        assertThat(jdbc.queryForObject("SELECT head_user_id FROM org_units WHERE id = ?", Long.class, donViId))
                .isNull();
    }

    @Test
    @DisplayName("⚠ Một người ⛔ vừa là trưởng vừa là phó — hai ô ấy là hai vai, ⛔ phải hai bản sao")
    void truongTrungPhoBiTuChoi() {
        ResponseEntity<String> sua = phien.goi(
                admin, HttpMethod.PUT, "/api/v1/org-units/" + donViPublic, donViJson(truongPublic, truongPublic));

        assertThat(sua.getStatusCode()).as("%s", sua.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sua.getBody()).contains("ADM-2026");
    }

    @Test
    @DisplayName("⛔⛔⛔ VẾ CHỊU LỰC — số đo vượt ngưỡng phải tới TRƯỞNG ĐƠN VỊ vừa đặt qua API (quy tắc 27)")
    void canhBaoNguongToiTruongDonVi() {
        assertThat(banDieuHanh())
                .as("⚠ TIỀN ĐỀ (luật 9): nhóm Ban điều hành phải RỖNG — ⛔ thì bài xanh vì nhánh dự phòng")
                .isEqualTo("[]");

        assertThat(phien.goi(admin, HttpMethod.PUT, "/api/v1/org-units/" + donViPublic, donViJson(truongPublic, null))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        long idDiemDo = themDiemDo();
        long idLoaiChiSo = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        taoQuyTac(idDiemDo, idLoaiChiSo);

        tx.executeWithoutResult(t -> nguongAlert.danhGia(
                idDiemDo, idLoaiChiSo, Instant.now(), new BigDecimal("9.999"), ReadingQuality.HOP_LE));

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE event_type = ?",
                        Integer.class,
                        NguongAlertService.SU_KIEN_VUOT_NGUONG))
                .as("⚠ vế chống tập rỗng: ⛔ có thông báo nào thì phép đếm người nhận ⛔ nói gì")
                .isEqualTo(1);

        assertThat(jdbc.queryForList(
                        """
                        SELECT r.user_id FROM notification_recipients r
                          JOIN notifications n ON n.id = r.notification_id
                         WHERE n.event_type = ?
                        """,
                        Long.class,
                        NguongAlertService.SU_KIEN_VUOT_NGUONG))
                .as("⛔⛔ cảnh báo ngưỡng của điểm đo thuộc đơn vị này phải tới TRƯỞNG đơn vị. "
                        + "Tập rỗng ⇒ alert_events có hàng, màn hình có dòng, và ⛔ ai được báo.")
                .contains(truongId);
    }

    // ---- Trợ giúp -----------------------------------------------------------

    private void taoVaiTro() {
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử H24', 'Tạm, xoá ở cuối bài', FALSE, now())",
                VAI_TRO);
        java.util.List<String> quyen = java.util.List.of("adm:org-unit:view", "adm:org-unit:manage");
        int gan = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN (?, ?)",
                VAI_TRO,
                quyen.get(0),
                quyen.get(1));
        assertThat(gan)
                .as("⚠ chống tập rỗng: mã quyền đổi tên thì gán thiếu trong im lặng")
                .isEqualTo(quyen.size());
    }

    private static String donViJson(UUID truong, UUID pho) {
        return """
                {"name":"Đơn vị kiểm thử H24","shortName":"H24","unitType":"XI_NGHIEP",
                 "address":null,"phone":null,"email":null,
                 "headUserPublicId":%s,"deputyUserPublicId":%s}"""
                .formatted(truong == null ? "null" : "\"" + truong + "\"", pho == null ? "null" : "\"" + pho + "\"");
    }

    private String banDieuHanh() {
        return jdbc.queryForObject(
                "SELECT coalesce(setting_value, default_value) FROM settings WHERE setting_key = ?",
                String.class,
                RecipientResolver.KEY_EXECUTIVE_BOARD);
    }

    private Object[] themNguoi(String ten, String trangThai) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (public_id, username, full_name, email, password_hash, status,
                                   org_unit_id, must_change_password, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'x', ?, ?, FALSE, now())
                RETURNING id, public_id
                """,
                (rs, n) -> new Object[] {rs.getLong("id"), rs.getObject("public_id")},
                ten,
                "Cán bộ " + ten,
                ten + "@example.invalid",
                trangThai,
                donViId);
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

    private long themDiemDo() {
        Long idNguon = jdbc.queryForObject(
                "SELECT id FROM api_sources WHERE deleted_at IS NULL ORDER BY id LIMIT 1", Long.class);
        assertThat(idNguon).as("⚠ vế chống tập rỗng: phải có nguồn seed").isNotNull();
        return jdbc.queryForObject(
                """
                INSERT INTO stations (code, name, api_code, api_source_id, position_role, active,
                                      org_unit_id, created_at)
                VALUES (?, 'Điểm đo kiểm thử H24', ?, ?, 'MN_SONG', TRUE, ?, now())
                RETURNING id
                """,
                Long.class,
                MA_DIEM_DO,
                MA_API,
                idNguon,
                donViId);
    }

    private void taoQuyTac(long idDiemDo, long idLoaiChiSo) {
        Long idMuc = jdbc.queryForObject(
                "INSERT INTO alert_levels (code, name, color_token, severity_rank, active, created_at) "
                        + "VALUES (?, 'Mức kiểm thử H24', 'alert-level-1', 176, TRUE, now()) RETURNING id",
                Long.class,
                MA_MUC);
        jdbc.update(
                """
                INSERT INTO alert_rules (station_id, measurement_type_id, alert_level_id, condition_type,
                                         threshold_value, delay_minutes, active, created_at)
                VALUES (?, ?, ?, 'GT', 2.000, 0, TRUE, now())
                """,
                idDiemDo,
                idLoaiChiSo,
                idMuc);
    }

    /** ⛔ Dọn theo thứ tự khoá ngoại, và dọn TRƯỚC mỗi lượt — lượt trước bị ngắt để lại rác (T48.8). */
    private void don() {
        jdbc.update(
                "DELETE FROM notification_recipients r USING notifications n "
                        + "WHERE n.id = r.notification_id AND n.event_type = ?",
                NguongAlertService.SU_KIEN_VUOT_NGUONG);
        jdbc.update("DELETE FROM notifications WHERE event_type = ?", NguongAlertService.SU_KIEN_VUOT_NGUONG);
        jdbc.update(
                "DELETE FROM alert_events WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update("DELETE FROM alert_rules WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update("DELETE FROM alert_levels WHERE code = ?", MA_MUC);
        jdbc.update(
                "DELETE FROM hydro_latest WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update(
                "DELETE FROM hydro_readings WHERE station_id IN (SELECT id FROM stations WHERE code = ?)", MA_DIEM_DO);
        jdbc.update("DELETE FROM stations WHERE code = ?", MA_DIEM_DO);

        // ⛔ Trả hai cột về NULL TRƯỚC khi xoá tài khoản — hai khoá ngoại org_units → users là hai
        //    khoá DUY NHẤT ⛔ mang `ON DELETE CASCADE` (T28.51); mọi bảng user_id khác tự dọn theo.
        jdbc.update("UPDATE org_units SET head_user_id = NULL, deputy_user_id = NULL WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM users WHERE username LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO);
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");

        // ⚠ Trả nhóm Ban điều hành về ĐÚNG `default_value`, ⛔ về một hằng ghi cứng (bản sao lệch trong im lặng).
        jdbc.update(
                "UPDATE settings SET setting_value = default_value WHERE setting_key = ?",
                RecipientResolver.KEY_EXECUTIVE_BOARD);
        thamSo.invalidate(RecipientResolver.KEY_EXECUTIVE_BOARD);
    }
}
