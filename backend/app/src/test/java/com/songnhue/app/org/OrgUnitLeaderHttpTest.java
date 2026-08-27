package com.songnhue.app.org;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Đường GHI của danh bạ lãnh đạo và ba ô liên hệ của đơn vị — đi qua HTTP.</b> WS-25.
 *
 * <h2>⚠⚠ Vì sao lớp này tồn tại: ba thứ trên cổng ĐỌC ĐƯỢC MÀ KHÔNG GHI ĐƯỢC</h2>
 *
 * Đo ngày 28/08/2026, trước lượt vá:
 *
 * <ul>
 *   <li>{@code org_unit_leaders} — bảng dựng 27/8 kèm repository và endpoint công khai, <b>không
 *       controller nào, không màn hình nào</b> ghi vào nó. Trang {@code /gioi-thieu/lanh-dao}
 *       (CR-25) đọc một bảng không ai điền được;
 *   <li>{@code org_units.address/phone/email} — ba cột có từ {@code V202608131004},
 *       {@code /public/org-units/subsidiaries} hiển thị chúng ở bảng 6 cột (CR-26), mà ba setter
 *       <b>không có lời gọi nào</b> ngoài chính lớp entity;
 *   <li>{@code CreateRequest.shortName} — biểu mẫu có ô "Tên viết tắt", {@code @Size} vẫn validate
 *       nó, nhưng {@code create()} không nhận tham số ấy nên giá trị bị vứt lặng lẽ và màn hình
 *       báo <i>tạo thành công</i>.
 * </ul>
 *
 * <p>Cả ba là quy tắc 15 ở chiều ghi, và cả ba <b>chặn nghiệm thu CR-25/CR-26</b>: hai trang đã
 * dựng, đã có bài kiểm, đã lên staging, và sẽ rỗng vĩnh viễn.
 *
 * <h2>Vì sao qua HTTP chứ không gọi thẳng service</h2>
 *
 * Luật 5. Thứ hỏng ở đây <b>không</b> phải logic nghiệp vụ — nó là <i>một trường không có mặt trong
 * DTO</i>. Gọi service với đủ bảy tham số thì bài kiểm luôn xanh, kể cả khi {@code CreateRequest} bỏ
 * quên ba trong số đó; chỉ lượt gọi đi qua Jackson mới chứng minh được trường ấy thật sự đi hết
 * đường từ trình duyệt xuống CSDL rồi quay lại.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgUnitLeaderHttpTest extends IntegrationTestBase {

    private static final String MA_XN = "T25-XN";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien duQuyen;
    private PhienHttp.Phien khongQuyen;
    private UUID goc;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        goc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = 'CTY'", UUID.class);

        // ⚠⚠ Phải dùng vai trò kiểm thử tạm, không dùng ADMIN — và điều đó nói lên một sự thật của
        //    hệ thống, không phải một chỗ lách của bài kiểm.
        //
        //    Đo được: `adm:org-unit:manage` chỉ nằm ở SUPER_ADMIN và ADMIN, mà cả hai đều thuộc
        //    `AuthenticatedUser.TWO_FACTOR_REQUIRED_ROLES` — lượt đăng nhập của chúng dừng ở
        //    `TWO_FACTOR_REQUIRED`, không ra thẳng `AUTHENTICATED`. Nói cách khác **không vai trò
        //    nào quản lý được cơ cấu tổ chức mà không qua 2FA**, và đó là thiết kế đúng: `org_units`
        //    chính là biên giới phân quyền tầng 3.
        //
        //    Nên bài kiểm cấp đúng MỘT quyền cho một vai trò tạm. Nó khẳng định **cổng quyền**
        //    (tầng 2 chặn đúng mã quyền), không khẳng định ma trận vai trò — vế ấy là việc của
        //    `RbacMatrixTest`. Cùng cách `ArticleLifecycleTest` đã làm cho `cms:article:approve`.
        duQuyen = phienHttp.dangNhap(taoNguoiDungCoQuyen("t25_org", "adm:org-unit:manage"));
        khongQuyen = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t25_zero"));
    }

    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM org_unit_leaders");
        jdbc.update("DELETE FROM org_units WHERE code = ?", MA_XN);
    }

    // ─────────────── Ba ô liên hệ của đơn vị ───────────────

    @Test
    @DisplayName("⭐ Tạo đơn vị kèm tên tắt + 3 ô liên hệ → cả BỐN đi tới CSDL và quay lại đủ")
    void taoDonViGiuDuTruongLienHe() {
        ResponseEntity<String> tao = phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/org-units", thanTaoXiNghiep());
        assertThat(tao.getStatusCode()).as("tạo đơn vị: %s", tao.getBody()).isEqualTo(HttpStatus.CREATED);

        // ⛔ Khẳng định ở CSDL, không chỉ ở thân trả về: một DTO có thể vọng lại đúng giá trị vừa
        //    nhận mà chưa hề ghi xuống đâu cả. `shortName` chính là trường đã hỏng theo kiểu ấy —
        //    nó nằm trong request, qua validate, rồi biến mất.
        assertThat(motDong("SELECT short_name FROM org_units WHERE code = ?"))
                .as("`shortName` từng bị `create()` bỏ rơi — biểu mẫu có ô, giá trị không tới CSDL")
                .isEqualTo("XNTL Kiểm");
        assertThat(motDong("SELECT address FROM org_units WHERE code = ?")).isEqualTo("Số 9 Đường T25");
        assertThat(motDong("SELECT phone FROM org_units WHERE code = ?")).isEqualTo("024.7777.8888");
        assertThat(motDong("SELECT email FROM org_units WHERE code = ?")).isEqualTo("t25@example.invalid");
    }

    @Test
    @DisplayName("⭐ Sửa đơn vị đổi được 3 ô liên hệ — endpoint PUT trước 28/8 không màn hình nào gọi")
    void suaDonViDoiDuocLienHe() {
        phienHttp.goi(duQuyen, HttpMethod.POST, "/api/v1/org-units", thanTaoXiNghiep());
        UUID id = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = ?", UUID.class, MA_XN);

        ResponseEntity<String> sua = phienHttp.goi(
                duQuyen,
                HttpMethod.PUT,
                "/api/v1/org-units/" + id,
                """
                {"name":"Xí nghiệp T25 đổi tên","shortName":"XNT25","unitType":"XI_NGHIEP",
                 "address":"Địa chỉ mới","phone":"024.0000.1111","email":"moi@example.invalid"}""");
        assertThat(sua.getStatusCode()).as("sửa đơn vị: %s", sua.getBody()).isEqualTo(HttpStatus.OK);

        assertThat(motDong("SELECT address FROM org_units WHERE code = ?")).isEqualTo("Địa chỉ mới");
        assertThat(motDong("SELECT phone FROM org_units WHERE code = ?")).isEqualTo("024.0000.1111");
    }

    @Test
    @DisplayName("⛔ Ô liên hệ bỏ trống lưu thành NULL, không thành chuỗi rỗng")
    void oTrongThanhNullChuKhongPhaiChuoiRong() {
        // Luật 16. AntD gửi ô trống lên dưới dạng `""`; giữ nguyên thì cổng công khai thấy giá trị
        // "có" và dựng ra một dòng địa chỉ trống — không phân biệt được với "đã nhập".
        phienHttp.goi(
                duQuyen,
                HttpMethod.POST,
                "/api/v1/org-units",
                """
                {"code":"%s","name":"Xí nghiệp không liên hệ","shortName":"","unitType":"XI_NGHIEP",
                 "parentPublicId":"%s","address":"   ","phone":"","email":null}"""
                        .formatted(MA_XN, goc));

        assertThat(motDong("SELECT address FROM org_units WHERE code = ?")).isNull();
        assertThat(motDong("SELECT phone FROM org_units WHERE code = ?")).isNull();
        assertThat(motDong("SELECT short_name FROM org_units WHERE code = ?")).isNull();
    }

    // ─────────────── Danh bạ lãnh đạo ───────────────

    @Test
    @DisplayName("⭐ Thêm → sửa → tắt → xoá một dòng danh bạ, mỗi bước đo ở CSDL")
    void vongDoiMotDongDanhBa() {
        ResponseEntity<String> tao = phienHttp.goi(
                duQuyen,
                HttpMethod.POST,
                "/api/v1/org-units/" + goc + "/leaders",
                """
                {"fullName":"Nguyễn Văn T25","title":"Chủ tịch kiêm Giám đốc",
                 "phone":"024.3333.4444","email":null,"sortOrder":10}""");
        assertThat(tao.getStatusCode()).as("thêm danh bạ: %s", tao.getBody()).isEqualTo(HttpStatus.CREATED);
        String publicId = PhienHttp.giaTriJson(tao.getBody(), "publicId");

        assertThat(jdbc.queryForObject(
                        "SELECT full_name FROM org_unit_leaders WHERE public_id = ?::uuid", String.class, publicId))
                .isEqualTo("Nguyễn Văn T25");

        ResponseEntity<String> sua = phienHttp.goi(
                duQuyen,
                HttpMethod.PUT,
                "/api/v1/org-units/" + goc + "/leaders/" + publicId,
                """
                {"fullName":"Nguyễn Văn T25","title":"Giám đốc","phone":"024.5555.6666",
                 "email":null,"sortOrder":20}""");
        assertThat(sua.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT title FROM org_unit_leaders WHERE public_id = ?::uuid", String.class, publicId))
                .isEqualTo("Giám đốc");

        // Tắt: dòng phải rời khỏi cổng NGAY nhưng còn nguyên trong bảng.
        phienHttp.goi(
                duQuyen,
                HttpMethod.PUT,
                "/api/v1/org-units/" + goc + "/leaders/" + publicId + "/active",
                "{\"active\":false}");
        assertThat(phienHttp.get(duQuyen, "/api/v1/public/org-units/leaders").getBody())
                .as("dòng đã tắt vẫn lọt ra cổng công khai — bộ lọc `active` ở repository không có tác dụng")
                .doesNotContain("Nguyễn Văn T25");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM org_unit_leaders WHERE public_id = ?::uuid", Integer.class, publicId))
                .as("tắt KHÔNG được xoá dòng — còn để đối chiếu và để bật lại")
                .isEqualTo(1);

        ResponseEntity<String> xoa =
                phienHttp.goi(duQuyen, HttpMethod.DELETE, "/api/v1/org-units/" + goc + "/leaders/" + publicId, null);
        assertThat(xoa.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject(
                        "SELECT deleted_at IS NOT NULL FROM org_unit_leaders WHERE public_id = ?::uuid",
                        Boolean.class,
                        publicId))
                .as("xoá phải là xoá MỀM — không có hàm xoá cứng ở tầng entity, cố ý")
                .isTrue();
    }

    @Test
    @DisplayName("⭐ Dòng đã thêm hiện NGAY trên endpoint công khai, kèm điện thoại")
    void themXongLaCongThayNgay() {
        phienHttp.goi(
                duQuyen,
                HttpMethod.POST,
                "/api/v1/org-units/" + goc + "/leaders",
                """
                {"fullName":"Trần Thị T25","title":"Phó Giám đốc","phone":"024.1212.3434",
                 "email":null,"sortOrder":5}""");

        String cong = phienHttp.get(duQuyen, "/api/v1/public/org-units/leaders").getBody();
        assertThat(cong)
                .as("⛔ Đây là cả lý do lớp này tồn tại: đường ghi mới phải nối tới đường đọc đã có")
                .contains("Trần Thị T25")
                .contains("Phó Giám đốc")
                .contains("024.1212.3434");
    }

    @Test
    @DisplayName("⛔ Điện thoại bỏ trống KHÔNG lọt ra cổng dưới dạng chuỗi rỗng")
    void dienThoaiTrongKhongThanhChuoiRong() {
        phienHttp.goi(
                duQuyen,
                HttpMethod.POST,
                "/api/v1/org-units/" + goc + "/leaders",
                """
                {"fullName":"Lê Không Số","title":"Phó Giám đốc","phone":"  ","email":null,"sortOrder":1}""");

        assertThat(jdbc.queryForObject(
                        "SELECT phone FROM org_unit_leaders WHERE full_name = 'Lê Không Số'", String.class))
                .as("luật 16: 'chưa công bố số' phải phân biệt được với 'đã công bố'")
                .isNull();
    }

    // ─────────────── Phân quyền ───────────────

    @Test
    @DisplayName("⛔ Không có `adm:org-unit:manage` thì mọi đường ghi trả 403")
    void khongQuyenThiKhongGhiDuoc() {
        assertThat(phienHttp
                        .goi(
                                khongQuyen,
                                HttpMethod.POST,
                                "/api/v1/org-units/" + goc + "/leaders",
                                """
                                {"fullName":"Kẻ Không Quyền","title":"X","phone":null,"email":null,"sortOrder":0}""")
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(phienHttp
                        .goi(khongQuyen, HttpMethod.POST, "/api/v1/org-units", thanTaoXiNghiep())
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("⛔ Danh bạ KHÔNG mở công khai đường quản trị — chưa đăng nhập là 401")
    void duongQuanTriKhongMoCongKhai() {
        // ⚠ Endpoint công khai `/public/org-units/leaders` cố ý mở; đường `/org-units/{id}/leaders`
        //   thì không — nó trả cả dòng đã tắt và cả email nội bộ. Hai đường, hai bề mặt khác nhau.
        ResponseEntity<String> khuyetDanh = http.getForEntity("/api/v1/org-units/" + goc + "/leaders", String.class);
        assertThat(khuyetDanh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────── Tiện ích ───────────────

    private String thanTaoXiNghiep() {
        return """
               {"code":"%s","name":"Xí nghiệp Thuỷ lợi T25","shortName":"XNTL Kiểm","unitType":"XI_NGHIEP",
                "parentPublicId":"%s","address":"Số 9 Đường T25","phone":"024.7777.8888",
                "email":"t25@example.invalid"}"""
                .formatted(MA_XN, goc);
    }

    private String motDong(String sql) {
        return jdbc.queryForObject(sql, String.class, MA_XN);
    }

    /** Tài khoản mang đúng một quyền, qua một vai trò tạm nằm ngoài nhóm bắt buộc 2FA. */
    private String taoNguoiDungCoQuyen(String hau, String quyen) {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, hau);
        jdbc.update("INSERT INTO roles (code, name) VALUES ('T25_ORG_PROBE', 'Vai trò kiểm thử WS-25') "
                + "ON CONFLICT DO NOTHING");
        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = 'T25_ORG_PROBE' AND p.code = ? ON CONFLICT DO NOTHING",
                quyen);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = 'T25_ORG_PROBE' ON CONFLICT DO NOTHING",
                username);
        return username;
    }
}
