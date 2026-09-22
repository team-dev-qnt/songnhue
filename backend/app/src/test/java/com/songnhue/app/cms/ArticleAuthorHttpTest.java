package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Tác giả bài viết — nửa ĐỌC của một cặp đọc–ghi đã thiếu 34 ngày</b> (T84.2).
 *
 * <h2>Trạng thái trước lượt này, đo được</h2>
 *
 * <ul>
 *   <li>{@code articles.author_user_id} — cột {@code NOT NULL} có từ {@code V202608191016:81};
 *   <li>{@code SaveRequest.authorPublicId} — chiều VÀO xong từ 19/08/2026;
 *   <li>{@code ArticleDetail}/{@code ArticleSummary} — <b>⛔ trả tác giả</b>, và
 *       {@code grep authorName} toàn kho = <b>0</b>.
 * </ul>
 *
 * <p>Tức là biểu mẫu gửi tác giả lên được, mà màn hình ⛔ bao giờ nhận lại được để gửi lần sau —
 * đúng hình dạng luật 27, và nó im lặng hoàn toàn: mọi lượt Lưu bỏ trống {@code authorPublicId},
 * {@code ArticleService.update} giữ nguyên giá trị cũ, và người dùng tưởng ô chọn ⛔ có tác dụng.
 *
 * <h2>Vì sao phải kiểm QUA HTTP</h2>
 *
 * Ba thứ của lượt này chỉ tồn tại ở tầng HTTP: định tuyến {@code /authors} cạnh
 * {@code /&#123;publicId&#125;} (một {@code UUID}) · {@code @RequirePermission} tầng 2 · và phép
 * ánh xạ DTO chạy <b>sau khi giao dịch đóng</b> — đúng chỗ đã làm mọi màn hình quản trị nội dung trả
 * 500 suốt từ WS-13 ({@code ArticleHttpTest}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleAuthorHttpTest extends IntegrationTestBase {

    private static final String DUONG = "/api/v1/cms/articles";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    private PhienHttp phien;
    private PhienHttp.Phien bienTapVien;
    private PhienHttp.Phien quanTriNoiDung;
    private String idBienTapVien;
    private String idQuanTri;

    @BeforeAll
    void dangNhapMotLanChoCaLop() {
        phien = new PhienHttp(http);
        bienTapVien = phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tg_vien", "CONTENT_EDITOR"));
        // Người thứ hai vừa để có MỘT tác giả khác — phép "đổi tác giả" mà đổi sang chính mình thì
        // ⛔ phân biệt được với "⛔ đổi gì" (luật 9) — vừa để có quyền duyệt/xuất bản.
        quanTriNoiDung =
                phien.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "tg_quantri", "CONTENT_MANAGER"));
        idBienTapVien = publicIdCua("tg_vien");
        idQuanTri = publicIdCua("tg_quantri");
    }

    /** ⚠ {@code PhienHttp.taoNguoiDung} gắn tiền tố {@code kiemtra_} — tra thiếu nó thì 0 hàng. */
    private String publicIdCua(String hau) {
        return jdbc.queryForObject(
                "SELECT public_id::text FROM users WHERE username = ?", String.class, "kiemtra_" + hau);
    }

    private String danhMucSeed() {
        return jdbc.queryForObject(
                "SELECT public_id::text FROM categories WHERE deleted_at IS NULL ORDER BY id LIMIT 1", String.class);
    }

    private String than(String tieuDe, String slug, String tacGia) {
        String oTacGia = tacGia == null ? "" : "\"authorPublicId\":\"%s\",".formatted(tacGia);
        return """
                {"title":"%s","slug":"%s",%s"content":"<p>Nội dung</p>","categoryPublicIds":["%s"]}"""
                .formatted(tieuDe, slug, oTacGia, danhMucSeed());
    }

    @Test
    @DisplayName("⭐⭐ Tạo bài kèm tác giả ⇒ chi tiết trả về CẢ publicId lẫn họ tên")
    void chiTietTraVeTacGia() {
        String tao = phien.goi(bienTapVien, HttpMethod.POST, DUONG, than("Bài có tác giả", "bai-co-tac-gia", idQuanTri))
                .getBody();
        String publicId = PhienHttp.giaTriJson(tao, "publicId");

        ResponseEntity<String> chiTiet = phien.get(bienTapVien, DUONG + "/" + publicId);
        assertThat(chiTiet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(PhienHttp.giaTriJson(chiTiet.getBody(), "authorPublicId"))
                .as("nửa ĐỌC của cặp đọc–ghi: thiếu nó thì biểu mẫu ⛔ có gì để gửi lại (luật 27)")
                .isEqualTo(idQuanTri);
        assertThat(chiTiet.getBody())
                .as("họ tên phải đi cùng — cổng công khai in nó, ⛔ in UUID")
                .contains("\"authorName\"");
        assertThat(PhienHttp.giaTriJson(chiTiet.getBody(), "authorName")).isNotBlank();
    }

    @Test
    @DisplayName("⭐⭐ PUT THIẾU authorPublicId ⇒ tác giả GIỮ NGUYÊN, ⛔ bị xoá trắng (§11.19)")
    void putThieuTruongThiGiuNguyenTacGia() {
        String tao = phien.goi(
                        bienTapVien, HttpMethod.POST, DUONG, than("Bài giữ tác giả", "bai-giu-tac-gia", idQuanTri))
                .getBody();
        String publicId = PhienHttp.giaTriJson(tao, "publicId");

        // Thân ⛔ mang `authorPublicId` — đúng thứ một client API cũ sẽ gửi.
        phien.goi(
                bienTapVien, HttpMethod.PUT, DUONG + "/" + publicId, than("Bài giữ tác giả", "bai-giu-tac-gia", null));

        ResponseEntity<String> sau = phien.get(bienTapVien, DUONG + "/" + publicId);
        assertThat(PhienHttp.giaTriJson(sau.getBody(), "authorPublicId"))
                .as("`null` nghĩa GIỮ NGUYÊN, ⛔ phải 'xoá tác giả' — `articles.author_user_id` là NOT NULL")
                .isEqualTo(idQuanTri);
    }

    @Test
    @DisplayName("⭐ ĐỔI tác giả qua PUT có hiệu lực thật — vế phân biệt của bài trên")
    void doiTacGiaCoHieuLuc() {
        String tao = phien.goi(
                        bienTapVien, HttpMethod.POST, DUONG, than("Bài đổi tác giả", "bai-doi-tac-gia", idQuanTri))
                .getBody();
        String publicId = PhienHttp.giaTriJson(tao, "publicId");

        phien.goi(
                bienTapVien,
                HttpMethod.PUT,
                DUONG + "/" + publicId,
                than("Bài đổi tác giả", "bai-doi-tac-gia", idBienTapVien));

        ResponseEntity<String> sau = phien.get(bienTapVien, DUONG + "/" + publicId);
        assertThat(PhienHttp.giaTriJson(sau.getBody(), "authorPublicId"))
                .as("⛔ có bài này thì bài 'giữ nguyên' xanh cả khi PUT ⛔ bao giờ ghi được tác giả")
                .isEqualTo(idBienTapVien);
    }

    @Test
    @DisplayName("⭐ Danh sách bài mang authorName — và lọc ?authorId= ra đúng tập")
    void danhSachMangTenVaLocDuoc() {
        phien.goi(bienTapVien, HttpMethod.POST, DUONG, than("Bài của quản trị", "bai-cua-quan-tri", idQuanTri));
        phien.goi(bienTapVien, HttpMethod.POST, DUONG, than("Bài của biên tập", "bai-cua-bien-tap", idBienTapVien));

        ResponseEntity<String> tatCa = phien.get(bienTapVien, DUONG + "?size=100");
        assertThat(tatCa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tatCa.getBody()).contains("\"authorName\"");

        ResponseEntity<String> loc = phien.get(bienTapVien, DUONG + "?size=100&authorId=" + idQuanTri);
        assertThat(loc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loc.getBody())
                .as("bộ lọc theo tác giả — backend nhận `authorId` từ WS-12, giao diện nối ở T84.3")
                .contains("bai-cua-quan-tri");
        assertThat(loc.getBody())
                .as("⛔ có vế PHỦ ĐỊNH thì một bộ lọc ⛔ lọc gì cũng xanh (luật 9)")
                .doesNotContain("bai-cua-bien-tap");
    }

    @Test
    @DisplayName("⭐⭐ GET /articles/authors trả 200 + danh sách khác rỗng — ĐO, ⛔ suy từ luật định tuyến")
    void duongAuthorsKhongBiNuotBoiDuongUuid() {
        ResponseEntity<String> ra = phien.get(bienTapVien, DUONG + "/authors");

        // ⚠ Spring xếp đoạn CHỮ trên đoạn BIẾN, nên `/authors` thắng `/{publicId}` — nhưng đó là
        //   "nghe có vẻ đúng". Nếu nó thua thì Spring cố đọc "authors" thành một UUID và trả 400,
        //   và ô chọn Tác giả sẽ RỖNG VĨNH VIỄN mà ⛔ một dòng lỗi nào ở phía người dùng.
        assertThat(ra.getStatusCode())
                .as("400 ở đây nghĩa là `/authors` bị `/{publicId}` nuốt mất; body: %s", ra.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(ra.getBody())
                .as("chống tập rỗng — một danh sách rỗng ⛔ phân biệt được với một endpoint hỏng")
                .contains("\"fullName\"");
        assertThat(ra.getBody()).contains(idBienTapVien);
    }

    @Test
    @DisplayName("⭐⭐ /authors CHỈ liệt người có quyền soạn bài — vế phân biệt, ⛔ chỉ 'khác rỗng'")
    void authorsLoaiNguoiKhongCoQuyenSoanBai() {
        // Tài khoản ⛔ vai trò nào ⇒ ⛔ có `cms:article:create`.
        String khongQuyen = PhienHttp.taoNguoiDung(users, passwords, jdbc, "tg_khongquyen");
        String idKhongQuyen = publicIdCua("tg_khongquyen");
        assertThat(khongQuyen).isNotBlank();

        String ra = phien.get(bienTapVien, DUONG + "/authors").getBody();

        assertThat(ra)
                .as("⛔ có vế PHỦ ĐỊNH thì một bản dựng trả về MỌI tài khoản cũng xanh (luật 9)")
                .doesNotContain(idKhongQuyen);
        assertThat(ra).as("và vế khẳng định phải còn đúng").contains(idBienTapVien);
    }

    @Test
    @DisplayName("⭐⭐ Tác giả bị xoá mềm ⇒ authorName NULL, bài viết vẫn đọc được")
    void tacGiaXoaMemThiTenRongChuBaiVanSong() {
        String slug = "bai-tac-gia-da-nghi";
        PhienHttp.taoNguoiDung(users, passwords, jdbc, "tg_sapnghi", "CONTENT_EDITOR");
        String idSapNghi = publicIdCua("tg_sapnghi");

        String tao = phien.goi(quanTriNoiDung, HttpMethod.POST, DUONG, than("Bài của người đã nghỉ", slug, idSapNghi))
                .getBody();
        String publicId = PhienHttp.giaTriJson(tao, "publicId");

        jdbc.update("UPDATE users SET deleted_at = now() WHERE username = 'kiemtra_tg_sapnghi'");

        ResponseEntity<String> chiTiet = phien.get(bienTapVien, DUONG + "/" + publicId);
        assertThat(chiTiet.getStatusCode())
                .as("một bài của người đã nghỉ vẫn là bài HỢP LỆ — ⛔ được 500 và ⛔ được 404")
                .isEqualTo(HttpStatus.OK);
        // ⚠⚠ Jackson của dự án bỏ HẲN trường `null` khỏi JSON (đo: thân của một bài Nháp ⛔ có
        //   `summary`, `source`, `publishedAt`, `reviewNote`…). Nên hợp đồng trên dây là *VẮNG MẶT*
        //   chứ ⛔ phải `"authorName":null` — bản đầu của bài này khẳng định chuỗi ấy và đỏ.
        assertThat(chiTiet.getBody())
                .as("tác giả đã xoá mềm ⇒ trường VẮNG MẶT, ⛔ phải một chuỗi bịa như 'Không rõ' (quy tắc 16)")
                .doesNotContain("\"authorName\"");
        assertThat(chiTiet.getBody())
                .as("nhưng chính bài viết thì vẫn đủ — chống việc bài trên xanh vì phản hồi rỗng")
                .contains("\"slug\":\"" + slug + "\"");

        assertThat(phien.get(bienTapVien, DUONG + "/authors").getBody())
                .as("và người đã xoá mềm ⛔ còn được đề nghị làm tác giả mới")
                .doesNotContain(idSapNghi);
    }

    @Test
    @DisplayName("⛔ /authors ⛔ tiết lộ username — VIEWER và EXECUTIVE cũng có cms:article:view")
    void authorsKhongLoTenDangNhap() {
        ResponseEntity<String> ra = phien.get(bienTapVien, DUONG + "/authors");

        // ⚠⚠ Khẳng định đầu của tôi là `doesNotContain("tg_vien")` và nó ĐỎ vì lý do SAI: đồ gá đặt
        //   `fullName = "Người dùng kiểm thử " + hau`, nên chuỗi ấy nằm trong HỌ TÊN — một trường
        //   PHẢI có mặt. Phép so ấy ⛔ phân biệt được *lộ username* với *in đúng họ tên* (luật 9).
        //   Vế phân biệt thật là tiền tố `kiemtra_`, thứ CHỈ username mang.
        assertThat(ra.getBody())
                .as("trả username ở đây là phát danh sách tên đăng nhập hợp lệ cho hai vai trò chỉ-đọc")
                .doesNotContain("\"username\"")
                .doesNotContain("kiemtra_");
        assertThat(ra.getBody()).as("và họ tên thì PHẢI có — ô chọn cần nó").contains("\"fullName\"");
    }

    /**
     * Đẩy một bài từ Nháp lên cổng — đi ĐÚNG đường workflow, ⛔ `UPDATE status` (quy tắc 4).
     *
     * <p>⚠ Đúng HAI bước. Bản đầu của bài kiểm này thêm một bước {@code PUBLISH} thứ ba và nhận
     * {@code SYS-0008}: {@code V202608191017:56} khai {@code CHO_DUYET --APPROVE--> XUAT_BAN}, tức
     * <b>duyệt LÀ xuất bản</b>. Quy trình duyệt nằm ở DỮ LIỆU, nên đoán tên bước là đoán một hàng
     * trong bảng — phải đọc `workflow_transitions`.
     */
    private void xuatBan(String publicId) {
        for (String hanhDong : new String[] {"SUBMIT", "APPROVE"}) {
            ResponseEntity<String> buoc = phien.goi(
                    quanTriNoiDung,
                    HttpMethod.POST,
                    DUONG + "/" + publicId + "/transitions",
                    "{\"action\":\"%s\"}".formatted(hanhDong));
            assertThat(buoc.getStatusCode())
                    .as(
                            "bước %s phải đi được, ⛔ thì bài kiểm chưa tới được chỗ cần kiểm; body: %s",
                            hanhDong, buoc.getBody())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @DisplayName("⭐⭐ Cổng công khai in tên tác giả của bài đã xuất bản")
    void congCongKhaiCoTenTacGia() {
        String slug = "bai-len-cong-tac-gia";
        String tao = phien.goi(quanTriNoiDung, HttpMethod.POST, DUONG, than("Bài lên cổng", slug, idQuanTri))
                .getBody();
        xuatBan(PhienHttp.giaTriJson(tao, "publicId"));

        // ⛔ KHÔNG token — đây là đường người đọc thật đi, và nó đi qua `PublicPortalService`,
        //   một lớp khác hẳn `ArticleController`.
        ResponseEntity<String> cong = http.getForEntity("/api/v1/public/articles/" + slug, String.class);
        assertThat(cong.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(PhienHttp.giaTriJson(cong.getBody(), "authorName"))
                .as("tác giả đọc từ `article` (cùng đường `source`), ⛔ từ bản chụp phiên bản — "
                        + "`article_versions` ⛔ chụp cột ấy")
                .isNotBlank();
    }
}
