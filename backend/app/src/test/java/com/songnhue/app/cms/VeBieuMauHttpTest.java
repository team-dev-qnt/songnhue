package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.VeBieuMauService;
import com.songnhue.core.application.settings.SettingService;

/**
 * <b>Biểu mẫu công khai đòi vé đủ tuổi — T73.9 (ASVS 11.1.2).</b>
 *
 * <p>Trước bản vá, thứ duy nhất chặn một máy gửi biểu mẫu là hạn mức 10 lượt/giờ theo IP (T61.37); một máy gửi ngay
 * khi tải trang — hoặc ⛔ tải trang — đều qua. Mỗi lượt gửi ở đây dùng một IP riêng ({@code PhienHttp} mới), để bài ⛔
 * đỏ vì hạn mức.
 *
 * <p>⚠ Thân yêu cầu KHAI {@code "ve"} tường minh — {@code PhienHttp.dangJson} chỉ tự gắn vé khi thân chưa khai.
 */
class VeBieuMauHttpTest extends IntegrationTestBase {

    private static final String LIEN_HE = "/api/v1/public/contacts";
    private static final String GOP_Y = "/api/v1/public/feedbacks";

    @Autowired
    private TestHttp http;

    @Autowired
    private VeBieuMauService veBieuMau;

    @Autowired
    private SettingService settings;

    @Autowired
    private JdbcTemplate jdbc;

    private static String lienHe(String ve) {
        String truongVe = ve == null ? "null" : "\"" + ve + "\"";
        return ("{\"fullName\":\"Nguyễn Văn A\",\"email\":\"a@example.invalid\",\"subject\":\"Hỏi\","
                        + "\"content\":\"Nội dung T73.9\",\"ve\":%s}")
                .formatted(truongVe);
    }

    @org.junit.jupiter.api.AfterEach
    void don() {
        jdbc.update("DELETE FROM contacts WHERE content = 'Nội dung T73.9'");
    }

    /** Trường SỐ của thân JSON — {@code PhienHttp.giaTriJson} chỉ đọc giá trị chuỗi. */
    private static long soNguyen(String json, String truong) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"" + truong + "\":(-?[0-9]+)").matcher(json);
        assertThat(m.find())
                .as("không thấy trường số %s trong %s", truong, json)
                .isTrue();
        return Long.parseLong(m.group(1));
    }

    private ResponseEntity<String> gui(String duong, String than) {
        return new PhienHttp(http).dangJson(duong, than);
    }

    @Test
    @DisplayName("GET /public/bieu-mau/ve ⇒ 200 · no-store · vé mang dạng v1.<giây>.<khoá>:<hex>")
    void phatVe() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Real-IP", "198.51.100.179");
        ResponseEntity<String> r =
                http.exchange("/api/v1/public/bieu-mau/ve", HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getHeaders().getCacheControl()).contains("no-store");
        assertThat(PhienHttp.giaTriJson(r.getBody(), "ve")).matches("v1\\.[0-9]+\\.[^:.]+:[0-9a-f]{64}");
        assertThat(soNguyen(r.getBody(), "tuoiToiThieuGiay"))
                .as("cổng CHỜ đúng số giây này trước khi gửi (veBieuMau.ts) — thiếu là giao diện ⛔ biết chờ bao lâu")
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("⛔⛔ Liên hệ: thiếu vé · vé VỪA phát · vé bị sửa · vé quá hạn ⇒ 422 CMS-2025; vé đủ tuổi ⇒ 204")
    void lienHeDoiVeDuTuoi() {
        String duTuoi = veBieuMau.phat(Instant.now().minusSeconds(30));
        String biSua = duTuoi.substring(0, duTuoi.length() - 1) + (duTuoi.endsWith("0") ? "1" : "0");

        for (String[] ca : new String[][] {
            {"thiếu vé", null},
            {"vé vừa phát (chưa đủ 3 giây)", veBieuMau.phat()},
            {"vé bị sửa một ký tự", biSua},
            {"vé quá 24 giờ", veBieuMau.phat(Instant.now().minus(Duration.ofHours(25)))}
        }) {
            ResponseEntity<String> r = gui(LIEN_HE, lienHe(ca[1]));
            assertThat(r.getStatusCode().value())
                    .as("[%s] %s", ca[0], r.getBody())
                    .isEqualTo(422);
            assertThat(r.getBody()).as("[%s]", ca[0]).contains("CMS-2025");
        }

        ResponseEntity<String> dung = gui(LIEN_HE, lienHe(duTuoi));
        assertThat(dung.getStatusCode().value())
                .as(
                        "⚠ ĐỐI CHỨNG phải-thành-công — thiếu nó thì một cổng từ chối TẤT CẢ cũng làm vế trên xanh: %s",
                        dung.getBody())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("⛔ Góp ý cũng đi qua cùng cổng: thiếu vé ⇒ 422 CMS-2025")
    void gopYCungDoiVe() {
        ResponseEntity<String> r =
                gui(GOP_Y, "{\"fullName\":\"B\",\"rating\":5,\"content\":\"Góp ý T73.9\",\"ve\":null}");
        assertThat(r.getStatusCode().value()).as("%s", r.getBody()).isEqualTo(422);
        assertThat(r.getBody()).contains("CMS-2025");
    }

    /** Đọc từ settings chứ ⛔ ghi cứng: đặt 0 thì vé VỪA phát cũng qua (T48.7 — giá trị thử ≠ mặc định 3). */
    @Test
    @DisplayName("Tuổi tối thiểu đọc từ settings — đặt 0 ⇒ vé vừa phát được nhận")
    void tuoiToiThieuDocTuSettings() {
        jdbc.update(
                "UPDATE settings SET setting_value = '0' WHERE setting_key = ?", VeBieuMauService.KHOA_GIAY_TOI_THIEU);
        settings.invalidate(VeBieuMauService.KHOA_GIAY_TOI_THIEU);
        try {
            ResponseEntity<String> r = gui(LIEN_HE, lienHe(veBieuMau.phat()));
            assertThat(r.getStatusCode().value()).as("%s", r.getBody()).isEqualTo(204);

            HttpHeaders h = new HttpHeaders();
            h.set("X-Real-IP", "198.51.100.180");
            ResponseEntity<String> ve =
                    http.exchange("/api/v1/public/bieu-mau/ve", HttpMethod.GET, new HttpEntity<>(h), String.class);
            assertThat(soNguyen(ve.getBody(), "tuoiToiThieuGiay"))
                    .as("số giây cổng chờ cũng đọc từ settings — ⛔ chép hằng số 3")
                    .isEqualTo(0L);
        } finally {
            jdbc.update(
                    "UPDATE settings SET setting_value = '3' WHERE setting_key = ?",
                    VeBieuMauService.KHOA_GIAY_TOI_THIEU);
            settings.invalidate(VeBieuMauService.KHOA_GIAY_TOI_THIEU);
        }
    }
}
