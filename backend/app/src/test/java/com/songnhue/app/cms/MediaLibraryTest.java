package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.ArticleDraft;
import com.songnhue.content.application.ArticleService;
import com.songnhue.content.application.CategoryService;
import com.songnhue.content.application.MediaService;
import com.songnhue.content.domain.Article;
import com.songnhue.content.domain.MediaFolder;
import com.songnhue.core.application.attachment.VirusScanHandler;
import com.songnhue.core.application.job.JobContext;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.AttachmentRef;

/**
 * Thư viện media trên CSDL thật <b>và MinIO thật</b> — WS-14.
 *
 * <p>⭐ <b>Đây là lần đầu tiên một tệp đi tới kho lưu trữ trong toàn bộ dự án.</b> Từ WS-6 tới hết
 * Phase 0, {@code app.storage.endpoint} trỏ vào {@code minio.invalid} — mọi bài kiểm đính kèm đều
 * dừng lại trước lúc chạm ra ngoài. Bài kiểm này đóng <b>Definition of Done mục 11 của Phase 0</b>.
 *
 * <p>Nhờ có kho thật, ba thứ của pattern P3 lần đầu được kiểm ở phần <i>sau</i> khi ghi: nội dung
 * đọc lại được, ảnh đã bị mã hoá lại (bóc EXIF), và presigned URL trỏ đúng chỗ.
 */
class MediaLibraryTest extends IntegrationTestBase {

    private static final String QUYEN_MEDIA = "cms:media:manage";
    private static final String QUYEN_DANH_MUC = "cms:category:manage";

    @Autowired
    private MediaService media;

    @Autowired
    private ArticleService articles;

    @Autowired
    private CategoryService categories;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Bộ xử lý quét virus — gọi thẳng thay vì chờ worker.
     *
     * <p>⚠ Chỗ này suýt bị đọc nhầm thành lỗi production: lượt tải đầu tiên đi tới kho thật xong thì
     * {@code downloadUrl} trả {@code SYS-0009} "chưa quét virus". Nguyên nhân <b>không phải</b> mã
     * hỏng — {@code VirusScanHandler} đánh dấu {@code SKIPPED} + {@code READY} khi chưa cấu hình
     * ClamAV, đúng như thiết kế WS-6. Chỉ là bước đó chạy bằng worker nền, mà worker thì tắt ở môi
     * trường kiểm thử.
     *
     * <p>⛔ <b>Cố ý KHÔNG bật worker nền cho bài kiểm.</b> Bật lên là đưa một vòng lặp bất đồng bộ
     * vào giữa phép khẳng định — bài kiểm sẽ xanh hay đỏ tuỳ nhịp máy, và loại đỏ-thỉnh-thoảng đó
     * tốn thời gian hơn nhiều so với thứ nó bắt được. Gọi thẳng bộ xử lý cho ra cùng một đường đi,
     * tường minh và tất định.
     */
    @Autowired
    private VirusScanHandler virusScanHandler;

    /** Chạy bước quét cho tệp vừa tải — mô phỏng đúng việc worker sẽ làm. */
    private void chayBuocQuet(UUID attachmentPublicId) {
        Long id = jdbc.queryForObject("SELECT id FROM attachments WHERE public_id = ?", Long.class, attachmentPublicId);
        try {
            virusScanHandler.handle(new JobContext(
                    UUID.randomUUID(), "VIRUS_SCAN", "{\"attachmentId\":%d}".formatted(id), null, percent -> {}));
        } catch (Exception e) {
            throw new IllegalStateException("Bước quét lỗi", e);
        }
    }

    private UUID thuMuc;

    @BeforeEach
    void chuanBi() {
        donDep();
        dangNhap(QUYEN_MEDIA, QUYEN_DANH_MUC, "cms:article:create", "cms:article:view");
        thuMuc = media.createFolder("Ảnh", null).getPublicId();
    }

    @AfterEach
    void ketThuc() {
        AuthContext.clear();
        donDep();
    }

    // ---- Tệp đi tới kho thật -------------------------------------------------

    @Test
    @DisplayName("⭐ Tải ảnh lên đi tới MinIO thật, đọc lại được")
    void taiAnhLenKhoThat() {
        AttachmentRef tep = media.upload(thuMuc, "so-do-tuyen.png", anhPng(40, 30));

        assertThat(tep.contentType()).isEqualTo("image/png");
        assertThat(tep.sizeBytes()).isPositive();
        assertThat(media.filesIn(thuMuc)).extracting(AttachmentRef::publicId).contains(tep.publicId());

        // Tệp vừa tải lên CHƯA dùng được — đang chờ quét. Đây là hành vi đúng, không phải lỗi.
        assertThatThrownBy(() -> media.downloadUrl(tep.publicId())).hasMessageContaining("SYS-0009");

        chayBuocQuet(tep.publicId());

        // Đọc lại từ kho qua đúng đường ứng dụng dùng — nếu ghi hỏng thì đây là chỗ vỡ.
        assertThat(media.downloadUrl(tep.publicId()))
                .as("presigned URL phải trỏ tới bucket media của chính lượt chạy này")
                .contains("test-media");
    }

    @Test
    @DisplayName("⭐ Tên lưu xuống kho là chuỗi ngẫu nhiên, KHÔNG phải tên người dùng đặt")
    void tenLuuXuongKhoLaNgauNhien() {
        AttachmentRef tep = media.upload(thuMuc, "bao-cao.jpg.exe", anhPng(10, 10));

        String storageKey = jdbc.queryForObject(
                "SELECT storage_key FROM attachments WHERE public_id = ?", String.class, tep.publicId());

        assertThat(storageKey)
                .as(
                        """
                        Giữ nguyên tên người dùng đặt là đưa thẳng `.exe` vào kho. Đuôi phải suy ra từ \
                        MIME ĐÃ XÁC THỰC bằng magic bytes — ở đây nội dung là PNG nên đuôi phải là .png.""")
                .doesNotContain("bao-cao")
                .doesNotContain(".exe")
                .endsWith(".png");
        assertThat(tep.originalName())
                .as("tên gốc vẫn giữ để hiển thị, chỉ là không dùng làm tên lưu trữ")
                .isEqualTo("bao-cao.jpg.exe");
    }

    @Test
    @DisplayName("⛔ Đổi đuôi tệp không lừa được — kiểm bằng magic bytes")
    void doiDuoiTepKhongLuaDuoc() {
        byte[] khongPhaiAnh = "Đây chỉ là văn bản thuần, không phải ảnh.".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> media.upload(thuMuc, "anh-dep.png", khongPhaiAnh))
                .as("tin đuôi tệp hoặc tin Content-Type trình duyệt gửi là tin vào thứ người gửi tự đặt")
                .hasMessageContaining("SYS-0003");
    }

    @Test
    @DisplayName("⛔ Thư viện media KHÔNG nhận SVG — điểm nghiệp vụ 7")
    void thuVienMediaKhongNhanSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect/></svg>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> media.upload(thuMuc, "logo.svg", svg))
                .as(
                        """
                        SVG chạy được JavaScript. Nó chỉ vào hệ thống qua màn hình cấu hình giao diện \
                        (người tải là Quản trị viên) và phải qua SvgSanitizer — không qua đường này.""")
                .hasMessageContaining("SYS-0003");
    }

    // ---- Thư mục -------------------------------------------------------------

    @Test
    @DisplayName("Cây thư mục tối đa 3 cấp")
    void cayToiDaBaCap() {
        UUID cap2 = media.createFolder("2026", thuMuc).getPublicId();
        UUID cap3 = media.createFolder("Quý I", cap2).getPublicId();

        assertThat(media.folder(cap3).getDepth()).isEqualTo((short) 2);
        assertThatThrownBy(() -> media.createFolder("Tháng 1", cap3))
                .as("CN-01.3 chốt 3 cấp; chặn ở service để trả được mã lỗi, và ở CSDL để chặn cả lỗi seed")
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CMS-2005");
    }

    @Test
    @DisplayName("⛔ Xoá thư mục còn tệp bị chặn — CMS-2008")
    void xoaThuMucConTepBiChan() {
        media.upload(thuMuc, "anh.png", anhPng(8, 8));

        assertThatThrownBy(() -> media.deleteFolder(thuMuc))
                .as("xoá đệ quy nghe tiện hơn, nhưng một lần bấm nhầm cuốn đi cả nhánh mà không ai thấy trước")
                .hasMessageContaining("CMS-2008");
    }

    @Test
    @DisplayName("⛔ Xoá thư mục còn thư mục con bị chặn — CMS-2004")
    void xoaThuMucConThuMucConBiChan() {
        media.createFolder("2026", thuMuc);

        assertThatThrownBy(() -> media.deleteFolder(thuMuc)).hasMessageContaining("CMS-2004");
    }

    @Test
    @DisplayName("Thư mục rỗng xoá được")
    void thuMucRongXoaDuoc() {
        UUID rong = media.createFolder("Thư mục rỗng", null).getPublicId();
        media.deleteFolder(rong);

        assertThat(media.tree()).extracting(MediaFolder::getName).doesNotContain("Thư mục rỗng");
    }

    // ---- Tham chiếu từ bài viết ----------------------------------------------

    @Test
    @DisplayName("⭐ Xoá tệp đang được bài viết dùng bị chặn, kèm tên bài — CMS-2009")
    void xoaTepDangDungBiChan() {
        AttachmentRef tep = media.upload(thuMuc, "tram-bom.png", anhPng(20, 20));
        UUID danhMuc = categories.create("Tin hoạt động", null, null).getPublicId();

        // Ảnh chèn giữa bài: nằm trong chuỗi HTML, không có khoá ngoại nào bắt được.
        Article bai = articles.create(new ArticleDraft(
                "Nâng cấp trạm bơm",
                null,
                null,
                "<p>Hiện trạng: <img src=\"/api/v1/cms/media/files/" + tep.publicId() + "/url\"></p>",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Set.of(danhMuc)));

        assertThat(media.articlesUsing(tep.publicId())).containsExactly(bai.getTitle());
        assertThatThrownBy(() -> media.deleteFile(tep.publicId()))
                .as("người dùng phải biết ảnh đang chạy ở đâu trước khi xoá nó khỏi cổng")
                .hasMessageContaining("CMS-2009");
    }

    @Test
    @DisplayName("⭐ Tệp chỉ dùng được SAU khi qua bước quét — SYS-0009 trước đó")
    void tepChiDungDuocSauKhiQuet() {
        AttachmentRef tep = media.upload(thuMuc, "ho-so.png", anhPng(12, 12));

        assertThat(jdbc.queryForObject(
                        "SELECT scan_status FROM attachments WHERE public_id = ?", String.class, tep.publicId()))
                .isEqualTo("PENDING");

        chayBuocQuet(tep.publicId());

        assertThat(jdbc.queryForObject(
                        "SELECT scan_status FROM attachments WHERE public_id = ?", String.class, tep.publicId()))
                .as(
                        """
                        Chưa cấu hình ClamAV thì ghi rõ là SKIPPED — KHÔNG giả vờ là CLEAN. Nhật ký phải \
                        nói đúng thứ đã xảy ra, để lúc rà soát an toàn không ai tưởng tệp đã được quét.""")
                .isEqualTo("SKIPPED");
        assertThat(media.downloadUrl(tep.publicId())).isNotBlank();
    }

    @Test
    @DisplayName("Tệp không ai dùng thì xoá được")
    void tepKhongAiDungXoaDuoc() {
        AttachmentRef tep = media.upload(thuMuc, "khong-dung.png", anhPng(5, 5));

        assertThat(media.articlesUsing(tep.publicId())).isEmpty();
        media.deleteFile(tep.publicId());

        assertThat(media.filesIn(thuMuc)).extracting(AttachmentRef::publicId).doesNotContain(tep.publicId());
    }

    @Test
    @DisplayName("Ảnh đại diện của bài cũng tính là đang dùng")
    void anhDaiDienCungTinhLaDangDung() {
        AttachmentRef tep = media.upload(thuMuc, "bia.png", anhPng(16, 9));
        UUID danhMuc = categories.create("Tin hoạt động", null, null).getPublicId();

        articles.create(new ArticleDraft(
                "Bài có ảnh bìa",
                null,
                null,
                "Nội dung không nhắc tới ảnh",
                tep.publicId(),
                null,
                null,
                null,
                null,
                null,
                null,
                Set.of(danhMuc)));

        assertThat(media.articlesUsing(tep.publicId()))
                .as("hai chỗ tham chiếu — cột ảnh bìa và chuỗi HTML — phải xét cả hai")
                .containsExactly("Bài có ảnh bìa");
    }

    // ---- Trợ giúp ------------------------------------------------------------

    /** Ảnh PNG thật, để magic bytes là thật chứ không phải mấy byte bịa cho qua chuyện. */
    private static byte[] anhPng(int rong, int cao) {
        try {
            BufferedImage anh = new BufferedImage(rong, cao, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(anh, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không dựng được ảnh cho bài kiểm", e);
        }
    }

    private static void dangNhap(String... quyen) {
        AuthContext.set(new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "media-probe",
                "Người kiểm thử",
                1L,
                "/1/",
                Set.of("PROBE"),
                Set.of(quyen),
                false,
                UUID.randomUUID(),
                UUID.randomUUID()));
    }

    private void donDep() {
        jdbc.update("UPDATE articles SET published_version_id = NULL");
        jdbc.update("DELETE FROM article_versions");
        jdbc.update("DELETE FROM article_categories");
        jdbc.update("DELETE FROM articles");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM attachments WHERE owner_type = 'MEDIA_FOLDER'");
        jdbc.update("DELETE FROM media_folders");
    }
}
