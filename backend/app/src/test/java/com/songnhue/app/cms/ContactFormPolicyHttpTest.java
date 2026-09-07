package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
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
import com.songnhue.content.application.ContactFormPolicy;
import com.songnhue.core.application.settings.SettingService;

/**
 * <b>Bật/tắt & đặt bắt buộc từng trường biểu mẫu liên hệ, và chỗ cắm reCAPTCHA.</b>
 * CN-01.4 — T36.7 + T36.6.
 *
 * <h2>Vì sao đi bằng HTTP</h2>
 *
 * <p>Ba cam kết nằm ngoài service: mã trạng thái trả về ({@code 400} chứ ⛔ không phải 500), việc
 * khoá cấu hình có <b>ra tới cổng công khai</b> hay ⛔ không (nhóm {@code SITE} mới ra
 * {@code /site-config}), và việc một lỗi cấu hình của <i>ta</i> ⛔ không được chặn kênh phản ánh
 * của người dân. Gọi thẳng service ⛔ không kiểm được cái nào (luật 5).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContactFormPolicyHttpTest extends IntegrationTestBase {

    private static final String CONG_KHAI = "/api/v1/public/contacts";
    private static final String CAU_HINH = "/api/v1/public/site-config";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SettingService settings;

    @AfterEach
    void traLaiMacDinh() {
        dat(ContactFormPolicy.KHOA_HIEN_DIEN_THOAI, "true");
        dat(ContactFormPolicy.KHOA_EMAIL_BAT_BUOC, "false");
        dat(ContactFormPolicy.KHOA_DIEN_THOAI_BAT_BUOC, "false");
        jdbc.update("DELETE FROM notification_recipients r USING notifications n "
                + "WHERE n.id = r.notification_id AND n.event_type = 'CONTACT_RECEIVED'");
        jdbc.update("DELETE FROM notifications WHERE event_type = 'CONTACT_RECEIVED'");
        jdbc.update("DELETE FROM jobs WHERE job_type LIKE 'CMS_CONTACT%'");
        jdbc.update("DELETE FROM contacts");
    }

    // ═══════════════ T36.7 ═══════════════

    @Test
    @DisplayName("⭐ Mặc định: chỉ có điện thoại vẫn gửi được — email ⛔ KHÔNG bắt buộc")
    void macDinhEmailKhongBatBuoc() {
        assertThat(gui(null, "0243354xxxx", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(soHang()).isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Bật `email.required` ⇒ thiếu email là 400, và ⛔ KHÔNG hàng nào được ghi")
    void batBuocEmailThiChanThat() {
        dat(ContactFormPolicy.KHOA_EMAIL_BAT_BUOC, "true");

        ResponseEntity<String> r = gui(null, "0243354xxxx", null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).contains("email");
        assertThat(soHang())
                .as("⛔ Một công tắc bật trên màn hình mà dữ liệu vẫn ghi xuống là đúng thứ luật 15 "
                        + "gọi là công tắc chưa ai đọc — chỉ tệ hơn, vì nó TRÔNG NHƯ đã đọc")
                .isZero();
    }

    /**
     * ⭐⭐ Bài chịu lực của T36.7 — một bất biến được <b>suy ra</b>, ⛔ không phải một khoá thứ tư.
     *
     * <p>{@code ck_contacts_lien_lac} đòi ít nhất một cách liên hệ ngược. Tắt ô Số điện thoại mà ⛔
     * không tự suy ra "email thành bắt buộc" thì tổ hợp <i>tắt điện thoại + email không bắt buộc</i>
     * để người dân điền xong biểu mẫu rồi nhận một lỗi <b>⛔ không sửa được</b> — biểu mẫu ⛔ không
     * còn ô nào để điền cho hợp lệ.
     */
    @Test
    @DisplayName("⭐⭐ Tắt ô Số điện thoại ⇒ email TỰ thành bắt buộc, dù khoá của nó vẫn tắt")
    void tatDienThoaiThiEmailTuBatBuoc() {
        dat(ContactFormPolicy.KHOA_HIEN_DIEN_THOAI, "false");
        // ⚠ Cố ý KHÔNG bật `email.required` — đó chính là điều bài này khẳng định.
        dat(ContactFormPolicy.KHOA_EMAIL_BAT_BUOC, "false");

        ResponseEntity<String> r = gui(null, "0243354xxxx", null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(soHang()).isZero();

        // Và có email thì qua — vế đối chứng, ⛔ không có nó thì bài trên xanh cả khi mọi lượt gửi
        // đều bị chặn vì một lý do khác (luật 9).
        assertThat(gui("a@example.invalid", null, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(soHang()).isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Ba khoá biểu mẫu RA TỚI cổng công khai — nếu không, giao diện ⛔ không đọc được")
    void khoaBieuMauRaToiCong() {
        String body = http.getForEntity(CAU_HINH, String.class).getBody();

        // ⭐ Đây là lý do bộ khoá nằm ở nhóm SITE chứ ⛔ không phải CMS: `siteConfig()` chỉ trả về
        //   SITE + COMPANY. Ở nhóm CMS thì biểu mẫu ⛔ không bao giờ biết trường nào bắt buộc — một
        //   nửa cặp đọc–ghi ngay từ dòng đầu (luật 27).
        assertThat(body)
                .contains(ContactFormPolicy.KHOA_HIEN_DIEN_THOAI)
                .contains(ContactFormPolicy.KHOA_EMAIL_BAT_BUOC)
                .contains(ContactFormPolicy.KHOA_DIEN_THOAI_BAT_BUOC);
    }

    // ═══════════════ T36.6 — chỗ cắm reCAPTCHA ═══════════════
    //
    // ⚠⚠ Nhánh "bật captcha mà thiếu khoá bí mật" ⛔ KHÔNG kiểm được ở tầng này nữa, và đó là hệ
    //    quả CÓ CHỦ ĐÍCH của một lượt sửa: bộ khoá `site.recaptcha.*` (trước là
    //    `site.contact.recaptcha.*`) đã bị GỠ khỏi
    //    migration (`PortalSettingsReadTest` đỏ đúng — ⛔ không dòng mã nào của cổng đọc chúng).
    //    Bật công tắc ấy bằng `UPDATE settings` nay tác động 0 hàng, nên một bài kiểm HTTP sẽ
    //    XANH vì captcha đang tắt — tức là xanh vì một lý do KHÁC với lý do nó khẳng định.
    //
    // ⇒ Nhánh ấy chuyển sang `InboundSubmissionGateTest` (unit, mock `SettingPort`), nơi nó là logic
    //   thuần và kiểm được đúng cái nó nói. ⛔ Giữ lại ở đây là giữ một bài kiểm nói dối.

    /**
     * ⛔⛔ Khoá <b>bí mật</b> ⛔ không bao giờ được có mặt ở {@code settings}.
     *
     * <p>{@code /api/v1/public/site-config} là endpoint ⛔ <b>không cần đăng nhập</b> và nó trả về
     * <i>toàn bộ</i> nhóm SITE + COMPANY. Một credential lọt vào đó là phát nó cho bất kỳ ai mở
     * cổng. Bài này canh ở tầng <b>dữ liệu thật đang phục vụ</b>, ⛔ không ở tầng lời dặn.
     */
    @Test
    @DisplayName("⛔⛔ Endpoint cấu hình công khai ⛔ KHÔNG chứa khoá bí mật nào")
    void cauHinhCongKhaiKhongLoKhoaBiMat() {
        String body = http.getForEntity(CAU_HINH, String.class).getBody();

        assertThat(body).as("⚠ vế chống tập rỗng (luật 7)").isNotNull().contains("site.");
        assertThat(body.toLowerCase())
                .as("⛔ Bảng `settings` nhóm SITE đi THẲNG ra endpoint này. Quy tắc 13: credential "
                        + "bên thứ 3 ⛔ không log, ⛔ không trả ra API, ⛔ không nằm trong bản export.")
                .doesNotContain("secret")
                .doesNotContain("private-key")
                .doesNotContain("password");

        // ⚠⚠ Vế thứ hai đo ở LƯỢC ĐỒ — nhưng CHỈ trong hai nhóm ra tới cổng.
        //
        // Bản đầu của bài này quét toàn bảng và ĐỎ GIẢ ngay lượt chạy đầu: `security.password
        // .min-length`, `.require-letter-and-digit`, `.max-age-days` khớp chữ "password" trong khi
        // chúng là THAM SỐ CHÍNH SÁCH, ⛔ không phải credential — và chúng ở nhóm SECURITY nên ⛔
        // không có đường nào ra endpoint công khai.
        //
        // ⇒ Đây lại đúng hình dạng đã trả giá nhiều lần: một bộ canh bắt theo CHỮ, khớp trúng thứ
        //   nó ⛔ không định bắt. Bất biến thật ⛔ không phải "⛔ không có khoá nào tên là secret",
        //   mà là "⛔ không credential nào nằm trong nhóm ĐI RA CỔNG".
        Integer soKhoaBiMatRaCong = jdbc.queryForObject(
                "SELECT count(*) FROM settings "
                        + " WHERE group_code IN ('SITE', 'COMPANY') "
                        + "   AND (setting_key ILIKE '%secret%' OR setting_key ILIKE '%password%' "
                        + "        OR setting_key ILIKE '%credential%' OR setting_key ILIKE '%api-key%' "
                        + "        OR setting_key ILIKE '%private%')",
                Integer.class);
        assertThat(soKhoaBiMatRaCong)
                .as("⛔⛔ Một khoá mang dáng credential trong nhóm SITE/COMPANY là một khoá đang "
                        + "được phát cho bất kỳ ai mở cổng — `siteConfig()` trả về TRỌN hai nhóm ấy.")
                .isZero();

        // ⭐ Vế phân biệt (luật 9): mẫu ILIKE ở trên phải thật sự khớp được thứ gì đó, nếu không thì
        //    số 0 kia là số 0 của một bộ dò đã chết. Nhóm SECURITY CÓ khoá khớp — và chúng đúng chỗ.
        Integer doiChungPhaiTimThay = jdbc.queryForObject(
                "SELECT count(*) FROM settings WHERE setting_key ILIKE '%password%'", Integer.class);
        assertThat(doiChungPhaiTimThay)
                .as("⛔ Mẫu ILIKE ⛔ không khớp một khoá nào trong TOÀN bảng — bộ dò đã chết, và số "
                        + "0 ở khẳng định trên là một xanh giả (§10.80: `strings` của macOS bỏ qua "
                        + "38/52 tệp mà vẫn thoát 0)")
                .isPositive();
    }

    // ─────────────── Tiện ích ───────────────

    private ResponseEntity<String> gui(String email, String dienThoai, String maCaptcha) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String than =
                """
                {
                  "fullName": "Nguyễn Văn A",
                  "email": %s,
                  "phone": %s,
                  "subject": "Phản ánh kênh N4",
                  "content": "Kênh N4 đoạn qua xã bị bồi lắng.",
                  "recaptchaToken": %s
                }
                """
                        .formatted(oChuoi(email), oChuoi(dienThoai), oChuoi(maCaptcha));
        return http.postForEntity(CONG_KHAI, new HttpEntity<>(than, h), String.class);
    }

    private static String oChuoi(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private int soHang() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM contacts WHERE deleted_at IS NULL", Integer.class);
        return n == null ? 0 : n;
    }

    /** ⚠ Sửa thẳng CSDL rồi <b>xoá đệm hai tầng</b>: {@code SettingService} và {@code SiteConfigService}. */
    private void dat(String khoa, String giaTri) {
        jdbc.update("UPDATE settings SET setting_value = ? WHERE setting_key = ?", giaTri, khoa);
        settings.invalidate(khoa);
    }
}
