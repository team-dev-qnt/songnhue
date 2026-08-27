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
import com.songnhue.content.infra.MediaFolderRepository;
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
    private final MediaFolderRepository mediaFolders;

    public PublicPortalService(
            ArticleRepository articles,
            CategoryRepository categories,
            MenuService menus,
            BannerService banners,
            SiteConfigService siteConfig,
            AttachmentPort attachments,
            ViewCountService viewCounts,
            MediaFolderRepository mediaFolders) {
        this.articles = articles;
        this.categories = categories;
        this.menus = menus;
        this.banners = banners;
        this.siteConfig = siteConfig;
        this.attachments = attachments;
        this.viewCounts = viewCounts;
        this.mediaFolders = mediaFolders;
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

    /**
     * Ảnh của thư viện trên trang chủ — đọc thư mục media mà {@code site.home.photos-folder} chỉ tới.
     *
     * <h3>⚠ Rỗng là một câu trả lời hợp lệ, không phải một lỗi</h3>
     *
     * Khoá chưa đặt, hoặc trỏ vào một thư mục đã xoá, đều trả về danh sách rỗng — và khối trên
     * trang chủ nói thẳng là chưa có ảnh. Đây là {@code CLAUDE.md} luật 16 ở dạng đường dẫn dữ
     * liệu: <i>ô nào chưa có nguồn thì nói là chưa có</i>, tuyệt đối không rơi về một bộ ảnh mặc
     * định cho "giao diện luôn sống động" (§10.54).
     *
     * <h3>⭐ Vì sao tiêu đề lấy từ {@code originalName}</h3>
     *
     * Bảng {@code attachments} không có cột tiêu đề, và thêm một cột chỉ để phục vụ một khối hiển
     * thị là mở rộng lược đồ trước khi biết hình dạng nhu cầu. Chú thích ảnh của Công ty <b>đang
     * nằm trong chính tên tệp họ gửi</b> — {@code "AN2. Đại hội Công đoàn Công ty nhiệm kỳ
     * 2023-2028.jpg"} — nên đọc nó ra là dùng đúng thứ đã có, không phải bịa thêm.
     *
     * <p>⚠ Việc bóc tiền tố đặt ở ĐÂY, một chỗ duy nhất (quy tắc 12). Để phía giao diện tự bóc là
     * mỗi nơi hiển thị một kiểu, và bộ seed lại bóc một kiểu thứ ba.
     */
    @Transactional(readOnly = true)
    public List<AnhThuVien> photos() {
        String khoa = siteConfig.effectiveValues().getOrDefault("site.home.photos-folder", "");
        if (khoa.isBlank()) {
            return List.of();
        }
        UUID idThuMuc;
        try {
            idThuMuc = UUID.fromString(khoa.trim());
        } catch (IllegalArgumentException e) {
            // Khoá do người nhập — giá trị hỏng KHÔNG được làm sập cả trang chủ.
            return List.of();
        }
        return mediaFolders
                .findByPublicIdAndDeletedAtIsNull(idThuMuc)
                .map(thuMuc -> attachments.refsOf("MEDIA_FOLDER", thuMuc.getId()).stream()
                        .map(a -> new AnhThuVien(a.publicId(), tieuDeTuTenTep(a.originalName())))
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * Bóc tiền tố kỹ thuật và đuôi tệp, giữ NGUYÊN VĂN phần còn lại.
     *
     * <p>Công ty đặt tên tệp theo quy ước của họ: {@code "Ảnh to. …"} cho ảnh lớn, {@code "AN1./AN2./AN3. …"}
     * cho ảnh thường. Phần sau tiền tố chính là chú thích. Không viết hoa lại, không cắt ngắn, không
     * thêm gì — đó là chữ của Công ty.
     *
     * <p>Tên tệp không theo quy ước (ảnh tải từ điện thoại, tên là một chuỗi băm) thì trả về nguyên
     * tên đã bỏ đuôi — xấu, nhưng THẬT. Bịa một tiêu đề đẹp cho nó mới là điều bị cấm.
     */
    static String tieuDeTuTenTep(String tenTep) {
        if (tenTep == null || tenTep.isBlank()) {
            return "";
        }
        String t = tenTep.replaceAll("(?i)\\.(jpe?g|png|webp|gif)$", "");
        t = t.replaceFirst("^(Ảnh to\\.|AN\\d\\.?)\\s*", "");
        return t.trim().replaceAll("\\s+", " ");
    }

    /** Một ảnh của thư viện: mã tệp để dựng URL, và chú thích của Công ty. */
    public record AnhThuVien(UUID publicId, String title) {}

    /**
     * Danh mục đang hiện — danh mục ẩn <b>và cả nhánh dưới nó</b> biến khỏi điều hướng của cổng.
     *
     * <h3>⚠⚠ Vì sao phải loại cả nhánh con, không chỉ loại đúng nút bị ẩn</h3>
     *
     * Bản trước chỉ lọc {@code isVisible()} trên từng dòng, nên ẩn một danh mục cha để lại các con
     * của nó <b>mồ côi nhưng vẫn hiện</b>. Đo được trên máy sau khi CR-01 ẩn mục "Thông báo": hai
     * danh mục con của nó ({@code lich-van-hanh}, {@code thong-bao-xa-lu}) vẫn nằm trong danh sách
     * trả về, và vì danh sách sắp theo {@code path} dạng chuỗi — {@code '/12/' < '/2/'} — chúng rơi
     * đúng sau {@code tien-do-san-xuat}. Trang "Tiến độ sản xuất" khi đó liệt kê
     * <i>"Lịch vận hành cống &amp; trạm bơm"</i> và <i>"Thông báo xả nước đệm"</i> làm các <b>Năm</b>.
     *
     * <p>Không lỗi nào báo ra: cả hai danh mục đều có thật, đều đang hiện, và trang vẫn dựng bình
     * thường. Chỉ có nội dung là vô nghĩa. Người tìm ra là người mở đúng trang ấy trên site đang
     * chạy — không bộ test nào của cả hai phía bắt được, vì cả hai đều đúng với dữ liệu của mình.
     *
     * <p>Ẩn một mục trong cây có nghĩa là <i>rút nhánh ấy khỏi điều hướng</i>. Đặt luật ở đây — chỗ
     * dữ liệu đi qua — thay vì bắt mỗi nơi hiển thị tự nhớ (quy tắc 12).
     */
    @Transactional(readOnly = true)
    public List<Category> categories() {
        return hienTrenCong(categories.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc());
    }

    /**
     * Lọc bỏ danh mục ẩn và mọi hậu duệ của chúng.
     *
     * <p>Dùng {@code path} (đường dẫn vật chất hoá {@code /1/4/9/}) chứ không truy ngược
     * {@code parentId} từng bậc: một lượt duyệt là đủ, và không phụ thuộc vào việc cha có nằm trong
     * danh sách hay không.
     */
    private static List<Category> hienTrenCong(List<Category> tatCa) {
        List<String> pathAn = tatCa.stream()
                .filter(c -> !c.isVisible())
                .map(Category::getPath)
                .toList();
        return tatCa.stream()
                .filter(Category::isVisible)
                // `path` của con luôn bắt đầu bằng `path` của cha, và dấu `/` cuối là thứ giữ cho
                // `/1/` không khớp nhầm `/10/` — xem MaterializedPath.
                .filter(c -> pathAn.stream().noneMatch(an -> c.getPath().startsWith(an)))
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
                // ⚠ Dùng CÙNG một luật với `categories()`: danh mục ẩn hoặc nằm dưới một danh mục
                //   ẩn đều không phục vụ bài. Lọc bằng `isVisible()` một mình thì một nhánh đã rút
                //   khỏi điều hướng vẫn mở được bằng địa chỉ trực tiếp — hai câu trả lời cho cùng
                //   một câu hỏi "danh mục này còn trên cổng không".
                : hienTrenCong(categories.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc()).stream()
                        .filter(c -> categorySlug.equals(c.getSlug()))
                        .findFirst()
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
