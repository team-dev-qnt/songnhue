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
 * <b>T61.37 — biểu mẫu công khai ⛔ được biến máy chủ thư của Công ty thành máy phát tán.</b>
 *
 * <h2>Trạng thái trước bản vá, đo 16/09/2026</h2>
 *
 * <ul>
 *   <li>Email ⛔ kiểm định dạng ở bất kỳ tầng nào ⇒ {@code "x"} thành <b>người nhận thư xác nhận</b>.
 *   <li>Tên/tiêu đề ⛔ có trần ⇒ 300 ký tự đi vào {@code varchar(255)} và ra <b>500</b>, ⛔ phải 422.
 *   <li>Hạn mức là {@code PUBLIC} 300 lượt/<b>phút</b> — hạn mức của người ĐỌC — ⇒ 18.000 lượt/giờ,
 *       mỗi lượt sinh một thư xác nhận cộng một thư cho <b>từng</b> cán bộ giữ quyền xử lý.
 *   <li>Thư xác nhận bật mặc định trong khi reCAPTCHA <b>tắt</b> mặc định (khoá chờ G13).
 * </ul>
 *
 * <p>⚠ Bài này đi bằng HTTP vì cả bốn cam kết nằm ở tầng controller/filter (luật 5), và mỗi lớp
 * kiểm mang <b>IP riêng</b> ({@code PhienHttp.dangJson}) — hạn mức mới là 10 lượt/giờ mỗi IP.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BieuMauCongKhaiChongLamDungTest extends IntegrationTestBase {

    private static final String CONG_KHAI = "/api/v1/public/contacts";

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM notification_recipients r USING notifications n "
                + "WHERE n.id = r.notification_id AND n.event_type = 'CONTACT_RECEIVED'");
        jdbc.update("DELETE FROM notifications WHERE event_type = 'CONTACT_RECEIVED'");
        jdbc.update("DELETE FROM jobs WHERE job_type LIKE 'CMS_CONTACT%'");
        jdbc.update("DELETE FROM contacts");
    }

    private static String than(String email, String hoTen) {
        return """
                {"fullName": "%s", "email": "%s", "phone": "0243000000",
                 "subject": "Phản ánh", "content": "Kênh N1 bị sạt lở khoảng 20m."}"""
                .formatted(hoTen, email);
    }

    @Test
    @DisplayName("⛔⛔ Email sai định dạng bị TỪ CHỐI — trước đây 'x' thành người nhận thư xác nhận")
    void emailSaiDinhDangBiTuChoi() {
        ResponseEntity<String> tl = new PhienHttp(http).dangJson(CONG_KHAI, than("x", "Nguyễn Văn A"));

        assertThat(tl.getStatusCode()).as("thân: %s", tl.getBody()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("⛔ Tên 300 ký tự ra 4xx chứ ⛔ phải 500 — varchar(255) ⛔ được là nơi phát hiện lỗi")
    void tenQuaDaiRaLoiNguoiDungDocDuoc() {
        ResponseEntity<String> tl = new PhienHttp(http).dangJson(CONG_KHAI, than("a@example.invalid", "N".repeat(300)));

        assertThat(tl.getStatusCode().value()).as("thân: %s", tl.getBody()).isBetween(400, 499);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("⛔⛔ Hạn mức đường GỬI là 10 lượt/giờ mỗi IP — ⛔ dùng chung 300/phút của đường ĐỌC")
    void hanMucDuongGuiTachKhoiDuongDoc() {
        PhienHttp mot = new PhienHttp(http);
        int soNhan = 0;
        HttpStatus cuoi = null;
        for (int i = 0; i < 12; i++) {
            ResponseEntity<String> tl = mot.dangJson(CONG_KHAI, than("a%d@example.invalid".formatted(i), "Người gửi"));
            cuoi = HttpStatus.valueOf(tl.getStatusCode().value());
            if (cuoi == HttpStatus.NO_CONTENT) {
                soNhan++;
            }
        }
        assertThat(soNhan).as("đúng 10 lượt đầu được nhận").isEqualTo(10);
        assertThat(cuoi).as("lượt thứ 11 trở đi bị chặn").isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // ⭐ Đối chứng: một IP KHÁC vẫn gửi được ⇒ thứ vừa đo là hạn mức theo IP, ⛔ phải một cánh cửa đóng hẳn.
        assertThat(new PhienHttp(http)
                        .dangJson(CONG_KHAI, than("khac@example.invalid", "Người khác"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("⛔⛔ reCAPTCHA chưa bảo vệ ⇒ ⛔ đặt việc gửi thư xác nhận, mà bản ghi VẪN lưu")
    void chuaCoCaptchaThiKhongGuiThuXacNhan() {
        ResponseEntity<String> tl =
                new PhienHttp(http).dangJson(CONG_KHAI, than("nguoidan@example.invalid", "Nguyễn Văn A"));

        assertThat(tl.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contacts", Integer.class))
                .as("người dân vẫn gửi được — ⛔ đổi một lỗ spam lấy một cánh cửa đóng")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM jobs WHERE job_type = 'CMS_CONTACT_ACK_MAIL'", Integer.class))
                .as("thư ra NGOÀI mới là thứ bị chặn")
                .isZero();
    }
}
