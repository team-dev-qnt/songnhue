package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;

/**
 * <b>T61.39 — thông báo quyền riêng tư + dấu đồng ý</b> (NĐ 13/2023 Điều 11, 13).
 *
 * <h2>Hai trạng thái, và chúng phải dẫn tới hai hành vi khác nhau</h2>
 *
 * <ul>
 *   <li><b>Chưa có thông báo</b> (mặc định — nội dung là văn bản pháp lý của Công ty, ⛔ seed được):
 *       biểu mẫu nhận như cũ, {@code consent_at} để NULL. ⛔ Bắt tick vào một thông báo rỗng là dựng
 *       <b>bằng chứng đồng ý giả</b>.
 *   <li><b>Đã có thông báo</b>: thiếu ô đồng ý ⇒ từ chối và <b>⛔ bản ghi nào được tạo</b>; có đồng ý
 *       ⇒ lưu kèm mốc thời gian.
 * </ul>
 *
 * <p>⚠ Đi qua HTTP vì luật nằm ở {@code InboundSubmissionGate} — một cổng dùng chung cho cả hai biểu
 * mẫu (luật 12), và vì {@code consent_at} phải được đo trên hàng thật trong CSDL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuyenRiengTuHttpTest extends IntegrationTestBase {

    private static final String LIEN_HE = "/api/v1/public/contacts";
    private static final String GOP_Y = "/api/v1/public/feedbacks";
    private static final String KHOA = "site.privacy.notice";

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.songnhue.core.infra.identity.UserRepository users;

    @Autowired
    private com.songnhue.core.application.auth.PasswordPolicyService passwords;

    private PhienHttp phienHttp;
    private PhienHttp.Phien quanTri;

    @org.junit.jupiter.api.BeforeAll
    void dangNhap() {
        phienHttp = new PhienHttp(http);
        quanTri = phienHttp.dangNhap(taoNguoiDungCoQuyen("t6139_qt", "adm:setting:update"));
    }

    @AfterEach
    void donDep() {
        // Trả thông báo về RỖNG qua đúng đường ghi (xoá đệm) — để sót là mọi lớp chạy sau đỏ.
        phienHttp.goi(
                quanTri, org.springframework.http.HttpMethod.PUT, "/api/v1/settings/" + KHOA, "{\"value\": \"\"}");
        jdbc.update("DELETE FROM notification_recipients r USING notifications n "
                + "WHERE n.id = r.notification_id AND n.event_type IN ('CONTACT_RECEIVED', 'FEEDBACK_RECEIVED')");
        jdbc.update("DELETE FROM notifications WHERE event_type IN ('CONTACT_RECEIVED', 'FEEDBACK_RECEIVED')");
        jdbc.update("DELETE FROM jobs WHERE job_type LIKE 'CMS_CONTACT%'");
        jdbc.update("DELETE FROM contacts");
        jdbc.update("DELETE FROM feedbacks");
    }

    /**
     * Đặt thông báo qua <b>đúng đường ghi của ứng dụng</b> ({@code PUT /settings/{key}}).
     *
     * <p>⛔⛔ ⛔ ghi thẳng SQL: {@code SettingService} có đệm Caffeine 60 giây và chỉ lượt ghi qua
     * service mới xoá đệm — một câu {@code UPDATE} trần ⛔ có hiệu lực, bài kiểm đỏ vì một lý do ⛔
     * liên quan gì tới thứ nó khẳng định (bẫy đã ghi sẵn ở {@code LoiTheoTruongToiDungOTest}).
     *
     * <p>⚠ Nhóm SITE ⛔ nằm trong danh sách nhạy cảm của T61.42 nên ⛔ cần nhập lại mã 2FA.
     */
    private void datThongBao() {
        ResponseEntity<String> tl = phienHttp.goi(
                quanTri,
                org.springframework.http.HttpMethod.PUT,
                "/api/v1/settings/" + KHOA,
                "{\"value\": \"Công ty xử lý dữ liệu cá nhân của bạn để tiếp nhận và trả lời phản ánh.\"}");
        assertThat(tl.getStatusCode().value())
                .as("chống tập rỗng: khoá %s phải CÓ trong settings (migration T61.39): %s", KHOA, tl.getBody())
                .isEqualTo(200);
    }

    private static String thanLienHe(Boolean dongY) {
        String them = dongY == null ? "" : ", \"dongY\": %b".formatted(dongY);
        return """
                {"fullName": "Nguyễn Văn A", "email": "a@example.invalid", "phone": "0243000000",
                 "subject": "Phản ánh", "content": "Kênh N1 sạt lở."%s}"""
                .formatted(them);
    }

    @Test
    @DisplayName("⭐ Chưa cấu hình thông báo ⇒ nhận như cũ, consent_at để NULL (⛔ bằng chứng đồng ý giả)")
    void chuaCoThongBaoThiNhanNhuCu() {
        ResponseEntity<String> tl = new PhienHttp(http).dangJson(LIEN_HE, thanLienHe(null));

        assertThat(tl.getStatusCode()).as("%s", tl.getBody()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts WHERE consent_at IS NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔⛔ Có thông báo mà thiếu ô đồng ý ⇒ TỪ CHỐI, và ⛔ bản ghi nào được tạo")
    void coThongBaoMaKhongDongYThiTuChoi() {
        datThongBao();

        ResponseEntity<String> tl = new PhienHttp(http).dangJson(LIEN_HE, thanLienHe(null));

        assertThat(tl.getStatusCode().value()).as("%s", tl.getBody()).isBetween(400, 499);
        assertThat(tl.getBody()).contains("dongY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts", Integer.class))
                .as("⛔ ghi rồi mới hỏi: một bản ghi ⛔ có đồng ý là dữ liệu thu thập trái Điều 11")
                .isZero();
    }

    @Test
    @DisplayName("⭐ Có thông báo + có đồng ý ⇒ nhận, và mốc thời gian đồng ý được LƯU")
    void coDongYThiLuuMocThoiGian() {
        datThongBao();

        ResponseEntity<String> tl = new PhienHttp(http).dangJson(LIEN_HE, thanLienHe(true));

        assertThat(tl.getStatusCode()).as("%s", tl.getBody()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts WHERE consent_at IS NOT NULL", Integer.class))
                .as("NĐ 13 Điều 11 đòi CHỨNG MINH được là đã có đồng ý — một cờ boolean ⛔ nói được lúc nào")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐⭐ Cùng một luật áp cho biểu mẫu GÓP Ý — một cổng dùng chung, ⛔ hai bản sao")
    void luatApChoCaGopY() {
        datThongBao();

        String than =
                """
                {"fullName": "Trần Thị B", "email": "b@example.invalid", "rating": 4,
                 "content": "Cổng dễ dùng."}""";
        ResponseEntity<String> thieu = new PhienHttp(http).dangJson(GOP_Y, than);
        assertThat(thieu.getStatusCode().value()).as("%s", thieu.getBody()).isBetween(400, 499);

        ResponseEntity<String> du =
                new PhienHttp(http).dangJson(GOP_Y, than.replace("\"content\"", "\"dongY\": true, \"content\""));
        assertThat(du.getStatusCode()).as("%s", du.getBody()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feedbacks WHERE consent_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }
    /** Vai trò riêng của lớp này — ⛔ dùng chung vai trò của lớp khác (trạng thái rò giữa các lớp). */
    private String taoNguoiDungCoQuyen(String hau, String quyen) {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, hau);
        jdbc.update("INSERT INTO roles (code, name) VALUES ('T6139_RIENG_TU', 'Kiểm thử quyền riêng tư') "
                + "ON CONFLICT DO NOTHING");
        int n = jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = 'T6139_RIENG_TU' AND p.code = ? ON CONFLICT DO NOTHING",
                quyen);
        assertThat(n).as("chống tập rỗng: mã quyền %s đổi tên?", quyen).isPositive();
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = 'T6139_RIENG_TU' ON CONFLICT DO NOTHING",
                username);
        return username;
    }
}
