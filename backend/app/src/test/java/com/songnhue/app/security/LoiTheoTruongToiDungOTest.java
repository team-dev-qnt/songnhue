package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

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
 * <b>Chi tiết lỗi 422 phải trỏ vào MỘT TRƯỜNG CÓ THẬT TRÊN BIỂU MẪU.</b>
 *
 * <h2>Sự cố bài kiểm này ra đời để chặn (01/09/2026)</h2>
 *
 * QuanTran báo: <i>"lúc tạo tài khoản, error 422 trong network F12 không hiển thị lên màn
 * hình"</i>. Chuỗi nhân quả có bốn mắt, và <b>mắt nào cũng đúng theo cách hiểu của riêng nó</b>:
 *
 * <ol>
 *   <li>{@code UserAdminService.create} gọi {@code passwordPolicy.validate(temporaryPassword, …)};
 *   <li>{@code PasswordPolicyService} ghi cứng {@code error.withDetail("newPassword", …)} — tên
 *       trường của <b>màn hình đổi mật khẩu</b>, không phải của màn hình thêm tài khoản;
 *   <li>{@code CreateUserRequest} khai trường là {@code temporaryPassword}, nên
 *       {@code Form.setFields([{name: 'newPassword'}])} của AntD <b>bỏ qua trong im lặng</b> —
 *       đặt lỗi lên một trường không tồn tại không phải lỗi với AntD;
 *   <li>nhánh xử lý ở FE {@code return} ngay sau {@code setFields}, nên <b>toast cũng không
 *       chạy</b>.
 * </ol>
 *
 * Kết quả: HTTP 422 mang lý do đầy đủ trong thân phản hồi, và màn hình <b>trắng trơn</b>. Người
 * dùng bấm "Tạo", không thấy gì xảy ra, bấm tiếp.
 *
 * <h2>⚠⚠ Vì sao 798 bài kiểm không thấy — và cảnh báo đã có sẵn</h2>
 *
 * Javadoc của {@code ApiClientError.fieldErrors} viết từ WS-5 nói thẳng: <i>"backend trả tên
 * trường theo DTO của nó, và nếu hai bên lệch tên thì AntD lặng lẽ bỏ qua dòng đó… Lệch tên trường
 * lộ ra ngay lần thử đầu tiên."</i>
 *
 * <p>Nó <b>không</b> lộ ra. Vế cuối của câu ấy là một dự đoán, không phải một cơ chế — và nó sai
 * vì cả hai lớp cùng im lặng. {@code PasswordPolicyServiceTest} thì đúng và đầy đủ, nhưng nó
 * khẳng định về {@code e.details()} của <i>một lượt gọi service</i>; nó không có cách nào biết
 * DTO của nơi gọi khai trường tên gì. Đây là quy tắc 5 và quy tắc 14 chồng lên nhau: <b>một cam
 * kết trải qua hai tệp thì phải kiểm ở chỗ hai tệp gặp nhau</b> — và chỗ ấy là HTTP.
 *
 * <h2>Bài này canh gì, và KHÔNG canh gì (quy tắc 28)</h2>
 *
 * Canh: mọi {@code error.details[].field} của các endpoint dưới đây phải là <b>một trường có thật
 * trong DTO của chính endpoint ấy</b>. KHÔNG canh: rằng giao diện làm gì với chi tiết đó — phần
 * ấy thuộc {@code loiTheoTruong.test.ts} ở {@code admin-app}, nơi có bài chứng minh
 * <b>không khớp tên nào thì phải rơi về toast</b> thay vì im lặng.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoiTheoTruongToiDungOTest extends IntegrationTestBase {

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    private PhienHttp phien;
    private PhienHttp.Phien quanTri;

    /**
     * Vai trò kiểm thử tối thiểu — <b>ba quyền</b>, không hơn.
     *
     * <h2>⚠ Vì sao không dùng thẳng {@code ADMIN} hay {@code SUPER_ADMIN}</h2>
     *
     * {@code AuthenticatedUser.TWO_FACTOR_REQUIRED_ROLES} = {@code {SUPER_ADMIN, ADMIN, ADMIN_HR}},
     * và {@code adm:user:create} chỉ nằm ở hai vai trò đầu. Đăng nhập bằng chúng dừng ở
     * {@code TWO_FACTOR_ENROLL_REQUIRED} — đúng và cố ý — nên {@code PhienHttp.dangNhap} không lấy
     * được token. Lượt viết đầu của bài này đã đỏ đúng ở đó, và <b>đó là một phép đo có ích</b>:
     * nó chứng minh cổng 2FA thật sự chặn, không phải một cờ khai rồi bỏ đó.
     *
     * <p>Dựng một vai trò riêng ba quyền vừa tránh được cổng ấy, vừa nói đúng phạm vi bài kiểm:
     * bài này <b>không</b> kiểm phân quyền (đã có {@code RbacMatrixTest}), nó kiểm tên trường
     * trong chi tiết lỗi.
     */
    private static final String VAI_TRO_KIEM_THU = "KIEMTRA_LOI_TRUONG";

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        // ⚠ Xoá-rồi-chèn, KHÔNG `ON CONFLICT (code)`: `roles.code` không có ràng buộc unique
        //   thường (chỉ số duy nhất của bảng này là chỉ số bộ phận), nên `ON CONFLICT` ném
        //   BadSqlGrammar. Cùng khuôn với `PhienHttp.taoNguoiDung`.
        donVaiTroKiemThu();
        jdbc.update(
                "INSERT INTO roles (code, name, description, is_system) VALUES (?, ?, ?, FALSE)",
                VAI_TRO_KIEM_THU,
                "Vai trò kiểm thử lỗi theo trường",
                "Chỉ dùng trong test tích hợp — xoá ở @AfterAll");
        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id)"
                        + " SELECT r.id, p.id FROM roles r JOIN permissions p"
                        + "   ON p.code IN ('adm:user:create', 'adm:setting:view', 'adm:setting:update',"
                        + "                 'hr:employee:create')"
                        + " WHERE r.code = ?",
                VAI_TRO_KIEM_THU);

        phien = new PhienHttp(http);
        quanTri = phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "loi_truong", VAI_TRO_KIEM_THU));
    }

    @AfterAll
    void donVaiTroKiemThu() {
        // ⛔ Không để lại rác trong bảng phân quyền: `RbacMatrixTest` khẳng định về CHÍNH hai bảng
        //    này, và một vai trò lạ còn sót là một lượt đỏ ở bài kiểm khác, cách đây rất xa.
        jdbc.update(
                "DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = ?)",
                VAI_TRO_KIEM_THU);
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = ?)", VAI_TRO_KIEM_THU);
        jdbc.update("DELETE FROM roles WHERE code = ?", VAI_TRO_KIEM_THU);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    //  0 · Phạm vi — HRM (T63.7, trả T51.10(c))
    //
    //  ⛔⛔ Bài này chạy theo một DANH SÁCH ENDPOINT, nên module mới sinh ra là mặc định nằm
    //     NGOÀI tầm quét: đúng luật 28, *"cái xanh của nó đọc như bảo đảm cho một phạm vi nó ⛔
    //     soi"*. HRM có ~24 trường hồ sơ và **12 nơi** gọi `withDetail(...)` — đo được bằng
    //     `grep -c "withDetail(" backend/hr/src/main/java` — mà ⛔ nơi nào từng bị hỏi *tên trường
    //     ấy có thật trên biểu mẫu ⛔*.
    //
    //  ⭐ Và vế khẳng định ở đây MẠNH HƠN hai mục dưới: thay vì chép tay tên trường kỳ vọng, nó
    //     ĐỌC danh sách trường từ chính `HrDtos.EmployeeRequest` rồi đòi `field` nằm trong đó.
    //     Backend đổi tên trường mà quên sửa `withDetail` ⇒ đỏ, ⛔ cần ai nhớ cập nhật bài kiểm.
    // ═════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("⭐⭐ HRM: 422 của hồ sơ CBNV trỏ vào một trường CÓ THẬT trong EmployeeRequest")
    void loi422CuaHoSoCanBoTroVaoTruongCoThat() {
        // Ca hỏng chọn có chủ đích: `status = NGHI_VIEC` mà thiếu `terminatedAt` — một luật NGHIỆP
        // VỤ (`EmployeeService:279`), ⛔ phải một ràng buộc `@NotBlank` do framework sinh. Chính
        // nhóm luật nghiệp vụ mới là nhóm tự gõ tên trường bằng tay, tức nhóm lệch được.
        String than =
                """
                {"code":"T637-01","fullName":"Lê Văn Kiểm Thử","dateOfBirth":"1990-01-01",\
                "gender":"NAM","orgUnitId":"%s","hiredAt":"2020-01-02",\
                "contractType":"KHONG_XAC_DINH_THOI_HAN","status":"NGHI_VIEC"}"""
                        .formatted(maCongTy());

        ResponseEntity<String> tl = phien.goi(quanTri, HttpMethod.POST, "/api/v1/hr/employees", than);

        // ⚠ `SYS-0003` ánh xạ **400**, ⛔ phải 422 — §11.18 đã trả giá cho đúng câu *"lỗi hợp lệ hoá
        //   thì phải 422"*, và lượt viết đầu của bài này mắc lại y hệt. Mã HTTP ở đây ⛔ phải thứ
        //   đang được canh; thứ được canh là **tên trường**, nên chỉ cần chốt nó ⛔ phải 2xx.
        assertThat(tl.getStatusCode().is4xxClientError())
                .as("thân trả về: %s", tl.getBody())
                .isTrue();
        assertThat(tl.getBody()).contains("SYS-0003");

        java.util.List<String> truongTrongLoi = truongTrongChiTietLoi(tl.getBody());
        assertThat(truongTrongLoi)
                .as("⚠ phản hồi ⛔ mang chi tiết `field` nào ⇒ bài này xanh trên tập rỗng (luật 7): %s", tl.getBody())
                .isNotEmpty();

        java.util.List<String> truongCuaDto = truongCuaRecord("EmployeeRequest");
        assertThat(truongCuaDto)
                .as("⚠ ⛔ đọc được trường nào từ HrDtos.EmployeeRequest ⇒ phép so dưới vô nghĩa")
                .hasSizeGreaterThanOrEqualTo(15);

        assertThat(truongTrongLoi)
                .as(
                        """
                        Chi tiết lỗi trỏ vào tên trường ⛔ có trong `HrDtos.EmployeeRequest`.

                          field trong phản hồi : %s
                          trường của DTO       : %s

                        AntD `Form.setFields` bỏ qua **trong im lặng** một tên trường ⛔ tồn tại, nên \
                        triệu chứng là: người dùng bấm Lưu, ⛔ thấy gì xảy ra, bấm tiếp. Đúng sự cố \
                        01/09 mà lớp này sinh ra để chặn — lần này ở HRM.""",
                        truongTrongLoi, truongCuaDto)
                .isSubsetOf(truongCuaDto);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    //  1 · Chính xác lượt gọi QuanTran đã làm
    // ═════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Tạo tài khoản với mật khẩu yếu: 422 trỏ vào 'temporaryPassword', KHÔNG phải 'newPassword'")
    void loi422CuaTaoTaiKhoanTroVaoTruongCuaChinhBieuMauDo() {
        String than =
                """
                {
                  "username": "kiemtra_matkhauyeu",
                  "fullName": "Nguyễn Văn Kiểm Thử",
                  "email": "kiemtra_matkhauyeu@songnhue.test",
                  "orgUnitPublicId": "%s",
                  "temporaryPassword": "abc"
                }
                """
                        .formatted(maCongTy());

        ResponseEntity<String> tl = phien.goi(quanTri, HttpMethod.POST, "/api/v1/admin/users", than);

        assertThat(tl.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(tl.getBody()).contains("AUTH-0006");

        // ⭐ Khẳng định TRUNG TÂM của bài này.
        assertThat(tl.getBody())
                .as("chi tiết lỗi phải trỏ vào trường của CreateUserRequest, nếu không AntD bỏ qua trong im lặng")
                .contains("\"field\":\"temporaryPassword\"");

        // Và phải KHÔNG còn tên trường của màn hình khác lẫn vào — đây là thứ đã gây ra sự cố.
        assertThat(tl.getBody())
                .as("'newPassword' là trường của màn hình ĐỔI mật khẩu; nó không tồn tại trên biểu mẫu này")
                .doesNotContain("\"field\":\"newPassword\"");
    }

    @Test
    @DisplayName("Đổi mật khẩu vẫn trỏ vào 'newPassword' — bản vá không được đổi bên đang đúng")
    void loi422CuaDoiMatKhauVanTroVaoNewPassword() {
        // ⚠ Nửa còn lại của quy tắc 9: một bản vá đổi hằng số thành tham số có thể *đúng ở chỗ
        //   mới* mà *sai ở chỗ cũ*. Không có bài này thì "sửa xong" và "hỏng chỗ khác" trông
        //   giống hệt nhau.
        String than = """
                {"currentPassword": "%s", "newPassword": "abc"}
                """
                .formatted(PhienHttp.MAT_KHAU);

        ResponseEntity<String> tl = phien.goi(quanTri, HttpMethod.POST, "/api/v1/auth/change-password", than);

        assertThat(tl.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(tl.getBody()).contains("AUTH-0006").contains("\"field\":\"newPassword\"");
    }

    @Test
    @DisplayName("Chi tiết lỗi mang RULE đọc được, không chỉ một câu chung chung")
    void chiTietLoiMangRuleDeGiaoDienNoiRaYeuCauCuThE() {
        // Câu của AUTH-0006 là "Mật khẩu chưa đạt yêu cầu an toàn" — đúng, và vô dụng với người
        // đang phải sửa mật khẩu. Thứ dùng được nằm ở `rule`, và giao diện dịch nó ra tiếng Việt
        // (`moTaLuatMatKhau` ở admin-app). Không có `rule` thì màn hình chỉ nói được câu chung.
        String than =
                """
                {
                  "username": "kiemtra_ruledetail",
                  "fullName": "Nguyễn Văn Kiểm Thử",
                  "email": "kiemtra_ruledetail@songnhue.test",
                  "orgUnitPublicId": "%s",
                  "temporaryPassword": "abc"
                }
                """
                        .formatted(maCongTy());

        ResponseEntity<String> tl = phien.goi(quanTri, HttpMethod.POST, "/api/v1/admin/users", than);

        assertThat(tl.getBody()).contains("MIN_LENGTH_").contains("REQUIRE_LETTER_AND_DIGIT");
        // ⛔ Và tuyệt đối không kèm mật khẩu bị từ chối — `rejectedValue` phải là null.
        assertThat(tl.getBody()).doesNotContain("\"abc\"");
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    //  2 · Chính sách mật khẩu đọc được mà không cần quyền quản trị
    // ═════════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /auth/password-policy: người dùng KHÔNG có quyền quản trị nào vẫn đọc được")
    void chinhSachMatKhauDocDuocBoiMoiNguoiDungDaDangNhap() {
        // ⭐ Đây là điều kiện tồn tại của endpoint. Màn hình cần chính sách nhất là màn hình BẮT
        //    BUỘC đổi mật khẩu lần đầu, nơi người dùng thường chưa có vai trò nào. Nếu chỉ đọc
        //    được qua `GET /settings` (đòi `adm:setting:view`) thì đúng nhóm người cần nhất sẽ
        //    nhận 403, và giao diện lặng lẽ quay về câu chung chung cũ — một tính năng "đã dựng"
        //    mà không chạy ở chỗ nó sinh ra để chạy.
        PhienHttp.Phien khongQuyen = phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "khong_quyen_ml"));

        ResponseEntity<String> tl = phien.get(khongQuyen, "/api/v1/auth/password-policy");

        assertThat(tl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tl.getBody()).contains("\"minLength\"").contains("\"requireLetterAndDigit\"");
    }

    @Test
    @DisplayName("Chính sách trả về ĐÚNG giá trị trong settings, không phải hằng số trong mã")
    void chinhSachDocTuSettingsChuKhongPhaiHangSoTrongMa() {
        // ⭐ Quy tắc 3 — canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH. Hằng số trong mã là
        //    10; nếu bài này chỉ khẳng định "minLength > 0" thì một endpoint trả cứng 10 cũng
        //    xanh, và giao diện sẽ nói dối ngay lần đầu Admin sửa tham số (§10.69).
        //
        // ⚠⚠ Đổi tham số phải đi qua `PUT /settings/{key}`, KHÔNG phải `UPDATE` thẳng vào bảng.
        //    Lượt viết đầu của bài này làm SQL thô và đỏ với `minLength: 10` — vì `SettingService`
        //    có cache in-process (không Redis ở v1) và chỉ lượt ghi qua service mới xoá đệm. Bản
        //    thân lượt đỏ ấy là một phép đo có ích: nó chứng minh endpoint đọc qua đúng lớp cache
        //    mà phần còn lại của hệ dùng, chứ không tự truy vấn thẳng CSDL.
        String khoa = "security.password.min-length";
        String truoc =
                jdbc.queryForObject("SELECT setting_value FROM settings WHERE setting_key = ?", String.class, khoa);
        assertThat(truoc)
                .as("khoá chính sách phải có trong settings, nếu không bài này không đo gì")
                .isNotNull();

        // ⚠ T61.42 (15/09/2026): nhóm SECURITY nay đòi mã 2FA nhập lại ⇒ lượt ghi đi bằng một tài khoản ĐÃ đăng ký
        //   2FA thật (cùng vai trò), ⛔ lách bằng SQL — lý do vẫn là đệm của SettingService (đoạn trên).
        String tenHaiBuoc = PhienHttp.taoNguoiDung(users, passwords, jdbc, "loi_truong_2fa", VAI_TRO_KIEM_THU);
        jdbc.update("UPDATE users SET two_factor_required = TRUE WHERE username = ?", tenHaiBuoc);
        PhienHttp.PhienHaiBuoc haiBuoc = phien.dangNhapHaiBuoc(tenHaiBuoc);

        try {
            ResponseEntity<String> ghi = phien.goi(
                    haiBuoc.phien(),
                    HttpMethod.PUT,
                    "/api/v1/settings/" + khoa,
                    "{\"value\": \"14\", \"maXacThuc\": \"%s\"}".formatted(PhienHttp.maTotp(haiBuoc.secret(), 1)));
            assertThat(ghi.getStatusCode())
                    .as("lượt ghi tham số phải thành công: %s", ghi.getBody())
                    .isEqualTo(HttpStatus.OK);

            ResponseEntity<String> tl = phien.get(quanTri, "/api/v1/auth/password-policy");
            assertThat(tl.getBody()).contains("\"minLength\":14");
        } finally {
            // Mã bước +1 đã dùng ⇒ đặt lại mốc chống dùng lại để lượt khôi phục ⛔ hỏng vì chính cơ chế ấy.
            jdbc.update(
                    "UPDATE user_totp SET last_used_step = NULL "
                            + "WHERE user_id = (SELECT id FROM users WHERE username = ?)",
                    tenHaiBuoc);
            ResponseEntity<String> khoiPhuc = phien.goi(
                    haiBuoc.phien(),
                    HttpMethod.PUT,
                    "/api/v1/settings/" + khoa,
                    "{\"value\": \"%s\", \"maXacThuc\": \"%s\"}"
                            .formatted(truoc, PhienHttp.maTotp(haiBuoc.secret(), 1)));
            assertThat(khoiPhuc.getStatusCode())
                    .as("khôi phục: %s", khoiPhuc.getBody())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    /** `orgUnitPublicId` của Công ty — dùng đơn vị gốc do migration seed, không tự đẻ nhánh mới. */
    private String maCongTy() {
        return jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", String.class);
    }

    /** Mọi {@code error.details[].field} trong một thân phản hồi JSON. */
    private static java.util.List<String> truongTrongChiTietLoi(String than) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"field\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(than == null ? "" : than);
        java.util.List<String> ket = new java.util.ArrayList<>();
        while (m.find()) {
            ket.add(m.group(1));
        }
        return ket;
    }

    /**
     * Tên các thành phần của một {@code record} trong {@code HrDtos.java} — ĐỌC từ nguồn.
     *
     * <p>⛔ Chép tay danh sách trường vào đây là dựng một bản sao thứ hai của cùng một sự thật, và
     * bản sao ấy hết đúng vào ngày ⛔ ai nhớ tới nó (luật 14). Khuôn lấy từ
     * {@code soanBaiVongKhuHoi.test.tsx}, nơi cùng phép đọc đã bắt được hai trường bị đánh rơi.
     */
    private static java.util.List<String> truongCuaRecord(String ten) {
        java.nio.file.Path p = timTuGocKho("backend/hr/src/main/java/com/songnhue/hr/api/HrDtos.java");
        String ma;
        try {
            ma = java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "record\\s+" + ten + "\\s*\\(([\\s\\S]*?)\\)\\s*\\{")
                .matcher(ma);
        if (!m.find()) {
            throw new IllegalStateException("⛔ Không tìm thấy record " + ten + " trong HrDtos.java");
        }
        return java.util.Arrays.stream(m.group(1)
                        .replaceAll("/\\*[\\s\\S]*?\\*/", " ")
                        .replaceAll("(?m)//.*$", " ")
                        .replaceAll("@[\\w.]+(\\([^)]*\\))?", " ")
                        .replaceAll("<[^<>]*>", " ")
                        .split(","))
                .map(String::trim)
                .map(x -> {
                    String[] phan = x.split("\\s+");
                    return phan.length == 0 ? "" : phan[phan.length - 1];
                })
                .filter(x -> x.matches("^[a-z]\\w*$"))
                .toList();
    }

    private static java.nio.file.Path timTuGocKho(String duongDanTuongDoi) {
        java.nio.file.Path hienTai =
                java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            java.nio.file.Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (java.nio.file.Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("⛔ Không tìm thấy " + duongDanTuongDoi);
    }
}
