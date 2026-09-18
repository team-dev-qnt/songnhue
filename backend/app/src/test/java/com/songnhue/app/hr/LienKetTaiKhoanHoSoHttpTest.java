package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
 * Liên kết tài khoản ↔ hồ sơ CBNV, và vế thứ hai của CN-04.7 — <b>T51.8</b>, đo qua HTTP.
 *
 * <h2>⭐ Vòng khép kín mà lượt này trả nợ</h2>
 *
 * <p>Cột {@code users.employee_id} có <b>0 đường ghi</b> suốt 28 ngày (đo 10/09/2026), trong khi
 * {@code EmployeeSensitiveService} tự khai rằng vế <i>"chính nhân viên đó xem được trường 🔒 của
 * mình"</i> ⛔ không dựng được vì thiếu đúng cột ấy. Đây là <b>nửa ghi + nửa đọc</b> của cùng một
 * cặp, và luật 27 nói cách đếm đúng là đếm <i>vòng</i>, ⛔ không đếm tính năng.
 *
 * <h2>Vì sao qua HTTP (luật 5) — ba tầng ⛔ không lượt gọi service nào chạm tới</h2>
 *
 * <ul>
 *   <li><b>{@code AuthorityLoader}</b> — cache in-process TTL 30 giây, <b>ngoài</b> transaction.
 *       Gọi thẳng service rồi đọc lại CSDL sẽ xanh cả khi ⛔ không ai gọi {@code invalidate()}, vì
 *       CSDL luôn đúng ngay; thứ sai là thứ người dùng gặp — <i>gỡ liên kết rồi mà vẫn đọc được
 *       trường 🔒 thêm nửa phút</i>. Chỉ hai lượt HTTP của <b>một phiên khác</b>, trước và sau, mới
 *       phân biệt được hai trạng thái ấy (luật 9).
 *   <li><b>Cổng quyền tầng 2</b> — bảo đảm lớn nhất ở đây là {@code /hr/ho-so-cua-toi} chạy được
 *       cho một tài khoản <b>⛔ không có một quyền {@code hr:*} nào</b>. Một bài gọi service ⛔
 *       không có {@code @RequirePermission} nào để đi qua.
 *   <li><b>Phạm vi đơn vị</b> — {@code ScopeFilterAspect} bật bộ lọc <b>quanh</b> transaction.
 * </ul>
 *
 * <h2>⛔ Dữ liệu của lớp này ⛔ KHÔNG phải seed</h2>
 *
 * <p>G6-a vẫn là ô trống và CLAUDE.md cấm seed hồ sơ CBNV. Mọi hàng sống đúng bằng thời gian lớp
 * chạy, bị xoá <b>cứng</b> ở {@code @AfterAll}, và có khẳng định ngay tại chỗ dọn.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LienKetTaiKhoanHoSoHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T518-";
    private static final String VAI_TRO_QT = "KIEMTRA_T518_QUANTRI";
    private static final String VAI_TRO_XEM_HS = "KIEMTRA_T518_XEMHOSO";

    /** Quyền của người quản trị dựng dữ liệu nền + đi liên kết. */
    private static final List<String> QUYEN_QT = List.of(
            "adm:user:view",
            "adm:user:update",
            "hr:employee:view",
            "hr:employee:create",
            "hr:employee:update",
            "hr:employee:view-sensitive");

    /**
     * ⛔⛔ CCCD phải KHÁC NHAU cho từng hồ sơ của lớp này.
     *
     * <p>{@code employee_sensitive} có cột <b>vân tay HMAC</b> ép tính duy nhất của CCCD (T51.2 —
     * {@code UNIQUE} trên một cột GCM là một bảo đảm ⛔ không bao giờ bắt được gì). Dùng chung một
     * số cho tám hồ sơ thì hồ sơ thứ hai trở đi trả {@code HR-1003}, và mọi bài sau đỏ ở bước dựng
     * dữ liệu nền — một triệu chứng chẳng liên quan gì tới thứ đang kiểm.
     */
    private static final String CCCD_GOC = "0010900";

    private static final String SO_TK = "19001234567890";

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

    private PhienHttp phienQt;
    private PhienHttp phienCanBo;
    private PhienHttp phienXemHoSo;

    private PhienHttp.Phien quanTri;
    private PhienHttp.Phien canBo;
    private PhienHttp.Phien xemHoSo;

    private String tenCanBo;
    private String tenXemHoSo;
    private UUID idCanBo;
    private UUID idXemHoSo;
    private UUID idQuanTri;
    private UUID donViGoc;

    @BeforeAll
    void dungNenVaDangNhap() {
        don();
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử T51.8', 'Tạm, xoá ở @AfterAll', FALSE, now())",
                VAI_TRO_QT);
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò chỉ xem hồ sơ (T51.8)', 'Tạm, xoá ở @AfterAll', FALSE, now())",
                VAI_TRO_XEM_HS);

        int soQuyen = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN (?, ?, ?, ?, ?, ?)",
                VAI_TRO_QT,
                QUYEN_QT.get(0),
                QUYEN_QT.get(1),
                QUYEN_QT.get(2),
                QUYEN_QT.get(3),
                QUYEN_QT.get(4),
                QUYEN_QT.get(5));
        assertThat(soQuyen)
                .as("⚠ Chống tập rỗng: một mã quyền đổi tên thì lệnh trên gán ít dòng hơn TRONG IM LẶNG, "
                        + "và mọi bài dưới đây đỏ với 403 — triệu chứng chẳng liên quan gì tới thứ đang kiểm")
                .isEqualTo(QUYEN_QT.size());

        assertThat(jdbc.update(
                        "INSERT INTO role_permissions (role_id, permission_id) "
                                + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = ?",
                        VAI_TRO_XEM_HS,
                        "hr:employee:view"))
                .isEqualTo(1);

        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);

        phienQt = new PhienHttp(http);
        phienCanBo = new PhienHttp(http);
        phienXemHoSo = new PhienHttp(http);

        String tenQt = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t518_qt", VAI_TRO_QT);
        // ⛔⛔ Tài khoản này CỐ Ý ⛔ không mang một vai trò nào — tức ⛔ không một quyền `hr:*` nào.
        //    Đó chính là vế phải chứng minh: "chính nhân viên đó" ⛔ không biểu diễn được bằng một mã
        //    quyền, nên đường tự đọc phải chạy được mà ⛔ không cần quyền nào cả.
        tenCanBo = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t518_canbo");
        tenXemHoSo = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t518_xemhs", VAI_TRO_XEM_HS);

        quanTri = phienQt.dangNhap(tenQt);
        canBo = phienCanBo.dangNhap(tenCanBo);
        xemHoSo = phienXemHoSo.dangNhap(tenXemHoSo);

        idQuanTri = publicIdCua(tenQt);
        idCanBo = publicIdCua(tenCanBo);
        idXemHoSo = publicIdCua(tenXemHoSo);
    }

    @AfterEach
    void traLienKetVeTrangThaiDau() {
        jdbc.update("UPDATE users SET employee_id = NULL WHERE username LIKE 'kiemtra_t518%'");
        authorities.invalidateAll();
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
    // Vòng khép kín: chưa liên kết → liên kết → tự đọc → gỡ
    // =========================================================================

    @Test
    @DisplayName("Chưa liên kết: /auth/me nói coHoSoNhanSu=false, và /ho-so-cua-toi trả SYS-0004")
    void chuaLienKetThiKhongCoHoSoCuaToi() {
        assertThat(chuoi(phienCanBo.get(canBo, "/api/v1/auth/me").getBody(), "coHoSoNhanSu"))
                .isEqualTo("false");

        ResponseEntity<String> ra = phienCanBo.get(canBo, "/api/v1/hr/ho-so-cua-toi");
        assertThat(ra.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ra.getBody()).contains("SYS-0004");
    }

    @Test
    @DisplayName("⭐⭐ Liên kết xong thì cán bộ ⛔ KHÔNG có quyền hr:* nào vẫn tự đọc được CCCD của mình")
    void lienKetXongThiTuDocDuocTruongKin() {
        UUID hoSo = taoHoSoCoTruongKin("TD-001");

        // ⛔ ĐỐI CHỨNG TRƯỚC: đường của Admin HR phải TỪ CHỐI cán bộ này. Thiếu vế này thì bài dưới
        //    xanh cả khi ai đó vô tình cấp `hr:employee:view-sensitive` cho mọi người (luật 9).
        assertThat(phienCanBo
                        .get(canBo, "/api/v1/hr/employees/" + hoSo + "/sensitive")
                        .getStatusCode())
                .as("⛔ Cán bộ này ⛔ không được đọc trường 🔒 của người khác qua đường của Admin HR")
                .isEqualTo(HttpStatus.FORBIDDEN);

        lienKet(idCanBo, hoSo, HttpStatus.OK);

        assertThat(chuoi(phienCanBo.get(canBo, "/api/v1/auth/me").getBody(), "coHoSoNhanSu"))
                .as("⛔ Liên kết phải có hiệu lực NGAY — `AuthorityLoader.invalidate` là thứ duy nhất "
                        + "làm được điều đó, cache TTL 30 giây nằm NGOÀI transaction")
                .isEqualTo("true");

        ResponseEntity<String> tuDoc = phienCanBo.get(canBo, "/api/v1/hr/ho-so-cua-toi");
        assertThat(tuDoc.getStatusCode()).as("%s", tuDoc.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(chuoi(tuDoc.getBody(), "nationalId"))
                .as("⭐ Vòng khép kín nhập → lưu → hiện: giá trị THẬT phải ra tới đây, ⛔ không phải "
                        + "một ô trống hay một cờ `đã có dữ liệu`")
                .isEqualTo(cccdCua("TD-001"));
        assertThat(chuoi(tuDoc.getBody(), "bankAccount")).isEqualTo(SO_TK);
        assertThat(chuoi(tuDoc.getBody(), "code")).isEqualTo(TIEN_TO + "TD-001");
    }

    @Test
    @DisplayName("⭐⭐ Gỡ liên kết là mất quyền tự đọc NGAY — ⛔ không chờ hết TTL cache 30 giây")
    void goLienKetThiHetHieuLucNgay() {
        UUID hoSo = taoHoSoCoTruongKin("TD-002");
        lienKet(idCanBo, hoSo, HttpStatus.OK);

        // Trạng thái 1: đọc được. Lượt gọi này cũng NẠP cache — đó chính là điều làm bài có nghĩa.
        assertThat(phienCanBo.get(canBo, "/api/v1/hr/ho-so-cua-toi").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        goLienKet(idCanBo);

        // Trạng thái 2: ⛔ không đọc được nữa — NGAY, trong cùng một lượt chạy vài mili giây.
        ResponseEntity<String> sau = phienCanBo.get(canBo, "/api/v1/hr/ho-so-cua-toi");
        assertThat(sau.getStatusCode())
                .as("⛔⛔ Thiếu `authorities.invalidate` ở đường gỡ thì bài này XANH trong tối đa 30 "
                        + "giây nữa — và người vừa bị gỡ vẫn đọc được CCCD của hồ sơ cũ")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chuoi(phienCanBo.get(canBo, "/api/v1/auth/me").getBody(), "coHoSoNhanSu"))
                .isEqualTo("false");
    }

    @Test
    @DisplayName("⛔ Hai tài khoản ⛔ không chia nhau một hồ sơ — ADM-2017 kèm TÊN tài khoản kia")
    void haiTaiKhoanKhongChiaNhauMotHoSo() {
        UUID hoSo = taoHoSoCoTruongKin("TD-003");
        lienKet(idCanBo, hoSo, HttpStatus.OK);

        ResponseEntity<String> hai = lienKet(idXemHoSo, hoSo, HttpStatus.CONFLICT);
        assertThat(hai.getBody()).contains("ADM-2017");
        assertThat(hai.getBody())
                .as("⛔ Thông điệp phải nói RÕ tài khoản nào đang giữ — ⛔ không thì người quản trị "
                        + "⛔ không có đường nào tìm ra chỗ gỡ")
                .contains(tenCanBo);

        // Anti-empty-set: liên kết CŨ phải còn nguyên. Một hệ xoá sạch rồi báo lỗi cũng qua vế trên.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM users WHERE username = ? AND employee_id IS NOT NULL",
                        Integer.class,
                        tenCanBo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔⛔ ⛔ Không tự liên kết tài khoản của CHÍNH MÌNH — ADM-2018, kèm đối chứng phải-thành-công")
    void khongTuLienKetChinhMinh() {
        UUID hoSo = taoHoSoCoTruongKin("TD-004");

        ResponseEntity<String> tuLienKet = lienKet(idQuanTri, hoSo, HttpStatus.FORBIDDEN);
        assertThat(tuLienKet.getBody()).contains("ADM-2018");
        assertThat(jdbc.queryForObject("SELECT employee_id FROM users WHERE public_id = ?", Long.class, idQuanTri))
                .as("⛔ Bị từ chối thì ⛔ không được ghi gì cả")
                .isNull();

        // ⛔ Đối chứng PHẢI-THÀNH-CÔNG: thiếu nó thì một endpoint hỏng hoàn toàn cũng xanh (luật 9).
        lienKet(idCanBo, hoSo, HttpStatus.OK);
    }

    @Test
    @DisplayName("⭐ Mỗi lượt liên kết và mỗi lượt tự đọc đều để lại ĐÚNG MỘT dòng security_events")
    void moiLuotDeLaiDungMotDongNhatKy() {
        UUID hoSo = taoHoSoCoTruongKin("TD-005");

        long lienKetTruoc = demSuKien("ACCOUNT_EMPLOYEE_LINK_CHANGED");
        long docTruoc = demSuKien("HR_SENSITIVE_FIELDS_READ");

        lienKet(idCanBo, hoSo, HttpStatus.OK);
        assertThat(demSuKien("ACCOUNT_EMPLOYEE_LINK_CHANGED"))
                .as("⛔ Liên kết là một thao tác CẤP QUYỀN — nó phải nằm ở nhật ký bảo mật, ⛔ không "
                        + "chỉ ở nhật ký thay đổi dữ liệu")
                .isEqualTo(lienKetTruoc + 1);

        assertThat(phienCanBo.get(canBo, "/api/v1/hr/ho-so-cua-toi").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(demSuKien("HR_SENSITIVE_FIELDS_READ"))
                .as("⛔⛔ Lượt TỰ đọc cũng phải ghi. Thiếu nó thì một người bị trỏ liên kết sai đọc "
                        + "hết hồ sơ người khác mà ⛔ không để lại dấu vết ở bảng nào")
                .isEqualTo(docTruoc + 1);

        goLienKet(idCanBo);
        assertThat(demSuKien("ACCOUNT_EMPLOYEE_LINK_CHANGED")).isEqualTo(lienKetTruoc + 2);
    }

    @Test
    @DisplayName("⭐⭐ Hồ sơ NGOÀI phạm vi đơn vị của tài khoản vẫn tự đọc được — phạm vi ⛔ không áp cho chính mình")
    void hoSoNgoaiPhamViDonViVanTuDocDuoc() {
        long goc = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        String pathGoc = jdbc.queryForObject("SELECT path FROM org_units WHERE code = 'CTY'", String.class);
        long xnA = themDonVi(TIEN_TO + "XN-A", goc, pathGoc);
        long xnB = themDonVi(TIEN_TO + "XN-B", goc, pathGoc);

        UUID hoSo = taoHoSoCoTruongKin("TD-006");
        jdbc.update("UPDATE employees SET org_unit_id = ? WHERE public_id = ?", xnA, hoSo);
        lienKet(idXemHoSo, hoSo, HttpStatus.OK);

        // Tài khoản nằm ở XN-B — một nhánh RỜI khỏi XN-A, nên bộ lọc phạm vi ⛔ không thấy hồ sơ.
        jdbc.update("UPDATE users SET org_unit_id = ? WHERE public_id = ?", xnB, idXemHoSo);
        authorities.invalidateAll();

        // Vế phân biệt: đường thường ĐÚNG LÀ bị chặn — nếu ⛔ không thì bài dưới ⛔ không chứng minh gì.
        assertThat(phienXemHoSo.get(xemHoSo, "/api/v1/hr/employees/" + hoSo).getStatusCode())
                .as("⛔ Tiền đề: hồ sơ này PHẢI nằm ngoài phạm vi. Xanh ở đây nghĩa là cây đơn vị "
                        + "dựng sai và bài dưới trở thành vô nghĩa")
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> tuDoc = phienXemHoSo.get(xemHoSo, "/api/v1/hr/ho-so-cua-toi");
        assertThat(tuDoc.getStatusCode())
                .as(
                        "⛔⛔ Phạm vi đơn vị trả lời câu *anh xem được dữ liệu của những AI*; câu ấy ⛔ "
                                + "không áp cho chính mình. Và đây cũng là chỗ neo một tính chất VAY MƯỢN của "
                                + "Hibernate: `@Filter` ⛔ không áp cho `find()` theo khoá chính. Ngày hành vi "
                                + "ấy đổi, bài này đỏ chứ ⛔ không phải người dùng: %s",
                        tuDoc.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(chuoi(tuDoc.getBody(), "nationalId")).isEqualTo(cccdCua("TD-006"));

        jdbc.update("UPDATE users SET org_unit_id = ? WHERE public_id = ?", goc, idXemHoSo);
        authorities.invalidateAll();
    }

    @Test
    @DisplayName("⛔⛔ Đường tự đọc ⛔ KHÔNG có động từ GHI — tự đọc lương là quyền, tự SỬA lương thì ⛔ không")
    void duongTuDocKhongCoDongTuGhi() {
        // ── Vế 1: CẤU TRÚC. Đây mới là vế phân biệt được hai trạng thái (luật 9). ────────────
        // ⛔⛔ Bản đầu của bài này chỉ có vế HTTP dưới đây và khẳng định 405. Đo thật ra **400**:
        //    `GlobalExceptionHandler:111` gộp `HttpRequestMethodNotSupportedException` vào nhóm
        //    "yêu cầu sai định dạng" — một quyết định CÓ SẴN của dự án, ⛔ không phải lỗi lượt này.
        //    Nhưng 400 thì một `@PutMapping` **có thật** mà từ chối thân yêu cầu **cũng trả**. ⇒ Vế
        //    HTTP một mình xanh ở CẢ HAI trạng thái, tức ⛔ không khẳng định gì.
        List<Class<? extends java.lang.annotation.Annotation>> dongTuGhi = List.of(
                org.springframework.web.bind.annotation.PutMapping.class,
                org.springframework.web.bind.annotation.PostMapping.class,
                org.springframework.web.bind.annotation.PatchMapping.class,
                org.springframework.web.bind.annotation.DeleteMapping.class);

        List<String> viPham = java.util.Arrays.stream(
                        com.songnhue.hr.api.HoSoCuaToiController.class.getDeclaredMethods())
                .filter(m -> dongTuGhi.stream().anyMatch(a -> m.getAnnotation(a) != null))
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertThat(viPham)
                .as("⛔⛔ `HoSoCuaToiController` mọc một động từ GHI là mở đường cho nhân viên tự nâng "
                        + "hệ số lương của chính mình. Trường 🔒 chỉ sửa qua `EmployeeSensitiveController`, "
                        + "nơi đòi `hr:employee:view-sensitive`")
                .isEmpty();

        // Đối chứng chống tập rỗng: lớp ấy PHẢI có ít nhất một động từ ĐỌC. Thiếu vế này thì bài
        // trên xanh trọn vẹn cả khi ai đó đổi tên lớp và phép quét trỏ vào hư không (luật 7).
        assertThat(java.util.Arrays.stream(com.songnhue.hr.api.HoSoCuaToiController.class.getDeclaredMethods())
                        .filter(m -> m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class) != null)
                        .count())
                .isEqualTo(1);

        // ── Vế 2: HÀNH VI. Yếu hơn, nhưng nó chứng minh tầng chạy thật cũng từ chối. ─────────
        for (HttpMethod dongTu : List.of(HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE)) {
            assertThat(phienCanBo
                            .goi(canBo, dongTu, "/api/v1/hr/ho-so-cua-toi", "{}")
                            .getStatusCode()
                            .is2xxSuccessful())
                    .as("⛔ `%s /ho-so-cua-toi` ⛔ không được thành công", dongTu)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("⭐ Nửa ĐỌC: danh sách tài khoản hiện hồ sơ đã liên kết — ⛔ không phải một ô trống")
    void danhSachTaiKhoanHienHoSoDaLienKet() {
        UUID hoSo = taoHoSoCoTruongKin("TD-007");

        String truoc = phienQt.get(quanTri, "/api/v1/admin/users").getBody();
        assertThat(truoc).doesNotContain(TIEN_TO + "TD-007");

        lienKet(idCanBo, hoSo, HttpStatus.OK);

        String sau = phienQt.get(quanTri, "/api/v1/admin/users").getBody();
        assertThat(sau)
                .as("⛔ Ghi được mà ⛔ không hiện ra là đúng một nửa cặp đọc–ghi (luật 27) — người "
                        + "quản trị nhìn ô trống rồi liên kết hồ sơ ấy sang một tài khoản khác")
                .contains(TIEN_TO + "TD-007");
        assertThat(sau).contains("\"hoSoNhanSu\"");
    }

    // =========================================================================
    // Trợ giúp
    // =========================================================================

    private ResponseEntity<String> lienKet(UUID taiKhoan, UUID hoSo, HttpStatus mongDoi) {
        ResponseEntity<String> ra = phienQt.goi(
                quanTri,
                HttpMethod.PUT,
                "/api/v1/admin/users/" + taiKhoan + "/ho-so-nhan-su",
                "{\"employeePublicId\":\"%s\"}".formatted(hoSo));
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(mongDoi);
        return ra;
    }

    private void goLienKet(UUID taiKhoan) {
        ResponseEntity<String> ra = phienQt.goi(
                quanTri,
                HttpMethod.PUT,
                "/api/v1/admin/users/" + taiKhoan + "/ho-so-nhan-su",
                "{\"employeePublicId\":null}");
        assertThat(ra.getStatusCode()).as("%s", ra.getBody()).isEqualTo(HttpStatus.OK);
    }

    private UUID taoHoSoCoTruongKin(String hau) {
        String than =
                """
                {"code":"%s","fullName":"Trần Văn Bình","dateOfBirth":"1988-07-04","gender":"NAM",\
                "orgUnitId":"%s","hiredAt":"2015-01-05",\
                "contractType":"KHONG_XAC_DINH_THOI_HAN","status":"DANG_LAM"}"""
                        .formatted(TIEN_TO + hau, donViGoc);
        ResponseEntity<String> tao = phienQt.goi(quanTri, HttpMethod.POST, "/api/v1/hr/employees", than);
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        UUID hoSo = UUID.fromString(chuoi(tao.getBody(), "publicId"));

        ResponseEntity<String> luu = phienQt.goi(
                quanTri,
                HttpMethod.PUT,
                "/api/v1/hr/employees/" + hoSo + "/sensitive",
                """
                {"nationalId":"%s","nationalIdIssuedOn":"2021-03-15","nationalIdIssuedPlace":"Cục CSQLHC",\
                "baseSalary":"7200000","salaryCoefficient":"3.33","bankAccount":"%s",\
                "taxCode":"8012345678","socialInsuranceNo":"0112345678"}"""
                        .formatted(cccdCua(hau), SO_TK));
        assertThat(luu.getStatusCode()).as("%s", luu.getBody()).isEqualTo(HttpStatus.NO_CONTENT);
        return hoSo;
    }

    /** Số CCCD riêng của từng hồ sơ — xem {@link #CCCD_GOC}. */
    private static String cccdCua(String hau) {
        return CCCD_GOC + "%05d".formatted(Integer.parseInt(hau.replaceAll("\\D", "")));
    }

    private long themDonVi(String ma, long chaId, String pathCha) {
        jdbc.update(
                // ⚠ ⛔ Không `ON CONFLICT (code)`: chỉ mục duy nhất của `org_units` là PARTIAL
                //   (`uq_org_units_code … WHERE deleted_at IS NULL`, `V202608131002:57`) nên Postgres
                //   ⛔ không suy ra được ràng buộc — nó trả `bad SQL grammar`, ⛔ không phải một lượt
                //   bỏ qua êm ái. `don()` ở `@BeforeAll` đã dọn sạch, nên INSERT trần là đúng.
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/', 1, 10, now())",
                ma,
                "Đơn vị kiểm thử " + ma,
                chaId);
        long id = jdbc.queryForObject("SELECT id FROM org_units WHERE code = ?", Long.class, ma);
        jdbc.update("UPDATE org_units SET path = ? WHERE id = ?", pathCha + id + "/", id);
        return id;
    }

    private long demSuKien(String loai) {
        return jdbc.queryForObject("SELECT count(*) FROM security_events WHERE event_type = ?", Long.class, loai);
    }

    private UUID publicIdCua(String username) {
        return jdbc.queryForObject("SELECT public_id FROM users WHERE username = ?", UUID.class, username);
    }

    private void don() {
        jdbc.update("UPDATE users SET employee_id = NULL WHERE username LIKE 'kiemtra_t518%'");
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE code = 'CTY') "
                + "WHERE username LIKE 'kiemtra_t518%'");
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update(
                "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code IN (?, ?))",
                VAI_TRO_QT,
                VAI_TRO_XEM_HS);
        jdbc.update(
                "DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code IN (?, ?))",
                VAI_TRO_QT,
                VAI_TRO_XEM_HS);
        jdbc.update("DELETE FROM roles WHERE code IN (?, ?)", VAI_TRO_QT, VAI_TRO_XEM_HS);
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
