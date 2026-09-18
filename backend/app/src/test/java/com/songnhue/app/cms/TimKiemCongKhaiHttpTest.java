package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.TestHttp;

/**
 * <b>Tìm kiếm công khai — CN-01.8.</b> WS-36 / T36.10.
 *
 * <h2>Ba phần đợt này dựng, và vì sao chúng phải đi bằng HTTP</h2>
 *
 * <ol>
 *   <li><b>Phạm vi Công trình</b> — một endpoint mới. Câu truy vấn dùng {@code sn_khong_dau},
 *       một hàm <b>của CSDL</b>: gọi thẳng service trên một mock repository ⛔ không chạy nó, và
 *       đúng chỗ ấy đã từng hỏng (*"function sn_khong_dau(bytea) does not exist"* khi tham số
 *       {@code null}).
 *   <li><b>Khoảng ngày đăng</b> — quy đổi ngày dương lịch <b>giờ VN</b> sang mốc UTC nằm ở tầng
 *       controller, ⛔ không ở service.
 *   <li><b>Ranh giới dữ liệu</b> — record công khai của công trình phải hẹp hơn bản quản trị.
 * </ol>
 *
 * <h2>⛔⛔ Bài chịu lực là {@link #denNgayLayTronCaNgayHomAy()}</h2>
 *
 * <p>Nhận thẳng {@code 00:00} của ngày người dùng chọn thì <b>mọi bài đăng trong chính ngày
 * đó</b> bị loại. Triệu chứng: <i>"lọc tới hôm nay thì mất tin hôm nay"</i> — một thứ ⛔ không ai
 * đọc ra được từ mã, và ⛔ không bài kiểm nào chạm tới nếu chỉ kiểm "bộ lọc có tác dụng".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimKiemCongKhaiHttpTest extends IntegrationTestBase {

    private static final String BAI_VIET = "/api/v1/public/articles";
    private static final String CONG_TRINH = "/api/v1/public/constructions/tim-kiem";

    @Autowired
    private TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM constructions WHERE code LIKE 'T3610%'");
    }

    // ═══════════════ Phạm vi Công trình ═══════════════

    @Test
    @DisplayName("⭐⭐ Tìm công trình KHÔNG DẤU — và câu truy vấn chạy thật trên `sn_khong_dau`")
    void timCongTrinhKhongDau() {
        taoCongTrinh("T3610A", "Cống Liên Mạc");
        taoCongTrinh("T3610B", "Trạm bơm Đan Hoài");

        String than = tai(CONG_TRINH + "?q=lien mac");

        assertThat(than)
                .as("⛔ Gõ không dấu phải ra chữ CÓ dấu — đó là cả lý do `sn_khong_dau` tồn tại")
                .contains("Cống Liên Mạc");
        assertThat(than)
                .as("⛔ Một bộ lọc bị bỏ qua trông y hệt một bộ lọc hoạt động, cho tới khi ai đó "
                        + "gõ một từ khoá hẹp và nhận về cả danh mục")
                .doesNotContain("Đan Hoài");
    }

    @Test
    @DisplayName("⭐ Tìm được theo MÃ công trình, ⛔ không chỉ theo tên")
    void timTheoMa() {
        taoCongTrinh("T3610C", "Đập tràn số 3");

        assertThat(tai(CONG_TRINH + "?q=T3610C")).contains("Đập tràn số 3");
    }

    @Test
    @DisplayName("⛔⛔ Từ khoá RỖNG trả về trang RỖNG — ⛔ KHÔNG đổ ra cả danh mục")
    void tuKhoaRongThiRong() {
        taoCongTrinh("T3610D", "Cống Hà Đông");

        // ⛔ "Tìm mọi thứ" đã có `GET /constructions` phục vụ, ở một trang riêng, có gom nhóm theo
        //    Xí nghiệp. Hai đường cho cùng một câu hỏi là một đường ⛔ không ai kiểm.
        assertThat(tai(CONG_TRINH)).doesNotContain("Cống Hà Đông");
        assertThat(tai(CONG_TRINH + "?q=")).doesNotContain("Cống Hà Đông");
        assertThat(tai(CONG_TRINH + "?q=%20%20")).doesNotContain("Cống Hà Đông");
    }

    @Test
    @DisplayName("⛔ Công trình ĐÃ THANH LÝ ⛔ không ra kết quả — khớp đúng luật của danh mục")
    void daThanhLyThiKhongRa() {
        taoCongTrinh("T3610E", "Cống đã thanh lý");
        jdbc.update("UPDATE constructions SET lifecycle_state = 'DA_THANH_LY' WHERE code = 'T3610E'");

        // ⛔ Lệch luật với `catalogByUnit()` nghĩa là tìm kiếm trả về một công trình mà bấm vào
        //    thì ⛔ không có trong danh mục — hoặc ngược lại.
        assertThat(tai(CONG_TRINH + "?q=da thanh ly")).doesNotContain("Cống đã thanh lý");
    }

    @Test
    @DisplayName("⛔⛔ Kết quả công trình ⛔ KHÔNG mang toạ độ, ⛔ không mang mã tệp nội bộ")
    void ketQuaCongTrinhKhongLoTruongThua() {
        taoCongTrinh("T3610F", "Cống Nhật Tựu");

        String than = tai(CONG_TRINH + "?q=nhat tuu");

        assertThat(than).as("⚠ vế chống tập rỗng (luật 7)").contains("Cống Nhật Tựu");
        // ⛔ Trang kết quả liệt kê nhiều loại đối tượng cạnh nhau; mỗi dòng chỉ mang thứ đủ để
        //    nhận ra và bấm vào. Toạ độ và mã tệp thuộc trang Danh mục công trình.
        assertThat(than)
                .doesNotContain("latitude")
                .doesNotContain("longitude")
                .doesNotContain("operatingProcedureFileId")
                .doesNotContain("protectionPlanFileId");
    }

    // ═══════════════ Khoảng ngày đăng của bài viết ═══════════════

    @Test
    @DisplayName("⛔⛔ `denNgay` lấy TRỌN ngày hôm ấy — ⛔ không cắt ở 00:00")
    void denNgayLayTronCaNgayHomAy() {
        // Bài đăng lúc 15:00 giờ VN của một ngày cụ thể.
        LocalDate ngay = LocalDate.of(2026, 3, 15);
        Long id = taoBaiDaXuatBan("t3610-loc-ngay", "Bài đăng giữa trưa", ngay + "T15:00:00+07:00");

        try {
            assertThat(tai(BAI_VIET + "?denNgay=" + ngay))
                    .as("⛔⛔ Quy `denNgay` về 00:00 của CHÍNH ngày ấy loại sạch mọi bài đăng "
                            + "TRONG ngày đó. Triệu chứng: \"lọc tới hôm nay thì mất tin hôm "
                            + "nay\" — ⛔ không ai đọc ra được từ mã.")
                    .contains("Bài đăng giữa trưa");

            assertThat(tai(BAI_VIET + "?tuNgay=" + ngay))
                    .as("⭐ Vế phân biệt: `tuNgay` lấy TỪ 00:00 nên bài 15:00 cùng ngày phải VÀO")
                    .contains("Bài đăng giữa trưa");

            assertThat(tai(BAI_VIET + "?denNgay=" + ngay.minusDays(1)))
                    .as("⛔ Vế đối chứng — ⛔ không có nó thì một bộ lọc bị BỎ QUA cũng xanh ở cả "
                            + "hai khẳng định trên (luật 9)")
                    .doesNotContain("Bài đăng giữa trưa");
            assertThat(tai(BAI_VIET + "?tuNgay=" + ngay.plusDays(1))).doesNotContain("Bài đăng giữa trưa");
        } finally {
            xoaBai(id);
        }
    }

    @Test
    @DisplayName("⭐ Khoảng ngày MỞ MỘT ĐẦU là hợp lệ — ⛔ không phải một tổ hợp cần chặn")
    void khoangNgayMoMotDau() {
        Long id = taoBaiDaXuatBan("t3610-mo-mot-dau", "Bài mở một đầu", "2026-03-15T09:00:00+07:00");
        try {
            assertThat(tai(BAI_VIET + "?tuNgay=2026-01-01")).contains("Bài mở một đầu");
            assertThat(tai(BAI_VIET + "?denNgay=2026-12-31")).contains("Bài mở một đầu");
        } finally {
            xoaBai(id);
        }
    }

    // ─────────────── Tiện ích ───────────────

    private String tai(String duong) {
        ResponseEntity<String> r = http.getForEntity(duong, String.class);
        assertThat(r.getStatusCode()).as("GET %s", duong).isEqualTo(HttpStatus.OK);
        return r.getBody() == null ? "" : r.getBody();
    }

    private void taoCongTrinh(String ma, String ten) {
        jdbc.update(
                """
                INSERT INTO constructions (code, name, construction_type, management_level,
                                           lifecycle_state, operational_status, org_unit_id)
                SELECT ?, ?, 'CONG', 'XI_NGHIEP', 'DANG_HOAT_DONG', 'BINH_THUONG', u.id
                  FROM org_units u WHERE u.deleted_at IS NULL ORDER BY u.id LIMIT 1
                """,
                ma,
                ten);
    }

    /** Bài đã xuất bản kèm một bản nội dung — đường đọc công khai JOIN vào bản ấy. */
    private Long taoBaiDaXuatBan(String slug, String tieuDe, String dangLuc) {
        jdbc.update(
                """
                INSERT INTO articles (title, slug, content, author_user_id, status, published_at)
                SELECT ?, ?, '<p>x</p>', u.id, 'XUAT_BAN', ?::timestamptz
                  FROM users u ORDER BY u.id LIMIT 1
                """,
                tieuDe,
                slug,
                dangLuc);
        Long articleId = jdbc.queryForObject("SELECT id FROM articles WHERE slug = ?", Long.class, slug);
        jdbc.update(
                "INSERT INTO article_versions (article_id, version_no, title, slug, content) "
                        + "VALUES (?, 1, ?, ?, '<p>x</p>')",
                articleId,
                tieuDe,
                slug);
        Long versionId =
                jdbc.queryForObject("SELECT id FROM article_versions WHERE article_id = ?", Long.class, articleId);
        jdbc.update("UPDATE articles SET published_version_id = ? WHERE id = ?", versionId, articleId);
        return articleId;
    }

    private void xoaBai(Long articleId) {
        jdbc.update("UPDATE articles SET published_version_id = NULL WHERE id = ?", articleId);
        jdbc.update("DELETE FROM article_versions WHERE article_id = ?", articleId);
        jdbc.update("DELETE FROM articles WHERE id = ?", articleId);
    }
}
