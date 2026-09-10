package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Vòng khứ hồi HTTP của hồ sơ CBNV và danh mục chức vụ — WS-51, CN-04.2.
 *
 * <h2>Vì sao lớp này kiểm QUA HTTP chứ ⛔ không gọi thẳng service</h2>
 *
 * <p>{@code HoSoNhanSuPhamViTest} đã canh tầng 3 (phạm vi đơn vị) bằng cách gọi
 * {@code EmployeeService} trực tiếp — đúng chỗ, vì thứ nó khẳng định nằm trong service. Nhưng
 * <b>ba</b> bảo đảm của MOD-04 ⛔ không nằm trong service, và ⛔ không lượt gọi service nào chạm tới
 * chúng:
 *
 * <ul>
 *   <li><b>Mã trạng thái + mã lỗi ra tới trình duyệt</b> — §11.18 đã trả giá đúng chỗ này: một lượt
 *       vá khẳng định {@code SYS-0003} là 422 trong khi nó là <b>400</b>. Một khẳng định về HTTP
 *       phải đo trên HTTP.
 *   <li><b>Vòng khứ hồi {@code GET} → {@code PUT}</b> — §11.19: một {@code PUT} thiếu trường đã xoá
 *       trắng tuyến sông/lý trình của 19 điểm đo và vô hình suốt hai tuần vì mọi ô vốn đã NULL. Hồ
 *       sơ CBNV có <b>21</b> trường tuỳ chọn đi qua đúng cơ chế thay-toàn-phần ấy.
 *   <li><b>Sắp xếp mặc định của màn hình</b> — {@code PageUtils} ném khi trường sort ngoài bảng
 *       trắng, nên một mặc định lệch làm màn hình trả lỗi ở <i>lượt tải đầu tiên</i> và trông y hệt
 *       "bảng vốn rỗng" (§10.62, {@code ConstructionHttpTest:151-193}).
 * </ul>
 *
 * <h2>⛔⛔ Lớp này ⛔ KHÔNG seed một dòng CBNV nào</h2>
 *
 * <p>G6-a (danh sách CBNV của Công ty) còn mở, và CLAUDE.md cấm seed dữ liệu "cho đẹp demo". Mọi
 * hàng ở đây do <b>chính bài kiểm tạo qua HTTP</b>, mang tiền tố {@link #TIEN_TO}, và bị xoá cứng ở
 * {@link #donVaiTroVaDuLieu()}. Bảng rỗng sau lượt chạy là trạng thái ĐÚNG.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HoSoNhanSuHttpTest extends IntegrationTestBase {

    private static final String DUONG_HO_SO = "/api/v1/hr/employees";
    private static final String DUONG_CHUC_VU = "/api/v1/hr/positions";

    /** Tiền tố của mọi mã do lớp này sinh ra — vừa để lọc khỏi dữ liệu lạ, vừa để dọn. */
    private static final String TIEN_TO = "WS51HTTP-";

    /**
     * ⚠ Vai trò TẠM mang <b>đúng bốn</b> quyền của hồ sơ CBNV — ⛔ KHÔNG dùng {@code ADMIN}.
     *
     * <p>{@code ADMIN} nằm trong {@code TWO_FACTOR_REQUIRED_ROLES} (chốt G12) nên lượt đăng nhập
     * trả {@code TWO_FACTOR_ENROLL_REQUIRED} chứ ⛔ không phải {@code AUTHENTICATED}, và mọi bài
     * dưới đây đỏ ở bước dựng phiên vì một lý do chẳng liên quan gì tới thứ đang kiểm.
     *
     * <p>⛔ Cố ý ⛔ KHÔNG cấp {@code hr:employee:view-sensitive}: lớp này ⛔ không đụng trường 🔒, và
     * cấp sẵn cả cụm quyền là cách chắc chắn nhất để một lượt kiểm quyền trở nên vô nghĩa mà vẫn
     * xanh.
     */
    private static final String VAI_TRO_TAM = "KIEMTRA_HR_HO_SO";

    private static final List<String> QUYEN_CAN =
            List.of("hr:employee:view", "hr:employee:create", "hr:employee:update", "hr:employee:delete");

    /**
     * Trường của {@code EmployeeDetail} phải sống sót qua một lượt {@code PUT} nguyên văn.
     *
     * <p>⛔ Đây là tập <b>giao</b> giữa {@code EmployeeDetail} và {@code EmployeeRequest} — tức đúng
     * những ô mà biểu mẫu đọc lên rồi ghi xuống. {@code publicId}, {@code orgUnitName},
     * {@code positionName}, {@code sensitive} ⛔ không có mặt vì chúng là giá trị <i>dẫn xuất</i>,
     * ⛔ không phải ô nhập.
     */
    private static final List<String> TRUONG_PHAI_CON = List.of(
            "code",
            "fullName",
            "dateOfBirth",
            "gender",
            // ⛔⛔ `educationLevel` vào danh sách ngày 10/09/2026 — T54.1. WS-53 dựng ĐỦ BẢY mảnh
            //    của trường "Học vấn" (cột `education_level`, CHECK 9 giá trị, chỉ mục, enum
            //    `EducationLevel`, trường trên `Employee`, nhãn `HOC_VAN`, một dòng trong
            //    `EnumBaNoiTest`) mà ⛔ KHÔNG một đường vào lẫn đường ra nào — 0 trong
            //    `EmployeeRequest`, 0 trong `EmployeeDetail`, 0 ô trên biểu mẫu. Luật 27 ở cỡ lớn
            //    nhất từng gặp, và bộ canh enum làm nó TRÔNG như đã nối.
            "educationLevel",
            "ethnicity",
            "hometown",
            "address",
            "phone",
            "workEmail",
            "personalEmail",
            "maritalStatus",
            "emergencyContactName",
            "emergencyContactPhone",
            "orgUnitId",
            "positionId",
            "jobTitle",
            "hiredAt",
            "contractType",
            "contractSignedAt",
            "contractExpiresAt",
            "status");

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien quanTri;

    /** {@code public_id} của đơn vị gốc — lấy TỪ CSDL, ⛔ không bịa một UUID. */
    private UUID donViGoc;

    @BeforeAll
    void dungVaiTroVaDangNhap() {
        donDuLieuThu();
        donSachVaiTroTam();

        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system, created_at) "
                        + "VALUES (?, 'Vai trò kiểm thử Hồ sơ CBNV', 'Tạm, xoá ở @AfterAll', FALSE, now())",
                VAI_TRO_TAM);
        int soQuyen = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + " WHERE r.code = ? AND p.code IN (?, ?, ?, ?)",
                VAI_TRO_TAM,
                QUYEN_CAN.get(0),
                QUYEN_CAN.get(1),
                QUYEN_CAN.get(2),
                QUYEN_CAN.get(3));
        assertThat(soQuyen)
                .as(
                        "⚠ Chống tập rỗng: một mã quyền đổi tên thì lệnh trên gán ÍT dòng hơn trong im "
                                + "lặng, và mọi bài dưới đây đỏ với 403 — một triệu chứng chẳng liên quan gì "
                                + "tới thứ đang kiểm. Bốn quyền ấy là %s",
                        QUYEN_CAN)
                .isEqualTo(4);

        donViGoc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);
        assertThat(donViGoc)
                .as("⛔ `orgUnitId` là NOT NULL và phải trỏ tới một đơn vị CÓ THẬT — bịa một UUID ở "
                        + "đây thì mọi lượt tạo hồ sơ trả 400 `orgUnitId NOT_FOUND`")
                .isNotNull();

        // ⚠ Đăng nhập MỘT LẦN cho cả lớp: `RateLimitFilter` cấp 30 lượt đăng nhập / 15 phút cho mỗi
        // IP, và `PhienHttp` cấp một IP giả lập cho mỗi thực thể. Đăng nhập ở @BeforeEach là tiêu
        // ngân sách ấy theo số bài kiểm.
        phienHttp = new PhienHttp(http);
        quanTri = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "hr_hoso", VAI_TRO_TAM));
    }

    /**
     * Dọn ở {@code @AfterAll} — ⛔ KHÔNG ở cuối từng phương thức (T48.8).
     *
     * <p>Dọn dẹp viết ở cuối một phương thức ⛔ không chạy khi một khẳng định phía trên đỏ; trạng
     * thái kẹt lại rò sang lớp kiểm chạy sau, và surefire xếp lớp theo <b>thứ tự hệ tệp</b> (macOS
     * ngược Linux) nên triệu chứng là một lớp vô can đỏ trên CI mà ở máy ⛔ không tái lập được.
     */
    @AfterAll
    void donVaiTroVaDuLieu() {
        donDuLieuThu();
        donSachVaiTroTam();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles WHERE code = ?", Integer.class, VAI_TRO_TAM))
                .as("⛔ Dọn dẹp hỏng trong im lặng ở đây làm RbacMatrixTest đỏ ở MỘT LỚP KHÁC — "
                        + "loại lỗi khó lần nhất. Khẳng định ngay tại chỗ dọn.")
                .isZero();
        assertThat(demHoSoThu())
                .as("⛔⛔ G6-a chưa có dữ liệu và CLAUDE.md cấm seed hồ sơ CBNV: bảng phải RỖNG lại "
                        + "sau lượt chạy, ⛔ không để lại một dòng nào cho lớp sau đọc nhầm là dữ liệu thật")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM positions WHERE code LIKE ?", Integer.class, TIEN_TO + "%"))
                .as("chức vụ do lớp này tạo cũng phải biến mất")
                .isZero();
    }

    // =========================================================================
    // 1 — Vòng đời
    // =========================================================================

    @Test
    @DisplayName(
            "Vòng đời: tạo chức vụ → tạo hồ sơ giữ chức vụ ấy → chi tiết đủ trường → có trong danh sách → xoá mềm → biến khỏi danh sách")
    void aFullEmployeeLifecycleGoesThroughHttp() {
        UUID chucVu = taoChucVu("VD-TP", "Trưởng phòng (kiểm thử)");
        String ma = TIEN_TO + "VD-001";

        ResponseEntity<String> tao =
                phienHttp.goi(quanTri, HttpMethod.POST, DUONG_HO_SO, thanHoSoDayDu(ma, chucVu, "DANG_LAM", null));
        assertThat(tao.getStatusCode()).as("tạo hồ sơ: %s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        UUID hoSo = UUID.fromString(chuoi(tao.getBody(), "publicId"));

        ResponseEntity<String> chiTiet = phienHttp.get(quanTri, DUONG_HO_SO + "/" + hoSo);
        assertThat(chiTiet.getStatusCode())
                .as("chi tiết: %s", chiTiet.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(chiTiet.getBody())
                .as("⛔ Tên đơn vị và tên chức vụ là hai giá trị DẪN XUẤT — thiếu chúng thì màn hình "
                        + "chi tiết hiện hai ô trống mà ⛔ không lỗi nào báo")
                .contains("\"code\":\"" + ma + "\"")
                .contains("\"orgUnitName\":")
                .contains("\"positionName\":\"Trưởng phòng (kiểm thử)\"");

        ResponseEntity<String> danhSach = phienHttp.get(quanTri, DUONG_HO_SO + "?q=" + ma + "&sort=code,asc");
        assertThat(danhSach.getStatusCode())
                .as("danh sách: %s", danhSach.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(danhSach.getBody()).contains(ma);
        assertThat(boc(danhSach.getBody(), "totalElements"))
                .as("⚠ Chống tập rỗng (luật 7): khẳng định 'sau khi xoá thì ⛔ không còn' chỉ có "
                        + "nghĩa khi TRƯỚC đó nó ĐANG CÓ — §11.19")
                .isEqualTo("1");

        ResponseEntity<String> xoa = phienHttp.goi(quanTri, HttpMethod.DELETE, DUONG_HO_SO + "/" + hoSo, null);
        assertThat(xoa.getStatusCode()).as("xoá: %s", xoa.getBody()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> sauXoa = phienHttp.get(quanTri, DUONG_HO_SO + "?q=" + ma + "&sort=code,asc");
        assertThat(sauXoa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sauXoa.getBody())
                .as("xoá MỀM: hàng còn trong CSDL nhưng ⛔ không được ra khỏi API nữa")
                .doesNotContain(ma);
        assertThat(boc(sauXoa.getBody(), "totalElements")).isEqualTo("0");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employees WHERE code = ? AND deleted_at IS NOT NULL", Integer.class, ma))
                .as("⛔ và nó phải là xoá MỀM thật — quy tắc 9 đòi soft delete + audit, ⛔ không DELETE cứng")
                .isEqualTo(1);
    }

    // =========================================================================
    // 2 — §11.19: PUT nguyên văn thân GET ⛔ KHÔNG được xoá trường nào
    // =========================================================================

    /**
     * ⭐⭐ <b>Bài quan trọng nhất của lớp này.</b> Vòng khứ hồi của màn hình sửa hồ sơ.
     *
     * <h2>Sự cố nó sinh ra để chặn</h2>
     *
     * <p>09/09/2026: một {@code PUT} thiếu {@code riverName}/{@code chainage} xoá trắng dữ liệu G8
     * của 19 điểm đo. Thay-toàn-phần hiểu <i>"⛔ không gửi"</i> là <i>"xoá"</i>, và tác dụng phụ ấy
     * <b>vô hình suốt từ WS-29</b> vì mọi ô vốn đã NULL — nó chỉ có triệu chứng kể từ ngày dữ liệu
     * tồn tại (§11.19). Hồ sơ CBNV hôm nay đang ở <b>đúng trạng thái ấy</b>: bảng rỗng, nên một
     * biểu mẫu đánh rơi 9 trường sẽ ⛔ không làm bài kiểm nào đỏ.
     *
     * <p>⚠ T47.17 đo được 17/18 endpoint thay-toàn-phần của kho ⛔ không có phép kiểm giữ-nguyên, và
     * ghi rõ một cái bẫy: phép kiểm <i>"PUT nguyên văn thân GET"</i> <b>xanh ở CẢ HAI trạng thái</b>
     * nếu thứ đánh rơi trường là <b>biểu mẫu</b> — tầng HTTP round-trip vốn hoàn hảo. ⇒ Bài này
     * canh <b>hợp đồng của backend</b>: nó chứng minh rằng một thân gửi ĐỦ thì ⛔ không trường nào
     * rơi. Vế còn lại — biểu mẫu có gửi đủ ⛔ không — thuộc về {@code EmployeeForm.tsx} và phải có
     * phép kiểm riêng ở FE ngày màn hình ấy ra đời. Ghi giới hạn ra thay vì để cái xanh này đọc như
     * một bảo đảm cho cả hai vế (luật 28).
     */
    @Test
    @DisplayName("⭐⭐ PUT gửi nguyên văn thân GET thì ⛔ KHÔNG trường nào bị xoá — cả 22 ô còn nguyên (§11.19)")
    void resendingTheGetBodyAsPutDropsNoField() {
        UUID chucVu = taoChucVu("GN-CV", "Chuyên viên (kiểm thử)");
        UUID hoSo = taoHoSo(TIEN_TO + "GN-001", chucVu, "DANG_LAM", null);

        String truoc = duLieu(phienHttp.get(quanTri, DUONG_HO_SO + "/" + hoSo));

        // ⛔⛔ CHỐNG TẬP RỖNG. `default-property-inclusion: non_null` BỎ HẲN trường null khỏi JSON,
        // nên một hồ sơ chỉ có hai ô bắt buộc vẫn làm mọi phép so bên dưới xanh — trên tập rỗng.
        // Đây đúng cách khuyết tật §11.19 sống sót hai tuần: "mọi ô vốn đã NULL".
        long coGiaTri = TRUONG_PHAI_CON.stream()
                .filter(truong -> boc(truoc, truong) != null)
                .count();
        assertThat(coGiaTri)
                .as(
                        "hồ sơ mốc phải ĐANG CÓ dữ liệu ở phần lớn các ô, nếu ⛔ không thì bài này ⛔ "
                                + "không chứng minh gì. Thân GET: %s",
                        truoc)
                .isGreaterThanOrEqualTo(13);

        // Thân PUT dựng TỪ CHÍNH thân GET — ⛔ không gõ lại một trường nào. Gõ lại là chép luôn cả
        // giả định của người viết, và bài kiểm sẽ sai theo đúng cách thứ nó kiểm đang sai (luật 29).
        ResponseEntity<String> sua = phienHttp.goi(quanTri, HttpMethod.PUT, DUONG_HO_SO + "/" + hoSo, truoc);
        assertThat(sua.getStatusCode()).as("lưu: %s", sua.getBody()).isEqualTo(HttpStatus.OK);

        String sau = duLieu(phienHttp.get(quanTri, DUONG_HO_SO + "/" + hoSo));

        for (String truong : TRUONG_PHAI_CON) {
            assertThat(boc(truoc, truong))
                    .as(
                            "⚠ `%s` vắng ngay ở thân GET — phép so dưới đây sẽ là null == null, tức "
                                    + "một khẳng định ⛔ không phân biệt được hai trạng thái (luật 9)",
                            truong)
                    .isNotNull();
            assertThat(boc(sau, truong))
                    .as(
                            "⛔⛔ `%s` bị lượt PUT làm mất. Đây ĐÚNG hình dạng §11.19: màn hình báo "
                                    + "'lưu thành công', dữ liệu biến mất, ⛔ không một dòng lỗi nào. Thân sau: %s",
                            truong, sau)
                    .isEqualTo(boc(truoc, truong));
        }

        // Một mốc CỤ THỂ ngoài phép so trước/sau: nếu cả hai lượt GET cùng hỏng theo một cách thì
        // phép so trên vẫn xanh. Chuỗi này có dấu tiếng Việt nên nó đồng thời đo vòng UTF-8.
        assertThat(boc(sau, "hometown")).isEqualTo("\"Xã Đại Áng, huyện Thanh Trì, Hà Nội\"");
        assertThat(boc(sau, "contractExpiresAt")).isEqualTo("\"2027-02-28\"");
    }

    // =========================================================================
    // 3, 4 — Mã trùng
    // =========================================================================

    @Test
    @DisplayName("Mã CBNV trùng → 409 kèm HR-1001, ⛔ không phải 500 của ràng buộc UNIQUE")
    void aDuplicateEmployeeCodeIsRejectedWithItsOwnErrorCode() {
        UUID chucVu = taoChucVu("TR-CV", "Nhân viên (kiểm thử)");
        String ma = TIEN_TO + "TRUNG-01";
        taoHoSo(ma, chucVu, "DANG_LAM", null);

        ResponseEntity<String> lai =
                phienHttp.goi(quanTri, HttpMethod.POST, DUONG_HO_SO, thanHoSoDayDu(ma, chucVu, "DANG_LAM", null));

        assertThat(lai.getStatusCode())
                .as(
                        "⛔ Để ràng buộc `uq_employees_code` bắt thay thì người dùng nhận một lỗi CSDL "
                                + "trần ⛔ không nói được ô nào sai: %s",
                        lai.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(lai.getBody()).contains("HR-1001");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employees WHERE code = ? AND deleted_at IS NULL", Integer.class, ma))
                .as("và lượt từ chối ⛔ không được để lại một hàng thứ hai")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Mã chức vụ trùng → 409 kèm HR-1002")
    void aDuplicatePositionCodeIsRejectedWithItsOwnErrorCode() {
        taoChucVu("TR-CV2", "Phó phòng (kiểm thử)");

        ResponseEntity<String> lai =
                phienHttp.goi(quanTri, HttpMethod.POST, DUONG_CHUC_VU, thanChucVu(TIEN_TO + "TR-CV2", "Tên khác hẳn"));

        assertThat(lai.getStatusCode())
                .as("mã chức vụ là khoá nghiệp vụ, ⛔ không phải nhãn: %s", lai.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(lai.getBody())
                .as("⛔ HR-1002 chứ ⛔ không HR-1001 — hai mã cho hai bảng khác nhau, dùng nhầm là "
                        + "người dùng đi sửa nhầm màn hình")
                .contains("HR-1002");
    }

    // =========================================================================
    // 5 — Xoá chức vụ còn người giữ
    // =========================================================================

    /**
     * ⛔⛔ Vế thứ hai của T40.26 — <i>hỏi trước khi xoá, ⛔ không dọn sau khi xoá</i>.
     *
     * <p>Xoá thẳng rồi để {@code position_id} của hồ sơ tự về rỗng thì màn hình hồ sơ ⛔ <b>không lộ
     * ra gì</b>: chức vụ chỉ đơn giản biến mất khỏi từng ấy hồ sơ, ⛔ không một dòng cảnh báo — đúng
     * hình dạng "3 trong 4 cửa xoá tệp ⛔ không hỏi ai đang dùng" đo được 08/09.
     */
    @Test
    @DisplayName("Xoá chức vụ còn hồ sơ đang giữ → 422 kèm HR-2002, và chức vụ VẪN CÒN sau lượt từ chối")
    void deletingAPositionStillHeldByAnEmployeeIsRefused() {
        UUID chucVu = taoChucVu("XO-CV", "Tổ trưởng (kiểm thử)");
        taoHoSo(TIEN_TO + "XO-001", chucVu, "DANG_LAM", null);

        ResponseEntity<String> xoa = phienHttp.goi(quanTri, HttpMethod.DELETE, DUONG_CHUC_VU + "/" + chucVu, null);

        assertThat(xoa.getStatusCode())
                .as("một lượt xoá âm thầm làm hồng hồ sơ của từng ấy con người: %s", xoa.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(xoa.getBody()).contains("HR-2002");

        // ⚠ Kiểm bằng đường DANH SÁCH, ⛔ không bằng một `GET /{publicId}`: endpoint chi tiết chức vụ
        //   đã bị GỠ ở WS-51 vì nó có 0 nơi gọi (quy tắc 15 — xem javadoc `PositionController`).
        //   Dựng lại nó chỉ để phục vụ một bài kiểm là tạo ra đúng thứ vừa gỡ: một endpoint mà
        //   người dùng thật ⛔ không có đường nào bấm tới.
        ResponseEntity<String> conDo = phienHttp.get(quanTri, DUONG_CHUC_VU);
        assertThat(conDo.getStatusCode())
                .as("⛔⛔ Một lượt từ chối mà vẫn ghi đè một nửa thì TỆ HƠN ⛔ không từ chối: %s", conDo.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(conDo.getBody())
                .as("⛔ chức vụ phải CÒN trong danh mục sau lượt từ chối")
                .contains(chucVu.toString());

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM positions WHERE public_id = ? AND deleted_at IS NULL",
                        Integer.class,
                        chucVu))
                .as("và ⛔ không được đánh dấu xoá mềm ở CSDL")
                .isEqualTo(1);
    }

    // =========================================================================
    // 6 — Cặp trạng thái ↔ ngày nghỉ việc
    // =========================================================================

    /**
     * Hai chiều của {@code ck_employees_terminated_pairs}, đo <b>qua HTTP</b>.
     *
     * <p>⚠ Thứ bài này canh ⛔ không chỉ là "bị từ chối" — nó canh <b>tên trường</b> trong
     * {@code error.details[].field}. AntD {@code Form.setFields} so đúng chuỗi ấy, và lệch một chữ
     * thì người dùng bấm Lưu rồi ⛔ không thấy gì: ⛔ không ô nào đỏ, ⛔ không thông báo nào
     * ({@code LoiTheoTruongToiDungOTest}). Tên phải là tên trường của <b>DTO request</b>
     * ({@code terminatedAt}), ⛔ không phải tên cột ({@code terminated_at}).
     */
    @Test
    @DisplayName(
            "Cặp trạng thái ↔ ngày nghỉ: thiếu `terminatedAt` bị từ chối, thừa cũng bị — và thân lỗi gọi ĐÚNG tên ô")
    void terminationDateAndStatusMustAgreeBothWays() {
        UUID chucVu = taoChucVu("NG-CV", "Nhân viên nghỉ việc (kiểm thử)");

        ResponseEntity<String> thieuNgay = phienHttp.goi(
                quanTri, HttpMethod.POST, DUONG_HO_SO, thanHoSoDayDu(TIEN_TO + "NG-001", chucVu, "NGHI_VIEC", null));
        assertThat(thieuNgay.getStatusCode())
                .as(
                        "⚠ SYS-0003 là **400**, ⛔ KHÔNG 422 — §11.18 ghi lại đúng lượt vá đã đoán nhầm "
                                + "chỗ này (\"lỗi hợp lệ hoá thì phải 422\"): %s",
                        thieuNgay.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(thieuNgay.getBody())
                .as("⛔ Tên trường phải trùng TÊN TRƯỜNG DTO — `terminated_at` hay `ngayNghiViec` "
                        + "đều làm AntD bỏ qua trong im lặng")
                .contains("\"field\":\"terminatedAt\"")
                .contains("REQUIRED_WHEN_TERMINATED");

        ResponseEntity<String> thuaNgay = phienHttp.goi(
                quanTri,
                HttpMethod.POST,
                DUONG_HO_SO,
                thanHoSoDayDu(TIEN_TO + "NG-002", chucVu, "DANG_LAM", "2026-03-31"));
        assertThat(thuaNgay.getStatusCode())
                .as(
                        "⛔ Chiều ngược cũng phải chặn: 'đã nghỉ mà vẫn ĐANG LÀM' làm mọi phép đếm quân "
                                + "số sai mà ⛔ không màn hình nào báo: %s",
                        thuaNgay.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(thuaNgay.getBody()).contains("\"field\":\"terminatedAt\"").contains("ONLY_WHEN_TERMINATED");

        // ⚠ Đối chứng (luật 7): một luật từ chối TẤT CẢ cũng làm hai khẳng định trên xanh, và nó sẽ
        // khoá cứng đúng thao tác "ghi nhận một người nghỉ việc".
        ResponseEntity<String> hopLe = phienHttp.goi(
                quanTri,
                HttpMethod.POST,
                DUONG_HO_SO,
                thanHoSoDayDu(TIEN_TO + "NG-003", chucVu, "NGHI_VIEC", "2026-03-31"));
        assertThat(hopLe.getStatusCode())
                .as("cặp KHỚP phải đi qua: %s", hopLe.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    // =========================================================================
    // 7 — Sắp xếp
    // =========================================================================

    /**
     * Sắp xếp mặc định của màn hình danh sách phải nằm trong bảng trắng của backend.
     *
     * <p>Sự cố đã có thật, đo 01/09/2026: {@code ConstructionsPage.tsx} khai
     * {@code useState('updatedAt,desc')} — tham số ấy đi kèm <b>mọi</b> lượt gọi, kể cả lượt tải
     * đầu — trong khi {@code updatedAt} ⛔ không có trong bảng trắng của service. Màn hình trả lỗi
     * ngay lượt tải đầu tiên, và ⛔ không ai thấy suốt thời gian ấy vì <b>bảng vốn đang rỗng thật</b>
     * (§10.62: triệu chứng trùng khít trạng thái đúng). Danh sách CBNV hôm nay đang rỗng y hệt.
     *
     * <p>⭐⭐ <b>Bài này đọc mọi tuỳ chọn sort TỪ CHÍNH GIAO DIỆN</b> ({@code hrVocabulary.ts}), ⛔
     * không chép lại. Bản đầu chép chuỗi {@code "updatedAt,desc"} vào đây kèm một ghi chú
     * <i>"⛔ chưa có màn hình danh sách"</i> — câu ấy <b>hết đúng trong cùng ngày</b>
     * ({@code EmployeesPage.tsx} ra đời sau bài kiểm 19 phút). Một bài kiểm chép hằng số của phía
     * bên kia thì nó canh <b>chính nó</b>: đổi mặc định ở giao diện, bài vẫn xanh, và màn hình
     * trắng ở lượt tải đầu. Khuôn lấy từ
     * {@code ConstructionHttpTest.defaultSortFromTheAdminScreenIsAccepted}.
     *
     * <p>⚠ Và nó kiểm <b>cả bảy</b> tuỳ chọn chứ ⛔ không riêng mặc định: sáu cái kia nằm sau một cú
     * bấm, nên một cái hỏng sẽ ⛔ không lộ ra ở lượt tải đầu — nó lộ ra với người dùng, vào ngày họ
     * cần sắp xếp.
     */
    @Test
    @DisplayName("Cả 7 tuỳ chọn sort ĐỌC TỪ giao diện đều trả 200; một trường ngoài bảng trắng bị TỪ CHỐI")
    void theScreenDefaultSortIsAcceptedAndAnythingElseIsRefused() throws java.io.IOException {
        java.nio.file.Path tuVung = gocKho().resolve("frontend/admin-app/src/features/hr/hrVocabulary.ts");
        String nguon = java.nio.file.Files.readString(tuVung, java.nio.charset.StandardCharsets.UTF_8);

        java.util.List<String> tuyChon = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("value: '([A-Za-z]+,(?:asc|desc))'")
                .matcher(nguon);
        while (m.find()) {
            tuyChon.add(m.group(1));
        }

        // ⚠ VẾ CHỐNG TẬP RỖNG (luật 7): giao diện đổi cách khai thì danh sách rỗng, vòng lặp dưới
        //   ⛔ không gọi lượt nào, và bài vẫn xanh — tức nó thôi canh gì mà ⛔ không ai biết.
        assertThat(tuyChon)
                .as("⛔ ⛔ Không bóc được tuỳ chọn sort nào từ %s — màn hình đổi cách khai?", tuVung)
                .hasSizeGreaterThanOrEqualTo(5);

        // Mặc định của màn hình là phần tử ĐẦU (`SAP_XEP_MAC_DINH = SAP_XEP_CBNV[0].value`) — nó đi
        // trong MỌI lượt gọi, nên nó là tuỳ chọn duy nhất mà hỏng = màn hình trắng ở lượt tải đầu.
        for (String sort : tuyChon) {
            ResponseEntity<String> phanHoi = phienHttp.get(quanTri, DUONG_HO_SO + "?page=1&size=20&sort=" + sort);

            assertThat(phanHoi.getStatusCode())
                    .as(
                            "ô \"Sắp xếp\" của màn hình chào `%s`; backend từ chối là người dùng bấm rồi "
                                    + "nhận một bảng trắng, ⛔ không lời giải thích: %s",
                            sort, phanHoi.getBody())
                    .isEqualTo(HttpStatus.OK);
            assertThat(phanHoi.getBody()).doesNotContain("SORT_FIELD_NOT_ALLOWED");
        }

        // ⚠ Đối chứng: một bảng trắng nhận MỌI thứ cũng làm khẳng định trên xanh — và nó mở đường
        // cho ORDER BY trên cột chưa đánh chỉ mục, tức một request đủ làm chậm cả hệ thống.
        // `baseSalary` cố ý là một trường 🔒: nó ⛔ không có trên `Employee`, nên nếu bảng trắng
        // thủng thì câu JPA vỡ ở tầng dưới với một thông báo lộ cấu trúc bảng.
        ResponseEntity<String> ngoaiBangTrang = phienHttp.get(quanTri, DUONG_HO_SO + "?sort=baseSalary,desc");

        assertThat(ngoaiBangTrang.getStatusCode())
                .as(
                        "⚠ **400**, ⛔ không 422: `PageUtils` ném `ValidationException` trần ⇒ SYS-0003 "
                                + "⇒ BAD_REQUEST (ErrorCode.java:28). §11.18 đã trả giá cho phép đoán ngược lại: %s",
                        ngoaiBangTrang.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ngoaiBangTrang.getBody()).contains("SYS-0003").contains("SORT_FIELD_NOT_ALLOWED");
    }

    /** Đi ngược lên tới thư mục chứa {@code .claude} — chạy được cả từ module lẫn từ gốc repo. */
    private static java.nio.file.Path gocKho() {
        java.nio.file.Path p = java.nio.file.Paths.get("").toAbsolutePath();
        while (p != null && !java.nio.file.Files.isDirectory(p.resolve(".claude"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("⛔ Không tìm được gốc kho (thư mục chứa .claude)");
        }
        return p;
    }

    // =========================================================================
    // 8 — Mã NV ⛔ không đổi được
    // =========================================================================

    @Test
    @DisplayName(
            "Mã NV ⛔ KHÔNG đổi được: PUT gửi `code` khác vẫn giữ mã CŨ (CN-04.2 — không đổi suốt quá trình công tác)")
    void theEmployeeCodeSurvivesAnUpdateThatTriesToChangeIt() {
        UUID chucVu = taoChucVu("MA-CV", "Kỹ thuật viên (kiểm thử)");
        String maCu = TIEN_TO + "MA-001";
        String maMoi = TIEN_TO + "MA-999";
        UUID hoSo = taoHoSo(maCu, chucVu, "DANG_LAM", null);

        ResponseEntity<String> sua = phienHttp.goi(
                quanTri, HttpMethod.PUT, DUONG_HO_SO + "/" + hoSo, thanHoSoDayDu(maMoi, chucVu, "DANG_LAM", null));
        assertThat(sua.getStatusCode())
                .as(
                        "⛔ Lượt sửa ⛔ không bị TỪ CHỐI — nó chỉ BỎ QUA `code`. Ném lỗi ở đây làm mọi "
                                + "lượt lưu bình thường của biểu mẫu đỏ, vì biểu mẫu vẫn gửi mã lên để hiển thị: %s",
                        sua.getBody())
                .isEqualTo(HttpStatus.OK);

        String chiTiet = duLieu(phienHttp.get(quanTri, DUONG_HO_SO + "/" + hoSo));
        assertThat(boc(chiTiet, "code"))
                .as("⛔⛔ Mã NV là thứ mọi quyết định nhân sự TRÊN GIẤY tham chiếu tới — đổi được nó "
                        + "là làm mồ côi toàn bộ hồ sơ giấy của một con người")
                .isEqualTo("\"" + maCu + "\"");
        assertThat(chiTiet).doesNotContain(maMoi);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees WHERE code = ?", Integer.class, maMoi))
                .as("và ⛔ không được có một hàng nào mang mã mới")
                .isZero();
    }

    // =========================================================================
    // Dựng dữ liệu — mọi hàng đi qua HTTP, ⛔ không một câu INSERT nghiệp vụ nào
    // =========================================================================

    private UUID taoChucVu(String hau, String ten) {
        ResponseEntity<String> tao =
                phienHttp.goi(quanTri, HttpMethod.POST, DUONG_CHUC_VU, thanChucVu(TIEN_TO + hau, ten));
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định bên dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private UUID taoHoSo(String ma, UUID chucVu, String trangThai, String ngayNghi) {
        ResponseEntity<String> tao =
                phienHttp.goi(quanTri, HttpMethod.POST, DUONG_HO_SO, thanHoSoDayDu(ma, chucVu, trangThai, ngayNghi));
        assertThat(tao.getStatusCode())
                .as("⛔ dựng dữ liệu nền hỏng thì mọi khẳng định bên dưới xanh trên tập rỗng: %s", tao.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(chuoi(tao.getBody(), "publicId"));
    }

    private static String thanChucVu(String ma, String ten) {
        return """
                {"code":"%s","name":"%s","positionGroup":"Kiểm thử WS-51",\
                "description":"Chức vụ do bài kiểm tạo — xoá ở @AfterAll","sortOrder":10,"active":true}"""
                .formatted(ma, ten);
    }

    /**
     * Hồ sơ điền <b>đủ mọi ô tuỳ chọn</b> — đó chính là điều kiện tiên quyết của §11.19.
     *
     * <p>⚠ Ngày sinh nằm trong {@code [1930-01-01 .. 2015-12-31]} ({@code ck_employees_dob_range},
     * đặc tả 18 ≤ tuổi ≤ 70) và {@code contractExpiresAt >= contractSignedAt}
     * ({@code ck_employees_contract_dates}). Sai một trong hai thì lượt tạo đỏ ở tầng CSDL với một
     * thông báo chẳng liên quan gì tới thứ bài kiểm đang canh.
     *
     * <p>⛔ Đây ⛔ KHÔNG phải seed: hàng này sống đúng bằng thời gian lớp kiểm chạy và bị xoá cứng ở
     * {@code @AfterAll}. G6-a vẫn là ô trống, và bảng vẫn rỗng sau lượt chạy.
     */
    private String thanHoSoDayDu(String ma, UUID chucVu, String trangThai, String ngayNghi) {
        return """
                {"code":"%s","fullName":"Nguyễn Văn Hoà","dateOfBirth":"1988-04-17","gender":"NAM",\
                "educationLevel":"DAI_HOC","ethnicity":"Kinh","hometown":"Xã Đại Áng, huyện Thanh Trì, Hà Nội",\
                "address":"Số 7 ngõ 12 phố Hồng Hà, phường Phúc Xá, Hà Nội","phone":"0912.345.678",\
                "workEmail":"hoa.nv@songnhue.test","personalEmail":"nvhoa@vi-du.test",\
                "maritalStatus":"DA_KET_HON","emergencyContactName":"Trần Thị Bích",\
                "emergencyContactPhone":"0987.654.321","orgUnitId":"%s","positionId":"%s",\
                "jobTitle":"Phụ trách trạm bơm","hiredAt":"2015-06-01","contractType":"XAC_DINH_THOI_HAN",\
                "contractSignedAt":"2025-03-01","contractExpiresAt":"2027-02-28","status":"%s",\
                "terminatedAt":%s,"terminationReason":%s}"""
                .formatted(
                        ma,
                        donViGoc,
                        chucVu,
                        trangThai,
                        ngayNghi == null ? "null" : "\"" + ngayNghi + "\"",
                        ngayNghi == null ? "null" : "\"Nghỉ theo nguyện vọng cá nhân\"");
    }

    // =========================================================================
    // Dọn — ⛔ KHÔNG có câu nào ở cuối một phương thức kiểm (T48.8)
    // =========================================================================

    private void donSachVaiTroTam() {
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_TAM);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_TAM);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO_TAM);
    }

    /**
     * Xoá CỨNG mọi hàng mang tiền tố của lớp này.
     *
     * <p>⚠ Xoá {@code employee_sensitive} TRƯỚC {@code employees}: khoá ngoại. Lớp này ⛔ không tạo
     * hàng 🔒 nào, nhưng câu lệnh vẫn phải có — một lượt chạy trước bị ngắt giữa chừng có thể để
     * lại, và lúc ấy lệnh xoá hồ sơ đỏ vì ràng buộc chứ ⛔ không vì lỗi nào của bài kiểm.
     *
     * <p>⚠ Cố ý ⛔ KHÔNG dọn {@code audit_logs}: vai trò runtime {@code songnhue_app} ⛔ không có
     * DELETE trên bảng đó (WS-2/T2.7, và §10.80 đã trả giá vì một bản dump nhập ngược lại quyền ấy).
     */
    private void donDuLieuThu() {
        jdbc.update(
                "DELETE FROM employee_sensitive WHERE employee_id IN (SELECT id FROM employees WHERE code LIKE ?)",
                TIEN_TO + "%");
        jdbc.update("DELETE FROM employees WHERE code LIKE ?", TIEN_TO + "%");
        jdbc.update("DELETE FROM positions WHERE code LIKE ?", TIEN_TO + "%");
    }

    private int demHoSoThu() {
        Integer so =
                jdbc.queryForObject("SELECT count(*) FROM employees WHERE code LIKE ?", Integer.class, TIEN_TO + "%");
        return so == null ? 0 : so;
    }

    // =========================================================================
    // Bóc JSON — đủ dùng, khỏi kéo thêm thư viện vào bài kiểm
    // =========================================================================

    /** Phần {@code data} của envelope, nguyên văn — đây chính là thân sẽ được gửi lại bằng PUT. */
    private static String duLieu(ResponseEntity<String> phanHoi) {
        assertThat(phanHoi.getStatusCode())
                .as("⛔ bóc `data` của một phản hồi lỗi là đo trên tập rỗng: %s", phanHoi.getBody())
                .isEqualTo(HttpStatus.OK);
        String data = boc(phanHoi.getBody(), "data");
        assertThat(data).as("⛔ không thấy `data` trong: %s", phanHoi.getBody()).isNotNull();
        return data;
    }

    /**
     * Giá trị JSON <b>nguyên văn</b> của một trường, hoặc {@code null} khi trường vắng mặt.
     *
     * <p>⚠⚠ Trả về token thô (chuỗi còn nguyên nháy kép, số, {@code true}/{@code false}) chứ ⛔
     * không bóc sẵn: {@code default-property-inclusion: non_null} <b>bỏ hẳn</b> trường {@code null}
     * khỏi JSON, nên <i>"vắng mặt"</i> và <i>"rỗng"</i> phải phân biệt được — mà một hàm luôn trả
     * chuỗi thì ⛔ không phân biệt nổi hai trạng thái ấy (luật 9). Đó đúng là hai trạng thái §11.19
     * nói tới.
     */
    private static String boc(String json, String truong) {
        String khoa = "\"" + truong + "\":";
        int viTri = json.indexOf(khoa);
        if (viTri < 0) {
            return null;
        }
        int dau = viTri + khoa.length();
        while (dau < json.length() && Character.isWhitespace(json.charAt(dau))) {
            dau++;
        }
        char mo = json.charAt(dau);
        if (mo == '"') {
            return json.substring(dau, cuoiChuoi(json, dau) + 1);
        }
        if (mo == '{' || mo == '[') {
            return json.substring(dau, cuoiKhoi(json, dau, mo) + 1);
        }
        int cuoi = dau;
        while (cuoi < json.length() && ",}]".indexOf(json.charAt(cuoi)) < 0) {
            cuoi++;
        }
        String tho = json.substring(dau, cuoi).trim();
        return "null".equals(tho) ? null : tho;
    }

    /** Chuỗi đã bóc nháy kép — dùng cho những chỗ cần chính giá trị, VD một UUID. */
    private static String chuoi(String json, String truong) {
        String tho = boc(json, truong);
        assertThat(tho)
                .as("⛔ không tìm thấy trường `%s` trong: %s", truong, json)
                .isNotNull()
                .startsWith("\"");
        return tho.substring(1, tho.length() - 1);
    }

    /** Chỉ số của nháy kép ĐÓNG, bỏ qua nháy đã thoát. */
    private static int cuoiChuoi(String json, int mo) {
        int i = mo + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '"') {
                return i;
            }
            i++;
        }
        throw new AssertionError("JSON hỏng — chuỗi ⛔ không đóng: " + json);
    }

    /**
     * Chỉ số của ngoặc ĐÓNG khớp cặp.
     *
     * <p>⚠ Phải bỏ qua ngoặc nằm TRONG chuỗi: một địa chỉ chứa {@code }} sẽ cắt khối sớm, và hàm
     * trả về một mảnh JSON <b>trông hợp lệ</b> — một sai số im lặng ngay giữa công cụ đo.
     */
    private static int cuoiKhoi(String json, int mo, char moNgoac) {
        char dongNgoac = moNgoac == '{' ? '}' : ']';
        int sau = 0;
        int i = mo;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                i = cuoiChuoi(json, i);
            } else if (c == moNgoac) {
                sau++;
            } else if (c == dongNgoac) {
                sau--;
                if (sau == 0) {
                    return i;
                }
            }
            i++;
        }
        throw new AssertionError("JSON hỏng — khối ⛔ không đóng: " + json);
    }
}
