package com.songnhue.app.org;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Giải thể đơn vị — CN-04.1, đo <b>qua HTTP</b>.
 *
 * <h2>⛔⛔ Bảo đảm của đặc tả trước lượt này chỉ đúng MỘT PHẦN BA</h2>
 *
 * <p>{@code function-spec.md:616}: <i>"giải thể/xóa đơn vị chỉ khi ⛔ <b>không còn nhân viên/công
 * trình liên kết</b>"</i>. Bản trước kiểm <b>đơn vị cấp dưới</b> và <b>tài khoản</b> — hai thứ đặc
 * tả ⛔ không nêu — và ⛔ <b>không kiểm</b> hai thứ đặc tả nêu đích danh.
 *
 * <p>⚠ Xoá <b>mềm</b> nên khoá ngoại ⛔ không nổ; triệu chứng thật là hồ sơ CBNV mất ô <i>"Đơn
 * vị"</i> và báo cáo <i>"nhân sự theo phòng ban"</i> <b>đếm thiếu</b> — một con số sai mà ⛔ không
 * dòng lỗi nào. Đó là lý do bài này khẳng định <b>cả hai</b> vế: từ chối khi còn, VÀ cho phép khi
 * đã chuyển đi (thiếu vế sau thì một hệ từ chối MỌI lượt giải thể cũng xanh — luật 9).
 *
 * <p>Bài đo <b>qua HTTP</b> vì chuỗi {@code OrgUnitUsagePort} chỉ được Spring gom lại trong một
 * context đầy đủ: gọi thẳng service ở một bài đơn vị sẽ nhận một {@code List} <b>rỗng</b> và xanh
 * trọn vẹn — đúng thứ luật 7 mô tả.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GiaiTheDonViHttpTest extends IntegrationTestBase {

    private static final String DUONG = "/api/v1/org-units";
    private static final String TIEN_TO = "GIAITHE-";
    private static final String VAI_TRO = "KIEMTRA_GIAITHE";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthorityLoader authorities;

    private PhienHttp phienHttp;
    private PhienHttp.Phien quanTri;
    private long idGoc;
    private UUID chucVu;

    @BeforeAll
    void dungNen() {
        don();
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử giải thể', 'Tạm, xoá ở @AfterAll', FALSE, now())",
                VAI_TRO);
        int gan = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN (?, ?, ?, ?, ?)",
                VAI_TRO,
                "adm:org-unit:view",
                "adm:org-unit:manage",
                "hr:employee:view",
                "hr:employee:create",
                "hr:employee:delete");
        assertThat(gan)
                .as("⚠ Chống tập rỗng: một mã quyền đổi tên thì lệnh trên gán ít dòng hơn TRONG IM "
                        + "LẶNG và mọi bài dưới đỏ với 403")
                .isEqualTo(5);

        idGoc = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        phienHttp = new PhienHttp(http);
        quanTri = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "giaithe", VAI_TRO));
        chucVu = taoChucVu();
    }

    @AfterAll
    void donSach() {
        don();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employees WHERE code LIKE ?", Integer.class, TIEN_TO + "%"))
                .as("⛔⛔ G6-a chưa có dữ liệu và CLAUDE.md cấm seed hồ sơ CBNV — bảng phải RỖNG lại")
                .isZero();
    }

    @Test
    @DisplayName("⛔⛔ Còn hồ sơ CBNV thì ⛔ KHÔNG giải thể được — và câu lỗi NÓI RÕ còn bao nhiêu")
    void conHoSoCbnvThiKhongGiaiTheDuoc() {
        UUID donVi = taoDonVi("PB-01");
        UUID hoSo = taoHoSo("NV-001", donVi);

        ResponseEntity<String> tuChoi = phienHttp.goi(quanTri, HttpMethod.DELETE, DUONG + "/" + donVi, null);
        assertThat(tuChoi.getStatusCode())
                .as(
                        "⛔⛔ Trước 10/09/2026 lượt này trả **204**: guard viết ở Phase 0 chỉ kiểm đơn vị "
                                + "cấp dưới và tài khoản, ⛔ không kiểm hồ sơ CBNV — đúng thứ đặc tả nêu đích "
                                + "danh. Thân: %s",
                        tuChoi.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(tuChoi.getBody()).contains("ADM-2004");
        assertThat(tuChoi.getBody())
                .as("⛔ Câu lỗi phải nói người vận hành PHẢI ĐI CHUYỂN CÁI GÌ — một lời từ chối trống "
                        + "làm họ ⛔ không biết bắt đầu từ đâu")
                .contains("hồ sơ cán bộ nhân viên");

        // Anti-empty-set + vế phân biệt: đơn vị PHẢI còn sống sau lượt từ chối.
        assertThat(conSong(donVi)).as("⛔ Bị từ chối thì ⛔ không được xoá gì cả").isTrue();

        // ⭐ VẾ PHẢI-THÀNH-CÔNG: chuyển hồ sơ đi rồi thì giải thể ĐƯỢC. Thiếu vế này thì một hệ từ
        //    chối MỌI lượt giải thể cũng xanh trọn vẹn (luật 9).
        assertThat(jdbc.update("UPDATE employees SET org_unit_id = ? WHERE public_id = ?", idGoc, hoSo))
                .isEqualTo(1);
        ResponseEntity<String> lanHai = phienHttp.goi(quanTri, HttpMethod.DELETE, DUONG + "/" + donVi, null);
        assertThat(lanHai.getStatusCode()).as("%s", lanHai.getBody()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(conSong(donVi)).isFalse();
    }

    @Test
    @DisplayName("⭐ Hồ sơ đã XOÁ MỀM ⛔ không chặn — nhưng hồ sơ của người ĐÃ NGHỈ VIỆC thì CÓ")
    void hoSoDaXoaMemKhongChanNhungNguoiDaNghiThiChan() {
        UUID donVi = taoDonVi("PB-02");
        UUID hoSoNghi = taoHoSo("NV-002", donVi);
        jdbc.update(
                "UPDATE employees SET status = 'NGHI_VIEC', terminated_at = DATE '2026-08-31' WHERE public_id = ?",
                hoSoNghi);

        // ⛔⛔ Vế phân biệt quan trọng: hồ sơ của người đã nghỉ việc VẪN trỏ vào đơn vị và báo cáo
        //    "biến động nhân sự theo phòng ban" VẪN đọc nó. Bỏ qua họ là để một lượt giải thể làm
        //    hỏng đúng báo cáo lịch sử.
        assertThat(phienHttp
                        .goi(quanTri, HttpMethod.DELETE, DUONG + "/" + donVi, null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // Còn hồ sơ đã XOÁ MỀM thì ⛔ không chặn — nó ⛔ không còn ra khỏi API nào nữa.
        assertThat(phienHttp
                        .goi(quanTri, HttpMethod.DELETE, "/api/v1/hr/employees/" + hoSoNghi, null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(phienHttp
                        .goi(quanTri, HttpMethod.DELETE, DUONG + "/" + donVi, null)
                        .getStatusCode())
                .as("⛔ Hồ sơ đã xoá mềm ⛔ không được chặn mãi mãi một đơn vị — nếu ⛔ không thì ⛔ "
                        + "không đơn vị nào giải thể được sau vài năm vận hành")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("⛔ Đơn vị TRỐNG thì giải thể được ngay — vế phân biệt của cả lớp")
    void donViTrongThiGiaiTheDuoc() {
        // Thiếu bài này thì hai bài trên xanh cả khi ai đó chặn MỌI lượt giải thể.
        UUID donVi = taoDonVi("PB-03");
        assertThat(phienHttp
                        .goi(quanTri, HttpMethod.DELETE, DUONG + "/" + donVi, null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(conSong(donVi)).isFalse();
    }

    // ===================== Trợ giúp =====================

    private UUID taoDonVi(String hau) {
        UUID chaPublicId = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, idGoc);
        ResponseEntity<String> tao = phienHttp.goi(
                quanTri,
                HttpMethod.POST,
                DUONG,
                """
                {"code":"%s","name":"Phòng kiểm thử %s","parentPublicId":"%s","unitType":"PHONG_BAN"}"""
                        .formatted(TIEN_TO + hau, hau, chaPublicId));
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private UUID taoChucVu() {
        ResponseEntity<String> tao = phienHttp.goi(
                quanTri,
                HttpMethod.POST,
                "/api/v1/hr/positions",
                """
                {"code":"%sCV","name":"Chuyên viên (kiểm thử giải thể)","sortOrder":10,"active":true}"""
                        .formatted(TIEN_TO));
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private UUID taoHoSo(String hau, UUID donVi) {
        ResponseEntity<String> tao = phienHttp.goi(
                quanTri,
                HttpMethod.POST,
                "/api/v1/hr/employees",
                """
                {"code":"%s","fullName":"Vũ Thị Mai","orgUnitId":"%s","positionId":"%s",\
                "hiredAt":"2018-01-02","contractType":"KHONG_XAC_DINH_THOI_HAN","status":"DANG_LAM"}"""
                        .formatted(TIEN_TO + hau, donVi, chucVu));
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private boolean conSong(UUID donVi) {
        Integer so = jdbc.queryForObject(
                "SELECT count(*) FROM org_units WHERE public_id = ? AND deleted_at IS NULL", Integer.class, donVi);
        return so != null && so > 0;
    }

    private void don() {
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM positions WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        for (String vt : List.of(VAI_TRO)) {
            jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM roles WHERE code = ?", vt);
        }
        authorities.invalidateAll();
    }

    private static String chuoi(String json, String truong) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(truong) + "\":\\s*(null|\"([^\"]*)\"|[^,}\\]]+)")
                .matcher(json == null ? "" : json);
        if (!m.find()) {
            return null;
        }
        return m.group(2) != null ? m.group(2) : m.group(1);
    }
}
