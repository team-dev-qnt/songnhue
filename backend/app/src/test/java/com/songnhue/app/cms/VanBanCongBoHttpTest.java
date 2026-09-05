package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.ArticleDraft;
import com.songnhue.content.application.ArticleService;
import com.songnhue.content.application.CategoryService;
import com.songnhue.content.domain.Article;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;

/**
 * Số ký hiệu và ngày ban hành của văn bản — đi <b>qua HTTP thật</b>, WS-39.
 *
 * <h2>⚠⚠ Vì sao phải qua HTTP chứ không gọi service</h2>
 *
 * Hai cột này đi qua một <b>biểu thức khởi tạo JPQL</b>
 * ({@code SELECT new …PublicArticleRow(a.slug, v.title, v.summary, …)}), và biểu thức ấy khớp tham
 * số <b>theo VỊ TRÍ</b>, không theo tên. Chèn hai cột mới sai chỗ thì {@code summary} nhận số ký
 * hiệu — và <b>trình biên dịch không nói gì</b>, vì cả hai đều là {@code String}. Chỉ có một phép
 * đọc trên dữ liệu thật mới phân biệt được hai trạng thái ấy (luật 9).
 *
 * <p>Thêm một tầng nữa chỉ HTTP mới bắt được: {@code LocalDate} phải tuần tự hoá thành
 * {@code "2015-05-04"}. Nếu Jackson được cấu hình sai thì nó ra một mảng {@code [2015,5,4]} và
 * {@code new Date(...)} phía cổng cho ra một ngày vô nghĩa — service gọi thẳng không thấy điều đó.
 *
 * <h2>⛔ Và một bất biến quan trọng hơn cả hai cột: COPY-ON-WRITE</h2>
 *
 * Số ký hiệu là <b>nội dung</b>, nên nó phải đi cùng đường với tiêu đề: cổng đọc từ bản đã duyệt.
 * Sửa nó trên một bài đang xuất bản mà cổng đổi ngay là đã lách qua quy trình duyệt — bài cuối
 * lớp này đo đúng chuyện đó.
 */
class VanBanCongBoHttpTest extends IntegrationTestBase {

    private static final String SO_KY_HIEU = "43/2015/NĐ-CP";
    private static final LocalDate NGAY_BAN_HANH = LocalDate.of(2015, 5, 4);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ArticleService articles;

    @Autowired
    private CategoryService categories;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID danhMuc;
    private String slugDanhMuc;

    @BeforeEach
    void chuanBi() {
        CmsFixtures.donDep(jdbc);
        dangNhap();
        var tao = categories.create("Văn bản kiểm thử", null, null);
        danhMuc = tao.getPublicId();
        slugDanhMuc = tao.getSlug();
    }

    @AfterEach
    void ketThuc() {
        AuthContext.clear();
        CmsFixtures.donDep(jdbc);
    }

    // === Đường đi của hai cột mới ============================================

    @Test
    @DisplayName("⭐⭐ Số ký hiệu và ngày ban hành ra tới DANH SÁCH công khai, và KHÔNG lẫn sang ô khác")
    void haiCotRaToiDanhSachVaKhongLechViTri() {
        Article bai = xuatBan("Quy định lập, quản lý hành lang bảo vệ nguồn nước", SO_KY_HIEU, NGAY_BAN_HANH);

        ResponseEntity<String> res =
                http.getForEntity("/api/v1/public/articles?category=" + slugDanhMuc + "&size=10", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = res.getBody();
        assertThat(body).isNotNull();

        assertThat(body)
                .as("số ký hiệu không ra tới danh sách ⇒ cột \"Số ký hiệu\" của bảng văn bản rỗng vĩnh viễn")
                .contains("\"docNumber\":\"" + SO_KY_HIEU + "\"");
        assertThat(body)
                .as(
                        """
                        Ngày ban hành phải tuần tự hoá thành chuỗi ISO. Ra một mảng [2015,5,4] thì cổng \
                        dựng ra một ngày vô nghĩa mà không lỗi nào — và service gọi thẳng KHÔNG thấy được.""")
                .contains("\"docIssuedDate\":\"2015-05-04\"");

        // ⭐⭐ Vế quan trọng nhất: biểu thức khởi tạo JPQL khớp theo VỊ TRÍ. Chèn hai cột sai chỗ thì
        //    `summary` nhận số ký hiệu, `docNumber` nhận tóm tắt — cùng kiểu String, không lỗi nào.
        assertThat(body)
                .as("ô `summary` đang mang số ký hiệu ⇒ hai tham số của projection JPQL bị lệch vị trí")
                .doesNotContain("\"summary\":\"" + SO_KY_HIEU + "\"");
        assertThat(body).contains("\"title\":\"Quy định lập, quản lý hành lang bảo vệ nguồn nước\"");
        assertThat(body).contains("\"slug\":\"" + bai.getSlug() + "\"");
    }

    @Test
    @DisplayName("⭐ …và ra tới TRANG CHI TIẾT (đường đọc thứ hai, dựng bằng tay chứ không bằng JPQL)")
    void haiCotRaToiTrangChiTiet() {
        Article bai = xuatBan("Phê duyệt quy hoạch phòng chống lũ", "1821/QĐ-TTg", LocalDate.of(2014, 10, 5));

        ResponseEntity<String> res = http.getForEntity("/api/v1/public/articles/" + bai.getSlug(), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Hai đường đọc khác nhau (một JPQL, một ghép tay ở `PublicPortalService.article`) — quên một
        // đường là đúng hình dạng "làm xong nửa đường trông y hệt làm xong".
        assertThat(res.getBody())
                .contains("\"docNumber\":\"1821/QĐ-TTg\"")
                .contains("\"docIssuedDate\":\"2014-10-05\"");
    }

    @Test
    @DisplayName("⛔ Bài KHÔNG phải văn bản ⇒ hai ô là `null`, không phải một giá trị suy ra")
    void baiThuongThiHaiOLaNull() {
        Article tin = xuatBan("Hà Nội ứng trực ngày đêm", null, null);

        ResponseEntity<String> res = http.getForEntity("/api/v1/public/articles/" + tin.getSlug(), String.class);

        assertThat(res.getBody())
                .as(
                        """
                        ⛔ Quy tắc 16. Suy `docIssuedDate` từ `publishedAt` là biến "chưa ai nhập" thành \
                        một câu khẳng định về ngày ký ban hành — và câu khẳng định ấy sai với MỌI văn bản \
                        được đăng lại sau ngày ký. Rỗng thì bảng để trống ô đó.""")
                // ⚠ Envelope JSON bỏ hẳn trường `null` (Jackson NON_NULL), nên phép kiểm đúng là
                //   TÊN TRƯỜNG VẮNG MẶT — không phải chuỗi `"docNumber":null`. Lượt viết đầu khẳng
                //   định chuỗi ấy và đỏ, đúng kiểu "khẳng định cái nghe có vẻ đúng" mà luật 9 cấm.
                .doesNotContain("docNumber")
                .doesNotContain("docIssuedDate");
    }

    // === Bất biến copy-on-write ==============================================

    @Test
    @DisplayName("⭐⭐ Sửa số ký hiệu của bài ĐANG xuất bản KHÔNG đổi cổng cho tới khi duyệt lại")
    void suaSoKyHieuKhongLenCongTruocKhiDuyet() {
        Article bai = xuatBan("Quy chế quản lý công trình", SO_KY_HIEU, NGAY_BAN_HANH);

        // ⚠⚠ ĐĂNG NHẬP LẠI KHÔNG CÓ `cms:article:publish`. `ArticleService.update` cố ý cho người CÓ
        //    quyền xuất bản đẩy bản sửa lên cổng ngay, không vòng qua duyệt — nên nếu giữ nguyên
        //    phiên ở trên thì bài kiểm này đo một đường đi khác và đỏ vì lý do không liên quan.
        //    (Lượt viết đầu mắc đúng lỗi ấy; cùng khuôn với `PublicPortalTest.congPhucVuBanDaDuyet`.)
        dangNhapKhongCoQuyenXuatBan();

        articles.update(
                bai.getPublicId(),
                new ArticleDraft(
                        "Quy chế quản lý công trình",
                        null,
                        null,
                        "<p>Nội dung</p>",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "SO-HIEU-CHUA-DUYET",
                        LocalDate.of(2026, 1, 1),
                        Set.of(danhMuc),
                        List.of()));

        ResponseEntity<String> res = http.getForEntity("/api/v1/public/articles/" + bai.getSlug(), String.class);

        assertThat(res.getBody())
                .as(
                        """
                        Cổng đọc NỘI DUNG từ bản đã duyệt (`publishedVersionId`). Số ký hiệu là nội dung, \
                        nên để nó chỉ ở bảng `articles` là mở một cửa hậu: một trường của bài đang xuất \
                        bản đổi ngay trên cổng, không qua ai duyệt. Triệu chứng thì im — bài vẫn hiện, \
                        chỉ một ô của nó đi trước quy trình.""")
                .contains("\"docNumber\":\"" + SO_KY_HIEU + "\"")
                .doesNotContain("SO-HIEU-CHUA-DUYET");
    }

    // -------------------------------------------------------------------------

    private Article xuatBan(String tieuDe, String soKyHieu, LocalDate ngayBanHanh) {
        Article bai = articles.create(new ArticleDraft(
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
                soKyHieu,
                ngayBanHanh,
                Set.of(danhMuc),
                List.of()));
        articles.execute(bai.getPublicId(), "SUBMIT", null);
        return articles.execute(bai.getPublicId(), "APPROVE", null);
    }

    private static void dangNhapKhongCoQuyenXuatBan() {
        AuthContext.clear();
        dangNhap(Set.of("cms:article:update", "cms:article:view", "cms:article:submit"));
    }

    private static void dangNhap() {
        dangNhap(Set.of(
                "cms:category:manage",
                "cms:article:create",
                "cms:article:update",
                "cms:article:view",
                "cms:article:submit",
                "cms:article:approve",
                "cms:article:publish"));
    }

    private static void dangNhap(Set<String> quyen) {
        AuthContext.set(new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "van-ban-probe",
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
