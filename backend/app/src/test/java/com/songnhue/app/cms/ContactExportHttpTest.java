package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Xuất danh sách liên hệ ra CSV.</b> CN-01.4 — T36.5.
 *
 * <h2>⛔⛔ Bài chịu lực: CHẶN CÔNG THỨC (CSV injection)</h2>
 *
 * <p>Cột <i>Nội dung</i> mang chữ do <b>người lạ trên Internet</b> gõ. Một ô bắt đầu bằng
 * {@code =}, {@code +}, {@code -}, {@code @} được Excel <b>thi hành như công thức</b> khi mở tệp, và
 * {@code =cmd|'/c calc'!A1} là một cách chạy lệnh trên máy người mở. Nạn nhân ở đây là cán bộ có
 * quyền quản trị — cùng hình dạng với XSS lưu trữ nhắm vào màn hình quản trị, chỉ khác là nó nổ
 * <i>ngoài</i> trình duyệt.
 *
 * <p>⚠ Lớp chống nằm ở {@code BangCsv.boc()} — lớp vừa được <b>chuyển</b> từ {@code hydro.domain}
 * lên {@code core.common.export} thay vì chép một bản sang {@code content}. Bài này là bằng chứng
 * lượt chuyển ấy giữ nguyên hiệu lực trên đường đi mới.
 *
 * <h2>Vì sao đi bằng HTTP</h2>
 *
 * <p>Ba thứ chỉ tồn tại ở tầng controller: header {@code Content-Disposition} (thiếu nó thì trình
 * duyệt hiện CSV thành chữ trong tab), việc phản hồi <b>⛔ không bị bọc envelope</b> (§10.52 —
 * envelope bọc {@code byte[]} biến tệp thành base64 ⛔ không ai giải), và BOM ba byte ở <i>đúng</i>
 * đầu luồng byte thật sự gửi đi.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContactExportHttpTest extends IntegrationTestBase {

    private static final String CONG_KHAI = "/api/v1/public/contacts";
    private static final String XUAT = "/api/v1/cms/contacts/export";

    /** ⭐ Excel nhận diện bảng mã qua ba byte này. */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien duQuyen;

    @BeforeAll
    void dangNhap() {
        phienHttp = new PhienHttp(http);
        duQuyen = phienHttp.dangNhap(taoNguoiDungCoQuyen("t36_xuat", "cms:contact:manage"));
    }

    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM notification_recipients r USING notifications n "
                + "WHERE n.id = r.notification_id AND n.event_type = 'CONTACT_RECEIVED'");
        jdbc.update("DELETE FROM notifications WHERE event_type = 'CONTACT_RECEIVED'");
        jdbc.update("DELETE FROM jobs WHERE job_type LIKE 'CMS_CONTACT%'");
        jdbc.update("DELETE FROM contacts");
    }

    @Test
    @DisplayName("⭐⭐ Xuất ra tệp CSV thật: có BOM, có tên tệp, và MANG dữ liệu đã gửi")
    void xuatRaTepThat() {
        gui("Nguyễn Văn A", "Kênh N4 bị bồi lắng");

        ResponseEntity<byte[]> r = tai(XUAT);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        byte[] noiDung = r.getBody();
        assertThat(noiDung).isNotNull();

        assertThat(new byte[] {noiDung[0], noiDung[1], noiDung[2]})
                .as("⭐ BOM UTF-8 — thiếu nó thì Excel đoán bảng mã theo địa phương và 'Nguyễn Văn "
                        + "A' thành ký tự lạ")
                .isEqualTo(BOM);

        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .as("⛔ Thiếu header này thì trình duyệt hiện CSV thành chữ trong tab, ⛔ không tải về")
                .contains("attachment")
                .contains("lien-he_");

        String csv = new String(noiDung, StandardCharsets.UTF_8);
        assertThat(csv)
                .as("⛔⛔ Phản hồi bị bọc envelope thì đây là base64 ⛔ không ai giải (§10.52)")
                .contains("Nguyễn Văn A")
                .contains("Kênh N4 bị bồi lắng");
        assertThat(csv).as("dòng tiêu đề").contains("Thời điểm nhận").contains("Trạng thái");

        assertThat(csv.split("\r\n"))
                .as("⚠ vế đếm (luật 29): 1 tiêu đề + 1 dữ liệu. ⛔ Không có nó thì một tệp CHỈ CÓ "
                        + "tiêu đề vẫn qua được mọi khẳng định `contains` ở trên")
                .hasSize(2);
    }

    /** ⛔⛔ Xem javadoc lớp. */
    @Test
    @DisplayName("⛔⛔ Nội dung bắt đầu bằng `=` bị CHẶN thành văn bản — Excel ⛔ không thi hành")
    void chanCongThucExcel() {
        gui("Trần Thị B", "=cmd|'/c calc'!A1");

        String csv = new String(tai(XUAT).getBody(), StandardCharsets.UTF_8);

        assertThat(csv)
                .as("⛔⛔ Ô này đi thẳng vào Excel của cán bộ quản trị. Dấu nháy đơn đứng trước là "
                        + "thứ duy nhất ngăn nó thành một công thức chạy được — và Excel ⛔ không "
                        + "hiện dấu nháy ấy ra.")
                .contains("\"'=cmd|'/c calc'!A1\"");
        assertThat(csv)
                .as("⛔ ⛔ Không được có một ô bắt đầu bằng `=` ngay sau dấu nháy kép mở")
                .doesNotContain("\"=cmd");
    }

    @Test
    @DisplayName("⭐ Lọc theo trạng thái đi tới tận tệp — ⛔ không phải một tham số bị bỏ qua")
    void locTheoTrangThai() {
        gui("Người A", "Nội dung A");

        String tatCa = new String(tai(XUAT).getBody(), StandardCharsets.UTF_8);
        assertThat(tatCa).contains("Người A");

        // ⛔ Chưa bản ghi nào ở DA_PHAN_HOI ⇒ tệp phải chỉ có dòng tiêu đề.
        String daPhanHoi = new String(tai(XUAT + "?status=DA_PHAN_HOI").getBody(), StandardCharsets.UTF_8);
        assertThat(daPhanHoi)
                .as("⛔ Một tham số lọc bị bỏ qua trông y hệt một tham số lọc hoạt động — cho tới "
                        + "ngày ai đó xuất nhầm cả hộp thư ra một tệp gửi đi ngoài")
                .doesNotContain("Người A");
        assertThat(daPhanHoi.split("\r\n")).hasSize(1);
    }

    @Test
    @DisplayName("⛔ Thiếu quyền `cms:contact:manage` → 401 khi chưa đăng nhập")
    void chuaDangNhapThiKhongXuatDuoc() {
        assertThat(http.getForEntity(XUAT, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─────────────── Tiện ích ───────────────

    private void gui(String hoTen, String noiDung) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String than =
                """
                {
                  "fullName": %s,
                  "email": "a@example.invalid",
                  "phone": null,
                  "subject": "Phản ánh",
                  "content": %s
                }
                """
                        .formatted(oJson(hoTen), oJson(noiDung));
        assertThat(http.postForEntity(CONG_KHAI, new HttpEntity<>(than, h), String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    /** ⚠ Thoát dấu nháy kép và dấu chéo ngược — nội dung bài kiểm có ký tự đặc biệt thật. */
    private static String oJson(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private ResponseEntity<byte[]> tai(String duong) {
        return http.exchange(duong, HttpMethod.GET, new HttpEntity<>(phienHttp.header(duQuyen)), byte[].class);
    }

    private String taoNguoiDungCoQuyen(String hau, String quyen) {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, hau);
        jdbc.update("INSERT INTO roles (code, name) VALUES ('T36_XUAT_PROBE', 'Vai trò kiểm thử WS-36 xuất') "
                + "ON CONFLICT DO NOTHING");
        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = 'T36_XUAT_PROBE' AND p.code = ? ON CONFLICT DO NOTHING",
                quyen);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = 'T36_XUAT_PROBE' ON CONFLICT DO NOTHING",
                username);
        return username;
    }
}
