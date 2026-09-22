package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.content.application.MediaService;
import com.songnhue.content.domain.KhoTep;
import com.songnhue.core.application.attachment.VirusScanHandler;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.JobContext;

/**
 * <b>Phát video nhúng trong bài — 302 sang kho đối tượng</b> (T84.12).
 *
 * <h2>Vì sao endpoint này ⛔ phát byte qua ứng dụng</h2>
 *
 * Đo trên kho 22/09/2026: {@code Accept-Ranges}/{@code ResourceRegion}/{@code HttpRange} = <b>0 kết
 * quả toàn backend</b>; {@code spring.threads.virtual} ⛔ bật; Tomcat 200 luồng nền tảng; trần
 * video <b>120MB</b>. Phát qua ứng dụng ⇒ người xem <b>⛔ tua được</b>, và mỗi lượt xem giữ một
 * luồng suốt <i>thời gian PHÁT</i>. MinIO có sẵn cả hai.
 *
 * <h2>⛔⛔ Bài này PHẢI khẳng định nhánh THÀNH CÔNG</h2>
 *
 * {@code ResponseEntity<Void>} ⛔ có thân, nên rất dễ viết một lớp kiểm chỉ đi bốn nhánh 404 rồi
 * tưởng đã phủ. Đó đúng là §10.52: bài kiểm cũ của {@code /public/files} chỉ đi nhánh 404 nên ảnh
 * cổng <b>chưa từng ra một byte</b> suốt nhiều tuần mà ⛔ ai thấy.
 */
class VideoCongKhaiHttpTest extends IntegrationTestBase {

    /**
     * Một MP4 tối thiểu — {@code FileValidator} nhận diện bằng {@code ftyp} ở <b>offset 4</b>.
     *
     * <p>⚠ ⛔ Dùng chuỗi bất kỳ: magic bytes là chốt chặn thật, nên một tệp "giả vờ là mp4" bị từ
     * chối ngay lúc tải lên và bài kiểm đỏ vì lý do ⛔ liên quan.
     */
    private static final byte[] MP4 = mp4ToiThieu();

    private static byte[] mp4ToiThieu() {
        byte[] b = new byte[64];
        b[0] = 0;
        b[1] = 0;
        b[2] = 0;
        b[3] = 0x20;
        System.arraycopy("ftypisom".getBytes(StandardCharsets.US_ASCII), 0, b, 4, 8);
        return b;
    }

    private static final byte[] PNG = anhPngToiThieu();

    private static byte[] anhPngToiThieu() {
        // PNG 1×1 — dùng làm vế PHÂN BIỆT: cùng kho `MEDIA`, cùng `MEDIA_FOLDER`, khác `contentType`.
        return java.util.Base64.getDecoder()
                .decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    @Autowired
    private TestHttp http;

    @Autowired
    private MediaService media;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VirusScanHandler virusScanHandler;

    private UUID thuMuc;

    @BeforeEach
    void chuanBi() {
        CmsFixtures.donDep(jdbc);
        AuthContext.set(new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "probe-video",
                "Người kiểm thử video",
                1L,
                "/1/",
                Set.of("PROBE"),
                Set.of("cms:media:manage"),
                false,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null));
        thuMuc = media.createFolder("Video", null).getPublicId();
    }

    @AfterEach
    void ketThuc() {
        AuthContext.clear();
        CmsFixtures.donDep(jdbc);
    }

    private UUID taiLen(String ten, byte[] noiDung) {
        AttachmentRef ref = media.upload(thuMuc, KhoTep.MEDIA, ten, noiDung);
        chayBuocQuet(ref.publicId());
        return ref.publicId();
    }

    /** Chạy bước quét virus đồng bộ — cùng khuôn `ArticleAttachmentTest.chayBuocQuet`. */
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

    private String duong(UUID id) {
        return "/api/v1/public/videos/" + id;
    }

    @Test
    @DisplayName("⭐⭐ Video đã quét xong ⇒ 302 + Location do kho KÝ, byte tệp ⛔ đi qua ứng dụng, no-store")
    void videoTra302SangKho() {
        UUID video = taiLen("phong-su.mp4", MP4);

        ResponseEntity<byte[]> ra = http.getForEntity(duong(video), byte[].class);

        assertThat(ra.getStatusCode())
                .as("200 ở đây nghĩa là byte đang đi QUA ứng dụng — đúng thứ endpoint này sinh ra để tránh")
                .isEqualTo(HttpStatus.FOUND);

        String location = ra.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location).as("302 mà ⛔ `Location` là một phản hồi chết").isNotBlank();
        assertThat(location)
                .as("URL phải do kho ký — thiếu chữ ký thì MinIO trả 403 và video im lặng ⛔ chạy")
                .contains("X-Amz-Signature=");
        assertThat(location)
                .as("⛔ ký `Content-Disposition` ⇒ trình duyệt PHÁT thay vì tải về (tenGoi = null)")
                .doesNotContain("response-content-disposition");

        // ⚠⚠ Thân ⛔ RỖNG, và khẳng định đầu của tôi ("isNullOrEmpty") ĐỎ vì điều đó:
        //   `ResponseEnvelopeAdvice` bọc CẢ phản hồi 302 ⇒ thân là `{"success":true,"traceId":…}`,
        //   45 byte. Vô hại (trình duyệt bỏ qua thân của 302 và đi theo `Location`), nhưng nó có
        //   nghĩa là phép so "rỗng" ⛔ đo đúng thứ cần đo.
        //
        //   Bất biến THẬT là: **byte của TỆP ⛔ đi qua ứng dụng**. ⇒ So với kích thước tệp và với
        //   magic bytes — một bản dựng phát video qua `PhatTepTrucTiep` sẽ trả 64 byte MP4 mở đầu
        //   bằng `ftyp`, và cả hai khẳng định dưới đây bắt được.
        byte[] than = ra.getBody() == null ? new byte[0] : ra.getBody();
        assertThat(than.length)
                .as("thân dài bằng cỡ tệp ⇒ byte ĐANG đi qua ứng dụng, đúng thứ endpoint này tránh")
                .isLessThan(256);
        assertThat(new String(than, StandardCharsets.UTF_8))
                .as("chỉ được là envelope, ⛔ phải nội dung tệp")
                .doesNotContain("ftyp");

        // ⛔⛔ `no-store` là vế BẮT BUỘC: đích hết hạn sau TTL_VIDEO, nên một lượt 302 nằm lại trong
        //    đệm lâu hơn thế trỏ vào một URL mà MinIO trả 403 ⇒ video vỡ ⛔ để lại dấu vết nào ở
        //    máy chủ — không log, không lỗi, chỉ một ô đen giữa bài.
        assertThat(ra.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    @DisplayName("⛔ ẢNH trong cùng thư mục ⇒ 404 — vế phân biệt của `chỉ video/*`")
    void anhTrongCungThuMucThi404() {
        UUID anh = taiLen("so-do.png", PNG);

        // ⚠ Đây là vế quan trọng nhất của lớp này. `LOAI_TEP_CONG_KHAI` CÓ `MEDIA_FOLDER`, nên ảnh
        //   đi qua đủ ba phép lọc — thứ duy nhất chặn nó là phép kiểm `contentType`. ⛔ Có bài này
        //   thì nới hàm ra cho mọi loại vẫn xanh, và endpoint thành một VÒI ĐÚC presigned URL cho
        //   toàn bộ tệp công khai (lật ngược §10.1 trong im lặng).
        assertThat(http.getForEntity(duong(anh), byte[].class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⛔ Tệp kho TAI_LIEU ⇒ 404 — loại chủ sở hữu ⛔ nằm trong danh sách công khai")
    void tepKhoTaiLieuThi404() {
        byte[] pdf = "%PDF-1.4\ntrailer\n<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        AttachmentRef ref = media.upload(thuMuc, KhoTep.TAI_LIEU, "cong-van.pdf", pdf);
        chayBuocQuet(ref.publicId());

        assertThat(http.getForEntity(duong(ref.publicId()), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⛔ Video CHƯA quét xong ⇒ 404 — ⛔ phát một tệp chưa ai kiểm")
    void videoChuaQuetThi404() {
        // ⛔ Gọi `virusScanHandler` ⇒ tệp ở trạng thái UPLOADING.
        UUID chuaQuet = media.upload(thuMuc, KhoTep.MEDIA, "chua-quet.mp4", MP4).publicId();

        assertThat(http.getForEntity(duong(chuaQuet), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⛔ UUID ⛔ tồn tại ⇒ 404 trần")
    void uuidLaThi404() {
        assertThat(http.getForEntity(duong(UUID.randomUUID()), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⛔⛔ Route Handler của cổng PHẢI khai `redirect: 'manual'` — thứ duy nhất giữ 302")
    void congPhaiDeLot302() throws Exception {
        // ⚠⚠ Cổng proxy `/api/v1/*` qua một Route Handler của Next, ⛔ qua nginx. Mất dòng
        //   `redirect: 'manual'` thì Next TỰ ĐI THEO 302 và kéo toàn bộ byte video qua tiến trình
        //   Node — đúng thứ endpoint này sinh ra để tránh, và ⛔ một lỗi nào báo: trang vẫn chạy,
        //   video vẫn phát, chỉ có máy chủ Next gánh thêm 120MB mỗi lượt xem.
        //
        // ⛔ Canh ở đây chứ ⛔ ở bộ kiểm frontend: đây là chỗ hậu quả hiện ra, và một bài Java đọc
        //    tệp TS là cách duy nhất buộc hai phía đi cùng nhau (luật 14).
        // ⚠ Surefire chạy với cwd = thư mục MODULE (`backend/app`), ⛔ phải gốc kho. Đi ngược lên
        //   tới khi thấy `frontend/` thay vì đếm tay số `../` — đếm tay là thứ hỏng lặng lẽ ngày
        //   ai đó chuyển bài kiểm sang module khác.
        Path goc = Path.of("").toAbsolutePath();
        while (goc != null && !Files.isDirectory(goc.resolve("frontend"))) {
            goc = goc.getParent();
        }
        assertThat(goc)
                .as("⛔ tìm thấy gốc kho — bài kiểm này đọc một tệp TS của cổng")
                .isNotNull();
        Path route = goc.resolve("frontend/public-web/src/app/api/v1/[...path]/route.ts");
        assertThat(Files.exists(route))
                .as("đường dẫn đổi ⇒ bài này thành một bộ canh CHẾT, phải sửa lại đường dẫn ⛔ xoá bài")
                .isTrue();

        String nguon = Files.readString(route);
        assertThat(nguon).contains("redirect: 'manual'");
        assertThat(nguon)
                .as("`Location` ⛔ được nằm trong danh sách header bị bỏ — ⛔ thì 302 mất đích")
                .doesNotContain("'location'");
    }
}
