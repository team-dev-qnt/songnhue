package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;

/**
 * <b>Bài viết rỗng trên thực tế phải bị từ chối</b> — T41.21.
 *
 * <h2>Vì sao {@code @NotBlank} không đủ, và vì sao chuyện này im lặng</h2>
 *
 * Trường {@code content} của DTO mang {@code @NotBlank}, nên bản ghi chép trước đây khẳng định
 * <i>"nội dung rỗng vẫn đi ra dây và bị backend từ chối"</i>. Khẳng định ấy <b>sai</b>: một trình
 * soạn thảo trống ⛔ không gửi lên chuỗi rỗng — TipTap luôn giữ ít nhất một đoạn văn, nên thứ đi ra
 * dây là {@code <p></p>}. Chuỗi ấy dài 7 ký tự, {@code @NotBlank} cho qua, và bài được lưu.
 *
 * <p>Hậu quả không dừng ở một bản ghi thừa: bài ấy đi tiếp qua quy trình duyệt như mọi bài khác,
 * người duyệt thấy một tiêu đề hợp lệ, và cổng công khai đăng <b>một trang trắng mang tiêu đề</b>.
 * ⛔ Không lỗi nào, không cảnh báo nào ở bất kỳ khâu nào.
 *
 * <h2>Phép đo là "có gì trong bài", ⛔ không phải "chuỗi khác rỗng"</h2>
 *
 * Một bài chỉ gồm một tấm ảnh, một bảng số liệu hay một video nhúng là <b>bài hợp lệ</b> — đó chính
 * là hình dạng của thông báo mực nước và của bản tin ảnh. Nên bài kiểm này phải chứng minh
 * <b>cả hai chiều</b>: chặn đúng thứ rỗng, và ⛔ không chặn nhầm thứ không có chữ (luật 9 — một
 * khẳng định không phân biệt được hai trạng thái thì không khẳng định gì).
 */
class ArticleContentRongTest extends IntegrationTestBase {

    private static final String MA_LOI = "CMS-2023";

    private static final String QUYEN_TAO = "cms:article:create";
    private static final String QUYEN_SUA = "cms:article:update";
    private static final String QUYEN_XEM = "cms:article:view";
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
        dangNhap(1L, QUYEN_DANH_MUC);
        danhMuc = categories.create("Chuyên mục bài rỗng", null, null).getPublicId();
        dangNhap(1L, QUYEN_XEM, QUYEN_TAO, QUYEN_SUA);
    }

    @AfterEach
    void donDep() {
        AuthContext.clear();
        donDepDuLieuCms();
    }

    // ---- Chiều CHẶN ---------------------------------------------------------

    @Test
    @DisplayName("⭐⭐ Trình soạn thảo trống gửi `<p></p>` — phải bị từ chối bằng CMS-2023")
    void trinhSoanThaoTrongBiTuChoi() {
        // ⚠ Đây ĐÚNG chuỗi TipTap gửi khi người dùng chưa gõ gì, không phải một chuỗi bịa cho dễ đỏ.
        assertThatThrownBy(() -> articles.create(banThao("Bài chưa gõ gì", "<p></p>")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(MA_LOI);
    }

    @Test
    @DisplayName("Chỉ khoảng trắng không ngắt cũng là rỗng — `<p>&nbsp;</p>`")
    void chiKhoangTrangKhongNgatCungLaRong() {
        assertThatThrownBy(() -> articles.create(banThao("Bài toàn dấu cách", "<p>&nbsp;&nbsp;</p>")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(MA_LOI);
    }

    @Test
    @DisplayName("⭐ Bài rỗng SAU khi khử trùng cũng bị chặn — đo trên chuỗi đã lọc, không phải chuỗi gửi lên")
    void rongSauKhiKhuTrungCungBiChan() {
        // `<script>` bị jsoup gỡ sạch ⇒ còn lại rỗng. Nếu phép đo chạy trên chuỗi NGƯỜI DÙNG gửi thì
        // chuỗi này trông "có nội dung" và đi lọt.
        assertThatThrownBy(() -> articles.create(banThao("Bài chỉ có script", "<script>alert(1)</script>")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(MA_LOI);
    }

    @Test
    @DisplayName("⭐⭐ Đường SỬA cũng bị chặn — hai cửa, một chốt")
    void duongSuaCungBiChan() {
        Article bai = articles.create(banThao("Bài có nội dung thật", "<p>Nội dung ban đầu.</p>"));
        UUID id = bai.getPublicId();

        assertThatThrownBy(() -> articles.update(id, banThao("Bài có nội dung thật", "<p></p>")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(MA_LOI);

        // ⭐ Và bản cũ phải còn nguyên: một lượt sửa bị từ chối ⛔ không được để lại nội dung nửa vời.
        Integer conNoiDung = jdbc.queryForObject(
                "SELECT count(*) FROM articles WHERE public_id = ? AND content LIKE '%Nội dung ban đầu%'",
                Integer.class, id);
        assertThat(conNoiDung)
                .as("Lượt sửa bị chặn mà nội dung cũ đã mất — giao dịch không rollback")
                .isEqualTo(1);
    }

    // ---- Chiều KHÔNG chặn nhầm (luật 9) -------------------------------------

    @Test
    @DisplayName("⭐⭐ Bài chỉ gồm MỘT TẤM ẢNH vẫn hợp lệ — bản tin ảnh là hình dạng có thật")
    void baiChiGomAnhVanHopLe() {
        String chiAnh = "<figure class=\"sn-align-center\">"
                + "<img src=\"/api/v1/public/files/8a7b6c5d-0000-0000-0000-000000000000\" alt=\"Ảnh\">"
                + "</figure>";
        assertThatCode(() -> articles.create(banThao("Bản tin ảnh", chiAnh))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("⭐ Bài chỉ gồm MỘT BẢNG SỐ LIỆU vẫn hợp lệ — thông báo mực nước là hình dạng có thật")
    void baiChiGomBangVanHopLe() {
        String chiBang = "<table><tbody><tr><td></td></tr></tbody></table>";
        assertThatCode(() -> articles.create(banThao("Thông báo mực nước", chiBang)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Bài có chữ bình thường thì đi qua — vế tiền đề, nếu không mọi bài trên xanh vì lý do sai")
    void baiCoChuDiQua() {
        assertThatCode(() -> articles.create(banThao("Bài thường", "<p>Một câu có chữ.</p>")))
                .doesNotThrowAnyException();
    }

    // ---- Bất biến cấu trúc --------------------------------------------------

    @Test
    @DisplayName("⭐⭐ Chỉ có MỘT đường đặt nội dung — thêm cửa thứ ba mà quên chốt là ĐỎ ở đây")
    void chiCoMotDuongDatNoiDung() {
        // Quy tắc 12: chốt chặn đặt ở chỗ dữ liệu đi qua. Bài này canh đúng điều đó — nếu ai đó thêm
        // một đường ghi mới gọi thẳng `HtmlSanitizer.clean(draft.content())`, chốt bị vòng qua trong
        // im lặng và chỉ lộ ra khi cổng đăng một trang trắng.
        String nguon = docNguonArticleService();

        assertThat(nguon)
                .as("Còn lời gọi `HtmlSanitizer.clean(draft.content())` đi thẳng — nó vòng qua CMS-2023")
                .doesNotContain("HtmlSanitizer.clean(draft.content())");

        int soLoiGoi = demXuatHien(nguon, "lamSachNoiDung(draft.content())");
        assertThat(soLoiGoi)
                .as("Phải có ĐÚNG hai lời gọi `lamSachNoiDung` — một cho `create`, một cho `update`")
                .isEqualTo(2);

        assertThat(demXuatHien(nguon, "private static String lamSachNoiDung"))
                .as("Chốt chặn phải là MỘT hàm, không phải hai bản chép")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Tự kiểm chứng: phép đếm trên đọc được mã nguồn thật, ⛔ không xanh trên chuỗi rỗng")
    void phepDemDocDuocNguonThat() {
        // conventions.md §1.5 — `docNguonArticleService()` trả rỗng thì bài trên xanh mà không so gì.
        String nguon = docNguonArticleService();
        assertThat(nguon).hasSizeGreaterThan(10_000);
        assertThat(demXuatHien(nguon, "class ArticleService")).isEqualTo(1);
        // Và phép đếm phải phân biệt được hai trạng thái, nếu không nó không khẳng định gì (luật 9).
        assertThat(demXuatHien(nguon, "mot_chuoi_khong_bao_gio_co_trong_ma_nguon"))
                .isZero();
    }

    // -------------------------------------------------------------------------

    /** Khuôn của {@code ArticleLifecycleTest} — danh tính chỉ sống trong {@link AuthContext}. */
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
                UUID.randomUUID(),
                null));
    }

    /** ⚠ Thứ tự bắt buộc nằm ở {@link CmsFixtures} — menu trỏ tới cả danh mục lẫn bài viết. */
    private void donDepDuLieuCms() {
        CmsFixtures.donDep(jdbc);
    }

    private ArticleDraft banThao(String tieuDe, String noiDung) {
        return new ArticleDraft(
                tieuDe,
                null,
                null,
                noiDung,
                null,
                null,
                null,
                (Instant) null,
                null,
                null,
                null,
                null,
                null,
                Set.of(danhMuc),
                List.of());
    }

    private static int demXuatHien(String trong, String mau) {
        int so = 0;
        int i = trong.indexOf(mau);
        while (i >= 0) {
            so++;
            i = trong.indexOf(mau, i + mau.length());
        }
        return so;
    }

    private static String docNguonArticleService() {
        Path p = timTuGocKho("backend/content/src/main/java/com/songnhue/content/application/ArticleService.java");
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fail("Không đọc được " + p, e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
