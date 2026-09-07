package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.content.application.CmsJobTypes;
import com.songnhue.content.application.ContactScheduler;
import com.songnhue.content.application.ContactService;
import com.songnhue.content.application.ContactSlaHandler;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;

/**
 * <b>Hai chiều thư của hộp thư liên hệ, và bộ nhắc SLA.</b> CN-01.4 — T36.3 + T36.4.
 *
 * <h2>Vì sao ⛔ không phải một bài unit test với mock</h2>
 *
 * <p>Ba trong bốn cam kết của đợt này chỉ tồn tại <b>ngoài</b> service:
 *
 * <ul>
 *   <li>Người nhận thư báo phân giải theo <b>quyền</b> — {@code RecipientResolver} tra CSDL. Mock nó
 *       là khẳng định một danh sách rỗng cũng đúng (luật 7: một cơ chế chạy qua tập rỗng vẫn xanh
 *       trọn vẹn).
 *   <li>Việc gửi thư xác nhận đi qua <b>hàng đợi</b> và phải ở <b>cùng giao dịch</b> với lượt lưu.
 *   <li>Khoá chống trùng theo ngày là một <b>chỉ mục duy nhất trong CSDL</b>, ⛔ không phải một câu
 *       {@code if} trong mã — nó ⛔ không kiểm được nếu ⛔ không có CSDL.
 * </ul>
 *
 * <h2>⛔ Bài này ⛔ KHÔNG gửi một email thật nào</h2>
 *
 * <p>Môi trường kiểm thử ⛔ không có {@code SMTP_HOST}, nên {@code EmailSender} ⛔ không tồn tại và
 * {@code MailPort.send} trả {@code false}. Đó chính là lý do {@code MailPort} trả {@code boolean}:
 * <i>chưa cấu hình</i> và <i>gửi hỏng</i> là hai kết cục khác nhau, và bài này đo được cái thứ nhất.
 * Thứ bài này khẳng định là <b>đường dây</b> — việc vào hàng đợi, người nhận được phân giải, chuông
 * kêu đúng lúc — chứ ⛔ không phải SMTP.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContactEmailSlaHttpTest extends IntegrationTestBase {

    private static final String CONG_KHAI = "/api/v1/public/contacts";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SettingService settings;

    @Autowired
    private ContactScheduler scheduler;

    @Autowired
    private ContactSlaHandler slaHandler;

    @Autowired
    private List<JobHandler> handlers;

    @BeforeAll
    void taoNguoiNhan() {
        // ⭐ Vế chống tập rỗng (luật 7): ⛔ không có tài khoản nào mang quyền này thì
        //   `RecipientResolver` trả danh sách rỗng, và mọi khẳng định "đã báo" bên dưới xanh vô
        //   nghĩa. Bài kiểm phải TỰ dựng người nhận.
        taoNguoiDungCoQuyen("t36_mail", ContactService.QUYEN_XU_LY);
    }

    /**
     * ⚠⚠ Dọn <b>trước</b> mỗi bài, ⛔ không chỉ sau — và đây là một bài học đo được, ⛔ không phải
     * thói quen.
     *
     * <p>Bản đầu của lớp này chỉ có {@code @AfterEach} và khẳng định {@code count(*) = 1} trên
     * <b>toàn bảng</b>. Chạy riêng: <b>7/7 xanh</b>. Chạy trong cả bộ: <b>đỏ, đếm được 11</b> —
     * {@code ContactHttpTest} và {@code ContactWorkflowHttpTest} chạy trước, mỗi lượt gửi biểu mẫu
     * của chúng cũng sinh một {@code CONTACT_RECEIVED}, và {@code @AfterEach} của <i>chúng</i> ⛔
     * không dọn bảng {@code notifications}.
     *
     * <p>⇒ Một khẳng định trên <b>trạng thái toàn cục</b> ⛔ không kiểm cái nó tưởng đang kiểm: nó
     * kiểm <i>"tổng số bản ghi trong CSDL dùng chung"</i>, một con số phụ thuộc thứ tự chạy. Cùng
     * họ với luật 9 — con số ấy ⛔ không phân biệt được "lượt gửi của tôi sinh đúng một thông báo"
     * với "cả bộ kiểm đã sinh mười một".
     *
     * <p>⛔ Và ⛔ KHÔNG dọn cả bảng: chỉ xoá đúng hai loại sự kiện của hộp thư liên hệ. Một
     * {@code DELETE FROM notifications} trần sẽ phá bất kỳ lớp kiểm nào dựng thông báo ở
     * {@code @BeforeAll} rồi khẳng định về sau.
     */
    @BeforeEach
    void donTruoc() {
        donThongBaoCuaLienHe();
        jdbc.update("DELETE FROM jobs WHERE job_type LIKE 'CMS_CONTACT%'");
        jdbc.update("DELETE FROM contacts");
        datHanSla("48");
    }

    @AfterEach
    void donSau() {
        donThongBaoCuaLienHe();
        jdbc.update("DELETE FROM jobs WHERE job_type LIKE 'CMS_CONTACT%'");
        jdbc.update("DELETE FROM contacts");
        datHanSla("48");
    }

    private void donThongBaoCuaLienHe() {
        jdbc.update("DELETE FROM notification_recipients r USING notifications n "
                + "WHERE n.id = r.notification_id AND n.event_type IN ('CONTACT_RECEIVED', 'CONTACT_SLA_BREACH')");
        jdbc.update("DELETE FROM notifications WHERE event_type IN ('CONTACT_RECEIVED', 'CONTACT_SLA_BREACH')");
    }

    // ═══════════════ T36.3 — hai chiều thư ═══════════════

    @Test
    @DisplayName("⭐⭐ Gửi biểu mẫu ⇒ cán bộ ĐƯỢC BÁO (có người nhận thật) và việc gửi thư vào hàng đợi")
    void motLuotGuiSinhCaHaiChieuThu() {
        gui("nguoidan@example.invalid", null);

        Integer soThongBao = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_type = 'CONTACT_RECEIVED'", Integer.class);
        assertThat(soThongBao).isEqualTo(1);

        Integer soNguoiNhan = jdbc.queryForObject(
                "SELECT count(*) FROM notification_recipients r "
                        + "JOIN notifications n ON n.id = r.notification_id "
                        + "WHERE n.event_type = 'CONTACT_RECEIVED'",
                Integer.class);
        assertThat(soNguoiNhan)
                .as("⛔⛔ Một thông báo ⛔ KHÔNG người nhận nào im lặng y như ⛔ không có thông báo. "
                        + "Đây là vế mà một bài unit test có mock ⛔ không thể phân biệt được.")
                .isPositive();

        assertThat(demViec(CmsJobTypes.CONTACT_ACK_MAIL)).isEqualTo(1);
    }

    @Test
    @DisplayName("⛔⛔ Payload việc gửi thư ⛔ KHÔNG chứa địa chỉ email — NĐ 13/2023")
    void payloadKhongMangDiaChiEmail() {
        gui("nguoidan@example.invalid", null);

        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM jobs WHERE job_type = ?", String.class, CmsJobTypes.CONTACT_ACK_MAIL);

        assertThat(payload)
                .as("⛔ `JobRequest` nói rõ: payload nằm NGUYÊN VĂN trong bảng `jobs` và lọt vào mọi "
                        + "bản sao lưu. Địa chỉ đã có ở `contacts` — chép thêm một bản vào hàng đợi "
                        + "là nhân đôi phạm vi mà ⛔ không đổi lấy gì.")
                .doesNotContain("nguoidan@example.invalid")
                .contains("contactPublicId");
    }

    @Test
    @DisplayName("⛔ Chỉ để lại điện thoại ⇒ vẫn báo cán bộ, nhưng ⛔ KHÔNG đặt việc gửi thư")
    void khongCoEmailThiKhongDatViec() {
        gui(null, "0243354xxxx");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM notifications WHERE event_type = 'CONTACT_RECEIVED'", Integer.class))
                .isEqualTo(1);
        assertThat(demViec(CmsJobTypes.CONTACT_ACK_MAIL))
                .as("biểu mẫu cho phép chỉ để lại số điện thoại — `ck_contacts_lien_lac`")
                .isZero();
    }

    // ═══════════════ T36.4 — nhắc SLA ═══════════════

    /**
     * ⭐⭐ Đây là phần <i>"dedup theo ngày"</i> của §9.13.5, và nó ⛔ <b>không</b> phải một câu
     * {@code if} trong mã — nó là {@code uq_jobs_dedup_active}, một chỉ mục duy nhất trong CSDL.
     * Bài kiểm phải chạy trên CSDL thật, nếu không thì nó ⛔ không kiểm gì cả.
     */
    @Test
    @DisplayName("⭐⭐ Hẹn giờ hai lần trong một ngày ⇒ ĐÚNG MỘT việc nhắc SLA")
    void hangDoiChongTrungTheoNgay() {
        scheduler.quetSla();
        scheduler.quetSla();

        assertThat(demViec(CmsJobTypes.CONTACT_SLA_REMIND))
                .as("⛔ Nhắc nhiều lần một ngày là dạy người dùng đặt quy tắc lọc thư — và lúc đó "
                        + "cảnh báo sự cố thật cũng chết theo")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐⭐ Liên hệ quá hạn ⇒ có chuông; chưa quá hạn ⇒ ⛔ IM LẶNG")
    void chuongKeuDungLuc() {
        gui("a@example.invalid", null);

        // Chưa quá hạn — bản ghi vừa tạo, hạn 48 giờ.
        slaHandler.handle(viecRong());
        assertThat(demChuongSla())
                .as("⛔⛔ Một chuông kêu mỗi ngày kể cả khi ⛔ không có gì là một chuông mang ĐÚNG "
                        + "MỘT BIT — nó bị lọc mất trước khi có việc thật (§10.76)")
                .isZero();

        // Đẩy bản ghi lùi 72 giờ — quá hạn 48 giờ.
        jdbc.update("UPDATE contacts SET created_at = now() - interval '72 hours'");
        slaHandler.handle(viecRong());

        assertThat(demChuongSla()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT title FROM notifications WHERE event_type = 'CONTACT_SLA_BREACH'", String.class))
                .as("⭐ Tiêu đề mang CON SỐ — đó là thứ đổi mỗi ngày và là thứ người đọc thật sự cần")
                .contains("1 liên hệ quá hạn");
    }

    @Test
    @DisplayName("⭐ `sla-hours = 0` là công tắc TẮT — ⛔ không phải một giá trị sai")
    void khongGioLaCongTacTat() {
        gui("a@example.invalid", null);
        jdbc.update("UPDATE contacts SET created_at = now() - interval '72 hours'");

        datHanSla("0");
        slaHandler.handle(viecRong());

        assertThat(demChuongSla())
                .as("⛔ Một khoá `sla-enabled` riêng tạo ra trạng thái vô nghĩa `enabled=true, "
                        + "hours=0`. Một khoá, một câu hỏi (luật 14).")
                .isZero();
    }

    // ═══════════════ Bộ canh: mã việc ↔ handler ═══════════════

    /**
     * ⭐⭐ {@code CmsJobTypes} nói rõ hậu quả của việc quên: <i>"job vào hàng đợi bình thường rồi nằm
     * đó mãi vì ⛔ không handler nào nhận, và triệu chứng duy nhất là 'việc ⛔ không bao giờ
     * xong'"</i>. Đó là một lời dặn — và một lời dặn ⛔ không phải một cơ chế (conventions.md §1.5).
     *
     * <p>⚠ Đọc hằng số bằng <b>phản chiếu</b>, ⛔ không liệt kê tay: một mã việc thêm vào tháng sau
     * tự động vào phạm vi bài này. Kèm khẳng định <b>về số lượng</b> (luật 29) — phản chiếu trả về
     * tập rỗng thì vòng lặp chạy 0 lần và cả bài xanh trọn vẹn.
     */
    @Test
    @DisplayName("⭐⭐ Mọi mã việc khai ở `CmsJobTypes` đều có handler đăng ký")
    void moiMaViecDeuCoNguoiNhan() throws Exception {
        List<String> maViec = new java.util.ArrayList<>();
        for (var f : CmsJobTypes.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                f.setAccessible(true);
                maViec.add((String) f.get(null));
            }
        }

        assertThat(maViec)
                .as("⛔ Phản chiếu ⛔ không thấy hằng số nào — bộ canh đang chạy qua TẬP RỖNG (luật 7)")
                .hasSizeGreaterThanOrEqualTo(3);

        List<String> daDangKy = handlers.stream().map(JobHandler::jobType).toList();
        assertThat(daDangKy)
                .as(
                        "⛔ %s khai mà ⛔ không handler nào nhận. Việc sẽ vào hàng đợi bình thường rồi "
                                + "nằm đó MÃI, và triệu chứng duy nhất là 'việc ⛔ không bao giờ xong'.",
                        maViec)
                .containsAll(maViec);
    }

    // ─────────────── Tiện ích ───────────────

    private void gui(String email, String dienThoai) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String than =
                """
                {
                  "fullName": "Nguyễn Văn A",
                  "email": %s,
                  "phone": %s,
                  "subject": "Phản ánh kênh N4",
                  "content": "Kênh N4 đoạn qua xã bị bồi lắng."
                }
                """
                        .formatted(oChuoi(email), oChuoi(dienThoai));
        ResponseEntity<String> r = http.postForEntity(CONG_KHAI, new HttpEntity<>(than, h), String.class);
        assertThat(r.getStatusCode()).as("thân: %s", r.getBody()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private static String oChuoi(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private int demViec(String loai) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM jobs WHERE job_type = ?", Integer.class, loai);
        return n == null ? 0 : n;
    }

    private int demChuongSla() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_type = 'CONTACT_SLA_BREACH'", Integer.class);
        return n == null ? 0 : n;
    }

    /** ⚠ Sửa thẳng CSDL rồi <b>xoá đệm</b>: {@code SettingService} có Caffeine, TTL vài phút. */
    private void datHanSla(String gio) {
        jdbc.update(
                "UPDATE settings SET setting_value = ? WHERE setting_key = ?", gio, ContactService.KHOA_HAN_SLA_GIO);
        settings.invalidate(ContactService.KHOA_HAN_SLA_GIO);
    }

    private static JobContext viecRong() {
        return new JobContext(UUID.randomUUID(), CmsJobTypes.CONTACT_SLA_REMIND, "{}", null, p -> {}, r -> {});
    }

    private String taoNguoiDungCoQuyen(String hau, String quyen) {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, hau);
        jdbc.update("INSERT INTO roles (code, name) VALUES ('T36_MAIL_PROBE', 'Vai trò kiểm thử WS-36 thư') "
                + "ON CONFLICT DO NOTHING");
        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = 'T36_MAIL_PROBE' AND p.code = ? ON CONFLICT DO NOTHING",
                quyen);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = 'T36_MAIL_PROBE' ON CONFLICT DO NOTHING",
                username);
        return username;
    }
}
