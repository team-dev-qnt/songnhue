package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.ArticleDraft;
import com.songnhue.content.application.ArticleService;
import com.songnhue.content.application.CategoryService;
import com.songnhue.content.application.PublicArticleDetail;
import com.songnhue.content.application.PublicArticleRow;
import com.songnhue.content.application.PublicPortalService;
import com.songnhue.content.application.ScheduledPublishScanner;
import com.songnhue.content.application.ViewCountService;
import com.songnhue.content.domain.Article;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.core.application.attachment.AttachmentService;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.AttachmentContent;

/**
 * Cổng công khai — WS-16.
 *
 * <p><b>Bài kiểm này canh một ranh giới, không canh một tính năng.</b> Sau
 * {@code PublicPortalService} không còn tầng phân quyền nào: người gọi là khách vãng lai. Mọi phép
 * lọc "được xem cái gì" nằm trong chính các truy vấn, nên mỗi phép lọc ở đây có một bài <b>cố tình
 * hỏi thứ không được phép xem</b> — hỏi bằng slug đúng, bằng mã tệp đúng.
 *
 * <p>Bốn thứ dựng ở Phase 0/1 tới nay chưa ai đi qua và được kiểm chứng lần đầu ở đây: hàng đợi việc
 * nền do <i>module nghiệp vụ</i> cắm vào, đếm lượt xem theo lô, job quét bài hẹn giờ, và bucket hạn
 * mức riêng cho nhóm công khai.
 */
class PublicPortalTest extends IntegrationTestBase {

    @Autowired
    private PublicPortalService portal;

    @Autowired
    private ArticleService articles;

    @Autowired
    private CategoryService categories;

    @Autowired
    private AttachmentService attachments;

    @Autowired
    private ViewCountService viewCounts;

    @Autowired
    private ScheduledPublishScanner scanner;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID danhMuc;

    @BeforeEach
    void chuanBi() {
        CmsFixtures.donDep(jdbc);
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'");
        dangNhap(
                "cms:category:manage",
                "cms:article:create",
                "cms:article:update",
                "cms:article:view",
                "cms:article:submit",
                "cms:article:approve",
                "cms:article:publish",
                "cms:article:unpublish");
        danhMuc = categories.create("Tin cổng", null, null).getPublicId();
    }

    @AfterEach
    void ketThuc() {
        AuthContext.clear();
        CmsFixtures.donDep(jdbc);
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'");
    }

    // ---- Cái gì được xem, cái gì không --------------------------------------

    @Test
    @DisplayName("⛔ Bài Nháp hỏi bằng ĐÚNG slug vẫn không xem được")
    void baiNhapKhongXemDuoc() {
        Article bai = articles.create(banThao("Bản nháp chưa ai duyệt"));

        assertThat(portal.article(bai.getSlug()))
                .as(
                        """
                        Biết slug không phải là quyền xem. Nếu chỗ này trả về nội dung thì mọi bài đang \
                        soạn dở đều đọc được từ Internet — chỉ cần đoán đúng đường dẫn.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Bài Chờ duyệt cũng không xem được")
    void baiChoDuyetKhongXemDuoc() {
        Article bai = articles.create(banThao("Bài đang chờ duyệt"));
        articles.execute(bai.getPublicId(), "SUBMIT", null);

        assertThat(portal.article(bai.getSlug())).isEmpty();
    }

    @Test
    @DisplayName("⛔ Bài đã Gỡ trả 404 nhưng dữ liệu còn nguyên")
    void baiDaGoTra404() {
        Article bai = taoBaiDaXuatBan("Bài sẽ bị gỡ");
        articles.execute(bai.getPublicId(), "UNPUBLISH", null);

        assertThat(portal.article(bai.getSlug())).isEmpty();
        assertThat(jdbc.queryForObject(
                        "SELECT published_version_id FROM articles WHERE slug = ?", Long.class, bai.getSlug()))
                .as("gỡ bài là rút khỏi công khai, KHÔNG phải xoá — đăng lại không cần duyệt lại")
                .isNotNull();
    }

    @Test
    @DisplayName("⭐ Bài Lưu trữ: KHÔNG trong danh sách, nhưng địa chỉ trực tiếp vẫn sống")
    void baiLuuTruVaoDuocBangDiaChiTrucTiep() {
        Article bai = taoBaiDaXuatBan("Thông báo cũ năm ngoái");
        articles.execute(bai.getPublicId(), "ARCHIVE", null);

        assertThat(portal.articles(null, null, 0, 20).getContent())
                .extracting(PublicArticleRow::slug)
                .doesNotContain(bai.getSlug());

        assertThat(portal.article(bai.getSlug()))
                .as(
                        """
                        Người ta đã chia sẻ liên kết đó. Trả 404 cho một bài còn nguyên dữ liệu là tự làm \
                        hỏng liên kết của chính mình.""")
                .isPresent()
                .get()
                .extracting(PublicArticleDetail::archived)
                .isEqualTo(true);
    }

    @Test
    @DisplayName("⭐ Bài hẹn giờ chưa tới hạn thì chưa hiện, dù đã duyệt xong")
    void baiHenGioChuaToiHanChuaHien() {
        Article bai = articles.create(new ArticleDraft(
                "Thông báo lịch xả",
                null,
                null,
                "<p>Nội dung</p>",
                null,
                null,
                null,
                Instant.now().plus(2, ChronoUnit.HOURS),
                null,
                null,
                null,
                Set.of(danhMuc)));
        articles.execute(bai.getPublicId(), "SUBMIT", null);
        articles.execute(bai.getPublicId(), "APPROVE", null);

        assertThat(portal.article(bai.getSlug())).isEmpty();
        assertThat(portal.articles(null, null, 0, 20).getContent())
                .extracting(PublicArticleRow::slug)
                .doesNotContain(bai.getSlug());
    }

    @Test
    @DisplayName("⭐⭐ Cổng phục vụ BẢN ĐÃ DUYỆT, không phải bản biên tập viên đang sửa")
    void congPhucVuBanDaDuyet() {
        Article bai = taoBaiDaXuatBan("Lịch tưới vụ Đông Xuân");

        // Người KHÔNG có quyền xuất bản sửa bài → bản mới nằm chờ duyệt.
        dangNhap("cms:article:update", "cms:article:view", "cms:article:submit");
        articles.update(bai.getPublicId(), banThao("Lịch tưới vụ Đông Xuân", "NỘI DUNG BẢN SỬA CHƯA DUYỆT"));

        assertThat(portal.article(bai.getSlug()))
                .get()
                .extracting(PublicArticleDetail::content)
                .asString()
                .as(
                        """
                        Đây là chỗ copy-on-write thật sự được nghiệm thu: truy vấn công khai JOIN vào \
                        published_version_id, nên bản đang gõ dở không có đường nào lên cổng.""")
                .doesNotContain("CHƯA DUYỆT");
    }

    @Test
    @DisplayName("⛔ Lọc theo danh mục không tồn tại trả RỖNG, không phải trả tất cả")
    void danhMucLaTraRong() {
        taoBaiDaXuatBan("Một bài có thật");

        assertThat(portal.articles("danh-muc-khong-co-that", null, 0, 20).getContent())
                .as("bỏ qua bộ lọc khi slug lạ là gõ sai một chữ thì nhận về toàn bộ bài của cổng")
                .isEmpty();
        assertThat(portal.articles(null, null, 0, 20).getContent()).isNotEmpty();
    }

    // ---- Tệp công khai (T16.6) ----------------------------------------------

    @Test
    @DisplayName("⛔⛔ Tệp hồ sơ nhân sự KHÔNG phục vụ qua cổng, dù biết đúng mã tệp")
    void tepNhanSuKhongPhucVuQuaCong() {
        UUID hoSo = attachments
                .upload("EMPLOYEE", 1L, "HO_SO", "so-yeu-ly-lich.png", anhPng(10, 10), List.of("image/png"))
                .getPublicId();

        assertThat(portal.file(hoSo))
                .as(
                        """
                        Một endpoint công khai nhận UUID rồi trả bất kỳ tệp nào là đặt toàn bộ kho tài liệu \
                        sau một mã đoán được bằng cách thử. Chốt chặn nằm ở tầng đính kèm, cùng chỗ với \
                        việc đọc — không ở nơi gọi, vì nơi gọi quên được.""")
                .isEmpty();
    }

    @Test
    @DisplayName("Ảnh trong thư viện media phục vụ được, kèm đúng kiểu nội dung")
    void anhMediaPhucVuDuoc() {
        Long thuMucId = 1L;
        UUID anh = attachments
                .upload("MEDIA_FOLDER", thuMucId, "MEDIA", "so-do.png", anhPng(20, 12), List.of("image/png"))
                .getPublicId();
        chayBuocQuet(anh);

        assertThat(portal.file(anh))
                .isPresent()
                .get()
                .extracting(AttachmentContent::contentType)
                .isEqualTo("image/png");
    }

    @Test
    @DisplayName("⛔ Tệp chưa qua bước quét thì chưa phục vụ")
    void tepChuaQuetChuaPhucVu() {
        UUID anh = attachments
                .upload("MEDIA_FOLDER", 1L, "MEDIA", "chua-quet.png", anhPng(8, 8), List.of("image/png"))
                .getPublicId();

        assertThat(portal.file(anh))
                .as("cổng công khai là nơi cuối cùng được phép phát tán một tệp chưa kiểm")
                .isEmpty();
    }

    // ---- Đếm lượt xem theo lô — nợ #64 --------------------------------------

    @Test
    @DisplayName("⭐ Lượt xem gom trong bộ nhớ rồi mới xuống CSDL một lần")
    void luotXemGomLoRoiMoiXuongCsdl() {
        Article bai = taoBaiDaXuatBan("Bài có người xem");
        Long id = bai.getId();

        portal.recordView(bai.getSlug());
        portal.recordView(bai.getSlug());
        portal.recordView(bai.getSlug());

        assertThat(viewCounts.dangCho(id)).isEqualTo(3);
        assertThat(demTrongCsdl(bai.getSlug()))
                .as("chưa đẩy thì CSDL chưa được đụng tới — đó là toàn bộ mục đích của việc gom lô")
                .isZero();

        // ⚠⚠ Gọi `dayXuongDinhKy()` chứ KHÔNG gọi `day()`. Bản đầu của bài kiểm này gọi thẳng
        // `day()` — đi qua proxy Spring nên giao dịch mở bình thường và bài kiểm xanh, trong khi
        // production gọi qua bộ hẹn giờ vào `dayXuongDinhKy()` rồi tự gọi `day()` bằng `this`, không
        // chạm proxy, và ném TransactionRequiredException mỗi phút. Bộ đếm chưa từng ghi được gì.
        // Bài kiểm phải đi ĐÚNG cửa mà production đi, nếu không nó chỉ chứng minh một đường khác.
        viewCounts.dayXuongDinhKy();

        assertThat(demTrongCsdl(bai.getSlug())).isEqualTo(3);
        assertThat(viewCounts.dangCho(id))
                .as("đẩy xong phải dọn, nếu không thì lượt sau cộng trùng")
                .isZero();
    }

    @Test
    @DisplayName("Lượt xem của slug không tồn tại bị bỏ qua, không ném lỗi")
    void luotXemSlugLaBiBoQua() {
        portal.recordView("slug-khong-co-that");
        viewCounts.dayXuongDinhKy();
        // Không có phép khẳng định nào ngoài "không nổ" — người xem không cần biết slug sai.
    }

    // ---- Dựng lại trang tĩnh — T16.5, nợ #63 --------------------------------

    @Test
    @DisplayName("⭐⭐ Duyệt bài → có việc dựng lại cổng nằm trong hàng đợi")
    void duyetBaiThiDatViecDungLaiCong() {
        Article bai = articles.create(banThao("Bài sẽ lên cổng"));
        articles.execute(bai.getPublicId(), "SUBMIT", null);
        articles.execute(bai.getPublicId(), "APPROVE", null);

        assertThat(payloadViecDungLai())
                .as(
                        """
                        Gọi thẳng HTTP trong luồng duyệt thì cổng chết là lượt bấm Duyệt treo theo, và \
                        không có lần thử lại nào. Đi qua hàng đợi mới có cả hai.""")
                .anyMatch(p -> p.contains("/bai-viet/" + bai.getSlug()))
                .anyMatch(p -> p.contains("\"tag\""));
    }

    @Test
    @DisplayName("⭐ Job quét bài hẹn giờ đặt việc dựng lại cho bài vừa tới hạn")
    void jobQuetBaiHenGioDatViec() {
        Article bai = taoBaiDaXuatBan("Bài tới hạn trong cửa sổ quét");
        jdbc.update("DELETE FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'");

        scanner.quetBaiToiHan();

        assertThat(payloadViecDungLai())
                .as("bài hẹn giờ tự hiện nhờ published_at <= now(), nhưng TRANG TĨNH thì không tự biết")
                .anyMatch(p -> p.contains(bai.getSlug()));
    }

    // ---- Đường dẫn công khai (lỗ do WS-16 làm lộ ra) -------------------------

    @Test
    @DisplayName("⭐⭐ Sửa tiêu đề bài ĐANG xuất bản KHÔNG làm đổi địa chỉ công khai")
    void suaTieuDeKhongLamDoiDiaChi() {
        Article bai = taoBaiDaXuatBan("Lịch tưới vụ Đông Xuân");
        String diaChiCu = bai.getSlug();

        articles.update(bai.getPublicId(), banThao("Lịch tưới vụ Đông Xuân 2026 (đã sửa lỗi chính tả)"));

        assertThat(articles.get(bai.getPublicId()).getSlug())
                .as(
                        """
                        Suy lại slug từ tiêu đề ở mọi lượt sửa nghĩa là sửa một lỗi chính tả cũng làm chết \
                        mọi liên kết đã chia sẻ và mọi kết quả tìm kiếm — và làm chết TRƯỚC khi có ai duyệt.""")
                .isEqualTo(diaChiCu);
        assertThat(portal.article(diaChiCu)).isPresent();
    }

    @Test
    @DisplayName("Gõ tay một slug mới thì vẫn đổi được — đó là hành động cố ý")
    void goTaySlugMoiThiVanDoiDuoc() {
        Article bai = taoBaiDaXuatBan("Bài đổi đường dẫn");

        articles.update(bai.getPublicId(), banThao("Bài đổi đường dẫn", "Nội dung", "dia-chi-moi-do-nguoi-dung-dat"));

        assertThat(articles.get(bai.getPublicId()).getSlug()).isEqualTo("dia-chi-moi-do-nguoi-dung-dat");
    }

    // ---- Khung cổng ----------------------------------------------------------

    @Test
    @DisplayName("Menu công khai trả slug để cổng dựng đường dẫn, không trả khoá nội bộ")
    void menuCongKhaiTraSlug() {
        assertThat(portal.menu(MenuPosition.HEADER))
                .isNotEmpty()
                .filteredOn(n -> n.categorySlug() != null || n.articleSlug() != null)
                .isNotEmpty();
    }

    @Test
    @DisplayName("Cấu hình cổng trả về cụm giá trị đang hiệu lực")
    void cauHinhCongTraGiaTriHieuLuc() {
        assertThat(portal.siteConfig()).containsKey("site.name").containsKey("site.footer.copyright");
    }

    // ---- Trợ giúp ------------------------------------------------------------

    private List<String> payloadViecDungLai() {
        return jdbc.queryForList("SELECT payload FROM jobs WHERE job_type = 'CMS_PORTAL_REVALIDATE'", String.class);
    }

    private long demTrongCsdl(String slug) {
        Long value = jdbc.queryForObject("SELECT view_count FROM articles WHERE slug = ?", Long.class, slug);
        return value == null ? 0 : value;
    }

    private void chayBuocQuet(UUID attachmentPublicId) {
        jdbc.update(
                "UPDATE attachments SET scan_status = 'SKIPPED', status = 'READY' WHERE public_id = ?",
                attachmentPublicId);
    }

    private Article taoBaiDaXuatBan(String tieuDe) {
        Article bai = articles.create(banThao(tieuDe));
        articles.execute(bai.getPublicId(), "SUBMIT", null);
        return articles.execute(bai.getPublicId(), "APPROVE", null);
    }

    private ArticleDraft banThao(String tieuDe) {
        return banThao(tieuDe, "<p>Nội dung của " + tieuDe + "</p>");
    }

    private ArticleDraft banThao(String tieuDe, String noiDung) {
        return banThao(tieuDe, noiDung, null);
    }

    private ArticleDraft banThao(String tieuDe, String noiDung, String slug) {
        return new ArticleDraft(tieuDe, slug, null, noiDung, null, null, null, null, null, null, null, Set.of(danhMuc));
    }

    private static byte[] anhPng(int rong, int cao) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(rong, cao, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không dựng được ảnh cho bài kiểm", e);
        }
    }

    private static void dangNhap(String... quyen) {
        AuthContext.set(new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "portal-probe",
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
