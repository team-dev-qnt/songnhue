package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.songnhue.app.testsupport.IntegrationTestBase;

/**
 * Nhóm API công khai, gọi <b>qua HTTP thật</b> — WS-16.
 *
 * <h2>Vì sao bài kiểm này tồn tại riêng</h2>
 *
 * Mọi bài kiểm CMS tới giờ gọi thẳng vào service, nên chưa có gì đi qua <i>chuỗi filter</i>: hạn mức
 * tần suất, xác thực, envelope, {@code traceId}. Đó là nợ #65. Ở đây trả phần quan trọng nhất của
 * nó — phần liên quan tới an ninh — vì nhóm công khai là nhóm duy nhất <b>cố tình</b> không có xác
 * thực, và một sai sót ở đó lộ ra Internet chứ không lộ ra mạng nội bộ.
 *
 * <p>Ba câu hỏi:
 *
 * <ol>
 *   <li>Endpoint công khai có thật sự vào được khi <b>không</b> có token không? (nếu không thì
 *       {@code @PublicEndpoint} chỉ là một chú thích)
 *   <li>Endpoint quản trị có thật sự bị chặn không? (401, và phải là 401 chứ không phải 404 — 404
 *       nghĩa là đường dẫn sai, tức là bài kiểm đang kiểm nhầm chỗ)
 *   <li>Hai nhóm có đếm hạn mức bằng <b>hai bucket khác nhau</b> không?
 * </ol>
 */
class PublicHttpTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("⭐ Endpoint công khai vào được mà KHÔNG cần đăng nhập")
    void congKhaiVaoDuocKhongCanDangNhap() {
        ResponseEntity<String> response = http.getForEntity("/api/v1/public/site-config", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("đi qua ResponseEnvelopeAdvice nên phải có đủ envelope và traceId (DoD #9)")
                .contains("\"success\":true")
                .contains("\"traceId\"")
                .contains("site.name");
    }

    @Test
    @DisplayName("⛔ Endpoint quản trị vẫn bị chặn — 401, không phải 404")
    void quanTriVanBiChan() {
        ResponseEntity<String> response = http.getForEntity("/api/v1/cms/menus/HEADER", String.class);

        assertThat(response.getStatusCode())
                .as("404 ở đây nghĩa là đường dẫn sai, tức là bài kiểm đang chứng minh nhầm chuyện")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("AUTH-0002");
    }

    @Test
    @DisplayName("⭐⭐ Cổng công khai và API quản trị đếm hạn mức bằng HAI bucket khác nhau")
    void haiBucketHanMucTachRoi() {
        String tranCongKhai = http.getForEntity("/api/v1/public/articles", String.class)
                .getHeaders()
                .getFirst("X-RateLimit-Limit");
        String tranQuanTri = http.getForEntity("/api/v1/cms/menus/HEADER", String.class)
                .getHeaders()
                .getFirst("X-RateLimit-Limit");

        assertThat(tranCongKhai)
                .as(
                        """
                        Gộp chung thì một con bọ tìm kiếm quét cổng sẽ tiêu hết hạn mức, và người đang soạn \
                        bài trong màn hình quản trị nhận SYS-0002 — cả hai bucket đều đếm theo IP, mà cả \
                        Công ty ra Internet qua một IP NAT.""")
                .isEqualTo("300");
        assertThat(tranQuanTri).isEqualTo("100");
    }

    @Test
    @DisplayName("⭐⭐ POST đếm lượt xem KHÔNG bị CSRF chặn — không có phiên thì không có gì để mượn")
    void demLuotXemKhongBiCsrfChan() {
        ResponseEntity<String> response =
                http.postForEntity("/api/v1/public/articles/bat-ky/views", null, String.class);

        assertThat(response.getStatusCode())
                .as(
                        """
                        CSRF là tấn công MƯỢN PHIÊN của nạn nhân. Endpoint công khai không đọc phiên nào, \
                        nên không có gì để mượn. Ngược lại, chặn ở đây thì trình duyệt của khách vãng lai \
                        không có cookie CSRF để gửi kèm và bộ đếm lượt xem KHÔNG BAO GIỜ chạy được — lỗi \
                        đã xảy ra thật ở lượt chạy thử đầu tiên của WS-16, mà bài kiểm gọi thẳng service \
                        không bắt được.""")
                .isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("⛔ POST tới nhóm quản trị VẪN bị chặn — ngoại lệ CSRF chỉ áp cho /public")
    void quanTriVanCanCsrf() {
        ResponseEntity<String> response = http.postForEntity("/api/v1/cms/banners/reorder", null, String.class);

        assertThat(response.getStatusCode())
                .as("bỏ nhầm phạm vi ngoại lệ là gỡ CSRF khỏi toàn bộ hệ thống mà không ai thấy")
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Bài không tồn tại trả 404 đúng envelope, không phải trang lỗi của servlet")
    void baiKhongTonTaiTra404DungEnvelope() {
        ResponseEntity<String> response =
                http.getForEntity("/api/v1/public/articles/khong-co-bai-nao-ten-nay", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("SYS-0004").contains("\"traceId\"");
    }

    @Test
    @DisplayName("⛔ Mã tệp không tồn tại trả 404, không lộ ra kho có gì")
    void maTepKhongTonTaiTra404() {
        ResponseEntity<String> response =
                http.getForEntity("/api/v1/public/files/00000000-0000-0000-0000-000000000000", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
