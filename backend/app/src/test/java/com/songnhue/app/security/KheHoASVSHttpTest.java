package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>T61.40 — vá nhóm khe hở ASVS đo lại được ngày 16/09/2026.</b>
 *
 * <p>Mỗi bài dưới đây tương ứng một dòng của {@code docs/bao-mat/asvs-l1-tu-danh-gia.md} §16.3–16.4,
 * và cả bốn đều đi qua HTTP vì chúng nằm ở tầng controller / exception handler (luật 5).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KheHoASVSHttpTest extends IntegrationTestBase {

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phienHttp;
    private PhienHttp.Phien phien;

    @BeforeAll
    void dangNhap() {
        phienHttp = new PhienHttp(http);
        phien = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6140"));
    }

    @Test
    @DisplayName("⛔⛔ ASVS 2.1.2 — mật khẩu > 72 BYTE ra lỗi đọc được, ⛔ phải 500 (chữ có dấu chạm ngưỡng rất sớm)")
    void matKhauVuot72ByteKhongConRa500() {
        // 30 chữ cái có dấu = 60–90 byte UTF-8 — đúng thứ người dùng gõ khi làm theo lời khuyên "đặt dài".
        String matKhauDaiTiengViet = "Đườngthuỷlợisôngnhuệmậtkhẩurấtdàiquá2026phảivượtbảymươihaibyte";
        assertThat(matKhauDaiTiengViet.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .as("chống tập rỗng: chuỗi thử phải THỰC SỰ vượt 72 byte")
                .isGreaterThan(PasswordPolicyService.TRAN_BYTE);

        ResponseEntity<String> tl = phienHttp.goi(
                phien,
                HttpMethod.POST,
                "/api/v1/auth/change-password",
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}"
                        .formatted(PhienHttp.MAT_KHAU, matKhauDaiTiengViet));

        assertThat(tl.getStatusCode().value())
                .as("500 = BCrypt ném 'password cannot be more than 72 bytes': %s", tl.getBody())
                .isEqualTo(422);
        assertThat(tl.getBody()).contains("MAX_BYTES_72");
    }

    @Test
    @DisplayName("⛔ ASVS 14.5.1 — sai ĐỘNG TỪ trả 405 (SYS-0013), ⛔ gộp vào 400 'dữ liệu không hợp lệ'")
    void saiDongTuTra405() {
        ResponseEntity<String> tl = phienHttp.goi(phien, HttpMethod.PUT, "/api/v1/auth/logout", "{}");

        assertThat(tl.getStatusCode().value()).as("%s", tl.getBody()).isEqualTo(405);
        assertThat(tl.getBody()).contains("SYS-0013");
    }
}
