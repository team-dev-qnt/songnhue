package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.hr.application.EmployeeSensitiveService;

/**
 * <b>Trường 🔒 của hồ sơ CBNV — bốn bảo đảm, đo qua HTTP</b> (WS-51, CN-04.2/CN-04.7, quy tắc 10 và
 * 13, NĐ 13/2023/NĐ-CP).
 *
 * <h2>Vì sao lớp này kiểm QUA HTTP chứ ⛔ không gọi thẳng service</h2>
 *
 * <p>Ba trong bốn bảo đảm nằm <b>ngoài</b> {@code EmployeeSensitiveService}: quyền
 * {@code hr:employee:view-sensitive} do {@code @RequirePermission} ép ở tầng 2, việc
 * {@code EmployeeDetail} ⛔ không mang giá trị 🔒 chỉ thấy được ở <i>thân phản hồi</i>, và mã trạng
 * thái 409/403 ra tới trình duyệt là thứ màn hình dựa vào. Luật 5 của dự án viết ra sau khi 391 bài
 * kiểm gọi thẳng service vẫn xanh trong lúc mọi màn hình trả 500; §11.18 thì đo được một lượt vá
 * khẳng định nhầm 422 cho một mã vốn là 400. Gọi thẳng service ở đây chỉ chứng minh được đúng phần
 * nằm trong service.
 *
 * <h2>Bài số 2 và số 5 là lý do lớp này tồn tại</h2>
 *
 * <p>Một javadoc nói <i>"cột này đã mã hoá"</i> ⛔ không phải một cổng kiểm. Bài số 2 đọc thẳng
 * {@code employee_sensitive} bằng {@link JdbcTemplate} và khẳng định <b>⛔ không giá trị thô nào có
 * mặt trong hàng ấy</b>; bài số 5 đo cái tính chất của GCM khiến {@code UNIQUE (national_id)} về
 * nguyên tắc ⛔ không bắt được bản trùng nào — tức là lý do cột {@code national_id_fingerprint} phải
 * tồn tại. Cả hai nói về thứ <b>đo được</b>, ⛔ không về thứ được hứa.
 *
 * <h2>⛔⛔ ⛔ Không một dòng CBNV nào được seed</h2>
 *
 * <p>G6-a (danh sách cán bộ nhân viên) còn mở, và CLAUDE.md cấm seed dữ liệu "cho đẹp demo". Mọi hồ
 * sơ ở đây do chính bài kiểm tạo qua API, mang tiền tố {@code KTMH-}, và bị xoá hẳn ở
 * {@code @AfterAll} kèm một khẳng định rằng lượt dọn đã thật sự chạy.
 *
 * <h2>Số CCCD dùng ở đây là số GIẢ</h2>
 *
 * <p>{@code 0012345678xx} — dãy tăng dần, ⛔ không thuộc về ai. Mỗi bài dùng một số riêng vì
 * {@code uq_employee_sensitive_cccd} là chỉ mục UNIQUE thật: hai bài dùng chung một số sẽ đỏ với
 * {@code HR-1003} ở một bài chẳng canh chuyện đó.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HoSoNhanSuMaHoaTest extends IntegrationTestBase {

    private static final String DUONG_DAN_NV = "/api/v1/hr/employees";

    /** Tiền tố mã CBNV của lớp này — đường dây duy nhất để {@code @AfterAll} dọn đúng phần của mình. */
    private static final String TIEN_TO_MA = "KTMH-";

    /**
     * Vai trò TẠM đủ quyền — ⛔ KHÔNG dùng {@code ADMIN_HR} có sẵn.
     *
     * <p>Hai lý do. Một: {@code ADMIN_HR} nằm trong nhóm bắt buộc 2FA (chốt G12) nên lượt đăng nhập
     * trả {@code TWO_FACTOR_ENROLL_REQUIRED} chứ ⛔ không phải {@code AUTHENTICATED} — đúng chỗ bản
     * đầu của {@code NguonDuLieuMaSoHttpTest} đã đỏ. Hai: bài số 8 cần một tài khoản <b>thiếu đúng
     * một quyền</b>; muốn nói được câu đó thì cả hai vai trò phải do bài kiểm tự dựng, ⛔ không mượn
     * một vai trò thật mà ma trận phân quyền có thể đổi bất cứ lúc nào.
     */
    private static final String VAI_TRO_DU_QUYEN = "KIEMTRA_HR_NHAY_CAM";

    /** Vai trò TẠM chỉ đọc được hồ sơ, ⛔ không đọc được trường 🔒 — đối chứng của bài số 8. */
    private static final String VAI_TRO_THIEU_QUYEN = "KIEMTRA_HR_KHONG_NHAY_CAM";

    // === Giá trị 🔒 dùng chung cho mọi bài — số GIẢ, ⛔ không thuộc về ai ===
    private static final String NGAY_CAP = "2019-05-20";
    private static final String NOI_CAP = "Cục Cảnh sát QLHC về TTXH";
    private static final String LUONG = "12500000";
    private static final String HE_SO = "3.66";
    private static final String MST = "8123456789";
    private static final String BHXH = "0123456789";

    /** Mẫu bản mã của {@code ck_employee_sensitive_banma} — chép nguyên từ migration, cố ý. */
    private static final String MAU_BAN_MA = "^[A-Za-z0-9_-]+:.+$";

    /** Mẫu vân tay của {@code ck_employee_sensitive_vantay}: {@code <key_id>:<64 hex>}. */
    private static final String MAU_VAN_TAY = "^[A-Za-z0-9_-]+:[0-9a-f]{64}$";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CryptoService crypto;

    @Autowired
    private EmployeeSensitiveService hoSoNhayCam;

    private PhienHttp phienHttp;
    private PhienHttp.Phien duQuyen;
    private PhienHttp.Phien thieuQuyen;
    private UUID donViGoc;

    @BeforeAll
    void dungVaiTroVaDangNhap() {
        donSachHoSo();
        donSachVaiTro();

        int quyenDayDu = capVaiTro(
                VAI_TRO_DU_QUYEN,
                "Vai trò kiểm thử trường nhạy cảm HR",
                List.of("hr:employee:view", "hr:employee:create", "hr:employee:view-sensitive"));
        assertThat(quyenDayDu)
                .as("⚠ Chống tập rỗng: một mã quyền đổi tên thì lệnh trên gán 0 dòng TRONG IM LẶNG, "
                        + "và mọi bài dưới đây đỏ với 403 — một triệu chứng chẳng liên quan gì tới "
                        + "thứ đang kiểm (luật 7)")
                .isEqualTo(3);

        int quyenThieu = capVaiTro(
                VAI_TRO_THIEU_QUYEN, "Vai trò kiểm thử HR không có view-sensitive", List.of("hr:employee:view"));
        assertThat(quyenThieu)
                .as("vai trò này phải có ĐÚNG một quyền — thừa `view-sensitive` thì bài số 8 xanh vì lý do sai")
                .isEqualTo(1);

        phienHttp = new PhienHttp(http);
        duQuyen = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "hr_nhaycam", VAI_TRO_DU_QUYEN));
        thieuQuyen =
                phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "hr_thuong", VAI_TRO_THIEU_QUYEN));

        donViGoc = UUID.fromString(
                jdbc.queryForObject("SELECT public_id::text FROM org_units WHERE code = 'CTY'", String.class));
    }

    @AfterAll
    void donDep() {
        donSachHoSo();
        donSachVaiTro();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employees WHERE code LIKE ?", Integer.class, TIEN_TO_MA + "%"))
                .as("⛔ Hồ sơ sót lại mang một CCCD đã chiếm chỗ ở chỉ mục UNIQUE — lượt chạy sau đỏ "
                        + "với HR-1003 ở một bài chẳng canh chuyện đó. Khẳng định ngay tại chỗ dọn.")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM roles WHERE code IN (?, ?)",
                        Integer.class,
                        VAI_TRO_DU_QUYEN,
                        VAI_TRO_THIEU_QUYEN))
                .as("⛔ Dọn dẹp hỏng trong im lặng ở đây làm RbacMatrixTest đỏ ở MỘT LỚP KHÁC — "
                        + "loại lỗi khó lần nhất")
                .isZero();
    }

    // =========================================================================
    // 1 — Vòng khứ hồi
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Vòng khứ hồi: PUT đủ 8 trường 🔒, GET trả lại ĐÚNG 8 giá trị ấy")
    void theSensitiveDialogRoundTripsAllEightFields() {
        // ⛔⛔ Bài này canh đúng cái bẫy §11.19: `EmployeeSensitiveForm` là THAY TOÀN PHẦN, và một
        // trường bị đánh rơi ở tầng biểu mẫu/DTO ⛔ không có triệu chứng nào cho tới ngày có dữ liệu
        // thật. Tám trường phải đi hết một vòng và về nguyên vẹn — ⛔ không phải "PUT trả 204".
        UUID hoSo = taoHoSo("VONG");
        String cccd = "001234567890";
        String taiKhoan = "19001111222233";

        luuNhayCam(hoSo, cccd, taiKhoan, HttpStatus.NO_CONTENT);

        ResponseEntity<String> doc = phienHttp.get(duQuyen, duongNhayCam(hoSo));
        assertThat(doc.getStatusCode()).as("%s", doc.getBody()).isEqualTo(HttpStatus.OK);

        String than = doc.getBody();
        assertThat(PhienHttp.giaTriJson(than, "nationalId")).isEqualTo(cccd);
        assertThat(PhienHttp.giaTriJson(than, "nationalIdIssuedOn")).isEqualTo(NGAY_CAP);
        assertThat(PhienHttp.giaTriJson(than, "nationalIdIssuedPlace")).isEqualTo(NOI_CAP);
        assertThat(PhienHttp.giaTriJson(than, "baseSalary")).isEqualTo(LUONG);
        assertThat(PhienHttp.giaTriJson(than, "salaryCoefficient")).isEqualTo(HE_SO);
        assertThat(PhienHttp.giaTriJson(than, "bankAccount")).isEqualTo(taiKhoan);
        assertThat(PhienHttp.giaTriJson(than, "taxCode")).isEqualTo(MST);
        assertThat(PhienHttp.giaTriJson(than, "socialInsuranceNo")).isEqualTo(BHXH);
    }

    // =========================================================================
    // 2 — Giá trị thô ⛔ không nằm trong CSDL
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ Sau khi lưu, HÀNG trong employee_sensitive không chứa một giá trị thô nào")
    void noPlaintextEverReachesTheSensitiveTable() {
        UUID hoSo = taoHoSo("MAHOA");
        String cccd = "001234567891";
        String taiKhoan = "19003333444455";

        luuNhayCam(hoSo, cccd, taiKhoan, HttpStatus.NO_CONTENT);

        Map<String, Object> hang = jdbc.queryForMap(
                """
                SELECT es.national_id, es.national_id_issued_on, es.national_id_issued_place,
                       es.base_salary, es.salary_coefficient, es.bank_account, es.tax_code,
                       es.social_insurance_no, es.national_id_fingerprint
                  FROM employee_sensitive es
                  JOIN employees e ON e.id = es.employee_id
                 WHERE e.public_id = ?
                """,
                hoSo);

        String banMaCccd = (String) hang.get("national_id");
        assertThat(banMaCccd)
                .as("⛔⛔ Đây là bài chứng minh mã hoá THẬT chứ ⛔ không phải một lời hứa trong javadoc")
                .isNotNull()
                .isNotEqualTo(cccd)
                .doesNotContain(cccd)
                .matches(MAU_BAN_MA);

        // Canh CẤU TRÚC chứ ⛔ không canh văn bản (luật 2): tiền tố phải là một key_id mà chính
        // CryptoService đọc ra được, ⛔ không phải chuỗi "v1" gõ tay ở đây — một bài kiểm ghi cứng
        // key_id sẽ đỏ oan vào ngày xoay khoá, tức đúng ngày ⛔ không được phép có tiếng ồn.
        String khoa = crypto.keyIdOf(banMaCccd);
        assertThat(khoa)
                .as("bản mã phải mang tiền tố key_id — thiếu nó thì lượt xoay khoá ⛔ không biết bản "
                        + "ghi này giải bằng khoá nào")
                .isNotBlank();
        assertThat(crypto.decrypt(banMaCccd))
                .as("⚠ Đối chứng phải-tìm-thấy: một cột bị ghi rác cũng 'khác giá trị thô' và cũng "
                        + "khớp mẫu. Chỉ phép giải mã mới phân biệt được hai trạng thái ấy (luật 9)")
                .isEqualTo(cccd);

        assertThat((String) hang.get("national_id_fingerprint"))
                .as("vân tay là thứ ép được CCCD unique — thiếu nó thì phép chống trùng câm lặng")
                .isNotNull()
                .doesNotContain(cccd)
                .matches(MAU_VAN_TAY)
                .startsWith(khoa + ":");

        // Luật 28 — phạm vi của một bộ canh do chính nó ĐO, ⛔ không do người viết liệt kê tay: quét
        // CẢ HÀNG thay vì soi mỗi `national_id`. Thêm một cột 🔒 mà quên mã hoá thì bài này đỏ ngay,
        // ⛔ không cần ai nhớ bổ sung một khẳng định.
        for (Map.Entry<String, Object> cot : hang.entrySet()) {
            if (cot.getValue() == null) {
                continue;
            }
            assertThat(cot.getValue().toString())
                    .as("cột `%s` phải ở dạng bản mã <key_id>:<...>", cot.getKey())
                    .matches(MAU_BAN_MA);
        }

        String caHang = hang.values().stream()
                .map(giaTri -> giaTri == null ? "" : giaTri.toString())
                .reduce("", (a, b) -> a + " " + b);
        assertThat(caHang)
                .as("⛔ Không MỘT giá trị thô nào được có mặt ở bất kỳ cột nào của hàng này")
                .doesNotContain(cccd)
                .doesNotContain(taiKhoan)
                .doesNotContain(LUONG)
                .doesNotContain(MST)
                .doesNotContain(BHXH)
                .doesNotContain(NOI_CAP);
    }

    // =========================================================================
    // 3 — Endpoint hồ sơ ⛔ không mang giá trị 🔒
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ GET /employees/{id} không mang giá trị 🔒 — nhưng CÓ cờ cccdDaCo")
    void theEmployeeDetailEndpointCarriesFlagsButNeverValues() {
        UUID hoSo = taoHoSo("LO");
        String cccd = "001234567892";
        String taiKhoan = "99887766550011";

        luuNhayCam(hoSo, cccd, taiKhoan, HttpStatus.NO_CONTENT);

        ResponseEntity<String> chiTiet = phienHttp.get(duQuyen, DUONG_DAN_NV + "/" + hoSo);
        assertThat(chiTiet.getStatusCode()).as("%s", chiTiet.getBody()).isEqualTo(HttpStatus.OK);

        String than = chiTiet.getBody();
        assertThat(than)
                .as("⛔⛔ Đường hồ sơ gác bằng `hr:employee:view` — quyền mà gần như mọi vai trò HR "
                        + "đều có. Một giá trị 🔒 lọt vào đây là bỏ qua trọn vẹn hàng rào "
                        + "`hr:employee:view-sensitive`")
                .doesNotContain(cccd)
                .doesNotContain(taiKhoan)
                .doesNotContain(LUONG)
                .doesNotContain(MST)
                .doesNotContain(BHXH);

        // ⚠ Đối chứng phải-tìm-thấy. Thiếu vế này thì một endpoint trả rỗng — hay một lượt 404 đọc
        // nhầm thành 200 — cũng làm năm khẳng định trên xanh trọn vẹn (luật 7, §11.19).
        assertThat(than)
                .as("cờ phải nói ĐÃ CÓ dữ liệu: người dùng cần phân biệt 'chưa nhập' với 'không được xem'")
                .contains("\"cccdDaCo\":true")
                .contains("\"taiKhoanDaCo\":true")
                .contains("\"" + TIEN_TO_MA + "LO\"");
    }

    // =========================================================================
    // 4 — CCCD trùng
    // =========================================================================

    @Test
    @DisplayName("CCCD trùng trả 409 + HR-1003 — và không nói hồ sơ nào đang giữ số ấy")
    void aDuplicateNationalIdIsRejectedWithoutNamingTheOtherRecord() {
        UUID hoSoA = taoHoSo("TRUNGA");
        UUID hoSoB = taoHoSo("TRUNGB");
        String cccdChung = "001234567893";
        String cccdRieng = "001234567894";

        luuNhayCam(hoSoA, cccdChung, "19005555666677", HttpStatus.NO_CONTENT);

        ResponseEntity<String> trung =
                phienHttp.goi(duQuyen, HttpMethod.PUT, duongNhayCam(hoSoB), thanNhayCam(cccdChung, "19007777888899"));
        assertThat(trung.getStatusCode())
                .as(
                        "⛔ `uq_employee_sensitive_cccd` là chỉ mục trên cột VÂN TAY — nếu phép chống "
                                + "trùng đi qua cột bản mã thì lượt này sẽ THÀNH CÔNG: %s",
                        trung.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(trung.getBody()).contains("HR-1003");
        assertThat(trung.getBody())
                .as("⛔ Nói ra hồ sơ nào đang giữ số CCCD ấy là một phép rò rỉ dữ liệu cá nhân qua "
                        + "đúng cái cửa dựng ra để bảo vệ nó")
                .doesNotContain(TIEN_TO_MA + "TRUNGA")
                .doesNotContain(cccdChung);

        // ⚠ Đối chứng: một phép chống trùng từ chối TẤT CẢ cũng làm khẳng định trên xanh, và nó khoá
        // cứng màn hình 🔒 — người vận hành sẽ đòi tắt luật đi, tức tệ hơn ⛔ không có luật.
        luuNhayCam(hoSoB, cccdRieng, "19007777888899", HttpStatus.NO_CONTENT);
        assertThat(PhienHttp.giaTriJson(
                        phienHttp.get(duQuyen, duongNhayCam(hoSoB)).getBody(), "nationalId"))
                .isEqualTo(cccdRieng);
    }

    // =========================================================================
    // 5 — Vì sao phải có cột vân tay
    // =========================================================================

    @Test
    @DisplayName("⭐ GCM: cùng một CCCD cho HAI bản mã khác nhau, nhưng CHỈ MỘT vân tay")
    void gcmMakesCiphertextUselessForUniquenessWhileTheFingerprintIsStable() {
        // ⛔⛔ Đây là bài trả lời câu "vì sao ⛔ không đặt thẳng UNIQUE lên `national_id`". Nếu đặt,
        // chỉ mục ấy sẽ TỒN TẠI, đọc như một bảo đảm, và ⛔ KHÔNG BAO GIỜ bắt được một bản trùng nào
        // — đúng hình dạng "một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai" (luật 7).
        String cccd = "001234567898";
        String cccdKhac = "001234567899";

        String lan1 = crypto.encrypt(cccd);
        String lan2 = crypto.encrypt(cccd);

        assertThat(lan1)
                .as("GCM sinh IV ngẫu nhiên mỗi lượt ⇒ hai bản mã của CÙNG một số phải KHÁC nhau; "
                        + "bằng nhau nghĩa là IV đã thành hằng số, một khuyết tật mật mã nặng")
                .isNotEqualTo(lan2);
        assertThat(crypto.decrypt(lan1)).isEqualTo(cccd);
        assertThat(crypto.decrypt(lan2)).isEqualTo(cccd);

        assertThat(crypto.fingerprint(cccd))
                .as("vân tay phải XÁC ĐỊNH — đó là toàn bộ lý do nó tồn tại")
                .isEqualTo(crypto.fingerprint(cccd));
        assertThat(crypto.fingerprint(cccd))
                .as("⚠ Đối chứng: một hàm trả hằng số cũng 'xác định'. Hai số khác nhau phải cho hai "
                        + "vân tay khác nhau, ⛔ không thì chỉ mục UNIQUE sẽ từ chối MỌI hồ sơ thứ hai")
                .isNotEqualTo(crypto.fingerprint(cccdKhac));
        assertThat(crypto.fingerprint(cccd))
                .as("và vân tay ⛔ không được để lộ chính số CCCD")
                .doesNotContain(cccd)
                .matches(MAU_VAN_TAY);
    }

    // =========================================================================
    // 6 — Cột vân tay chỉ mang MỘT key_id
    // =========================================================================

    @Test
    @DisplayName("⭐ Cột vân tay chỉ mang MỘT key_id — nhiều hơn là chữ ký của một lượt xoay khoá dở dang")
    void theFingerprintColumnCarriesExactlyOneKeyId() {
        // ⚠⚠ Vân tay PHỤ THUỘC KHOÁ. Sau một lượt xoay khoá mà job (⛔ chưa tồn tại) ⛔ không tính
        // lại cột này, cùng một số CCCD cho hai vân tay khác nhau ⇒ phép chống trùng câm lặng, ⛔
        // không một dòng lỗi. Tập key_id có nhiều hơn MỘT phần tử chính là chữ ký của chuyện đó.
        UUID hoSo = taoHoSo("VANTAY");
        luuNhayCam(hoSo, "001234567895", "19009999000011", HttpStatus.NO_CONTENT);

        Integer soHangCoVanTay = jdbc.queryForObject(
                "SELECT count(*) FROM employee_sensitive WHERE deleted_at IS NULL "
                        + "AND national_id_fingerprint IS NOT NULL",
                Integer.class);
        assertThat(soHangCoVanTay)
                .as("⚠ Chống tập rỗng — ⛔ KHÔNG phải thủ tục. Bảng ⛔ không có hàng nào thì "
                        + "`khoaDangDungOVanTay()` trả danh sách RỖNG, size 0 ≤ 1, và bài này xanh "
                        + "trọn vẹn trong đúng tình huống nó sinh ra để bắt (luật 7, §11.19)")
                .isPositive();

        List<String> khoa = hoSoNhayCam.khoaDangDungOVanTay();
        assertThat(khoa).isNotEmpty();
        assertThat(khoa)
                .as("⛔ Hai key_id ở cột vân tay nghĩa là hai hồ sơ mang CÙNG một số CCCD ⛔ không "
                        + "còn đụng nhau — chỉ mục UNIQUE vẫn ở đó và ⛔ không bắt được gì nữa")
                .hasSizeLessThanOrEqualTo(1);
    }

    // =========================================================================
    // 7 — Lượt ĐỌC để lại một dòng security_events
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ Mỗi lượt ĐỌC trường 🔒 để lại ĐÚNG một dòng security_events — có mã NV, không có CCCD")
    void everySensitiveReadLeavesExactlyOneSecurityEvent() {
        // ⛔⛔ `audit_logs` chỉ sinh dòng khi có THAY ĐỔI. Thiếu dòng nhật ký ĐỌC thì một người có
        // quyền mở lần lượt toàn bộ hồ sơ để chép số tài khoản ⛔ không để lại một dấu vết nào ở bất
        // kỳ bảng nào — đúng thứ nguyên tắc tối thiểu của NĐ 13/2023 cần nhìn thấy.
        UUID hoSo = taoHoSo("NHATKY");
        String cccd = "001234567896";
        luuNhayCam(hoSo, cccd, "19001212343456", HttpStatus.NO_CONTENT);

        int truoc = soSuKienDoc();
        ResponseEntity<String> doc = phienHttp.get(duQuyen, duongNhayCam(hoSo));
        assertThat(doc.getStatusCode()).as("%s", doc.getBody()).isEqualTo(HttpStatus.OK);
        int sau = soSuKienDoc();

        assertThat(sau)
                .as("⛔ Lượt LƯU (PUT) ở trên ⛔ không được sinh sự kiện ĐỌC, và một lượt ĐỌC phải "
                        + "sinh ĐÚNG một dòng — ⛔ không 0 (không ai biết), ⛔ không 2 (số liệu điều "
                        + "tra sai)")
                .isEqualTo(truoc + 1);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM security_events WHERE event_type = 'HR_SENSITIVE_FIELDS_READ' "
                                + "AND detail::text LIKE ?",
                        Integer.class,
                        "%" + TIEN_TO_MA + "NHATKY%"))
                .as("⚠ Đối chứng phải-tìm-thấy: dòng nhật ký phải nói ĐỌC HỒ SƠ NÀO, ⛔ không thì nó "
                        + "chỉ là một con số đếm được mà ⛔ không điều tra được gì")
                .isPositive();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM security_events WHERE detail::text LIKE ?",
                        Integer.class,
                        "%" + cccd + "%"))
                .as("⛔⛔ Nhật ký bảo mật lưu 5 năm và nhiều người đọc được hơn bảng gốc. Ghi giá trị "
                        + "vừa đọc vào đây là đổ đúng thứ vừa mã hoá vào một bảng ⛔ không mã hoá")
                .isZero();
    }

    // =========================================================================
    // 8 — Đối chứng quyền
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ Thiếu hr:employee:view-sensitive thì GET /sensitive trả 403 — dù hồ sơ vẫn đọc được")
    void aUserWithoutTheSensitivePermissionIsRefused() {
        UUID hoSo = taoHoSo("QUYEN");
        String cccd = "001234567897";
        String taiKhoan = "19003434565678";
        luuNhayCam(hoSo, cccd, taiKhoan, HttpStatus.NO_CONTENT);

        // ⚠ Đối chứng phải-tìm-thấy TRƯỚC: chính tài khoản ấy đọc được HỒ SƠ. Thiếu vế này thì một
        // phiên hỏng, một token hết hạn hay một đường dẫn gõ sai cũng cho ra 403 và bài vẫn xanh —
        // xanh vì lý do sai (luật 9).
        ResponseEntity<String> hoSoThuong = phienHttp.get(thieuQuyen, DUONG_DAN_NV + "/" + hoSo);
        assertThat(hoSoThuong.getStatusCode())
                .as("`hr:employee:view` phải đủ để mở hồ sơ: %s", hoSoThuong.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(hoSoThuong.getBody())
                .as("và người ⛔ không được xem vẫn phải phân biệt được 'chưa nhập' với 'không được xem'")
                .contains("\"cccdDaCo\":true");

        ResponseEntity<String> nhayCam = phienHttp.get(thieuQuyen, duongNhayCam(hoSo));
        assertThat(nhayCam.getStatusCode())
                .as(
                        "⛔⛔ `V202608131007:169` loại trừ TƯỜNG MINH cả ADMIN khỏi quyền này — hàng "
                                + "rào ấy phải đứng ở tầng 2, ⛔ không ở một nhánh `if` bên trong "
                                + "controller: %s",
                        nhayCam.getBody())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(nhayCam.getBody())
                .as("mã lỗi phải nói ĐÚNG chuyện gì sai — thiếu quyền là AUTH-3001")
                .contains("AUTH-3001");
        assertThat(nhayCam.getBody())
                .as("⛔ Và thân của một lượt TỪ CHỐI tuyệt đối ⛔ không được mang giá trị 🔒")
                .doesNotContain(cccd)
                .doesNotContain(taiKhoan);
    }

    // =========================================================================
    // Bộ đồ gá
    // =========================================================================

    private static String duongNhayCam(UUID hoSo) {
        return DUONG_DAN_NV + "/" + hoSo + "/sensitive";
    }

    /**
     * Tạo một hồ sơ qua API — ⛔ KHÔNG chèn thẳng bằng SQL.
     *
     * <p>Đi đúng đường người dùng đi thì bài kiểm cũng đo luôn rằng {@code POST} còn sống; một
     * {@code INSERT} tay sẽ vẫn xanh vào ngày endpoint tạo hồ sơ hỏng.
     */
    private UUID taoHoSo(String hau) {
        String than =
                """
                {"code":"%s","fullName":"Nguyễn Văn Kiểm Thử %s","orgUnitId":"%s","status":"THU_VIEC"}"""
                        .formatted(TIEN_TO_MA + hau, hau, donViGoc);
        ResponseEntity<String> tao = phienHttp.goi(duQuyen, HttpMethod.POST, DUONG_DAN_NV, than);
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định bên dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(PhienHttp.giaTriJson(tao.getBody(), "publicId"));
    }

    private void luuNhayCam(UUID hoSo, String cccd, String taiKhoan, HttpStatus mongDoi) {
        ResponseEntity<String> luu =
                phienHttp.goi(duQuyen, HttpMethod.PUT, duongNhayCam(hoSo), thanNhayCam(cccd, taiKhoan));
        assertThat(luu.getStatusCode()).as("%s", luu.getBody()).isEqualTo(mongDoi);
    }

    private static String thanNhayCam(String cccd, String taiKhoan) {
        return """
                {"nationalId":"%s","nationalIdIssuedOn":"%s","nationalIdIssuedPlace":"%s",\
                "baseSalary":"%s","salaryCoefficient":"%s","bankAccount":"%s",\
                "taxCode":"%s","socialInsuranceNo":"%s"}"""
                .formatted(cccd, NGAY_CAP, NOI_CAP, LUONG, HE_SO, taiKhoan, MST, BHXH);
    }

    private int soSuKienDoc() {
        Integer so = jdbc.queryForObject(
                "SELECT count(*) FROM security_events WHERE event_type = 'HR_SENSITIVE_FIELDS_READ'", Integer.class);
        return so == null ? 0 : so;
    }

    private int capVaiTro(String ma, String ten, List<String> quyen) {
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, ?, 'Tạm, xoá ở @AfterAll', FALSE, now())",
                ma,
                ten);
        int tong = 0;
        for (String p : quyen) {
            tong += jdbc.update(
                    "INSERT INTO role_permissions (role_id, permission_id) "
                            + "SELECT r.id, pm.id FROM roles r, permissions pm WHERE r.code = ? AND pm.code = ?",
                    ma,
                    p);
        }
        return tong;
    }

    private void donSachHoSo() {
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO_MA + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO_MA + "%");
        // ⚠ Cố ý ⛔ KHÔNG dọn `security_events`: vai trò `songnhue_app` ⛔ không có DELETE trên bảng
        // đó (WS-2/T2.7) — thử dọn là nhận "permission denied", và bản thân điều ấy là một bằng
        // chứng nhỏ rằng việc tách vai trò ở tầng CSDL đang có hiệu lực. Bài số 7 đếm CHÊNH LỆCH
        // trước/sau nên dữ liệu sót ⛔ không ảnh hưởng.
    }

    private void donSachVaiTro() {
        for (String ma : List.of(VAI_TRO_DU_QUYEN, VAI_TRO_THIEU_QUYEN)) {
            jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", ma);
            jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", ma);
            jdbc.update("DELETE FROM roles WHERE code = ?", ma);
        }
    }
}
