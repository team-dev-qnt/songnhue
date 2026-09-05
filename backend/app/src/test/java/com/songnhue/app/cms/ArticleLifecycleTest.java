package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
import com.songnhue.content.domain.Article;
import com.songnhue.content.domain.ArticleState;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.AllowedAction;

/**
 * Vòng đời bài viết trên CSDL thật — WS-13/T13.12.
 *
 * <p>Bài kiểm này là <b>lần đầu tiên một entity nghiệp vụ đi qua workflow engine</b>. Suốt Phase 0
 * engine chỉ chạy trên bản ghi dựng riêng cho test, nên mọi thứ ở đây đều là "chưa ai đi qua":
 * seed quy trình bằng migration, tách vai trò bằng dữ liệu, và copy-on-write.
 *
 * <p><b>Ba câu hỏi bài kiểm phải trả lời</b>, và cả ba đều là chỗ hỏng im lặng nếu sai:
 *
 * <ol>
 *   <li>Biên tập viên có tự xuất bản được bài của mình không? (SRS §3.1.3 — phải là <b>không</b>)
 *   <li>Sửa một bài đang chạy trên cổng thì cổng có mất bài không? (copy-on-write — phải là
 *       <b>không</b>)
 *   <li>Ai nhận thông báo "có bài chờ duyệt"? (phải là quản trị nội dung, <b>không phải</b> Ban điều
 *       hành)
 * </ol>
 */
class ArticleLifecycleTest extends IntegrationTestBase {

    private static final String QUYEN_XEM = "cms:article:view";
    private static final String QUYEN_TAO = "cms:article:create";
    private static final String QUYEN_SUA = "cms:article:update";
    private static final String QUYEN_GUI = "cms:article:submit";
    private static final String QUYEN_DUYET = "cms:article:approve";
    private static final String QUYEN_XUAT_BAN = "cms:article:publish";
    private static final String QUYEN_GO_BAI = "cms:article:unpublish";
    private static final String QUYEN_DANH_MUC = "cms:category:manage";

    @Autowired
    private ArticleService articles;

    @Autowired
    private CategoryService categories;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID danhMuc;

    @BeforeEach
    void chuanBi() {
        donDepDuLieuCms();

        // Quyền quản lý danh mục thuộc vai trò khác, nên mượn tạm để dựng dữ liệu nền…
        dangNhap(1L, QUYEN_DANH_MUC);
        // ⚠ Tên phải khác danh mục do migration V…1021 seed: "Tin hoạt động" đã có slug từ WS-15,
        // và trùng slug là CMS-2001 — bài kiểm đỏ vì dữ liệu nền chứ không vì thứ nó kiểm.
        danhMuc = categories.create("Chuyên mục kiểm thử", null, null).getPublicId();

        // …rồi PHẢI đổi lại. Thiếu dòng này thì mọi bài kiểm chạy dưới danh tính chỉ có quyền danh
        // mục, và `SUBMIT` trả AUTH-3001 — trông y hệt như quy trình duyệt bị hỏng.
        laBienTapVien();
    }

    @AfterEach
    void donDep() {
        AuthContext.clear();
        donDepDuLieuCms();
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Bài mới luôn bắt đầu ở NHAP và có ngay một phiên bản")
    void taoMoiBatDauONhap() {
        Article bai = articles.create(banThao("Nâng cấp trạm bơm Yên Nghĩa"));

        assertThat(bai.getStatus()).isEqualTo(ArticleState.NHAP);
        assertThat(bai.getSlug()).isEqualTo("nang-cap-tram-bom-yen-nghia");
        assertThat(articles.versionsOf(bai.getPublicId())).hasSize(1);
        assertThat(bai.getPublishedVersionId())
                .as("chưa ai duyệt thì cổng công khai không có gì để phục vụ")
                .isNull();
    }

    @Test
    @DisplayName("⭐ Biên tập viên KHÔNG tự xuất bản được bài của mình — SRS §3.1.3")
    void bienTapVienKhongTuXuatBanDuoc() {
        Article bai = articles.create(banThao("Bài của biên tập viên"));
        articles.execute(bai.getPublicId(), "SUBMIT", null);

        assertThatThrownBy(() -> articles.execute(bai.getPublicId(), "APPROVE", null))
                .as(
                        """
                        Ràng buộc này nằm ở DỮ LIỆU (workflow_transitions.required_permission), không ở \
                        câu `if` nào trong service — nên nó không thể bị quên khi thêm đường đi mới.""")
                .hasMessageContaining("AUTH-3001");

        assertThat(trangThaiTrongCsdl(bai.getPublicId())).isEqualTo(ArticleState.CHO_DUYET);
    }

    @Test
    @DisplayName("Ô nút của biên tập viên không chứa hành động duyệt")
    void nutDuyetKhongHienChoBienTapVien() {
        Article bai = articles.create(banThao("Bài chờ duyệt"));
        articles.execute(bai.getPublicId(), "SUBMIT", null);

        assertThat(articles.allowedActions(bai.getPublicId()))
                .as("FE render từ danh sách này — hiện nút mà bấm vào bị 403 là lỗi giao diện")
                .extracting(AllowedAction::action)
                .doesNotContain("APPROVE", "REQUEST_CHANGES");
    }

    @Test
    @DisplayName("Quản trị nội dung duyệt → bài lên cổng, có bản phục vụ công khai")
    void duyetThiLenCong() {
        Article bai = articles.create(banThao("Bài sẽ được duyệt"));
        articles.execute(bai.getPublicId(), "SUBMIT", null);

        laQuanTriNoiDung();
        Article daDuyet = articles.execute(bai.getPublicId(), "APPROVE", null);

        assertThat(daDuyet.getStatus()).isEqualTo(ArticleState.XUAT_BAN);
        assertThat(daDuyet.getPublishedVersionId()).isNotNull();
        assertThat(daDuyet.isPubliclyVisible(Instant.now())).isTrue();
    }

    @Test
    @DisplayName("⭐⭐ Sửa bài ĐANG xuất bản không hạ bài xuống cổng — copy-on-write")
    void suaBaiDangXuatBanKhongHaBaiXuong() {
        Article bai = taoBaiDaXuatBan("Lịch tưới vụ Đông Xuân", "Nội dung bản đầu");
        Long banDangPhucVu = bai.getPublishedVersionId();

        laBienTapVien();
        articles.update(bai.getPublicId(), banThao("Lịch tưới vụ Đông Xuân", "Nội dung bản sửa"));
        articles.execute(bai.getPublicId(), "SUBMIT", null);

        Article sauKhiSua = articles.get(bai.getPublicId());
        assertThat(sauKhiSua.getStatus()).isEqualTo(ArticleState.CHO_DUYET);
        assertThat(sauKhiSua.getPublishedVersionId())
                .as(
                        """
                        Đây là toàn bộ ý nghĩa của copy-on-write: cổng vẫn phục vụ bản CŨ trong lúc bản \
                        mới chờ duyệt. Nếu con trỏ này chạy theo bản sửa thì nội dung chưa ai duyệt đã \
                        nằm trên trang chủ.""")
                .isEqualTo(banDangPhucVu);
        assertThat(sauKhiSua.isPubliclyVisible(Instant.now()))
                .as("bài không được biến mất khỏi cổng chỉ vì có người bấm Sửa")
                .isTrue();

        laQuanTriNoiDung();
        Article sauKhiDuyet = articles.execute(bai.getPublicId(), "APPROVE", null);
        assertThat(sauKhiDuyet.getPublishedVersionId())
                .as("duyệt xong mới thay thế")
                .isNotEqualTo(banDangPhucVu);
    }

    @Test
    @DisplayName("⛔ Bài đang CHO_DUYET thì khoá chỉnh sửa — CMS-2007")
    void baiChoDuyetThiKhoaSua() {
        Article bai = articles.create(banThao("Bài đang chờ duyệt"));
        articles.execute(bai.getPublicId(), "SUBMIT", null);

        assertThatThrownBy(() -> articles.update(bai.getPublicId(), banThao("Bài đang chờ duyệt", "Sửa trộm")))
                .as("người duyệt đang đọc một bản; sửa dưới chân họ là họ duyệt cho thứ chưa từng thấy")
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CMS-2007");
    }

    @Test
    @DisplayName("Trả bài về giữ được lý do, và bài gửi lại được")
    void traBaiVeGiuLyDo() {
        Article bai = articles.create(banThao("Bài bị trả về"));
        articles.execute(bai.getPublicId(), "SUBMIT", null);

        laQuanTriNoiDung();
        articles.execute(bai.getPublicId(), "REQUEST_CHANGES", "Thiếu số liệu lưu lượng");

        Article bitra = articles.get(bai.getPublicId());
        assertThat(bitra.getStatus()).isEqualTo(ArticleState.YEU_CAU_CHINH_SUA);
        assertThat(bitra.getReviewNote()).isEqualTo("Thiếu số liệu lưu lượng");

        laBienTapVien();
        assertThat(articles.execute(bai.getPublicId(), "SUBMIT", null).getStatus())
                .as("YEU_CAU_CHINH_SUA là trạng thái riêng chứ không phải quay về NHAP — vẫn gửi lại được")
                .isEqualTo(ArticleState.CHO_DUYET);
    }

    @Test
    @DisplayName("Gỡ bài → khỏi cổng; đăng lại KHÔNG cần duyệt lại")
    void goBaiRoiDangLai() {
        Article bai = taoBaiDaXuatBan("Thông báo lịch cắt nước", "Nội dung");
        Long banPhucVu = bai.getPublishedVersionId();

        laQuanTriNoiDung();
        Article daGo = articles.execute(bai.getPublicId(), "UNPUBLISH", null);
        assertThat(daGo.getStatus()).isEqualTo(ArticleState.GO_BAI);
        assertThat(daGo.isPubliclyVisible(Instant.now())).isFalse();

        Article dangLai = articles.execute(bai.getPublicId(), "REPUBLISH", null);
        assertThat(dangLai.getStatus()).isEqualTo(ArticleState.XUAT_BAN);
        assertThat(dangLai.getPublishedVersionId())
                .as("nội dung không đổi thì không có gì để duyệt lại — chỉ bật lại hiển thị")
                .isEqualTo(banPhucVu);
    }

    @Test
    @DisplayName("Hẹn giờ đăng: đã duyệt nhưng chưa tới hạn thì chưa hiện")
    void henGioDang() {
        Instant maiMoiDang = Instant.now().plus(1, ChronoUnit.DAYS);
        Article bai = articles.create(banThao("Bài hẹn giờ", "Nội dung", maiMoiDang));
        articles.execute(bai.getPublicId(), "SUBMIT", null);

        laQuanTriNoiDung();
        Article daDuyet = articles.execute(bai.getPublicId(), "APPROVE", null);

        assertThat(daDuyet.getStatus()).isEqualTo(ArticleState.XUAT_BAN);
        assertThat(daDuyet.isPubliclyVisible(Instant.now()))
                .as("published_at ở tương lai = 'Đã lên lịch'; không cần trạng thái thứ bảy")
                .isFalse();
        assertThat(daDuyet.isPubliclyVisible(maiMoiDang.plusSeconds(1)))
                .as("tới giờ là tự hiện, kể cả khi job bắn revalidate có chết")
                .isTrue();
    }

    @Test
    @DisplayName("⛔ Slug trùng bị chặn cứng — CMS-2001, không phải chỉ cảnh báo")
    void slugTrungBiChan() {
        articles.create(banThao("Kiểm tra đê điều"));

        assertThatThrownBy(() -> articles.create(banThao("Kiểm tra đê điều")))
                .as("slug là địa chỉ công khai — trùng là hai bài tranh nhau một URL")
                .hasMessageContaining("CMS-2001");
    }

    @Test
    @DisplayName("⛔ Bài không có danh mục nào bị chặn — CMS-2006")
    void baiPhaiCoDanhMuc() {
        ArticleDraft khongDanhMuc = new ArticleDraft(
                "Bài mồ côi",
                null,
                null,
                "Nội dung",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Set.of(),
                List.of());

        assertThatThrownBy(() -> articles.create(khongDanhMuc)).hasMessageContaining("CMS-2006");
    }

    @Test
    @DisplayName("⛔ Xoá danh mục còn bài viết bị chặn — CMS-2003")
    void xoaDanhMucConBaiBiChan() {
        articles.create(banThao("Bài giữ chỗ danh mục"));

        dangNhap(1L, QUYEN_DANH_MUC);
        assertThatThrownBy(() -> categories.delete(danhMuc))
                .as("tự gỡ bài ra là lặng lẽ làm mồ côi nội dung rồi báo 'xoá thành công'")
                .hasMessageContaining("CMS-2003");
    }

    @Test
    @DisplayName("Phục hồi bản cũ ghi thêm phiên bản mới, không xoá lịch sử")
    void phucHoiBanCu() {
        Article bai = articles.create(banThao("Bài có nhiều bản", "Nội dung gốc"));
        UUID banGoc = articles.versionsOf(bai.getPublicId()).get(0).getPublicId();

        articles.update(bai.getPublicId(), banThao("Bài có nhiều bản", "Nội dung đã đổi"));
        assertThat(articles.get(bai.getPublicId()).getContent()).isEqualTo("Nội dung đã đổi");

        Article daPhucHoi = articles.restoreVersion(bai.getPublicId(), banGoc);

        assertThat(daPhucHoi.getContent()).isEqualTo("Nội dung gốc");
        assertThat(articles.versionsOf(bai.getPublicId()))
                .as(
                        """
                        Phục hồi là một LẦN SỬA chứ không phải lùi thời gian: bản bị thay vẫn còn dấu vết, \
                        vì đó đúng là bản người ta cần tra khi đi tìm 'ai đã bỏ đoạn này đi'.""")
                .hasSize(3);
    }

    @Test
    @DisplayName("⭐ Thông báo 'có bài chờ duyệt' tới người DUYỆT, không tới Ban điều hành")
    void thongBaoGuiDuyetToiDungNguoi() {
        Long nguoiDuyet = seedNguoiDungCoQuyen("qtnd-probe", QUYEN_DUYET);

        Article bai = articles.create(banThao("Bài kiểm người nhận"));
        articles.execute(bai.getPublicId(), "SUBMIT", null);

        List<Long> nguoiNhan = jdbc.queryForList(
                """
                SELECT r.user_id FROM notification_recipients r
                         JOIN notifications n ON n.id = r.notification_id
                WHERE n.event_type = 'ARTICLE_SUBMITTED'
                """,
                Long.class);

        assertThat(nguoiNhan)
                .as(
                        """
                        Trước WS-13, WorkflowEngine chỉ biết luật G11 (Ban điều hành ∪ người phụ trách đơn \
                        vị) — nghĩa là mỗi lần một biên tập viên bấm Gửi duyệt thì toàn bộ ban lãnh đạo \
                        nhận email. Vài tuần là không ai đọc thông báo nữa, và cảnh báo sự cố thật chết theo.""")
                .contains(nguoiDuyet);
    }

    // ---- Trợ giúp ------------------------------------------------------------

    private Article taoBaiDaXuatBan(String tieuDe, String noiDung) {
        laBienTapVien();
        Article bai = articles.create(banThao(tieuDe, noiDung));
        articles.execute(bai.getPublicId(), "SUBMIT", null);
        laQuanTriNoiDung();
        return articles.execute(bai.getPublicId(), "APPROVE", null);
    }

    private ArticleDraft banThao(String tieuDe) {
        return banThao(tieuDe, "Nội dung mặc định của bài kiểm.");
    }

    private ArticleDraft banThao(String tieuDe, String noiDung) {
        return banThao(tieuDe, noiDung, null);
    }

    private ArticleDraft banThao(String tieuDe, String noiDung, Instant hienLuc) {
        return new ArticleDraft(
                tieuDe,
                null,
                null,
                noiDung,
                null,
                null,
                null,
                hienLuc,
                null,
                null,
                null,
                null,
                null,
                Set.of(danhMuc),
                List.of());
    }

    private void laBienTapVien() {
        dangNhap(1L, QUYEN_XEM, QUYEN_TAO, QUYEN_SUA, QUYEN_GUI);
    }

    private void laQuanTriNoiDung() {
        dangNhap(2L, QUYEN_XEM, QUYEN_TAO, QUYEN_SUA, QUYEN_GUI, QUYEN_DUYET, QUYEN_XUAT_BAN, QUYEN_GO_BAI);
    }

    private static void dangNhap(Long userId, String... quyen) {
        AuthContext.set(new AuthenticatedUser(
                userId,
                UUID.randomUUID(),
                "probe-" + userId,
                "Người kiểm thử",
                1L,
                "/1/",
                Set.of("PROBE"),
                Set.of(quyen),
                false,
                UUID.randomUUID(),
                UUID.randomUUID()));
    }

    /**
     * Dựng một tài khoản thật mang quyền cần kiểm.
     *
     * <p>Phải là tài khoản trong CSDL chứ không phải {@code AuthContext}: người nhận thông báo được
     * tra bằng truy vấn {@code users × user_roles × role_permissions}, nên một danh tính chỉ tồn tại
     * trong bộ nhớ sẽ không bao giờ xuất hiện trong kết quả.
     */
    private Long seedNguoiDungCoQuyen(String username, String quyen) {
        jdbc.update(
                """
                INSERT INTO users (username, full_name, password_hash, status, org_unit_id)
                VALUES (?, 'Người duyệt kiểm thử', 'x', 'ACTIVE', 1)
                ON CONFLICT DO NOTHING
                """,
                username);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);

        jdbc.update(
                """
                INSERT INTO roles (code, name) VALUES ('PROBE_APPROVER', 'Vai trò kiểm thử')
                ON CONFLICT DO NOTHING
                """);
        Long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'PROBE_APPROVER'", Long.class);

        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT ?, id FROM permissions WHERE code = ? ON CONFLICT DO NOTHING",
                roleId,
                quyen);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING", userId, roleId);
        return userId;
    }

    private String trangThaiTrongCsdl(UUID publicId) {
        return jdbc.queryForObject("SELECT status FROM articles WHERE public_id = ?", String.class, publicId);
    }

    /**
     * ⚠ Thứ tự bắt buộc và luật "giữ lại dòng seed" nằm ở {@link CmsFixtures} — từ WS-15 có menu trỏ
     * tới cả danh mục lẫn bài viết, nên xoá sạch hai bảng đó là vi phạm khoá ngoại.
     */
    private void donDepDuLieuCms() {
        CmsFixtures.donDep(jdbc);
        jdbc.update("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE code = 'PROBE_APPROVER')");
        jdbc.update("DELETE FROM users WHERE username LIKE 'qtnd-probe%'");
    }
}
