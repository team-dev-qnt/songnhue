package com.songnhue.app.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.SongnhuePostgres;

/**
 * <b>Migration seed nội dung chạy THẬT trên lược đồ THẬT.</b>
 *
 * <h2>Vì sao bài này phải tồn tại</h2>
 *
 * Bộ seed vừa chuyển từ script bấm tay sang migration Flyway. Một migration <b>xoá dữ liệu</b> và
 * chạy <b>một chiều</b>: chỗ duy nhất nó lộ ra là lượt triển khai, tức là quá muộn. Cả hai lỗi tìm
 * ra khi chạy thật bản script (đọc {@code deploy/seed/README.md}) đều là loại <b>không đọc lược đồ
 * mà thấy được</b>:
 *
 * <ul>
 *   <li>{@code ON CONFLICT (slug)} không khớp, vì {@code uq_articles_slug} là chỉ mục MỘT PHẦN;
 *   <li>{@code author_user_id} là {@code NOT NULL}.
 * </ul>
 *
 * Và bản migration thêm một rủi ro mới mà script cũ không có: khối <b>xoá bài</b>.
 * {@code menu_items.article_id} tham chiếu {@code articles(id)} không khai {@code ON DELETE} — tức
 * RESTRICT — nên một vị từ sai là migration dừng giữa chừng, <b>sau khi đã xoá được một phần</b>.
 *
 * <h2>Cách chạy: giao dịch rồi quay lui</h2>
 *
 * Bài chạy trên đúng CSDL đã migrate của bộ test tích hợp, bằng vai trò {@code songnhue_owner} (vai
 * trò mà Flyway dùng thật — {@code songnhue_app} cố ý không đủ quyền), rồi {@code ROLLBACK}. Không
 * dựng CSDL riêng: một CSDL "gần giống" là một lược đồ thứ hai sẽ trôi xa dần, mà bản test thì luôn
 * xanh.
 *
 * <p>⚠ Khối xoá được kiểm bằng một bài viết <b>dựng riêng</b> trước khi chạy: nếu không, "xoá đúng"
 * và "không xoá gì cả" cho ra cùng một kết quả — đúng dạng CLAUDE.md luật 9.
 */
class SeedPortalMigrationTest extends IntegrationTestBase {

    private static final String TEP_SEED = "db/seed/portal/V202608251100__seed_portal_content.sql";

    /** public_id của một ảnh seed — cũng chính là đích mà smoke test của CD gọi tới. */
    private static final String ANH_MOC = "15509c57-8e04-57e6-8d36-6a9cd1c68334";

    @Test
    @DisplayName("⭐⭐ Khối xoá đúng phạm vi: dọn bài rác, giữ nguyên 4 trang tĩnh và cây menu")
    void xoaDungPhamVi() throws Exception {
        try (Connection ket = moKetNoiChuSoHuu()) {
            ket.setAutoCommit(false);
            try (Statement st = ket.createStatement()) {

                // ── Dựng hiện trường: một bài "rác" KHÔNG có menu nào trỏ tới ────────
                // Nếu thiếu bước này thì "khối xoá chạy đúng" và "khối xoá không chạy"
                // cho ra cùng một kết quả, và bài kiểm không khẳng định được gì.
                st.execute(
                        """
                        INSERT INTO articles (title, slug, summary, content, author_user_id, created_by, status)
                        SELECT 'Bài rác dựng riêng cho phép kiểm', 'bai-rac-cho-phep-kiem', 'x', '<p>x</p>',
                               u.id, u.id, 'NHAP'
                          FROM users u WHERE u.username = 'superadmin'
                        """);
                assertThat(dem(st, "SELECT count(*) FROM articles WHERE slug = 'bai-rac-cho-phep-kiem'"))
                        .as("hiện trường chưa dựng được — các khẳng định sau sẽ vô nghĩa")
                        .isEqualTo(1);

                long menuTruoc = dem(st, "SELECT count(*) FROM menu_items");
                assertThat(menuTruoc)
                        .as("CSDL test phải có cây menu của V202608191021")
                        .isPositive();

                // ── Chạy migration seed ─────────────────────────────────────────────
                st.execute(docTepSeed());

                // ── 1. Khối xoá đã THẬT SỰ chạy ─────────────────────────────────────
                assertThat(dem(st, "SELECT count(*) FROM articles WHERE slug = 'bai-rac-cho-phep-kiem'"))
                        .as("bài không có menu trỏ tới vẫn còn — khối xoá chưa chạy hoặc vị từ sai")
                        .isZero();

                // ── 2. …nhưng KHÔNG chạm 4 trang tĩnh và cây menu ───────────────────
                assertThat(dem(st, "SELECT count(*) FROM articles WHERE slug = 'tong-quan'"))
                        .as(
                                """
                                Trang tĩnh CÒN mục menu trỏ tới đã bị xoá — menu Header/Footer gãy theo.                                 Vị từ theo quan hệ của bộ seed sinh ra để bảo vệ đúng trường hợp này.""")
                        .isEqualTo(1);

                // ⭐ Vế ngược, và nó mới là phần đắt: ba trang tĩnh MẤT mục menu ở V202608271031
                //    (CR-22/23/24 thay chúng bằng trang thật ở đường dẫn khác) thì bộ seed PHẢI
                //    dọn đi. Không có khẳng định này thì một vị từ "bảo vệ mọi bài do migration
                //    tạo" cũng xanh y hệt — trong khi nó để lại ba bài mồ côi rỗng trên cổng.
                assertThat(
                                dem(
                                        st,
                                        """
                                SELECT count(*) FROM articles
                                 WHERE slug IN ('chuc-nang-nhiem-vu','co-cau-to-chuc','lien-he')
                                """))
                        .as("ba trang tĩnh đã bị cây nội dung mới thay thế vẫn còn — chúng không còn lối vào nào")
                        .isZero();
                assertThat(dem(st, "SELECT count(*) FROM menu_items")).isEqualTo(menuTruoc);
                assertThat(
                                dem(
                                        st,
                                        """
                                SELECT count(*) FROM menu_items m
                                 WHERE m.article_id IS NOT NULL
                                   AND NOT EXISTS (SELECT 1 FROM articles a WHERE a.id = m.article_id)
                                """))
                        .as("có mục menu trỏ vào bài không còn tồn tại")
                        .isZero();

            } finally {
                ket.rollback();
            }
        }
    }

    @Test
    @DisplayName("⭐ Nội dung seed vào đủ, và chạy lại không nhân đôi")
    void noiDungVaoDuVaChayLaiKhongNhanDoi() throws Exception {
        try (Connection ket = moKetNoiChuSoHuu()) {
            ket.setAutoCommit(false);
            try (Statement st = ket.createStatement()) {
                st.execute(docTepSeed());

                // ── 3. Nội dung seed đã vào đủ ──────────────────────────────────────
                assertThat(dem(st, "SELECT count(*) FROM attachments WHERE purpose = 'SEED_PORTAL'"))
                        .isEqualTo(4);
                assertThat(dem(st, "SELECT count(*) FROM articles WHERE source LIKE 'http%'"))
                        .isEqualTo(5);
                assertThat(dem(
                                st,
                                "SELECT count(*) FROM articles WHERE source LIKE 'http%' "
                                        + "AND published_version_id IS NOT NULL"))
                        .as("bài không có `published_version_id` thì cổng dựng ra một trang hợp lệ mà RỖNG")
                        .isEqualTo(5);
                assertThat(
                                dem(
                                        st,
                                        """
                                SELECT count(*) FROM article_versions v
                                  JOIN articles a ON a.id = v.article_id
                                 WHERE a.source LIKE 'http%' AND v.cover_attachment_public_id IS NOT NULL
                                """))
                        .as(
                                "thiếu ảnh bìa thì danh sách bài trên cổng không có thumbnail — cột `ArticleRepository` đọc")
                        .isEqualTo(5);
                assertThat(
                                dem(
                                        st,
                                        """
                                SELECT count(*) FROM article_categories ac
                                  JOIN articles a ON a.id = ac.article_id
                                  JOIN categories c ON c.id = ac.category_id
                                 WHERE a.source LIKE 'http%' AND c.slug = 'tin-tuc'
                                """))
                        .as("bài không gắn danh mục thì không xuất hiện ở trang danh mục nào")
                        .isEqualTo(5);

                // ── 4. Không còn đường dẫn ảnh ghi cứng của bản local ────────────────
                assertThat(dem(st, "SELECT count(*) FROM article_versions WHERE content LIKE '%/images/%'"))
                        .as("còn `src=\"/images/…\"` — đường ấy trả 404 ở giao diện quản trị")
                        .isZero();
                assertThat(dem(
                                st,
                                "SELECT count(*) FROM article_versions WHERE content LIKE '%/api/v1/public/files/"
                                        + ANH_MOC + "%'"))
                        .as("ảnh mốc mà smoke test của CD gọi tới không xuất hiện trong thân bài nào")
                        .isPositive();

                // ── 5. Chạy lại không nhân đôi ──────────────────────────────────────
                // Flyway sẽ không chạy lại tệp có phiên bản, nhưng người vận hành thì có
                // thể `psql -f` nó lúc chữa cháy. Lời hứa "idempotent" ở đầu tệp phải đúng.
                st.execute(docTepSeed());
                assertThat(dem(st, "SELECT count(*) FROM articles WHERE source LIKE 'http%'"))
                        .as("chạy lần hai nhân đôi số bài — `ON CONFLICT` không khớp chỉ mục")
                        .isEqualTo(5);
                assertThat(dem(st, "SELECT count(*) FROM attachments WHERE purpose = 'SEED_PORTAL'"))
                        .isEqualTo(4);
                assertThat(dem(
                                st,
                                "SELECT count(*) FROM article_versions v JOIN articles a ON a.id = v.article_id "
                                        + "WHERE a.source LIKE 'http%'"))
                        .as("chạy lần hai sinh thêm bản nội dung")
                        .isEqualTo(5);
            } finally {
                // ⚠ Quay lui LUÔN, kể cả khi bài đỏ: bài này xoá bài viết, và CSDL test dùng
                //   chung cho cả lượt chạy JVM.
                ket.rollback();
            }
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Vai trò {@code songnhue_owner} — đúng vai trò Flyway dùng. Chạy bằng {@code songnhue_app} sẽ
     * đỏ vì thiếu quyền, và đó là một lỗi giả không nói gì về bộ seed.
     */
    private static Connection moKetNoiChuSoHuu() throws SQLException {
        return DriverManager.getConnection(
                SongnhuePostgres.instance().getJdbcUrl(), "songnhue_owner", SongnhuePostgres.password());
    }

    /** Đọc từ CLASSPATH, không đọc từ đĩa: cũng là phép kiểm rằng tệp seed được đóng vào jar. */
    private static String docTepSeed() throws IOException {
        try (InputStream vao = SeedPortalMigrationTest.class.getClassLoader().getResourceAsStream(TEP_SEED)) {
            assertThat(vao)
                    .as("`%s` không có trên classpath — Flyway ở migrator cũng sẽ không thấy nó", TEP_SEED)
                    .isNotNull();
            return new String(vao.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long dem(Statement st, String cau) throws SQLException {
        try (ResultSet rs = st.executeQuery(cau)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }
}
