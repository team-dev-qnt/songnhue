package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.MediaService;
import com.songnhue.content.domain.KhoTep;
import com.songnhue.core.application.attachment.VirusScanHandler;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.JobContext;

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

    @Autowired
    private MediaService media;

    @Autowired
    private VirusScanHandler virusScanHandler;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void ketThuc() {
        AuthContext.clear();
        CmsFixtures.donDep(jdbc);
    }

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
    @DisplayName("⭐⭐ `/photos` vào được không cần đăng nhập, và RỖNG khi chưa chỉ định thư mục")
    void thuVienAnhCongKhaiVaRongKhiChuaCauHinh() {
        ResponseEntity<String> response = http.getForEntity("/api/v1/public/photos", String.class);

        assertThat(response.getStatusCode())
                .as("Khối ảnh trang chủ dựng ở phía máy chủ — 401 ở đây là cả trang chủ hỏng")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"success\":true").contains("\"traceId\"");

        // ⛔ `site.home.photos-folder` chưa đặt trong bộ kiểm ⇒ PHẢI rỗng. Đây là chỗ một bộ ảnh
        //    dự phòng "cho giao diện luôn sống động" sẽ lộ ra (§10.54 — luật 16).
        assertThat(response.getBody())
                .as("Chưa chỉ định thư mục mà vẫn có ảnh nghĩa là ở đâu đó có bộ ảnh mặc định")
                .contains("\"data\":[]");
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

    /**
     * Ảnh công khai trả về <b>nguyên byte</b> — đi hết đường thật: MinIO → service → controller →
     * chuỗi filter → {@code ResponseEnvelopeAdvice} → HTTP.
     *
     * <h2>Vì sao bài này phải tồn tại dù {@code core} đã có bài cho advice</h2>
     *
     * Bài ở {@code core} dùng controller giả lập. Bài này đi qua đúng controller đang chạy trên
     * cổng, đúng chuỗi filter, đúng kho tệp — luật 5: cam kết nằm ở controller/filter thì phải
     * kiểm qua HTTP.
     *
     * <h2>Nó trả món nợ nào</h2>
     *
     * Bài kiểm duy nhất của endpoint này trước đây dùng UUID không tồn tại, nên chỉ đi nhánh 404.
     * Nhánh trả byte chạy lần đầu trên staging và trả <b>500</b>:
     * {@code ClassCastException: ApiResponse cannot be cast to [B}. Mọi ảnh bìa trên cổng hỏng, mà
     * 391 bài kiểm vẫn xanh (§10.52).
     */
    @Test
    @DisplayName("⭐⭐ Ảnh công khai trả NGUYÊN BYTE qua HTTP thật — không bọc envelope")
    void anhCongKhaiTraNguyenByte() {
        dangNhap("cms:media:manage");
        byte[] goc = anhPng(24, 18);
        AttachmentRef tep =
                media.upload(media.createFolder("Ảnh cổng", null).getPublicId(), KhoTep.MEDIA, "bia.png", goc);
        chayBuocQuet(tep.publicId());
        AuthContext.clear();

        // ⚠ Xin byte[] chứ không xin String: xin String thì RestTemplate tự giải mã theo charset và
        //   một thân JSON sai vẫn "đọc được", nên phép so sánh không phân biệt được hai trạng thái.
        ResponseEntity<byte[]> response = http.getForEntity("/api/v1/public/files/" + tep.publicId(), byte[].class);

        assertThat(response.getStatusCode())
                .as("500 ở đây = envelope đang bọc byte[]; 404 = tệp chưa qua bước quét")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody())
                .as(
                        """
                        Thân phải là ĐÚNG byte đã tải lên. Khẳng định "không rỗng" hay "có magic PNG" \
                        đều không đủ: cả hai vẫn xanh nếu ai đó trả nhầm một ảnh khác.""")
                .isEqualTo(goc);
    }

    // ---- Trợ giúp ------------------------------------------------------------

    /** Ảnh PNG thật — magic bytes thật, không phải mấy byte bịa cho qua bộ kiểm định dạng. */
    private static byte[] anhPng(int rong, int cao) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(rong, cao, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không dựng được ảnh cho bài kiểm", e);
        }
    }

    /**
     * Chạy bước quét virus cho tệp vừa tải — worker nền cố ý tắt ở môi trường kiểm thử.
     *
     * <p>Không có bước này thì {@code isDownloadable()} false và endpoint trả 404, tức là bài kiểm
     * lại rơi đúng vào nhánh mà bản cũ đã kiểm rồi.
     */
    private void chayBuocQuet(UUID attachmentPublicId) {
        Long id = jdbc.queryForObject("SELECT id FROM attachments WHERE public_id = ?", Long.class, attachmentPublicId);
        try {
            virusScanHandler.handle(new JobContext(
                    UUID.randomUUID(),
                    "VIRUS_SCAN",
                    "{\"attachmentId\":%d}".formatted(id),
                    null,
                    percent -> {},
                    conTro -> {}));
        } catch (Exception e) {
            throw new IllegalStateException("Bước quét lỗi", e);
        }
    }

    private static void dangNhap(String... quyen) {
        AuthContext.set(new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "public-http-probe",
                "Người kiểm thử",
                1L,
                "/1/",
                Set.of("PROBE"),
                Set.of(quyen),
                false,
                UUID.randomUUID(),
                UUID.randomUUID()));
    }
}
