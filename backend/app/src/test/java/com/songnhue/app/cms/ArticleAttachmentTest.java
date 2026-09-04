package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.ArticleDraft;
import com.songnhue.content.application.ArticleService;
import com.songnhue.content.application.CategoryService;
import com.songnhue.content.application.MediaService;
import com.songnhue.content.domain.Article;
import com.songnhue.content.domain.KhoTep;
import com.songnhue.core.application.attachment.VirusScanHandler;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.JobContext;

/**
 * Tài liệu đính kèm bài viết — <b>qua HTTP thật, trên CSDL và MinIO thật</b> (WS-40).
 *
 * <h2>⛔⛔ Vì sao lớp này tồn tại, và vì sao nó phải đi qua HTTP</h2>
 *
 * <p>Ba cơ chế của đợt này <b>không cơ chế nào</b> bị bắt bởi bộ kiểm sẵn có:
 *
 * <ol>
 *   <li>{@code ArticleVersionSnapshotTest} đếm trường bằng <b>phản chiếu</b>, và một {@code List}
 *       khởi tạo rỗng vẫn khác {@code null} — nó xanh trọn vẹn kể cả khi {@code snapshotOf} quên
 *       chép danh sách tài liệu (quy tắc 9);
 *   <li>đường công khai hẹp là một <b>endpoint mới</b>, chưa bài nào gọi (quy tắc 7: một cơ chế
 *       chưa ai đi qua thì chưa biết nó đúng hay sai);
 *   <li>chốt chặn xoá là một câu SQL <b>native</b> — gõ sai tên cột vẫn biên dịch được, và câu sai
 *       trả về danh sách rỗng, tức là "không ai dùng tệp này", tức là <b>cho xoá</b>.
 * </ol>
 *
 * <p>⭐ Bốn vế của đường hẹp có <b>bốn bài riêng</b>, mỗi bài phá đúng một vế. Kiểm một vế rồi kết
 * luận cả bốn là ba vế chưa ai đi qua.
 */
class ArticleAttachmentTest extends IntegrationTestBase {

    /**
     * Một PDF tối thiểu — Tika nhận diện bằng chữ ký {@code %PDF-} ở đầu tệp.
     *
     * <p>⚠ Không dùng chuỗi bất kỳ: {@code AttachmentPort} kiểm <b>magic bytes</b> và không tin đuôi
     * tệp, nên một tệp "giả vờ là PDF" sẽ bị từ chối — và bài kiểm sẽ đỏ vì lý do không liên quan.
     */
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<<>>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private MediaService media;

    @Autowired
    private ArticleService articles;

    @Autowired
    private CategoryService categories;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VirusScanHandler virusScanHandler;

    private UUID danhMuc;
    private UUID thuMuc;

    @BeforeEach
    void chuanBi() {
        CmsFixtures.donDep(jdbc);
        laQuanTriNoiDung();
        danhMuc = categories.create("Tài liệu kiểm thử WS-40", null, null).getPublicId();
        thuMuc = media.createFolder("Tài liệu", null).getPublicId();
    }

    @AfterEach
    void ketThuc() {
        AuthContext.clear();
        CmsFixtures.donDep(jdbc);
    }

    // === Vòng khép kín ========================================================

    @Test
    @DisplayName("⭐⭐ Tải lên → đính kèm → xuất bản → cổng thấy NHÃN → tải ra BYTE THẬT")
    void vongKhepKin() {
        UUID tep = taiLenTaiLieu("quyet-dinh-thanh-lap.pdf");
        Article bai = xuatBan("Quyết định thành lập Hội đồng", List.of(taiLieu(tep, "Xem quyết định ở đây")));

        String chiTiet = mo("/api/v1/public/articles/" + bai.getSlug());
        assertThat(chiTiet)
                .as("tài liệu không ra tới DTO công khai ⇒ khối đính kèm trên cổng rỗng vĩnh viễn")
                .contains("\"documents\":[")
                .contains("\"title\":\"Xem quyết định ở đây\"")
                .contains("\"contentType\":\"application/pdf\"");
        assertThat(chiTiet)
                .as("cổng đang hiện TÊN TỆP thay vì nhãn gợi nhớ — nhãn là thứ QuanTran yêu cầu 04/09")
                .doesNotContain("quyet-dinh-thanh-lap.pdf");

        // ⭐ Byte thật, không phải 200 rỗng. §10.52: bài kiểm cũ chỉ đi nhánh 404 nên ảnh cổng chưa
        //   từng ra được một byte suốt nhiều tuần mà không ai thấy.
        ResponseEntity<byte[]> tai = http.getForEntity(duongTaiLieu(tep), byte[].class);
        assertThat(tai.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tai.getBody()).isNotNull().startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));

        // ⚠ ĐO header, không khẳng định điều nghe có vẻ đúng (quy tắc 9): `download` của HTML bị bỏ
        //   qua khi khác gốc, nên header này là thứ DUY NHẤT quyết định tên tệp lúc lưu.
        String disposition = tai.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition)
                .as("`inline` thì trình duyệt mở PDF trong tab thay vì tải về — đây là văn bản để phát hành")
                .startsWith("attachment;")
                .contains("quyet-dinh-thanh-lap.pdf");
    }

    @Test
    @DisplayName("⭐ Nhãn rỗng ⇒ cổng hiện TÊN GỐC — ⛔ không sinh 'Tài liệu 1'")
    void nhanRongRoiVeTenGoc() {
        UUID tep = taiLenTaiLieu("phu-luc-01.pdf");
        Article bai = xuatBan("Bài có phụ lục", List.of(taiLieu(tep, null)));

        assertThat(mo("/api/v1/public/articles/" + bai.getSlug()))
                .contains("\"title\":\"phu-luc-01.pdf\"")
                .as("⛔ quy tắc 16: không bịa nhãn mặc định cho một ô người dùng chưa điền")
                .doesNotContain("Tài liệu 1");
    }

    // === Bốn vế của đường hẹp — mỗi vế một bài ================================

    @Test
    @DisplayName("⛔ VẾ 1 — bài còn NHÁP: tài liệu 404")
    void baiNhapThiTaiLieu404() {
        UUID tep = taiLenTaiLieu("du-thao.pdf");
        articles.create(banThao("Dự thảo chưa gửi duyệt", List.of(taiLieu(tep, "Dự thảo"))));

        assertThat(maCua(duongTaiLieu(tep)))
                .as("tài liệu của một bài CHƯA xuất bản tải được ⇒ toàn bộ ý nghĩa của việc 'siết' biến mất")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⛔ VẾ 2 — GỠ BÀI: tài liệu 404 theo, không sống sót")
    void goBaiThiTaiLieu404Theo() {
        UUID tep = taiLenTaiLieu("thong-bao.pdf");
        Article bai = xuatBan("Thông báo tạm", List.of(taiLieu(tep, "Thông báo")));
        assertThat(maCua(duongTaiLieu(tep))).isEqualTo(HttpStatus.OK);

        articles.execute(bai.getPublicId(), "UNPUBLISH", "Đăng nhầm");

        assertThat(maCua(duongTaiLieu(tep)))
                .as("gỡ bài là quyết định RÚT nội dung khỏi công khai — tệp đính kèm là một phần của nội dung ấy")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⛔ VẾ 3 — tệp CHƯA QUÉT XONG: không đính kèm được, và không ra DTO")
    void tepChuaQuetKhongDinhKemDuoc() {
        UUID chuaQuet =
                media.upload(thuMuc, KhoTep.TAI_LIEU, "chua-quet.pdf", PDF).publicId();

        assertThatThrownBy(() -> articles.create(banThao("Bài lỗi", List.of(taiLieu(chuaQuet, "Tệp mới")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CMS-2016");

        // …và nếu tệp bị đưa về trạng thái chưa quét SAU khi đã xuất bản, nó phải biến khỏi DTO —
        // ⛔ không phải "còn một dòng có tên mà bấm vào là 404" (§10.52).
        UUID tep = taiLenTaiLieu("da-quet.pdf");
        Article bai = xuatBan("Bài đã đăng", List.of(taiLieu(tep, "Tệp đã quét")));
        jdbc.update("UPDATE attachments SET status = 'UPLOADING' WHERE public_id = ?", tep);

        assertThat(mo("/api/v1/public/articles/" + bai.getSlug())).contains("\"documents\":[]");
        assertThat(maCua(duongTaiLieu(tep))).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("⛔ VẾ 4 — tệp KHÔNG nằm trong bản chụp đang xuất bản: 404, dù bài vẫn công khai")
    void tepNgoaiBanChupThi404() {
        UUID daDuyet = taiLenTaiLieu("ban-cu.pdf");
        UUID chuaDuyet = taiLenTaiLieu("ban-moi.pdf");
        Article bai = xuatBan("Quy chế", List.of(taiLieu(daDuyet, "Bản cũ")));

        // ⚠⚠ Đăng nhập LẠI, KHÔNG có `cms:article:publish`: `ArticleService.update` cố ý cho người
        //    có quyền xuất bản đẩy bản sửa lên cổng ngay. Giữ nguyên phiên ở trên là đo một đường
        //    đi khác và bài kiểm đỏ vì lý do không liên quan.
        laBienTapVien();
        articles.update(bai.getPublicId(), banThao("Quy chế", List.of(taiLieu(chuaDuyet, "Bản mới"))));

        assertThat(maCua(duongTaiLieu(chuaDuyet)))
                .as(
                        """
                        Đổi tài liệu của bài ĐANG xuất bản mà cổng phục vụ ngay là lách qua bước duyệt — \
                        đúng thứ cơ chế bản chụp sinh ra để chặn (CN-01.1).""")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(maCua(duongTaiLieu(daDuyet)))
                .as("bản đã duyệt phải tiếp tục phục vụ — copy-on-write")
                .isEqualTo(HttpStatus.OK);
        assertThat(mo("/api/v1/public/articles/" + bai.getSlug()))
                .contains("\"title\":\"Bản cũ\"")
                .doesNotContain("\"title\":\"Bản mới\"");
    }

    @Test
    @DisplayName("⛔ Tệp thuộc kho MEDIA không đính kèm được — nếu không, một dòng có tên mà bấm vào là 404")
    void tepKhoMediaKhongDinhKemDuoc() {
        UUID anh = media.upload(thuMuc, KhoTep.MEDIA, "so-do.png", anhPng()).publicId();
        chayBuocQuet(anh);

        assertThatThrownBy(() -> articles.create(banThao("Bài lỗi kho", List.of(taiLieu(anh, "Sơ đồ")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CMS-2016");
    }

    // === Ranh giới cũ không bị nới ============================================

    @Test
    @DisplayName("⭐⭐ Đường công khai CŨ /public/files/{id} trả 404 cho chính tệp ấy")
    void duongCongKhaiCuKhongPhucVuTaiLieu() {
        UUID tep = taiLenTaiLieu("van-ban.pdf");
        xuatBan("Bài có văn bản", List.of(taiLieu(tep, "Văn bản")));

        // ⭐ Vế CẤU TRÚC, đứng cạnh vế HTTP. Nó không chia sẻ giả định nào với lượt gọi mạng ở
        //   dưới, nên hai vế không thể cùng sai vì một lý do (quy tắc 29). Và nó nói ra ranh giới
        //   bằng chữ, ngay chỗ người sửa `LOAI_TEP_CONG_KHAI` sẽ nhìn thấy.
        assertThat(com.songnhue.content.application.PublicPortalService.LOAI_TEP_CONG_KHAI)
                .as(
                        """
                        Thêm TAI_LIEU vào whitelist là làm MỌI tệp trong Kho tài liệu công khai ngay khi \
                        tải lên — kể cả bản dự thảo chưa ai duyệt, kể cả tệp của bài đã gỡ. Đó đúng là \
                        thứ QuanTran chốt 04/09 là phải SIẾT.""")
                .doesNotContain("TAI_LIEU");

        assertThat(maCua(duongTaiLieu(tep))).isEqualTo(HttpStatus.OK);
        assertThat(maCua("/api/v1/public/files/" + tep))
                .as(
                        """
                        Đây là bằng chứng "siết" có thật. Nếu đường cũ cũng phục vụ thì đường hẹp chỉ là \
                        một endpoint mới đặt cạnh một cửa sau còn mở, và bốn vế kiểm ở trên không chặn gì.""")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // === Chốt chặn xoá ========================================================

    @Test
    @DisplayName("⭐ Xoá tệp bị chặn khi CHỈ đính bằng bảng nối — nội dung HTML không nhắc tới nó")
    void xoaBiChanKhiChiDinhBangBangNoi() {
        UUID tep = taiLenTaiLieu("bien-ban.pdf");
        articles.create(banThao("Biên bản họp", List.of(taiLieu(tep, "Biên bản"))));

        assertThat(media.articlesUsing(tep))
                .as(
                        """
                        Bốn vế cũ của `findTitlesReferencing` chỉ dò ẢNH BÌA và CHUỖI trong HTML. Tài liệu \
                        đính kèm là tham chiếu CÓ KHOÁ, nên không vế nào thấy nó — và tệp xoá được ngay \
                        khi đang nằm trong một bài.""")
                .isNotEmpty();
        assertThatThrownBy(() -> media.deleteFile(tep))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CMS-2009");
    }

    @Test
    @DisplayName("⭐⭐ …và cả khi tệp CHỈ còn trong BẢN CHỤP đang xuất bản — vế nguy hiểm nhất")
    void xoaBiChanKhiChiConTrongBanChup() {
        UUID tep = taiLenTaiLieu("quy-trinh.pdf");
        Article bai = xuatBan("Quy trình vận hành", List.of(taiLieu(tep, "Quy trình")));

        // Gỡ khỏi bản LÀM VIỆC nhưng không duyệt ⇒ tệp chỉ còn trong bản chụp cổng đang phục vụ.
        laBienTapVien();
        articles.update(bai.getPublicId(), banThao("Quy trình vận hành", List.of()));

        assertThat(soDongLamViec(bai))
                .as("tiền đề: tệp đã rời khỏi bản làm việc")
                .isZero();
        assertThatThrownBy(() -> media.deleteFile(tep))
                .as(
                        """
                        Thiếu vế `article_version_attachments` là xoá được một tài liệu ĐANG nằm trong bài \
                        đã xuất bản. Triệu chứng chỉ hiện ra ở phía người đọc cổng.""")
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CMS-2009");
    }

    // === Trần dung lượng ======================================================

    @Test
    @DisplayName("⛔ Trần phục vụ công khai là một NGƯỠNG THẬT, không phải một hằng số trang trí")
    void tranPhucVuLaMotNguongThat() {
        long tran = KhoTep.TRAN_PHUC_VU_CONG_KHAI_MB * 1024L * 1024L;
        assertThat(KhoTep.vuotTranPhucVu(tran)).isFalse();
        assertThat(KhoTep.vuotTranPhucVu(tran + 1)).isTrue();
        // ⛔ Phải THẤP HƠN trần tải lên (50MB, V202608131009) — bằng hoặc cao hơn thì nó không chặn
        //    được gì, và một PDF 50MB vẫn đọc trọn vào `byte[]` như trước.
        assertThat(KhoTep.TRAN_PHUC_VU_CONG_KHAI_MB).isLessThan(50);
    }

    // -------------------------------------------------------------------------

    private String duongTaiLieu(UUID tep) {
        return "/api/v1/public/article-documents/" + tep;
    }

    private String mo(String duongDan) {
        ResponseEntity<String> res = http.getForEntity(duongDan, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody() == null ? "" : res.getBody();
    }

    private HttpStatus maCua(String duongDan) {
        return HttpStatus.valueOf(
                http.getForEntity(duongDan, byte[].class).getStatusCode().value());
    }

    private UUID taiLenTaiLieu(String ten) {
        AttachmentRef ref = media.upload(thuMuc, KhoTep.TAI_LIEU, ten, PDF);
        chayBuocQuet(ref.publicId());
        return ref.publicId();
    }

    private static ArticleDraft.TaiLieu taiLieu(UUID publicId, String label) {
        return new ArticleDraft.TaiLieu(publicId, label);
    }

    private ArticleDraft banThao(String tieuDe, List<ArticleDraft.TaiLieu> taiLieu) {
        return new ArticleDraft(
                tieuDe,
                null,
                null,
                "<p>Nội dung của " + tieuDe + "</p>",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Set.of(danhMuc),
                taiLieu);
    }

    private Article xuatBan(String tieuDe, List<ArticleDraft.TaiLieu> taiLieu) {
        Article bai = articles.create(banThao(tieuDe, taiLieu));
        articles.execute(bai.getPublicId(), "SUBMIT", null);
        return articles.execute(bai.getPublicId(), "APPROVE", null);
    }

    private int soDongLamViec(Article bai) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM article_attachments aa JOIN articles a ON a.id = aa.article_id"
                        + " WHERE a.public_id = ?",
                Integer.class,
                bai.getPublicId());
        return n == null ? 0 : n;
    }

    /** Chạy bước quét cho tệp vừa tải — mô phỏng đúng việc worker sẽ làm (xem {@code MediaLibraryTest}). */
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

    private static byte[] anhPng() {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(
                    new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không dựng được ảnh cho bài kiểm", e);
        }
    }

    private static void laQuanTriNoiDung() {
        dangNhap(Set.of(
                "cms:category:manage",
                "cms:media:manage",
                "cms:article:create",
                "cms:article:view",
                "cms:article:update",
                "cms:article:submit",
                "cms:article:approve",
                "cms:article:publish",
                "cms:article:unpublish"));
    }

    private static void laBienTapVien() {
        AuthContext.clear();
        dangNhap(Set.of("cms:article:view", "cms:article:update", "cms:article:submit", "cms:media:manage"));
    }

    private static void dangNhap(Set<String> quyen) {
        AuthContext.set(new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "probe-ws40",
                "Người kiểm thử",
                1L,
                "/1/",
                Set.of("PROBE"),
                quyen,
                false,
                UUID.randomUUID(),
                UUID.randomUUID()));
    }
}
