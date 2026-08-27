package com.songnhue.content.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Article;
import com.songnhue.content.domain.ArticleVersion;
import com.songnhue.content.domain.Banner;
import com.songnhue.content.domain.Category;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.content.infra.ArticleRepository;
import com.songnhue.content.infra.CategoryRepository;
import com.songnhue.core.common.util.VietnameseUtils;
import com.songnhue.core.spi.AttachmentContent;
import com.songnhue.core.spi.AttachmentPort;

/**
 * Mọi thứ cổng công khai đọc — T16.1.
 *
 * <h2>⛔ Lớp này là ranh giới giữa "có thể xem" và "không được xem"</h2>
 *
 * Không có phân quyền nào phía sau nó: người gọi là khách vãng lai. Vì vậy <b>mọi</b> phép lọc phải
 * nằm trong chính các truy vấn ở đây, và mỗi phép lọc đều phải có một bài kiểm cố tình vi phạm.
 * Chốt chặn dạng "controller nhớ truyền đúng tham số" không đủ — nó là một dòng người ta quên được.
 *
 * <h2>Không có bộ nhớ đệm ở tầng này, và đó là chủ ý</h2>
 *
 * Bộ đệm thật nằm ở cổng (ISR của Next): trang tĩnh sống hàng phút tới hàng giờ, nên số lượt gọi
 * xuống đây vốn đã ít. Thêm một tầng đệm nữa ở backend là thêm một nơi nữa có thể giữ nội dung cũ
 * sau khi biên tập viên bấm duyệt — mà toàn bộ T16.5 sinh ra để tránh đúng điều đó. Ngoại lệ duy
 * nhất là cấu hình giao diện, vốn đã có bộ đệm dọn bằng sự kiện ({@code SiteConfigService}).
 */
@Service
public class PublicPortalService {

    /**
     * Loại chủ sở hữu tệp được phép phục vụ công khai — T16.6.
     *
     * <p>⛔⛔ <b>Danh sách này là toàn bộ ranh giới bảo vệ kho tài liệu.</b> Thêm một dòng vào đây là
     * mở đường cho bất kỳ ai đọc mọi tệp thuộc loại đó, chỉ cần biết {@code publicId}. Hồ sơ nhân sự
     * ({@code EMPLOYEE}) và tài liệu công trình ({@code CONSTRUCTION}) <b>không</b> nằm ở đây và
     * không được thêm vào — chúng có màn hình riêng, sau đăng nhập.
     */
    private static final List<String> LOAI_TEP_CONG_KHAI = List.of("MEDIA_FOLDER", "BANNER", "SITE_CONFIG");

    /** Trần số bài mỗi trang. Người gọi xin 10.000 thì đó là một lượt quét, không phải một lượt xem. */
    private static final int TRAN_MOI_TRANG = 50;

    private final ArticleRepository articles;
    private final CategoryRepository categories;
    private final MenuService menus;
    private final BannerService banners;
    private final SiteConfigService siteConfig;
    private final AttachmentPort attachments;
    private final ViewCountService viewCounts;

    public PublicPortalService(
            ArticleRepository articles,
            CategoryRepository categories,
            MenuService menus,
            BannerService banners,
            SiteConfigService siteConfig,
            AttachmentPort attachments,
            ViewCountService viewCounts) {
        this.articles = articles;
        this.categories = categories;
        this.menus = menus;
        this.banners = banners;
        this.siteConfig = siteConfig;
        this.attachments = attachments;
        this.viewCounts = viewCounts;
    }

    // ---- Khung cổng ----------------------------------------------------------

    @Transactional(readOnly = true)
    public java.util.Map<String, String> siteConfig() {
        return siteConfig.effectiveValues();
    }

    @Transactional(readOnly = true)
    public List<MenuService.MenuNode> menu(MenuPosition position) {
        return menus.tree(position).stream()
                .filter(MenuService.MenuNode::active)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Banner> banners() {
        return banners.listVisible(Instant.now());
    }

    /** Danh mục đang hiện — danh mục ẩn thì không có mặt trong điều hướng của cổng. */
    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categories.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc().stream()
                .filter(Category::isVisible)
                .toList();
    }

    // ---- Bài viết ------------------------------------------------------------

    /**
     * Danh sách bài đã xuất bản.
     *
     * @param categorySlug {@code null} = mọi danh mục
     * @param tuKhoa {@code null} = không tìm kiếm
     */
    @Transactional(readOnly = true)
    public Page<PublicArticleRow> articles(String categorySlug, String tuKhoa, int page, int size) {
        Long categoryId = categorySlug == null || categorySlug.isBlank()
                ? null
                : categories
                        .findBySlugAndDeletedAtIsNull(categorySlug)
                        .filter(Category::isVisible)
                        .map(Category::getId)
                        // Danh mục lạ hoặc đang ẩn → coi như không có bài nào, KHÔNG phải bỏ qua bộ
                        // lọc. Bỏ qua thì gõ sai một slug là nhận về toàn bộ bài của cổng.
                        .orElse(-1L);

        String mau = tuKhoa == null || tuKhoa.isBlank() ? null : "%" + VietnameseUtils.normalizeForSearch(tuKhoa) + "%";

        int kichThuoc = Math.clamp(size, 1, TRAN_MOI_TRANG);
        Page<PublicArticleRow> trang =
                articles.findPublic(mau, categoryId, Instant.now(), PageRequest.of(Math.max(page, 0), kichThuoc));
        return trang.map(nhanChuyenMuc(trang.getContent()));
    }

    /**
     * Ghép nhãn chuyên mục vào từng dòng — CR-12.
     *
     * <p>Gom bằng <b>một</b> lượt hỏi cho cả trang rồi tra trong bộ nhớ. Đọc
     * {@code article.getCategories()} trong vòng lặp cũng cho kết quả đúng, nhưng đó là quan hệ
     * LAZY: 12 bài trên trang chủ thành 13 lượt hỏi CSDL, và con số ấy tăng theo độ dài trang.
     *
     * @return hàm ánh xạ; bài không có chuyên mục nào đang hiện thì giữ nguyên {@link List#of()}
     */
    private java.util.function.Function<PublicArticleRow, PublicArticleRow> nhanChuyenMuc(List<PublicArticleRow> dong) {
        if (dong.isEmpty()) {
            return row -> row;
        }
        Map<String, List<PublicArticleDetail.CategoryRef>> theoSlug = new LinkedHashMap<>();
        for (Object[] hang : articles.findCategoryLabels(
                dong.stream().map(PublicArticleRow::slug).toList())) {
            theoSlug.computeIfAbsent((String) hang[0], k -> new ArrayList<>())
                    .add(new PublicArticleDetail.CategoryRef((String) hang[1], (String) hang[2]));
        }
        return row -> row.withCategories(theoSlug.getOrDefault(row.slug(), List.of()));
    }

    /**
     * Một bài, tra theo slug.
     *
     * <p>Trả {@link Optional#empty()} cho bài Nháp, Chờ duyệt, Gỡ bài, bài hẹn giờ chưa tới hạn và
     * slug không tồn tại — <b>cùng một kết quả cho tất cả</b>. Phân biệt được là nói cho người hỏi
     * biết cổng đang có một bài chưa công bố mang tên đó.
     */
    @Transactional(readOnly = true)
    public Optional<PublicArticleDetail> article(String slug) {
        List<Object[]> rows = articles.findPublicBySlug(slug, Instant.now());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Article article = (Article) rows.get(0)[0];
        ArticleVersion version = (ArticleVersion) rows.get(0)[1];

        List<PublicArticleDetail.CategoryRef> danhMuc = article.getCategories().stream()
                .filter(Category::isVisible)
                .map(c -> new PublicArticleDetail.CategoryRef(c.getSlug(), c.getName()))
                .toList();

        return Optional.of(new PublicArticleDetail(
                article.getSlug(),
                version.getTitle(),
                version.getSummary(),
                version.getContent(),
                version.getCoverAttachmentPublicId(),
                version.getMetaTitle(),
                version.getMetaDescription(),
                version.getMetaKeywords(),
                article.getPublishedAt(),
                article.getViewCount(),
                com.songnhue.content.domain.ArticleState.LUU_TRU.equals(article.getStatus()),
                danhMuc));
    }

    /** Ghi nhận một lượt xem — gom trong bộ nhớ, đẩy xuống CSDL theo lô. */
    public void recordView(String slug) {
        viewCounts.record(slug);
    }

    // ---- Tệp -----------------------------------------------------------------

    /**
     * Nội dung một tệp công khai — T16.6.
     *
     * <p>⛔ <b>Không dùng presigned URL.</b> Presigned sống 10 phút, còn trang ISR sống hàng giờ:
     * trang dựng lúc 9h vẫn nằm trong bộ đệm lúc 11h và mọi ảnh trong đó đã chết. Triệu chứng là ảnh
     * hỏng hàng loạt vào lúc không ai đụng gì tới hệ thống.
     *
     * <p>Cái giá là backend phải đẩy byte thay vì để MinIO làm. Chấp nhận được ở quy mô này (200
     * người dùng đồng thời, cổng nội bộ), và đổi lại thì bucket <b>không</b> phải mở công khai — mở
     * bucket là một quyết định khó rút lại hơn nhiều.
     */
    @Transactional(readOnly = true)
    public Optional<AttachmentContent> file(UUID publicId) {
        return attachments.readForPublic(publicId, LOAI_TEP_CONG_KHAI);
    }
}
