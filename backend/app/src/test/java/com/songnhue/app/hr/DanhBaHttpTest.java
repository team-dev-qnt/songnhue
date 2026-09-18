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
 * Danh bạ nội bộ đo <b>qua HTTP</b> — CN-04.6 (SRS M4.11).
 *
 * <h2>Vì sao qua HTTP (luật 5) — ba tầng ⛔ không lượt gọi service nào chạm tới</h2>
 *
 * <ul>
 *   <li><b>Cổng quyền tầng 2.</b> Bảo đảm lớn nhất của lượt này là danh bạ mở cho
 *       {@code hr:directory:view} — mã quyền có <b>0 endpoint</b> từ 13/08/2026 — và <b>⛔ không</b>
 *       mở cho {@code hr:employee:view}. Hai vế ấy chỉ tồn tại ở {@code @RequirePermission}.
 *   <li><b>Bộ lọc phạm vi.</b> {@code ScopeFilterAspect} bật {@code @Filter} <b>quanh</b>
 *       transaction. Một bài gọi service ⛔ không dựng được cảnh *"người ở Xí nghiệp B tra người ở
 *       Xí nghiệp A"* — mà đó chính là khác biệt cốt lõi giữa CN-04.6 và CN-04.7.
 *   <li><b>Thân JSON thật.</b> Bài cấu trúc ({@code DanhBaKhongLoDuLieuCaNhanTest}) canh <i>hợp
 *       đồng của kiểu</i>; bài này canh <i>byte đi ra dây</i>. Hai câu hỏi khác nhau.
 * </ul>
 *
 * <h2>⛔ Dữ liệu của lớp này ⛔ KHÔNG phải seed</h2>
 *
 * <p>G6-a vẫn là ô trống và CLAUDE.md cấm seed hồ sơ CBNV. Mọi hàng sống đúng bằng thời gian lớp
 * chạy, bị xoá <b>cứng</b> ở {@code @AfterAll}, có khẳng định ngay tại chỗ dọn.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DanhBaHttpTest extends IntegrationTestBase {

    private static final String DUONG = "/api/v1/hr/danh-ba";
    private static final String TIEN_TO = "DANHBA-";
    private static final String VAI_TRO_HR = "KIEMTRA_DANHBA_HR";
    private static final String VAI_TRO_DANHBA = "KIEMTRA_DANHBA_XEM";
    private static final String VAI_TRO_HOSO = "KIEMTRA_DANHBA_HOSO";

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

    private PhienHttp phienHr;
    private PhienHttp phienDanhBa;
    private PhienHttp phienHoSo;
    private PhienHttp.Phien hr;
    private PhienHttp.Phien nguoiTraDanhBa;
    private PhienHttp.Phien nguoiChiXemHoSo;

    private UUID donViGoc;
    private long idGoc;
    private long idXnA;
    private long idToA1;
    private long idXnB;
    private UUID publicXnA;
    private UUID publicXnB;
    private UUID chucVuKyThuat;
    private UUID chucVuVanThu;

    private UUID hoSoA1;
    private UUID hoSoXnA;
    private UUID hoSoXnB;
    private UUID hoSoDaNghi;
    private UUID hoSoThaiSan;

    @BeforeAll
    void dungNen() {
        don();
        capVaiTro(VAI_TRO_HR, "hr:employee:view", "hr:employee:create", "hr:employee:update");
        capVaiTro(VAI_TRO_DANHBA, "hr:directory:view");
        capVaiTro(VAI_TRO_HOSO, "hr:employee:view");

        idGoc = jdbc.queryForObject("SELECT id FROM org_units WHERE code = 'CTY'", Long.class);
        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);
        String pathGoc = jdbc.queryForObject("SELECT path FROM org_units WHERE code = 'CTY'", String.class);

        idXnA = themDonVi(TIEN_TO + "XN-A", idGoc, pathGoc);
        idXnB = themDonVi(TIEN_TO + "XN-B", idGoc, pathGoc);
        String pathA = jdbc.queryForObject("SELECT path FROM org_units WHERE id = ?", String.class, idXnA);
        idToA1 = themDonVi(TIEN_TO + "TO-A1", idXnA, pathA);
        publicXnA = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, idXnA);
        publicXnB = jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, idXnB);

        phienHr = new PhienHttp(http);
        phienDanhBa = new PhienHttp(http);
        phienHoSo = new PhienHttp(http);
        hr = phienHr.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "danhba_hr", VAI_TRO_HR));
        nguoiTraDanhBa =
                phienDanhBa.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "danhba_xem", VAI_TRO_DANHBA));
        String tenHoSo = PhienHttp.taoNguoiDung(users, passwords, jdbc, "danhba_hoso", VAI_TRO_HOSO);
        nguoiChiXemHoSo = phienHoSo.dangNhap(tenHoSo);

        chucVuKyThuat = taoChucVu("KT", "Cán bộ kỹ thuật (kiểm thử)");
        chucVuVanThu = taoChucVu("VT", "Văn thư (kiểm thử)");

        // ⛔ Hồ sơ dựng qua HTTP (nên đi đủ tầng), rồi ĐẶT LẠI đơn vị bằng SQL: biểu mẫu chỉ nhận
        //   đơn vị trong phạm vi người tạo, mà cảnh cần dựng là hai NHÁNH RỜI NHAU.
        hoSoA1 = taoHoSo("A1-001", "Nguyễn Văn Hoà", "NAM", chucVuKyThuat, "DANG_LAM");
        hoSoXnA = taoHoSo("XNA-002", "Trần Thị Bích", "NU", chucVuVanThu, "DANG_LAM");
        hoSoXnB = taoHoSo("XNB-003", "Lê Quốc Huy", "NAM", chucVuKyThuat, "DANG_LAM");
        hoSoThaiSan = taoHoSo("TS-004", "Phạm Thị Lan", "NU", chucVuVanThu, "THAI_SAN");
        hoSoDaNghi = taoHoSo("NV-005", "Đỗ Văn Tuấn", "NAM", chucVuKyThuat, "NGHI_VIEC");

        datDonVi(hoSoA1, idToA1);
        datDonVi(hoSoXnA, idXnA);
        datDonVi(hoSoXnB, idXnB);
        datDonVi(hoSoThaiSan, idXnB);
        datDonVi(hoSoDaNghi, idXnA);
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
    @DisplayName("⭐⭐ hr:directory:view MỞ danh bạ; hr:employee:view thì KHÔNG — hai màn hình, hai quyền")
    void danhBaGacBangQuyenRieng() {
        ResponseEntity<String> mo = phienDanhBa.get(nguoiTraDanhBa, DUONG);
        assertThat(mo.getStatusCode()).as("%s", mo.getBody()).isEqualTo(HttpStatus.OK);

        // ⛔ VẾ PHÂN BIỆT. Thiếu nó thì bài trên xanh cả khi ai đó gác danh bạ bằng một quyền rộng
        //    hơn "cho tiện" — và cái xanh ấy đọc như "đã canh".
        assertThat(phienHoSo.get(nguoiChiXemHoSo, DUONG).getStatusCode())
                .as("⛔ `hr:employee:view` ⛔ KHÔNG mở được danh bạ: 3 vai trò có nó, còn danh bạ "
                        + "dành cho 11/12 vai trò — gộp hai quyền là hỏng theo cả hai chiều")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("⭐⭐ Danh bạ thấy TOÀN Công ty — người ở nhánh B tra được người ở nhánh A")
    void danhBaKhongCatTheoPhamViDonVi() {
        datDonViNguoiDung("kiemtra_danhba_xem", idXnB);
        datDonViNguoiDung("kiemtra_danhba_hoso", idXnB);
        try {
            // Tiền đề: đường HỒ SƠ ĐÚNG LÀ bị cắt. Xanh ở đây nghĩa là cây đơn vị dựng sai và
            // khẳng định dưới trở thành vô nghĩa.
            assertThat(phienHoSo
                            .get(nguoiChiXemHoSo, "/api/v1/hr/employees/" + hoSoA1)
                            .getStatusCode())
                    .as("⛔ Tiền đề: hồ sơ ở Tổ đội A1 PHẢI nằm ngoài phạm vi của người ở Xí nghiệp B")
                    .isEqualTo(HttpStatus.FORBIDDEN);

            String than = phienDanhBa.get(nguoiTraDanhBa, DUONG + "?size=60").getBody();
            assertThat(than)
                    .as("⛔⛔ Một cuốn danh bạ chỉ thấy đơn vị mình là một cuốn danh bạ vô dụng — "
                            + "đây là khác biệt cốt lõi giữa CN-04.6 và CN-04.7 (M4.13)")
                    .contains(TIEN_TO + "A1-001")
                    .contains(TIEN_TO + "XNA-002")
                    .contains(TIEN_TO + "XNB-003");
        } finally {
            datDonViNguoiDung("kiemtra_danhba_xem", idGoc);
            datDonViNguoiDung("kiemtra_danhba_hoso", idGoc);
        }
    }

    @Test
    @DisplayName("⭐⭐ Chỉ người CÒN LÀM VIỆC — nghỉ thai sản VẪN có, nghỉ việc thì KHÔNG")
    void chiHienNguoiConLamViec() {
        String than = phienDanhBa.get(nguoiTraDanhBa, DUONG + "?size=60").getBody();

        assertThat(than).as("⛔ Người đã nghỉ việc ⛔ không còn trong danh bạ").doesNotContain(TIEN_TO + "NV-005");
        // ⛔⛔ VẾ PHÂN BIỆT — và là vế quan trọng nhất của bài này. Đọc *"chỉ NV 'Đang làm'"* thành
        //    `status = DANG_LAM` sẽ làm khẳng định trên vẫn xanh, trong khi người nghỉ thai sản
        //    biến mất khỏi danh bạ — một quyết định nhân sự ⛔ không ai duyệt.
        assertThat(than)
                .as("⛔⛔ Nghỉ thai sản vẫn là người của Công ty và vẫn có số điện thoại")
                .contains(TIEN_TO + "TS-004");

        // Và liên kết cũ của người đã nghỉ cũng phải chết — thiếu vế này thì họ biến khỏi danh sách
        // mà vẫn mở được bằng URL cũ (nửa cặp đọc–ghi ở dạng khó thấy nhất).
        assertThat(phienDanhBa.get(nguoiTraDanhBa, DUONG + "/" + hoSoDaNghi).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⛔⛔ Thân JSON ⛔ KHÔNG mang một trường cá nhân nào — đo trên byte đi ra dây")
    void thanJsonKhongLoDuLieuCaNhan() {
        String than = phienDanhBa.get(nguoiTraDanhBa, DUONG + "?size=60").getBody();

        // Chống tập rỗng: phải ĐANG CÓ dữ liệu thì phép so "⛔ không chứa" mới có nghĩa (luật 7).
        assertThat(than).contains(TIEN_TO + "A1-001").contains("Nguyễn Văn Hoà");

        for (String cam : List.of(
                "dateOfBirth",
                "hometown",
                "address",
                "personalEmail",
                "maritalStatus",
                "emergencyContactName",
                "emergencyContactPhone",
                "nationalId",
                "baseSalary",
                "bankAccount")) {
            assertThat(than)
                    .as(
                            "⛔⛔ `%s` lọt ra danh bạ = công bố nó cho 11/12 vai trò, ⛔ không qua bộ "
                                    + "lọc phạm vi nào",
                            cam)
                    .doesNotContain("\"" + cam + "\"");
        }
        // Đối chứng phải-tìm-thấy: hai ô liên hệ công vụ mà đặc tả vẽ trên thẻ PHẢI có mặt.
        assertThat(than).contains("\"phone\"").contains("\"workEmail\"");
    }

    @Test
    @DisplayName("⭐ Tìm bỏ dấu HAI CHIỀU — gõ 'hoa' ra 'Hoà', và gõ 'Hoà' cũng ra")
    void timBoDauHaiChieu() {
        assertThat(phienDanhBa.get(nguoiTraDanhBa, DUONG + "?q=hoa").getBody())
                .as("⛔ Gõ ⛔ không dấu phải tìm được tên CÓ dấu")
                .contains(TIEN_TO + "A1-001");
        // ⚠ Gõ ký tự CÓ DẤU nguyên bản, ⛔ KHÔNG gõ chuỗi đã mã hoá `%C3%A0`: `RestTemplate` coi
        //   đối số là một URI **template** và mã hoá nó lần nữa (`%` → `%25`), nên `%C3%A0` tới
        //   máy chủ dưới dạng ba ký tự thường — và `%` trong đó còn là ký tự đại diện của LIKE.
        //   Bản đầu của bài này đỏ vì đúng chuyện ấy: một lỗi của ĐỒ GÁ đọc y hệt một lỗi của mã.
        assertThat(phienDanhBa.get(nguoiTraDanhBa, DUONG + "?q=Hoà").getBody())
                .as("⛔ Và chiều ngược lại — `sn_khong_dau` áp cho CẢ HAI vế của phép so")
                .contains(TIEN_TO + "A1-001");

        // Vế phân biệt: một từ khoá ⛔ không khớp phải cho tập RỖNG, ⛔ không phải cho tất cả.
        assertThat(soTong(phienDanhBa
                        .get(nguoiTraDanhBa, DUONG + "?q=khongkhopvoiaica")
                        .getBody()))
                .isZero();
    }

    @Test
    @DisplayName("⭐⭐ Lọc đơn vị khớp CẢ NHÁNH CON — chọn Xí nghiệp A ra cả người của Tổ đội A1")
    void locDonViKhopCaNhanhCon() {
        String than = phienDanhBa
                .get(nguoiTraDanhBa, DUONG + "?donVi=" + publicXnA + "&size=60")
                .getBody();

        assertThat(than)
                .as("⛔⛔ Khớp đúng `org_unit_id` thì người dùng chọn một Xí nghiệp và thấy 1 người "
                        + "rồi tin rằng đơn vị ấy có 1 người — một câu trả lời SAI mà IM LẶNG")
                .contains(TIEN_TO + "A1-001")
                .contains(TIEN_TO + "XNA-002");
        assertThat(than).doesNotContain(TIEN_TO + "XNB-003");
    }

    @Test
    @DisplayName("⭐ Lọc ĐA CHỌN, và danh sách RỖNG nghĩa là KHÔNG LỌC — ⛔ không phải ⛔ không khớp gì")
    void locDaChonVaTapRongNghiaLaKhongLoc() {
        String haiDonVi = phienDanhBa
                .get(nguoiTraDanhBa, DUONG + "?donVi=" + publicXnA + "&donVi=" + publicXnB + "&size=60")
                .getBody();
        assertThat(haiDonVi).contains(TIEN_TO + "A1-001").contains(TIEN_TO + "XNB-003");

        String motChucVu = phienDanhBa
                .get(nguoiTraDanhBa, DUONG + "?chucVu=" + chucVuVanThu + "&size=60")
                .getBody();
        assertThat(motChucVu).contains(TIEN_TO + "XNA-002").doesNotContain(TIEN_TO + "A1-001");

        String motGioi =
                phienDanhBa.get(nguoiTraDanhBa, DUONG + "?gioiTinh=NU&size=60").getBody();
        assertThat(motGioi).contains(TIEN_TO + "XNA-002").doesNotContain(TIEN_TO + "XNB-003");

        // ⛔⛔ Vế quan trọng nhất: bỏ HẾT dấu tick phải ra TẤT CẢ, ⛔ không phải một trang trắng.
        long khongLoc =
                soTong(phienDanhBa.get(nguoiTraDanhBa, DUONG + "?size=60").getBody());
        long locMotDonVi = soTong(haiDonVi);
        assertThat(khongLoc)
                .as("⛔ Danh sách rỗng = ⛔ không lọc. Đọc nó thành `IN ()` là người dùng bỏ tick "
                        + "rồi thấy danh bạ TRỐNG RỖNG")
                .isGreaterThanOrEqualTo(locMotDonVi);
        assertThat(khongLoc).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("⭐ Chi tiết: đường dẫn đơn vị từ GỐC xuống, và đồng nghiệp ĐÚNG đơn vị (⛔ không cả nhánh)")
    void chiTietCoDuongDanDonViVaDongNghiep() {
        String than = phienDanhBa.get(nguoiTraDanhBa, DUONG + "/" + hoSoXnB).getBody();
        assertThat(than).isNotNull();

        assertThat(than)
                .as("⛔ *Vị trí trên sơ đồ* dựng từ `org_units.path` — nó có TRƯỚC CN-04.1 và ⛔ " + "không chờ ai")
                .contains("duongDanDonVi")
                .contains(TIEN_TO + "XN-B");

        // `hoSoThaiSan` cùng XN-B ⇒ là đồng nghiệp; `hoSoXnA` khác đơn vị ⇒ ⛔ không phải.
        assertThat(than)
                .as("⛔ *Đồng nghiệp cùng đơn vị* nghĩa là người ngồi cùng phòng — ⛔ KHÔNG phải cả "
                        + "nhánh, khác chủ ý với bộ lọc đơn vị ở màn hình danh sách")
                .contains(TIEN_TO + "TS-004")
                .doesNotContain(TIEN_TO + "XNA-002");
    }

    // ===================== Trợ giúp =====================

    private void capVaiTro(String vaiTro, String... quyen) {
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử danh bạ', 'Tạm, xoá ở @AfterAll', FALSE, now())",
                vaiTro);
        int gan = 0;
        for (String q : quyen) {
            gan += jdbc.update(
                    "INSERT INTO role_permissions (role_id, permission_id) "
                            + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = ?",
                    vaiTro,
                    q);
        }
        assertThat(gan)
                .as("⚠ Chống tập rỗng: một mã quyền đổi tên thì lệnh trên gán ít dòng hơn TRONG IM "
                        + "LẶNG và mọi bài dưới đỏ với 403 — triệu chứng chẳng liên quan gì")
                .isEqualTo(quyen.length);
    }

    private UUID taoChucVu(String hau, String ten) {
        ResponseEntity<String> tao = phienHr.goi(
                hr,
                HttpMethod.POST,
                "/api/v1/hr/positions",
                """
                {"code":"%s","name":"%s","positionGroup":"CN-04.6","sortOrder":10,"active":true}"""
                        .formatted(TIEN_TO + hau, ten));
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private UUID taoHoSo(String hau, String hoTen, String gioi, UUID chucVu, String trangThai) {
        String ngayNghi = "NGHI_VIEC".equals(trangThai) || "NGHI_HUU".equals(trangThai) ? "\"2026-08-31\"" : "null";
        String than =
                """
                {"code":"%s","fullName":"%s","gender":"%s","orgUnitId":"%s","positionId":"%s",\
                "jobTitle":"Phụ trách trạm bơm","phone":"0912.345.678","workEmail":"%s@songnhue.test",\
                "personalEmail":"rieng@vi-du.test","address":"Số 7 phố Hồng Hà","hometown":"Thanh Trì",\
                "dateOfBirth":"1988-04-17","emergencyContactName":"Người thân","emergencyContactPhone":"0987.654.321",\
                "hiredAt":"2015-06-01","contractType":"KHONG_XAC_DINH_THOI_HAN","status":"%s","terminatedAt":%s}"""
                        .formatted(
                                TIEN_TO + hau, hoTen, gioi, donViGoc, chucVu, hau.toLowerCase(), trangThai, ngayNghi);
        ResponseEntity<String> tao = phienHr.goi(hr, HttpMethod.POST, "/api/v1/hr/employees", than);
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private void datDonVi(UUID hoSo, long donViId) {
        assertThat(jdbc.update("UPDATE employees SET org_unit_id = ? WHERE public_id = ?", donViId, hoSo))
                .isEqualTo(1);
    }

    private void datDonViNguoiDung(String username, long donViId) {
        jdbc.update("UPDATE users SET org_unit_id = ? WHERE username = ?", donViId, username);
        authorities.invalidateAll();
    }

    private long themDonVi(String ma, long chaId, String pathCha) {
        jdbc.update(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/', 1, 10, now())",
                ma,
                "Đơn vị kiểm thử " + ma,
                chaId);
        long id = jdbc.queryForObject("SELECT id FROM org_units WHERE code = ?", Long.class, ma);
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", pathCha + id + "/", 1, id);
        return id;
    }

    private static long soTong(String json) {
        String giaTri = chuoi(json, "tong");
        return giaTri == null ? -1 : Long.parseLong(giaTri);
    }

    private void don() {
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM positions WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE code = 'CTY') "
                + "WHERE username LIKE 'kiemtra_danhba%'");
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        for (String vt : List.of(VAI_TRO_HR, VAI_TRO_DANHBA, VAI_TRO_HOSO)) {
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
