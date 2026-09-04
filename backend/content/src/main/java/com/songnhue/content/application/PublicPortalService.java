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
import com.songnhue.content.domain.KhoTep;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.content.infra.ArticleRepository;
import com.songnhue.content.infra.CategoryRepository;
import com.songnhue.content.infra.MediaFolderRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
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
     *
     * <p>{@code MENU_ITEM} vào danh sách ngày 29/08 cho logo cơ quan cấp trên ở dải "Liên kết
     * website" (CR-21). Cùng lập luận với {@code BANNER}: những tệp ấy sinh ra để hiện trên trang
     * chủ, nên "ai biết publicId thì tải được" chính là hành vi mong muốn.
     *
     * <p>{@code TAI_LIEU} (kho tài liệu bài viết, WS-40) <b>cố ý không</b> ở đây — xem
     * {@link com.songnhue.content.domain.KhoTep}. Tài liệu chỉ ra cổng qua
     * {@link #articleDocument(UUID)}, đường đòi tệp nằm trong bản chụp đang xuất bản của một bài
     * đang công khai.
     *
     * <p>⛔ Danh sách này có bài kiểm riêng — đây là một ranh giới bảo mật, và một ranh giới không
     * có phép kiểm nào thì lượt sửa kế tiếp không có gì cản (quy tắc 1). Bài kiểm khẳng định cả hai
     * chiều: bốn loại này CÓ, còn {@code EMPLOYEE} / {@code CONSTRUCTION} / {@code TAI_LIEU} KHÔNG.
     *
     * <p>⚠⚠ 04/09: câu trên trước đây nêu tên {@code PublicFileScopeTest} — <b>một tệp KHÔNG tồn
     * tại trong kho</b> (đo bằng {@code find}, 0 kết quả). Bài làm việc thật là
     * {@code MenuLogoAndMapImageTest} (hai khẳng định về danh sách này) và
     * {@code ArticleAttachmentTest} (đo qua HTTP rằng {@code /public/files/&#123;id&#125;} trả 404
     * cho một tệp tài liệu). Đây là lần thứ hai trong hai ngày cùng một hình dạng — lần trước là
     * {@code CongTacTrangChuTest} ở {@code PortalCache}. Một javadoc nêu tên bài kiểm <b>đọc như
     * một lời bảo đảm</b>, nên tên ấy phải có thật: tìm bằng {@code grep} trước khi viết vào
     * (quy tắc 28).
     */
    public static final List<String> LOAI_TEP_CONG_KHAI = List.of("MEDIA_FOLDER", "BANNER", "SITE_CONFIG", "MENU_ITEM");

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
        t = t.trim().replaceAll("\\s+", " ");

        // ⛔ Tên do MÁY sinh không phải một chú thích. Đo trên staging 28/8: 3 trong 25 ảnh Công ty
        //    gửi mang tên kiểu `1785224749554_4602082902160469425_…_683fabe93a80ea…` (tệp tải từ
        //    điện thoại/Zalo). Cổng đang hiện nguyên chuỗi ấy làm tiêu đề ảnh.
        //
        //    Trả RỖNG chứ không bịa: không có nguồn cho chú thích thì ô ấy phải trống và nói thẳng
        //    là chưa có (luật 16). Cổng bỏ hẳn dải chú thích khi tiêu đề rỗng.
        return laTenMaySinh(t) ? "" : t;
    }

    /**
     * Tên tệp do máy sinh: không khoảng trắng, đủ dài, và <b>đậm đặc chữ số</b>.
     *
     * <h2>Ba ngưỡng lấy từ dữ liệu THẬT, không từ ví dụ tự nghĩ ra (luật 25)</h2>
     *
     * Đo trên 25 ảnh Công ty gửi đang chạy ở staging, cộng vài dạng tên máy ảnh phổ biến:
     *
     * <pre>
     *   tên máy      : 80,0 – 82,6 % chữ số   (dài 30 – 86)
     *   chú thích thật:  0,0 – 28,6 % chữ số
     * </pre>
     *
     * Ngưỡng <b>40 %</b> nằm giữa một khoảng trống rộng, nên biên rất an toàn ở cả hai phía.
     *
     * <p>⚠ Cố ý KHÔNG bắt theo dải ký tự hex: {@code IMG_20240115_103045_…} có chữ {@code I},
     * {@code M}, {@code G} nên lọt, còn nới dải ra thì bắt đầu ăn cả chú thích không dấu kiểu
     * {@code Nha-may-nuoc-Ha-Dong}. Tỉ lệ chữ số tách hai nhóm sạch hơn hẳn.
     *
     * <p>⚠ Tên có dù chỉ MỘT khoảng trắng thì luôn được giữ — người đặt tên đã có ý diễn đạt.
     */
    static boolean laTenMaySinh(String t) {
        if (t.length() <= 20 || t.indexOf(' ') >= 0) {
            return false;
        }
        long soChuSo = t.chars().filter(Character::isDigit).count();
        return soChuSo * 100 >= t.length() * 40L;
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
                article.getSource(),
                com.songnhue.content.domain.ArticleState.LUU_TRU.equals(article.getStatus()),
                // ⚠ Lấy từ BẢN ĐÃ DUYỆT (`version`), không từ `article`: hai cột này là nội dung,
                //   nên chúng đi cùng đường với title/summary/content. Đọc từ `article` là để bản
                //   biên tập viên đang sửa dở lọt lên cổng — xem javadoc `Article`.
                version.getDocNumber(),
                version.getDocIssuedDate(),
                danhMuc,
                // ⛔ Lấy từ BẢN ĐÃ DUYỆT — cùng lập luận với hai cột ngay trên. Đọc từ `article` là
                //    để một biên tập viên đổi tài liệu của bài đang chạy mà không qua ai duyệt.
                taiLieuCongKhai(version)));
    }

    /**
     * Tài liệu của một bản chụp, đã lọc còn <b>những tệp phục vụ được thật</b> — WS-40.
     *
     * <h2>⛔⛔ Ba phép lọc, và bỏ sót cái nào cũng cho ra CÙNG một triệu chứng</h2>
     *
     * Một dòng có tên trên cổng mà bấm vào là 404 — §10.52 ở dạng thuần khiết.
     *
     * <ol>
     *   <li><b>Còn sống</b>: {@code findRef} lọc {@code deleted_at IS NULL}, nên tệp đã xoá mềm
     *       <b>biến mất khỏi danh sách</b> chứ không thành một dòng chết;
     *   <li><b>Đã quét xong</b>: {@code findRef} <b>KHÔNG</b> lọc trạng thái quét — chỉ
     *       {@code readForPublic} mới lọc {@code isDownloadable()}. Phải tự kiểm ở đây, nếu không
     *       thì một tệp vừa tải lên vẫn ra DTO trong khi byte trả 404;
     *   <li><b>Đúng kho</b>: {@code owner_type = 'TAI_LIEU'}. Ảnh media gắn nhầm vào đây sẽ 404 ở
     *       đường hẹp vì đường ấy chỉ nhận {@code TAI_LIEU}.
     * </ol>
     *
     * <p>⚠ Một lượt hỏi cho mỗi tệp. Chấp nhận có ý thức: đây là <b>một bài</b>, và số tệp đính kèm
     * đếm bằng đơn vị. ⛔ Nếu về sau cần {@code documents} cho cả <i>danh sách</i> bài thì phải đi
     * khuôn {@code findCategoryLabels} — một lượt hỏi cho cả trang — chứ không lặp cái này.
     */
    private List<PublicArticleDetail.TaiLieuRef> taiLieuCongKhai(ArticleVersion version) {
        return version.getDocuments().stream()
                .map(d -> attachments
                        .findRef(d.getAttachmentPublicId())
                        .filter(ref -> KhoTep.TAI_LIEU.ownerType().equals(ref.ownerType()) && ref.downloadable())
                        .map(ref -> new PublicArticleDetail.TaiLieuRef(
                                ref.publicId(),
                                // ⭐ Rơi về tên gốc khi chưa ai đặt nhãn — quyết định ở MỘT chỗ.
                                //    ⛔ Không sinh "Tài liệu 1" (quy tắc 16).
                                d.getLabel() == null ? ref.originalName() : d.getLabel(),
                                ref.contentType(),
                                ref.sizeBytes()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Nội dung một tài liệu đính kèm — đường ra cổng <b>duy nhất</b> của kho tài liệu (WS-40).
     *
     * <h2>Vì sao một đường hẹp riêng thay vì nới danh sách của cổng</h2>
     *
     * Nới {@link #LOAI_TEP_CONG_KHAI} thêm {@code TAI_LIEU} là làm mọi tệp trong kho tài liệu công
     * khai <b>ngay khi tải lên</b> — kể cả bản dự thảo chưa ai duyệt, kể cả tệp của một bài đã gỡ.
     * QuanTran chốt 04/09 là <b>siết</b>: chỉ tài liệu thuộc bài ĐÃ xuất bản mới tải được. Cùng
     * khuôn với {@code PublicConstructionCatalogService.publishedDocument} của WS-27, sinh ra từ
     * đúng bài học này.
     *
     * <p>Hai lớp, cả hai đều phải qua:
     *
     * <ol>
     *   <li>{@code publicId} phải nằm trong bản chụp đang xuất bản của một bài đang công khai —
     *       {@link ArticleRepository#daCongBoTaiLieu} (bốn vế trong một câu);
     *   <li>tệp phải thuộc kho {@code TAI_LIEU}, còn sống và đã quét xong — kiểm ở tầng đính kèm
     *       của Core.
     * </ol>
     *
     * <h2>⛔ Trần dung lượng — chặn một nút tắt máy chủ, không phải một nợ để lại</h2>
     *
     * Số đo đã có đủ: {@code readForPublic} trả {@code byte[]} <b>toàn phần</b>; trần tải tài liệu
     * là <b>50MB</b> ({@code V202608131009}); production {@code mem_limit: 3g} +
     * {@code MaxRAMPercentage=70} ⇒ heap ~2.1GB, <b>và {@code -XX:+ExitOnOutOfMemoryError}: OOM
     * không suy giảm, container CHẾT</b>; hạn mức công khai đếm <i>request</i>, không đếm byte;
     * nginx {@code proxy_buffering off}. ⇒ 20 lượt tải song song một PDF 50MB ≈ 1GB {@code byte[]}
     * sống cùng lúc. Không cần ác ý — chỉ cần một văn bản được nhiều người mở.
     *
     * <p>Trần {@code KhoTep.TRAN_PHUC_VU_CONG_KHAI_MB} là hằng số trong mã, cùng loại với
     * {@link #TRAN_MOI_TRANG} ngay trên: đây là <b>giới hạn an toàn bộ nhớ</b>, không phải tham số
     * nghiệp vụ, nên nó không thuộc bảng {@code settings} (quy tắc 12 nói về tham số nghiệp vụ).
     *
     * <p>📌 Đường sạch hơn <b>đã đo được là khả thi</b>: {@code deploy/nginx} có sẵn một server
     * block {@code ${FILES_DOMAIN}} chuyển tiếp thẳng vào MinIO và {@code MINIO_ENDPOINT} là tên
     * miền công khai, nên trình duyệt tới được MinIO — tức là 302 → presigned URL chạy được. Nó cần
     * {@code ObjectStorage} nhận thêm {@code response-content-disposition} để giữ tên tệp, nên
     * không làm trong đợt này (nợ T40.19).
     *
     * @return rỗng cho <b>mọi</b> lý do từ chối — cố ý không phân biệt. Nói <i>"bài chưa xuất
     *     bản"</i> là xác nhận tệp có tồn tại
     */
    @Transactional(readOnly = true)
    public Optional<AttachmentContent> articleDocument(UUID attachmentPublicId) {
        if (attachmentPublicId == null || !articles.daCongBoTaiLieu(attachmentPublicId, Instant.now())) {
            return Optional.empty();
        }
        // ⛔ Trần dung lượng ném 413 chứ KHÔNG trả rỗng. Ba vế trên im lặng vì phân biệt được là
        //    tiết lộ tệp có tồn tại; tới đây thì tệp ĐÃ công khai, nên biến "quá lớn" thành "không
        //    tồn tại" chỉ giấu mất lý do khỏi đúng người cần biết. Lớp ép chính nằm ở
        //    `ArticleService` — lúc đính kèm; đây là lớp thứ hai cho tệp đã đính từ trước.
        attachments
                .findRef(attachmentPublicId)
                .filter(ref -> KhoTep.vuotTranPhucVu(ref.sizeBytes()))
                .ifPresent(ref -> {
                    throw new BusinessRuleException(
                            ErrorCode.CMS_2017,
                            ref.originalName(),
                            ref.sizeBytes() / (1024 * 1024),
                            KhoTep.TRAN_PHUC_VU_CONG_KHAI_MB);
                });
        return attachments.readForPublic(attachmentPublicId, List.of(KhoTep.TAI_LIEU.ownerType()));
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
