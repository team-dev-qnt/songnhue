package com.songnhue.app.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.content.application.SiteConfigService;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Tệp vượt trần phải trả <b>413 + SYS-0011</b>, không phải 500 — sự cố staging 30/08/2026.
 *
 * <h2>⚠ Lớp này cố ý dựng một context Spring THỨ HAI. Đây là chi phí có chủ đích</h2>
 *
 * {@code IntegrationTestBase} gom mọi bài kiểm tích hợp về cùng một context để chỉ dựng một lần.
 * Lớp này phá lệ vì trần thật là <b>120MB</b>: không có cách nào vượt nó trong một bài kiểm mà
 * không gửi đi 120MB. Hạ trần xuống 2MB cho riêng context này là cách duy nhất để đi qua đúng
 * nhánh mã cần kiểm.
 *
 * <p>⛔ Và phải đi qua nó THẬT. Gọi thẳng {@code handler.handleUploadTooLarge(ex)} sẽ xanh kể cả
 * khi {@code @RestControllerAdvice} không bao giờ được gọi — mà đó chính là câu hỏi ở đây:
 * {@code MaxUploadSizeExceededException} sinh ra ở {@code DispatcherServlet.checkMultipart},
 * <b>trước khi có handler method nào được chọn</b>. Một bộ bắt ngoại lệ không được gọi trông y hệt
 * một bộ bắt ngoại lệ đúng (quy tắc 7).
 *
 * <p>Trạng thái trước bản vá — đo trên staging, không suy luận:
 *
 * <pre>{@code
 * ERROR ... GlobalExceptionHandler ... "Lỗi không lường trước"
 *   error.type: org.springframework.web.multipart.MaxUploadSizeExceededException
 * }</pre>
 *
 * → người dùng nhận {@code SYS-0001} <i>"Lỗi hệ thống, vui lòng thử lại"</i> cho một tệp chỉ cần
 * nén nhỏ lại, và log ghi ERROR kèm stacktrace cho một lỗi nhập liệu thường ngày.
 */
@TestPropertySource(
        properties = {"spring.servlet.multipart.max-file-size=2MB", "spring.servlet.multipart.max-request-size=3MB"})
class UploadTooLargeResponseTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Test
    @DisplayName("Tệp vượt trần: 413 kèm SYS-0011 và số MB thật, KHÔNG phải 500 SYS-0001")
    void vuotTranTra413ChuKhongPhai500() throws Exception {
        PhienHttp phien = new PhienHttp(http);
        PhienHttp.Phien quanTri =
                phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tran_413", "CONTENT_MANAGER"));

        byte[] anh = anhNhieu(1100, 900);
        assertThat(anh.length)
                .as("ảnh thử phải thật sự vượt trần 2MB của context này")
                .isGreaterThan(2 * 1024 * 1024);

        ResponseEntity<String> phanHoi = phien.dangTep(
                quanTri, "/api/v1/cms/site-config/brand-images/" + SiteConfigService.KEY_HOME_MAP, anh, "qua-to.png");

        assertThat(phanHoi.getStatusCode())
                .as("thân phản hồi: %s", phanHoi.getBody())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        // Khẳng định cả hai chiều: có mã mới, và KHÔNG còn rơi vào lưới an toàn cuối. Chỉ khẳng
        // định vế đầu thì một ngày nào đó cả hai cùng xuất hiện mà bài kiểm vẫn xanh.
        assertThat(phanHoi.getBody()).contains("SYS-0011").doesNotContain("SYS-0001");

        // Số MB trong câu thông báo phải là trần ĐÃ GIẢI của context này (2), không phải một hằng
        // số chép tay trong mã (120) — quy tắc 3.
        assertThat(phanHoi.getBody())
                .as("câu thông báo phải mang đúng trần đang có hiệu lực")
                .contains("(2 MB)");
    }

    /** Nhiễu ngẫu nhiên: một ảnh trơn màu nén xuống vài KB và bài kiểm sẽ không vượt trần nào cả. */
    private static byte[] anhNhieu(int rong, int cao) throws Exception {
        BufferedImage img = new BufferedImage(rong, cao, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(20260830L);
        for (int y = 0; y < cao; y++) {
            for (int x = 0; x < rong; x++) {
                img.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
