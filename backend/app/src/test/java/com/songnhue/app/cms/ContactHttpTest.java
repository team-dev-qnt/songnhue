package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Vòng khép kín của biểu mẫu liên hệ — đi qua HTTP thật.</b> CN-01.4, WS-26.
 *
 * <h2>Vì sao bài kiểm này phải đi bằng HTTP, không gọi thẳng service</h2>
 *
 * Ba cam kết của tính năng này nằm ở <b>tầng controller/filter</b>, không nằm trong service:
 * đường gửi <i>không cần đăng nhập</i>, đường đọc <i>bắt buộc có quyền</i>, và hai đường ấy nằm
 * ở hai tiền tố khác nhau. Gọi thẳng service thì cả ba đều không được kiểm — đúng luật 5.
 *
 * <h2>Cam kết đắt nhất: người dân gửi xong thì CÓ NGƯỜI ĐỌC ĐƯỢC</h2>
 *
 * Chú thích cũ ở trang Liên hệ nói thẳng: *"một form gửi đi mà không ai nhận tệ hơn hẳn không có
 * form"*. Nên bài kiểm không dừng ở "POST trả 204": nó gửi bằng đường công khai rồi <b>đọc lại
 * bằng đường quản trị</b>, tức đi trọn cả hai nửa của cặp đọc–ghi trong một lượt (luật 27).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContactHttpTest extends IntegrationTestBase {

    private static final String CONG_KHAI = "/api/v1/public/contacts";
    private static final String QUAN_TRI = "/api/v1/cms/contacts";

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

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phienHttp = new PhienHttp(http);
        duQuyen = phienHttp.dangNhap(taoNguoiDungCoQuyen("t26_ct", "cms:contact:manage"));
        khongQuyen = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t26_zero"));
    }

    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM contacts");
    }

    // ─────────────── Đường GỬI (công khai, ẩn danh) ───────────────

    @Test
    @DisplayName("⭐ Khách vãng lai gửi được — và bản ghi ĐI TỚI CSDL, không chỉ trả 204")
    void khachGuiDuocVaLuuThat() {
        ResponseEntity<String> gui =
                http.postForEntity(CONG_KHAI, json("nguyenvana@example.invalid", null), String.class);

        assertThat(gui.getStatusCode()).as("thân: %s", gui.getBody()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(gui.getBody())
                .as("204 thì KHÔNG trả publicId — đường ẩn danh không phát tay cầm")
                .isNull();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM contacts", String.class))
                .isEqualTo("MOI");
        assertThat(jdbc.queryForObject("SELECT full_name FROM contacts", String.class))
                .isEqualTo("Nguyễn Văn A");
    }

    @Test
    @DisplayName("⛔ Không email lẫn điện thoại → 400, không hàng nào được ghi")
    void thieuDuongLienLacNguocThiTuChoi() {
        ResponseEntity<String> gui = http.postForEntity(CONG_KHAI, json(null, null), String.class);

        assertThat(gui.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts", Integer.class))
                .as("một lời nhắn không có cách trả lời là một bản ghi không dùng được")
                .isZero();
    }

    @Test
    @DisplayName("⭐ Chỉ có số điện thoại vẫn nhận — email KHÔNG phải trường bắt buộc")
    void chiCoDienThoaiVanNhan() {
        ResponseEntity<String> gui = http.postForEntity(CONG_KHAI, json(null, "0243354xxxx"), String.class);
        assertThat(gui.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts", Integer.class))
                .isEqualTo(1);
    }

    // ─────────────── Đường ĐỌC (quản trị, có quyền) ───────────────

    @Test
    @DisplayName("⛔ Hộp thư KHÔNG mở cho người chưa đăng nhập — 401, không phải 404")
    void chuaDangNhapThiKhongDocDuoc() {
        ResponseEntity<String> doc = http.getForEntity(QUAN_TRI, String.class);
        // 404 nghĩa là đường dẫn sai, tức bài kiểm đang kiểm nhầm chỗ.
        assertThat(doc.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("⛔ Đăng nhập nhưng KHÔNG có `cms:contact:manage` → 403")
    void thieuQuyenThiKhongDocDuoc() {
        ResponseEntity<String> doc = phienHttp.get(khongQuyen, QUAN_TRI);
        assertThat(doc.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("⭐⭐ Gửi bằng đường công khai → đọc lại được bằng đường quản trị (vòng khép kín)")
    void guiRoiDocLaiDuoc() {
        http.postForEntity(CONG_KHAI, json("b@example.invalid", null), String.class);

        ResponseEntity<String> doc = phienHttp.get(duQuyen, QUAN_TRI);

        assertThat(doc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(doc.getBody())
                .as("nội dung người dân gửi phải quay lại nguyên văn cho người xử lý")
                .contains("Nguyễn Văn A")
                .contains("Kênh N4 đoạn qua xã bị bồi lắng");
    }

    @Test
    @DisplayName("⭐ Đánh dấu đã đọc ghi dấu MỘT lần — lần sau không đổi người đọc đầu tiên")
    void danhDauDaDocChiGhiLanDau() {
        http.postForEntity(CONG_KHAI, json("c@example.invalid", null), String.class);
        String publicId = jdbc.queryForObject("SELECT public_id FROM contacts", String.class);

        ResponseEntity<String> lan1 =
                phienHttp.goi(duQuyen, HttpMethod.PATCH, QUAN_TRI + "/" + publicId + "/read", null);
        assertThat(lan1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT status FROM contacts", String.class))
                .isEqualTo("DA_DOC");

        String mocDau = jdbc.queryForObject("SELECT read_at::text FROM contacts", String.class);
        phienHttp.goi(duQuyen, HttpMethod.PATCH, QUAN_TRI + "/" + publicId + "/read", null);

        assertThat(jdbc.queryForObject("SELECT read_at::text FROM contacts", String.class))
                .as("lần đọc thứ hai không được ghi đè mốc của lần đầu")
                .isEqualTo(mocDau);
    }

    // ─────────────── Quyền riêng tư ───────────────

    @Test
    @DisplayName("⛔⛔ Bảng KHÔNG có cột nào chứa địa chỉ IP — NĐ 13/2023")
    void khongLuuDiaChiIp() {
        // Canh ở tầng LƯỢC ĐỒ chứ không ở tầng mã: một cột đã tồn tại thì sớm muộn cũng có người
        // ghi vào nó, và lúc ấy chẳng ai nhớ vì sao nó ra đời.
        Integer soCot = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 'contacts' AND (column_name LIKE '%ip%' OR column_name LIKE '%agent%')",
                Integer.class);
        assertThat(soCot)
                .as("IP và user-agent là dữ liệu cá nhân; chống lạm dụng đã do bộ lọc tần suất lo, trong bộ nhớ")
                .isZero();
    }

    // ─────────────── Tiện ích ───────────────

    /** Bọc thân JSON kèm `Content-Type` — mặc định của `TestRestTemplate` cho `String` là text/plain. */
    private static HttpEntity<String> json(String email, String dienThoai) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(than(email, dienThoai), h);
    }

    private static String than(String email, String dienThoai) {
        return """
                {
                  "fullName": "Nguyễn Văn A",
                  "email": %s,
                  "phone": %s,
                  "subject": "Phản ánh kênh N4",
                  "content": "Kênh N4 đoạn qua xã bị bồi lắng, đề nghị Công ty kiểm tra."
                }
                """
                .formatted(oChuoi(email), oChuoi(dienThoai));
    }

    private static String oChuoi(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private String taoNguoiDungCoQuyen(String hau, String quyen) {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, hau);
        jdbc.update("INSERT INTO roles (code, name) VALUES ('T26_CONTACT_PROBE', 'Vai trò kiểm thử WS-26') "
                + "ON CONFLICT DO NOTHING");
        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = 'T26_CONTACT_PROBE' AND p.code = ? ON CONFLICT DO NOTHING",
                quyen);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = 'T26_CONTACT_PROBE' ON CONFLICT DO NOTHING",
                username);
        return username;
    }
}
