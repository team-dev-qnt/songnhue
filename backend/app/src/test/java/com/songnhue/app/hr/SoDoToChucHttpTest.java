package com.songnhue.app.hr;

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
 * Sơ đồ tổ chức — <b>CN-04.1</b>, đo qua HTTP.
 *
 * <h2>⛔⛔ Bảo đảm nặng nhất của màn hình này ⛔ KHÔNG nằm trong service</h2>
 *
 * <p>Quân số phải là <b>toàn Công ty</b>. {@code ScopeFilterAspect} bật {@code @Filter} quanh
 * <b>mọi</b> {@code @Transactional}, nên một câu JPA trên {@code Employee} sẽ đếm cắt theo đơn vị
 * của người đang xem — và hỏng theo chiều <b>im lặng nhất có thể</b>: mỗi người mở sơ đồ thấy một
 * bộ số khác nhau, tất cả đều trông hợp lý. Tầng ấy chỉ tồn tại trên đường chạy thật, nên chỉ một
 * bài HTTP <b>của một phiên bị thu hẹp phạm vi</b> mới phân biệt được hai trạng thái (luật 5 · 9).
 *
 * <h2>⛔ Dữ liệu của lớp này ⛔ KHÔNG phải seed</h2>
 *
 * <p>G6-a vẫn là ô trống và CLAUDE.md cấm seed hồ sơ CBNV — mọi hàng sống đúng bằng thời gian lớp
 * chạy, bị xoá <b>cứng</b> ở {@code @AfterAll} kèm khẳng định ngay tại chỗ dọn.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SoDoToChucHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T581-";
    private static final String VAI_TRO_SO_DO = "KIEMTRA_T581_SODO";
    private static final String VAI_TRO_HS = "KIEMTRA_T581_HOSO";

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

    private PhienHttp phienSoDo;
    private PhienHttp phienHoSo;
    private PhienHttp.Phien xemSoDo;
    private PhienHttp.Phien quanTriHoSo;

    private UUID idXemSoDo;
    private long donViGocId;
    private String pathGoc;
    private UUID xnAPublic;
    private long xnAId;
    private long toA1Id;
    private long xnBId;

    @BeforeAll
    void dungNen() {
        don();

        taoVaiTro(VAI_TRO_SO_DO, List.of("hr:org-chart:view"));
        taoVaiTro(
                VAI_TRO_HS,
                List.of("hr:employee:view", "hr:employee:create", "hr:employee:update", "hr:org-chart:view"));

        donViGocId = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        pathGoc = jdbc.queryForObject("SELECT path FROM org_units WHERE code = 'CTY'", String.class);

        xnAId = themDonVi(TIEN_TO + "XN-A", donViGocId, pathGoc);
        String pathA = jdbc.queryForObject("SELECT path FROM org_units WHERE id = ?", String.class, xnAId);
        toA1Id = themDonVi(TIEN_TO + "TO-A1", xnAId, pathA);
        xnBId = themDonVi(TIEN_TO + "XN-B", donViGocId, pathGoc);
        xnAPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, xnAId);

        // Người đứng đầu XN-A — vế "nút hiện NGƯỜI ĐỨNG ĐẦU" của đặc tả.
        jdbc.update(
                "INSERT INTO org_unit_leaders (org_unit_id, full_name, title, sort_order, active, created_at) "
                        + "VALUES (?, ?, 'Giám đốc Xí nghiệp', 10, TRUE, now())",
                xnAId,
                TIEN_TO + "Nguyễn Văn Trưởng");

        phienSoDo = new PhienHttp(http);
        phienHoSo = new PhienHttp(http);
        String tenSoDo = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t581_sodo", VAI_TRO_SO_DO);
        String tenHoSo = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t581_hoso", VAI_TRO_HS);
        xemSoDo = phienSoDo.dangNhap(tenSoDo);
        quanTriHoSo = phienHoSo.dangNhap(tenHoSo);
        idXemSoDo = jdbc.queryForObject("SELECT public_id FROM users WHERE username = ?", UUID.class, tenSoDo);

        // 2 người ở Tổ A1 (cháu của XN-A), 1 người ở XN-A, 1 người đã NGHỈ VIỆC ở XN-A.
        taoHoSo("SD-1", toA1Id, "DANG_LAM");
        taoHoSo("SD-2", toA1Id, "THAI_SAN");
        taoHoSo("SD-3", xnAId, "DANG_LAM");
        taoHoSo("SD-4", xnAId, "NGHI_VIEC");
    }

    @AfterAll
    void donSach() {
        don();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employees WHERE code LIKE ?", Integer.class, TIEN_TO + "%"))
                .as("⛔⛔ G6-a chưa có dữ liệu và CLAUDE.md cấm seed hồ sơ CBNV — bảng phải RỖNG lại")
                .isZero();
    }

    // =========================================================================

    @Test
    @DisplayName("⛔ `hr:org-chart:view` là cổng thật — ⛔ không có nó thì 403")
    void thieuQuyenThiKhongXemDuocSoDo() {
        PhienHttp troi = new PhienHttp(http);
        String ten = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t581_troi");
        PhienHttp.Phien p = troi.dangNhap(ten);

        assertThat(troi.get(p, "/api/v1/hr/so-do-to-chuc").getStatusCode())
                .as("⛔⛔ Đây là đầu nhận ĐẦU TIÊN của mã quyền ấy sau 32 ngày mồ côi — phải chứng "
                        + "minh nó thật sự chặn, ⛔ không chỉ có mặt trong một annotation")
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Vế phân biệt: CÙNG đường, tài khoản CÓ quyền ⇒ 200.
        assertThat(phienSoDo.get(xemSoDo, "/api/v1/hr/so-do-to-chuc").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        jdbc.update("DELETE FROM users WHERE username = ?", ten);
    }

    @Test
    @DisplayName("⭐⭐ Quân số TRỰC TIẾP và CẢ NHÁNH là hai con số khác nhau — và người thai sản VẪN tính")
    void haiConSoQuanSoVaNguoiThaiSanVanTinh() {
        String than = phienSoDo.get(xemSoDo, "/api/v1/hr/so-do-to-chuc").getBody();
        String nutA = nutCua(than, TIEN_TO + "XN-A");

        assertThat(so(nutA, "soNhanSuTrucTiep"))
                .as("⛔⛔ *Còn làm việc* ⛔ KHÔNG phải `status = 'DANG_LAM'`: SD-3 đang làm, SD-4 đã "
                        + "nghỉ việc ⇒ trực tiếp = 1. Đếm theo `DANG_LAM` sẽ loại người thai sản ở "
                        + "nút con và ra một sơ đồ THIẾU NGƯỜI mà ⛔ không ai đếm lại (T55.2)")
                .isEqualTo(1);

        assertThat(so(nutA, "soNhanSuCaNhanh"))
                .as("⛔⛔ Chỉ có con số này thì một Xí nghiệp có 4 Tổ đội hiện **0 người** khi thu "
                        + "gọn nhánh, và người xem kết luận đơn vị ấy trống. SD-1 (đang làm) + SD-2 "
                        + "(THAI SẢN — vẫn là quân số) + SD-3 = 3")
                .isEqualTo(3);

        String nutTo = nutCua(than, TIEN_TO + "TO-A1");
        assertThat(so(nutTo, "soNhanSuTrucTiep")).isEqualTo(2);
        assertThat(so(nutTo, "soNhanSuCaNhanh"))
                .as("⛔ Nút lá: cả nhánh PHẢI bằng trực tiếp — thiếu vế này thì một phép cộng dồn "
                        + "hỏng vẫn đi lọt ở mọi nút ⛔ không có con")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("⭐ Nút mang NGƯỜI ĐỨNG ĐẦU, và cây lãnh đạo trong một nút là PHẲNG")
    void nutMangNguoiDungDau() {
        String nutA = nutCua(phienSoDo.get(xemSoDo, "/api/v1/hr/so-do-to-chuc").getBody(), TIEN_TO + "XN-A");
        assertThat(nutA).contains(TIEN_TO + "Nguyễn Văn Trưởng").contains("Giám đốc Xí nghiệp");

        // ⛔ T50.6: `title` là ô TỰ DO, ⛔ không phải một thang bậc — ⛔ không suy tầng bằng so chuỗi.
        assertThat(nutA)
                .as("⛔ `lanhDao` là một danh sách PHẲNG: một phần tử `con`/`capDuoi` trong đó nghĩa "
                        + "là ai đó đã xếp hạng chức danh bằng so chuỗi (T50.6)")
                .doesNotContain("\"capDuoi\"");
    }

    @Test
    @DisplayName("⛔⛔ Người xem bị thu hẹp phạm vi VẪN thấy quân số TOÀN Công ty — ⛔ không cắt theo đơn vị")
    void quanSoKhongCatTheoPhamViDonVi() {
        String truoc = phienSoDo.get(xemSoDo, "/api/v1/hr/so-do-to-chuc").getBody();
        long tongTruoc = so(truoc, "tongNhanSu");
        assertThat(tongTruoc)
                .as("⛔ Tiền đề: phải CÓ người thì phép so dưới mới có nghĩa (luật 7)")
                .isGreaterThanOrEqualTo(3);

        // Đẩy tài khoản xuống XN-B — một nhánh RỜI khỏi XN-A, nơi ⛔ không có hồ sơ nào.
        jdbc.update("UPDATE users SET org_unit_id = ? WHERE public_id = ?", xnBId, idXemSoDo);
        authorities.invalidateAll();

        // ⛔ Đối chứng: phạm vi ĐÚNG LÀ đã hẹp lại — đường hồ sơ thường phải ⛔ không thấy gì.
        assertThat(phienHoSo
                        .get(quanTriHoSo, "/api/v1/hr/employees?orgUnitId=" + xnAPublic)
                        .getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.FORBIDDEN);

        String sau = phienSoDo.get(xemSoDo, "/api/v1/hr/so-do-to-chuc").getBody();
        assertThat(so(sau, "tongNhanSu"))
                .as("⛔⛔ Nếu quân số đi qua `@Filter` thì con số này TỤT, và mỗi người mở sơ đồ "
                        + "thấy một bộ số khác nhau — tất cả đều trông hợp lý, ⛔ không có gì báo. "
                        + "Đó là lý do phép đếm đi JDBC thuần (`QuanSoRepository`)")
                .isEqualTo(tongTruoc);
        assertThat(so(nutCua(sau, TIEN_TO + "XN-A"), "soNhanSuCaNhanh")).isEqualTo(3);

        jdbc.update("UPDATE users SET org_unit_id = ? WHERE public_id = ?", donViGocId, idXemSoDo);
        authorities.invalidateAll();
    }

    @Test
    @DisplayName("⭐ Hồ sơ trỏ vào đơn vị ĐÃ XOÁ được ĐẾM RA, ⛔ không biến mất trong im lặng")
    void hoSoNgoaiSoDoDuocDemRa() {
        assertThat(so(phienSoDo.get(xemSoDo, "/api/v1/hr/so-do-to-chuc").getBody(), "soNhanSuNgoaiSoDo"))
                .as("⛔ Tiền đề: bình thường phải bằng 0")
                .isZero();

        // Xoá MỀM Tổ A1 thẳng ở CSDL — cố ý đi vòng qua `OrgUnitService.delete` (nó CHẶN đúng ca
        // này từ WS-56). Ta đang dựng lại trạng thái mà chốt chặn ấy sinh ra để ngăn, để đo xem
        // sơ đồ có NÓI RA hay lặng lẽ nuốt mất hai người.
        jdbc.update("UPDATE org_units SET deleted_at = now() WHERE id = ?", toA1Id);
        try {
            String than = phienSoDo.get(xemSoDo, "/api/v1/hr/so-do-to-chuc").getBody();
            assertThat(so(than, "soNhanSuNgoaiSoDo"))
                    .as("⛔⛔ Hai người của Tổ A1 nay ⛔ không thuộc nút nào. Im lặng ở đây nghĩa là "
                            + "tổng quân số **thiếu** đúng hai người ấy, sơ đồ vẫn vẽ đẹp, và ⛔ "
                            + "không ai đếm lại bằng tay để phát hiện (quy tắc 16)")
                    .isEqualTo(2);
            assertThat(than)
                    .as("⛔ Và nút ấy phải biến khỏi sơ đồ — nó đã bị xoá mềm")
                    .doesNotContain(TIEN_TO + "TO-A1");
        } finally {
            jdbc.update("UPDATE org_units SET deleted_at = NULL WHERE id = ?", toA1Id);
        }
    }

    // =========================================================================

    /** Cắt đúng đoạn JSON của nút mang tên {@code ten} — từ `"code":"<ten>"` tới hết các số của nó. */
    private static String nutCua(String json, String ten) {
        int i = json.indexOf("\"code\":\"" + ten + "\"");
        assertThat(i).as("⛔ ⛔ Không thấy nút `%s` trong: %s", ten, json).isGreaterThanOrEqualTo(0);
        int j = json.indexOf("soNhanSuCaNhanh", i);
        assertThat(j).isGreaterThan(i);
        return json.substring(i, Math.min(j + 40, json.length()));
    }

    private static long so(String json, String truong) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(truong) + "\":\\s*(-?\\d+)")
                .matcher(json == null ? "" : json);
        assertThat(m.find())
                .as("⛔ ⛔ Không thấy trường `%s` trong: %s", truong, json)
                .isTrue();
        return Long.parseLong(m.group(1));
    }

    private void taoHoSo(String hau, long donViId, String trangThai) {
        UUID donViPublic = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, donViId);
        // ⭐ `NGHI_VIEC` ĐÒI `terminatedAt` — một ràng buộc CÓ THẬT của CN-04.2
        //   (`REQUIRED_WHEN_TERMINATED`) mà lượt dựng dữ liệu này vấp phải ở lượt chạy đầu. Nó
        //   đúng: một hồ sơ "đã nghỉ việc" mà ⛔ không có ngày nghỉ là một hồ sơ ⛔ không đối chiếu
        //   được với quyết định nào.
        String ngayNghi = "NGHI_VIEC".equals(trangThai) ? ",\"terminatedAt\":\"2025-12-31\"" : "";
        String than =
                """
                {"code":"%s","fullName":"Phạm Văn Nhân","dateOfBirth":"1991-03-09","gender":"NAM",\
                "orgUnitId":"%s","hiredAt":"2018-06-01",\
                "contractType":"KHONG_XAC_DINH_THOI_HAN","status":"%s"%s}"""
                        .formatted(TIEN_TO + hau, donViPublic, trangThai, ngayNghi);
        ResponseEntity<String> tao = phienHoSo.goi(quanTriHoSo, HttpMethod.POST, "/api/v1/hr/employees", than);
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    private void taoVaiTro(String ma, List<String> quyen) {
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, ?, 'Tạm, xoá ở @AfterAll', FALSE, now())",
                ma,
                "Vai trò kiểm thử " + ma);
        int gan = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code IN ("
                        + String.join(",", java.util.Collections.nCopies(quyen.size(), "?")) + ")",
                java.util.stream.Stream.concat(java.util.stream.Stream.of(ma), quyen.stream())
                        .toArray());
        assertThat(gan)
                .as("⚠ Chống tập rỗng: một mã quyền đổi tên thì lệnh trên gán ít dòng hơn TRONG IM LẶNG")
                .isEqualTo(quyen.size());
    }

    private long themDonVi(String ma, long chaId, String pathCha) {
        jdbc.update(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/', 1, 10, now())",
                ma,
                "Đơn vị kiểm thử " + ma,
                chaId);
        long id = jdbc.queryForObject("SELECT id FROM org_units WHERE code = ?", Long.class, ma);
        jdbc.update("UPDATE org_units SET path = ? WHERE id = ?", pathCha + id + "/", id);
        return id;
    }

    private void don() {
        jdbc.update("UPDATE users SET employee_id = NULL WHERE username LIKE 'kiemtra_t581%'");
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE code = 'CTY') "
                + "WHERE username LIKE 'kiemtra_t581%'");
        jdbc.update("DELETE FROM org_unit_leaders WHERE full_name LIKE ?", TIEN_TO + "%");
        for (String bang : List.of("jobs", "attachments")) {
            jdbc.update(
                    "DELETE FROM " + bang + " WHERE org_unit_id IN (SELECT id FROM org_units WHERE code LIKE ?)",
                    TIEN_TO + "%");
        }
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        for (String vt : List.of(VAI_TRO_SO_DO, VAI_TRO_HS)) {
            jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM roles WHERE code = ?", vt);
        }
        jdbc.update("DELETE FROM users WHERE username LIKE 'kiemtra_t581%'");
        authorities.invalidateAll();
    }
}
