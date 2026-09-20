package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import com.songnhue.hr.application.EmployeeForm;
import com.songnhue.hr.application.importer.EmployeeImportService;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.infra.EmployeeRepository;

/**
 * <b>Nhập danh sách CBNV từ tệp — T68.23 (G6-a), đo qua HTTP.</b>
 *
 * <h2>Vì sao QUA HTTP (luật 5)</h2>
 *
 * <p>Ba thứ chỉ tồn tại trên đường chạy thật: multipart ({@code @RequestPart} và trần dung lượng), cổng quyền
 * ({@code hr:employee:create} cho cả tệp, {@code hr:employee:update} cho riêng dòng SỬA), và bộ lọc phạm vi bật
 * quanh {@code @Transactional} — thứ quyết định hồ sơ của đơn vị khác là "chưa có" hay "đã có". Gọi thẳng service
 * thì cả ba biến mất, và {@code ConstructionImportTest} đã trả giá đúng chuyện ấy (10 bài gọi service ⇒ ba khuyết
 * tật nằm trọn ở giao diện về nguyên tắc ⛔ lộ được — §11.18).
 *
 * <h2>Dữ liệu của lớp này ⛔ phải seed</h2>
 *
 * <p>G6-a vẫn là ô trống và CLAUDE.md cấm seed hồ sơ CBNV: mọi hàng sống đúng bằng thời gian lớp chạy và bị xoá
 * cứng ở {@code @AfterAll}, có khẳng định ngay tại chỗ dọn.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NhapCbnvHttpTest extends IntegrationTestBase {

    private static final String TIEN_TO = "T6823-";
    private static final String VAI_TRO_DU = "KIEMTRA_T6823_DU";
    private static final String VAI_TRO_CHI_THEM = "KIEMTRA_T6823_THEM";

    private static final String DUONG = "/api/v1/hr/employees";

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private AuthorityLoader authorities;

    @Autowired
    private EmployeeImportService nhapTep;

    @Autowired
    private EmployeeRepository hoSo;

    private PhienHttp phienDu;
    private PhienHttp.Phien du;
    private PhienHttp phienThem;
    private PhienHttp.Phien chiThem;
    private long rootId;
    private long xnA;
    private long xnB;
    private String maXnA;
    private String maXnB;

    @BeforeAll
    void dung() {
        don();
        rootId = jdbc.queryForObject("SELECT id FROM org_units WHERE parent_id IS NULL", Long.class);
        String pathRoot = jdbc.queryForObject("SELECT path FROM org_units WHERE parent_id IS NULL", String.class);
        maXnA = TIEN_TO + "XN-A";
        maXnB = TIEN_TO + "XN-B";
        xnA = themDonVi(maXnA, pathRoot);
        xnB = themDonVi(maXnB, pathRoot);

        taoVaiTro(VAI_TRO_DU, List.of("hr:employee:view", "hr:employee:create", "hr:employee:update"));
        taoVaiTro(VAI_TRO_CHI_THEM, List.of("hr:employee:view", "hr:employee:create"));

        phienDu = new PhienHttp(http);
        phienThem = new PhienHttp(http);
        du = phienDu.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6823_du", VAI_TRO_DU));
        chiThem = phienThem.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6823_them", VAI_TRO_CHI_THEM));
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
    @DisplayName("⭐ Tệp mẫu do BACKEND sinh — đủ 16 cột và một dòng mô tả; ⛔ phải tệp tĩnh (luật 14)")
    void taiTepMau() {
        ResponseEntity<String> tep = phienDu.get(du, DUONG + "/import/template");

        assertThat(tep.getStatusCode()).as("%s", tep.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(tep.getHeaders().getFirst("Content-Disposition")).contains("mau-danh-sach-cbnv.csv");
        String csv = tep.getBody();
        for (var cot : EmployeeImportService.COT_MAU) {
            assertThat(csv).as("thiếu cột %s", cot.ten()).contains(cot.ten());
            assertThat(csv).as("thiếu mô tả cột %s", cot.ten()).contains(cot.moTa());
        }
        assertThat(csv)
                .as("⛔ tệp TĨNH cũ ghi 'Thời vụ' (ràng buộc CSDL ⛔ nhận) và 'Sau đại học' (enum tách Tiến sĩ/Thạc sĩ)")
                .doesNotContain("Thời vụ")
                .doesNotContain("Sau đại học");
    }

    @Test
    @DisplayName("⛔⛔ Chạy khô KHÔNG ghi gì và chỉ đúng dòng sai; sửa xong nhập thật thì dữ liệu vào đúng ô")
    void chayKhoRoiNhapThat() {
        String tepSai = csv(
                dong("NV-01", "Nguyễn Văn A", maXnA, "11/02/1990", "Nam", "Đại học", "Đang làm"),
                dong("NV-02", "Trần Thị B", maXnA, "01/03/1992", "Không rõ", "Đại học", "Đang làm"));

        ResponseEntity<String> kho = phienDu.dangTep(du, DUONG + "/import/preview", tepSai.getBytes(UTF), "a.csv");
        assertThat(kho.getStatusCode()).as("%s", kho.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(kho.getBody()).contains("\"totalRows\":2").contains("Không rõ");
        assertThat(demHoSo("NV-0%"))
                .as("⛔⛔ chạy khô mà ghi một dòng là đúng thứ nó sinh ra để ⛔ làm")
                .isZero();

        String tepDung = csv(
                dong("NV-01", "Nguyễn Văn A", maXnA, "11/02/1990", "Nam", "Đại học", "Đang làm"),
                dong("NV-02", "Trần Thị B", maXnA, "1/3/1992", "Nữ", "Cao đẳng", ""));
        ResponseEntity<String> nhap = phienDu.dangTep(du, DUONG + "/import", tepDung.getBytes(UTF), "a.csv");
        assertThat(nhap.getStatusCode()).as("%s", nhap.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(nhap.getBody()).contains("\"toCreate\":2").contains("\"applied\":true");

        assertThat(jdbc.queryForMap("SELECT * FROM employees WHERE code = ?", TIEN_TO + "NV-02"))
                .containsEntry("full_name", "Trần Thị B")
                .containsEntry("gender", "NU")
                .containsEntry("education_level", "CAO_DANG")
                .containsEntry("date_of_birth", java.sql.Date.valueOf("1992-03-01"))
                .containsEntry("org_unit_id", xnA)
                .as("⛔ bỏ trống trạng thái ⇒ Đang làm (tệp này dùng để đưa người ĐANG công tác vào hệ), "
                        + "⛔ phải Thử việc như mặc định của EmployeeService")
                .containsEntry("status", "DANG_LAM");
    }

    @Test
    @DisplayName("⛔⛔ Ô TRỐNG ⛔ phải lệnh xoá — nhập lại bằng tệp 3 cột giữ nguyên 24 trường của hồ sơ (T47.16)")
    void oTrongGiuNguyenTruongCu() {
        String ma = TIEN_TO + "NV-GIU";
        ResponseEntity<String> tao = phienDu.goi(du, HttpMethod.POST, DUONG, hoSoDayDu(ma));
        assertThat(tao.getStatusCode()).as("%s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        UUID publicId = UUID.fromString(PhienHttp.giaTriJson(tao.getBody(), "publicId"));
        String truoc = phienDu.get(du, DUONG + "/" + publicId).getBody();

        String tep = csv("ma_cbnv;ho_ten;ma_don_vi\n" + ma + ";Tên đã sửa;" + maXnA + "\n");
        ResponseEntity<String> nhap = phienDu.dangTep(du, DUONG + "/import", tep.getBytes(UTF), "b.csv");
        assertThat(nhap.getStatusCode()).as("%s", nhap.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(nhap.getBody()).contains("\"toUpdate\":1").contains("\"toCreate\":0");

        String sau = phienDu.get(du, DUONG + "/" + publicId).getBody();
        assertThat(PhienHttp.giaTriJson(sau, "fullName")).isEqualTo("Tên đã sửa");
        for (String truong : List.of(
                "ethnicity",
                "hometown",
                "address",
                "personalEmail",
                "emergencyContactName",
                "emergencyContactPhone",
                "jobTitle",
                "workEmail",
                "phone")) {
            assertThat(PhienHttp.giaTriJson(sau, truong))
                    .as("⛔⛔ Cột `%s` ⛔ có trong tệp mẫu — một lượt nhập thay-toàn-phần xoá trắng nó, im lặng", truong)
                    .isEqualTo(PhienHttp.giaTriJson(truoc, truong));
        }
        assertThat(PhienHttp.giaTriJson(sau, "dateOfBirth")).isEqualTo(PhienHttp.giaTriJson(truoc, "dateOfBirth"));
    }

    @Test
    @DisplayName("⭐ Bộ hợp nhất chép ĐỦ mọi thành phần của EmployeeForm — thêm trường mà quên chép là xoá trắng nó")
    void hopNhatChepDuTruong() {
        // ⛔ Dựng qua API: một hồ sơ hợp lệ ⛔ thể có ĐỒNG THỜI *đang làm* và *ngày nghỉ việc* (ràng buộc
        //   `ck_employees_terminated_pairs`), nên đường API ⛔ bao giờ cho ra một hồ sơ đủ 24 trường. Ở đây cần một
        //   hồ sơ NỀN đủ trường để hỏi đúng một câu: bộ hợp nhất có chép thiếu trường nào ⛔.
        long chucVuId = jdbc.queryForObject(
                "INSERT INTO positions (code, name, created_at) VALUES (?, 'Chức vụ kiểm thử', now()) RETURNING id",
                Long.class,
                TIEN_TO + "CV");
        try {
            Employee e = new Employee();
            e.setCode(TIEN_TO + "NV-DU");
            e.setFullName("Người đủ trường");
            e.setDateOfBirth(java.time.LocalDate.of(1988, 4, 5));
            e.setGender(com.songnhue.hr.domain.Gender.NAM);
            e.setEducationLevel(com.songnhue.hr.domain.EducationLevel.DAI_HOC);
            e.setEthnicity("Kinh");
            e.setHometown("Hà Nội");
            e.setAddress("Số 1 phố A");
            e.setPhone("0912000111");
            e.setWorkEmail("a@songnhue.test");
            e.setPersonalEmail("b@songnhue.test");
            e.setMaritalStatus(com.songnhue.hr.domain.MaritalStatus.DOC_THAN);
            e.setEmergencyContactName("Người thân");
            e.setEmergencyContactPhone("0912000222");
            e.setOrgUnitId(xnA);
            e.setPositionId(chucVuId);
            e.setJobTitle("Cán bộ kỹ thuật");
            e.setHiredAt(java.time.LocalDate.of(2015, 1, 5));
            e.setContractType(com.songnhue.hr.domain.ContractType.XAC_DINH_THOI_HAN);
            e.setContractSignedAt(java.time.LocalDate.of(2015, 1, 5));
            e.setContractExpiresAt(java.time.LocalDate.of(2027, 1, 5));
            e.setStatus(com.songnhue.hr.domain.EmploymentStatus.NGHI_VIEC);
            e.setTerminatedAt(java.time.LocalDate.of(2026, 6, 30));
            e.setTerminationReason("Chuyển công tác");

            EmployeeForm form = nhapTep.hopNhat(e);

            List<String> thieu = new java.util.ArrayList<>();
            for (var thanhPhan : EmployeeForm.class.getRecordComponents()) {
                try {
                    if (thanhPhan.getAccessor().invoke(form) == null) {
                        thieu.add(thanhPhan.getName());
                    }
                } catch (ReflectiveOperationException ex) {
                    throw new AssertionError(ex);
                }
            }
            assertThat(thieu)
                    .as("⛔⛔ Hồ sơ nền có GIÁ TRỊ ở mọi trường, nên mọi thành phần phải được chép. Trường nào null ở "
                            + "đây là trường sẽ bị XOÁ TRẮNG ở mọi lượt nhập — im lặng (T47.16 · T63.10)")
                    .isEmpty();
        } finally {
            jdbc.update("DELETE FROM positions WHERE code = ?", TIEN_TO + "CV");
        }
    }

    @Test
    @DisplayName("⛔⛔ Ô ngày của XLSX đi trọn đường multipart → bộ đọc → hồ sơ (chặng cuối, T42.29)")
    void oNgayXlsxDiTronDuong() throws Exception {
        byte[] xlsx = xlsxMotDong(TIEN_TO + "NV-XLSX", "Lê Văn Excel", maXnA, 42009);

        ResponseEntity<String> nhap = phienDu.dangTep(du, DUONG + "/import", xlsx, "c.xlsx");

        assertThat(nhap.getStatusCode()).as("%s", nhap.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT hired_at FROM employees WHERE code = ?", java.sql.Date.class, TIEN_TO + "NV-XLSX"))
                .as("⛔ Excel lưu ngày thành SỐ SÊ-RI; ⛔ đổi ở bộ đọc thì người nhập nhận *'ngày ⛔ hợp lệ: 42009'*")
                .isEqualTo(java.sql.Date.valueOf("2015-01-05"));
    }

    @Test
    @DisplayName("⛔⛔ Dòng trỏ đơn vị NGOÀI phạm vi · mã đã thuộc hồ sơ ngoài phạm vi ⇒ lỗi DÒNG, ⛔ ghi gì")
    void ngoaiPhamViLaLoiDong() {
        // Hồ sơ của XN-B do tài khoản gốc tạo; tài khoản XN-A ⛔ thấy nó.
        String maAn = TIEN_TO + "NV-AN";
        assertThat(phienDu.goi(du, HttpMethod.POST, DUONG, hoSoToiThieu(maAn, maXnB))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        PhienHttp phienXnA = new PhienHttp(http);
        String ten = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6823_xna", VAI_TRO_DU);
        jdbc.update("UPDATE users SET org_unit_id = ? WHERE username = ?", xnA, ten);
        authorities.invalidateAll();
        PhienHttp.Phien xnAPhien = phienXnA.dangNhap(ten);

        try {
            String tep = csv(
                    dong("NV-NGOAI", "Người của B", maXnB, "", "", "", ""),
                    dong(maAn.substring(TIEN_TO.length()), "Ghi đè hồ sơ ẩn", maXnA, "", "", "", ""));
            ResponseEntity<String> kho =
                    phienXnA.dangTep(xnAPhien, DUONG + "/import/preview", tep.getBytes(UTF), "d.csv");

            assertThat(kho.getStatusCode()).as("%s", kho.getBody()).isEqualTo(HttpStatus.OK);
            assertThat(kho.getBody())
                    .as("⛔ ghi vào đơn vị ngoài phạm vi phải là một dòng lỗi chỉ đúng chỗ, ⛔ một 403 cho cả tệp")
                    .contains("ngoài phạm vi");
            assertThat(kho.getBody()).contains("\"toCreate\":0");
        } finally {
            jdbc.update("UPDATE users SET org_unit_id = ? WHERE username = ?", rootId, ten);
            authorities.invalidateAll();
        }
    }

    @Test
    @DisplayName("⛔ Chỉ có hr:employee:create thì ⛔ SỬA được hồ sơ đã có bằng một tệp")
    void chiThemThiKhongSuaDuocBangTep() {
        String ma = TIEN_TO + "NV-QUYEN";
        assertThat(phienDu.goi(du, HttpMethod.POST, DUONG, hoSoToiThieu(ma, maXnA))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        String tep = csv(dong("NV-QUYEN", "Tên mới", maXnA, "", "", "", ""));
        ResponseEntity<String> kho = phienThem.dangTep(chiThem, DUONG + "/import/preview", tep.getBytes(UTF), "e.csv");

        assertThat(kho.getStatusCode()).as("%s", kho.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(kho.getBody()).contains("hr:employee:update");

        // ⭐ Vế phân biệt: CÙNG tài khoản ấy, một mã MỚI ⇒ thêm được. Thiếu nó thì một hệ chặn mọi lượt nhập cũng xanh.
        String tepMoi = csv(dong("NV-QUYEN-2", "Người mới", maXnA, "", "", "", ""));
        assertThat(phienThem
                        .dangTep(chiThem, DUONG + "/import/preview", tepMoi.getBytes(UTF), "f.csv")
                        .getBody())
                .contains("\"toCreate\":1")
                .doesNotContain("hr:employee:update");
    }

    // ---- Trợ giúp -----------------------------------------------------------

    private static final java.nio.charset.Charset UTF = StandardCharsets.UTF_8;

    private static String csv(String... phan) {
        if (phan.length == 1 && phan[0].startsWith("ma_cbnv")) {
            return phan[0];
        }
        StringBuilder b = new StringBuilder(
                "ma_cbnv;ho_ten;ma_don_vi;ngay_sinh;gioi_tinh;trinh_do;trang_thai;ngay_vao_cong_ty\n");
        for (String d : phan) {
            b.append(d).append('\n');
        }
        return b.toString();
    }

    private String dong(
            String ma, String ten, String donVi, String ngaySinh, String gioi, String trinhDo, String trangThai) {
        return String.join(";", TIEN_TO + ma, ten, donVi, ngaySinh, gioi, trinhDo, trangThai, "");
    }

    private String hoSoDayDu(String ma) {
        return """
                {"code":"%s","fullName":"Người đầy đủ","dateOfBirth":"1988-04-05","gender":"NAM",\
                "educationLevel":"DAI_HOC","ethnicity":"Kinh","hometown":"Hà Nội","address":"Số 1 phố A",\
                "phone":"0912000111","workEmail":"a@songnhue.test","personalEmail":"b@songnhue.test",\
                "maritalStatus":"DOC_THAN","emergencyContactName":"Người thân","emergencyContactPhone":"0912000222",\
                "orgUnitId":"%s","jobTitle":"Cán bộ kỹ thuật","hiredAt":"2015-01-05",\
                "contractType":"KHONG_XAC_DINH_THOI_HAN","contractSignedAt":"2015-01-05","status":"DANG_LAM"}"""
                .formatted(ma, publicIdDonVi(xnA));
    }

    private String hoSoToiThieu(String ma, String maDonVi) {
        long id = maDonVi.equals(maXnA) ? xnA : xnB;
        return """
                {"code":"%s","fullName":"Hồ sơ %s","orgUnitId":"%s","status":"DANG_LAM"}"""
                .formatted(ma, ma, publicIdDonVi(id));
    }

    /** XLSX một dòng dữ liệu với ô {@code ngay_vao_cong_ty} là ô NGÀY thật (số sê-ri + định dạng). */
    private static byte[] xlsxMotDong(String ma, String ten, String donVi, int soSeriNgay) throws Exception {
        String sheet =
                """
                <worksheet><sheetData>
                <row r="1">%s</row>
                <row r="2"><c r="A2" t="inlineStr"><is><t>%s</t></is></c>\
                <c r="B2" t="inlineStr"><is><t>%s</t></is></c>\
                <c r="C2" t="inlineStr"><is><t>%s</t></is></c>\
                <c r="D2" s="1"><v>%d</v></c></row>
                </sheetData></worksheet>"""
                        .formatted(tieuDeXlsx(), ma, ten, donVi, soSeriNgay);
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(ra)) {
            them(zip, "xl/workbook.xml", "<workbook><workbookPr/></workbook>");
            them(
                    zip,
                    "xl/styles.xml",
                    "<styleSheet><cellXfs count=\"2\"><xf numFmtId=\"0\"/><xf numFmtId=\"14\"/>"
                            + "</cellXfs></styleSheet>");
            them(zip, "xl/worksheets/sheet1.xml", sheet);
        }
        return ra.toByteArray();
    }

    private static String tieuDeXlsx() {
        String[] cot = {"ma_cbnv", "ho_ten", "ma_don_vi", "ngay_vao_cong_ty"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cot.length; i++) {
            b.append("<c r=\"%s1\" t=\"inlineStr\"><is><t>%s</t></is></c>".formatted((char) ('A' + i), cot[i]));
        }
        return b.toString();
    }

    private static void them(ZipOutputStream zip, String ten, String noiDung) throws Exception {
        zip.putNextEntry(new ZipEntry(ten));
        zip.write(noiDung.getBytes(UTF));
        zip.closeEntry();
    }

    /** ⚠ Đếm theo MẪU MÃ của riêng bài gọi: các bài khác cùng lớp cũng để lại hồ sơ, và lớp chỉ dọn ở @AfterAll. */
    private int demHoSo(String mau) {
        return jdbc.queryForObject("SELECT count(*) FROM employees WHERE code LIKE ?", Integer.class, TIEN_TO + mau);
    }

    private UUID publicIdDonVi(long id) {
        return jdbc.queryForObject("SELECT public_id FROM org_units WHERE id = ?", UUID.class, id);
    }

    private long themDonVi(String ma, String pathCha) {
        Long id = jdbc.queryForObject(
                "INSERT INTO org_units (code, name, unit_type, parent_id, path, depth, sort_order, created_at) "
                        + "VALUES (?, ?, 'XI_NGHIEP', ?, '/0/', 0, 0, now()) RETURNING id",
                Long.class,
                ma,
                "Đơn vị kiểm thử " + ma,
                rootId);
        String path = pathCha + id + "/";
        jdbc.update("UPDATE org_units SET path = ?, depth = ? WHERE id = ?", path, path.split("/").length - 1, id);
        return id;
    }

    private void taoVaiTro(String ma, List<String> quyen) {
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, ?, 'Tạm, xoá ở @AfterAll', FALSE, now())",
                ma,
                "Vai trò kiểm thử " + ma);
        int gan = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = ? AND p.code IN ("
                        + String.join(",", java.util.Collections.nCopies(quyen.size(), "?")) + ")",
                java.util.stream.Stream.concat(java.util.stream.Stream.of(ma), quyen.stream())
                        .toArray());
        assertThat(gan)
                .as("⚠ chống tập rỗng: mã quyền đổi tên thì gán thiếu trong im lặng")
                .isEqualTo(quyen.size());
    }

    private void don() {
        jdbc.update("UPDATE users SET employee_id = NULL WHERE username LIKE 'kiemtra_t6823%'");
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM positions WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("UPDATE users SET org_unit_id = (SELECT id FROM org_units WHERE parent_id IS NULL) "
                + "WHERE username LIKE 'kiemtra_t6823%'");
        for (String bang : List.of("jobs", "attachments")) {
            jdbc.update(
                    "DELETE FROM " + bang + " WHERE org_unit_id IN (SELECT id FROM org_units WHERE code LIKE ?)",
                    TIEN_TO + "%");
        }
        jdbc.update("DELETE FROM org_units WHERE code LIKE ?", TIEN_TO + "%");
        for (String vt : List.of(VAI_TRO_DU, VAI_TRO_CHI_THEM)) {
            jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", vt);
            jdbc.update("DELETE FROM roles WHERE code = ?", vt);
        }
        authorities.invalidateAll();
    }
}
