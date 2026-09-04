package com.songnhue.app.testsupport;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dọn dữ liệu CMS giữa các bài kiểm — <b>giữ nguyên dữ liệu do migration seed</b>.
 *
 * <h2>Vì sao phải có lớp này từ WS-15</h2>
 *
 * Trước đó mỗi bài kiểm CMS tự dọn bằng {@code DELETE FROM categories} / {@code articles}. Từ khi
 * migration V…1021 seed khung danh mục, bốn trang tĩnh và hai cây menu, cách đó hỏng theo <b>hai</b>
 * đường cùng lúc:
 *
 * <ul>
 *   <li>{@code menu_items} có khoá ngoại tới {@code categories} và {@code articles}, nên xoá sạch hai
 *       bảng kia là vi phạm ràng buộc — bài kiểm đỏ vì lý do chẳng liên quan tới thứ nó kiểm.
 *   <li>Xoá luôn cả dữ liệu seed thì bài kiểm chạy sau thấy một cổng rỗng. Thứ tự giữa các lớp không
 *       xác định, nên đó là công thức của loại đỏ-thỉnh-thoảng.
 * </ul>
 *
 * <h2>⚠ Cách phân biệt dòng seed: mốc id, KHÔNG phải {@code created_by}</h2>
 *
 * Bản đầu của lớp này dùng {@code created_by IS NOT NULL} làm dấu hiệu "dòng do bài kiểm tạo". Nó
 * <b>không chạy</b>, và lý do đáng ghi lại: {@code AuditorAwareImpl} đọc {@link
 * com.songnhue.core.common.filter.AuditContext} — thứ do <i>filter</i> đặt — chứ không đọc
 * {@code AuthContext}. Bài kiểm tích hợp chỉ đặt {@code AuthContext}, nên <b>mọi dòng do bài kiểm
 * tạo cũng có {@code created_by = NULL}</b>, y hệt dòng seed. Phép dọn không xoá gì cả, dữ liệu tích
 * tụ qua từng bài, và triệu chứng là những con số đếm cứ lớn dần.
 *
 * <p>Thay bằng <b>mốc id</b>: khoá chính là IDENTITY tăng dần, còn migration chạy trước mọi bài kiểm,
 * nên mọi dòng seed đều có id nhỏ hơn mọi dòng do bài kiểm tạo. Ghi lại mốc ở lượt dọn <i>đầu
 * tiên</i> — lượt đó luôn nằm trong {@code @BeforeEach} của bài kiểm đầu tiên, tức là trước khi có ai
 * chèn gì.
 *
 * <p>Giữ lại dữ liệu seed <b>là điều đúng</b> chứ không phải nhân nhượng: môi trường thật cũng có
 * đúng những dòng đó. Bài kiểm nào chỉ xanh trên một cơ sở dữ liệu rỗng thì nó đang kiểm một hệ thống
 * không tồn tại.
 */
public final class CmsFixtures {

    private CmsFixtures() {}

    /** Id lớn nhất của mỗi bảng tại thời điểm chỉ còn dữ liệu seed. */
    private static Map<String, Long> mocSeed;

    /** Thứ tự các lệnh là thứ tự khoá ngoại cho phép — đổi chỗ là vi phạm ràng buộc. */
    public static synchronized void donDep(JdbcTemplate jdbc) {
        if (mocSeed == null) {
            mocSeed = ghiNhanMocSeed(jdbc);
        }
        long mocDanhMuc = mocSeed.get("categories");
        long mocBaiViet = mocSeed.get("articles");
        long mocMenu = mocSeed.get("menu_items");

        jdbc.update("DELETE FROM notification_recipients WHERE notification_id IN "
                + "(SELECT id FROM notifications WHERE event_type LIKE 'ARTICLE_%')");
        jdbc.update("DELETE FROM notifications WHERE event_type LIKE 'ARTICLE_%'");

        // Menu trước: nó trỏ tới cả danh mục lẫn bài viết.
        jdbc.update("DELETE FROM menu_items WHERE id > ?", mocMenu);

        // Banner trước attachments: ảnh banner là khoá ngoại KHÔNG có ON DELETE.
        jdbc.update("DELETE FROM banners");

        jdbc.update("UPDATE articles SET published_version_id = NULL WHERE id > ?", mocBaiViet);
        jdbc.update("DELETE FROM article_versions WHERE article_id > ?", mocBaiViet);
        jdbc.update("DELETE FROM article_categories WHERE article_id > ? OR category_id > ?", mocBaiViet, mocDanhMuc);
        jdbc.update("DELETE FROM article_tags WHERE article_id > ?", mocBaiViet);
        jdbc.update("DELETE FROM articles WHERE id > ?", mocBaiViet);
        jdbc.update("DELETE FROM categories WHERE id > ?", mocDanhMuc);

        // ⚠ `TAI_LIEU` vào danh sách 04/09 (WS-40) — kho tài liệu dùng CHUNG bảng `attachments` với
        //   thư viện media, chỉ khác `owner_type`. Quên nó thì tệp tài liệu tích tụ qua từng bài
        //   kiểm, và triệu chứng là những con số đếm cứ lớn dần — đúng lỗi mà javadoc lớp này kể.
        //   ⛔ Hai bảng nối (`article_attachments`, `article_version_attachments`) KHÔNG cần dòng
        //      xoá riêng: chúng có ON DELETE CASCADE từ `articles`/`article_versions` đã xoá ở trên.
        jdbc.update(
                "DELETE FROM attachments WHERE owner_type IN ('MEDIA_FOLDER', 'BANNER', 'SITE_CONFIG', 'TAI_LIEU')");
        jdbc.update("DELETE FROM media_folders");
    }

    /**
     * Trả các tham số nhóm {@code SITE} về giá trị mặc định của danh mục.
     *
     * <p>Đặt lại bằng {@code default_value} chứ không xoá dòng: xoá dòng là gỡ mất cả nhãn và luật
     * kiểm tra do migration seed, và bài kiểm kế tiếp sẽ thấy một màn hình cấu hình trống rỗng.
     */
    public static void datLaiCauHinhSite(JdbcTemplate jdbc) {
        jdbc.update("UPDATE settings SET setting_value = default_value WHERE group_code = 'SITE'");
    }

    private static Map<String, Long> ghiNhanMocSeed(JdbcTemplate jdbc) {
        Map<String, Long> moc = new LinkedHashMap<>();
        for (String bang : new String[] {"categories", "articles", "menu_items"}) {
            Long max = jdbc.queryForObject("SELECT COALESCE(max(id), 0) FROM " + bang, Long.class);
            moc.put(bang, max == null ? 0L : max);
        }
        return moc;
    }
}
