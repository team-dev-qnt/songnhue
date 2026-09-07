package com.songnhue.content.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Article;
import com.songnhue.content.domain.Category;
import com.songnhue.content.domain.MenuItem;
import com.songnhue.content.domain.MenuLinkType;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.content.infra.ArticleRepository;
import com.songnhue.content.infra.CategoryRepository;
import com.songnhue.content.infra.MenuItemRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.tree.MaterializedPath;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.AttachmentUploadCommand;

/**
 * Menu điều hướng của cổng — CN-01.5, T15.1.
 *
 * <p>Header và Footer là hai cây độc lập nằm chung một bảng. Ràng buộc "mục con cùng vị trí với cha"
 * do CSDL giữ (khoá ngoại ghép {@code (parent_id, position)}); lớp này chặn sớm hơn để trả về mã lỗi
 * đọc được thay vì để người dùng nhận một lỗi ràng buộc.
 */
@Service
public class MenuService {

    /** Giá trị giữ chỗ cho lượt INSERT đầu — {@code path} chứa chính id do CSDL sinh. */
    private static final String PATH_TAM = "/";

    /**
     * Định dạng nhận cho logo cơ quan.
     *
     * <p>⛔ KHÔNG có {@code image/svg+xml}. {@code SiteConfigService} là <b>nơi duy nhất</b> trong hệ
     * thống nhận SVG (điểm nghiệp vụ 7) và nó có lý do: logo Công ty do chính Công ty làm. Logo cơ
     * quan cấp trên thì tải từ nơi khác về, tức là tệp của người lạ — và SVG là một tài liệu chạy
     * được script. Khử trùng thì vẫn có ({@code AttachmentService} luôn đưa SVG qua
     * {@code SvgSanitizer}), nhưng không mở thêm một cửa nữa khi không cần.
     */
    private static final List<String> DINH_DANG_LOGO = List.of("image/png", "image/jpeg", "image/webp");

    private final MenuItemRepository items;
    private final CategoryRepository categories;
    private final ArticleRepository articles;
    private final AttachmentPort attachments;

    public MenuService(
            MenuItemRepository items,
            CategoryRepository categories,
            ArticleRepository articles,
            AttachmentPort attachments) {
        this.items = items;
        this.categories = categories;
        this.articles = articles;
        this.attachments = attachments;
    }

    /**
     * Cây menu của một vị trí, đã phân giải mọi khoá nội bộ sang {@code publicId}.
     *
     * <p>⚠ Phân giải cả đích ({@code categoryPublicId}, {@code articlePublicId}) chứ không chỉ cha.
     * Thiếu chúng thì biểu mẫu sửa mở ra với ô "Danh mục" trống ở mọi mục — người dùng chọn lại đúng
     * giá trị cũ, hoặc tệ hơn là bấm Lưu và mục menu mất đích. Đây là loại hỏng không có thông báo
     * nào và chỉ lộ ra khi có người thật ngồi sửa menu.
     *
     * <p>Danh sách đã sắp theo {@code path} nên cha luôn đứng trước con — giao diện dựng cây bằng một
     * lượt duyệt.
     */
    @Transactional(readOnly = true)
    public List<MenuNode> tree(MenuPosition position) {
        List<MenuItem> all = items.findByPositionAndDeletedAtIsNullOrderByPathAscSortOrderAsc(position);
        return toNodes(all);
    }

    @Transactional(readOnly = true)
    public MenuItem get(UUID publicId) {
        return items.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /**
     * Một mục menu ở dạng giao diện dùng được — không khoá chạy số nào lọt ra ngoài (§4.2).
     *
     * <p>Đích trả ra ở <b>hai dạng</b>, phục vụ hai người dùng khác nhau: màn hình quản trị cần
     * {@code publicId} để nạp lại ô chọn, còn cổng công khai cần {@code slug} để dựng đường dẫn. Tra
     * hai lần ở hai nơi thì có hai đường phân giải, và chúng lệch nhau lúc ai đó sửa một bên.
     *
     * <p>⛔ Cố ý <b>không</b> trả sẵn {@code href}. Sơ đồ đường dẫn của cổng là việc của giao diện;
     * chốt nó ở backend là mỗi lần đổi cấu trúc URL phải đi sửa Java.
     */
    public record MenuNode(
            UUID publicId,
            String label,
            MenuLinkType linkType,
            UUID parentPublicId,
            UUID categoryPublicId,
            String categorySlug,
            UUID articlePublicId,
            String articleSlug,
            String url,
            boolean openNewTab,
            Short depth,
            Integer sortOrder,
            boolean active,
            /**
             * {@code publicId} của logo, hoặc {@code null}. Chỉ mục ở vị trí {@code LIEN_KET} mới
             * đặt được — xem {@link #uploadLogo}.
             */
            UUID logoAttachmentId) {}

    /**
     * Thêm một mục.
     *
     * @param parentPublicId {@code null} = mục gốc của menu
     */
    @Transactional
    public MenuNode create(MenuPosition position, UUID parentPublicId, String label, Target target) {
        MenuItem parent = parentPublicId == null ? null : get(parentPublicId);
        if (parent != null && parent.getPosition() != position) {
            // Chặn ở đây thay vì để khoá ngoại ghép từ chối: thông báo của CSDL không nói được cho
            // người dùng rằng họ vừa kéo một mục Header vào giữa menu Footer.
            throw new BusinessRuleException(ErrorCode.CMS_2013);
        }

        MenuItem item = new MenuItem(position, label, target.linkType());
        applyTarget(item, target);
        // Ba bước giống CategoryService/MediaService: path chứa chính id, mà cột là NOT NULL.
        item.placeAt(parent == null ? null : parent.getId(), PATH_TAM, (short) 0);
        MenuItem saved = items.saveAndFlush(item);

        String path = parent == null
                ? MaterializedPath.rootPath(saved.getId())
                : MaterializedPath.childPath(parent.getPath(), saved.getId());
        short depth = (short) MaterializedPath.depthOf(path);
        if (depth > MenuItem.MAX_DEPTH) {
            throw new BusinessRuleException(ErrorCode.CMS_2010);
        }
        saved.placeAt(parent == null ? null : parent.getId(), path, depth);
        return toNode(items.saveAndFlush(saved), parent == null ? null : parent.getPublicId());
    }

    @Transactional
    public MenuNode update(UUID publicId, String label, Target target, boolean openNewTab, boolean active) {
        MenuItem item = get(publicId);
        item.setLabel(label);
        item.setOpenNewTab(openNewTab);
        item.setActive(active);
        applyTarget(item, target);
        items.flush();
        UUID parentPublicId = item.getParentId() == null
                ? null
                : items.findById(item.getParentId()).map(MenuItem::getPublicId).orElse(null);
        return toNode(item, parentPublicId);
    }

    /**
     * Đổi thứ tự trong cùng một cấp.
     *
     * <p>Nhận cả danh sách chứ không nhận từng cặp "mục này lên trên mục kia": giao diện là kéo thả,
     * và thứ nó biết sau mỗi thao tác là trật tự cuối cùng. Gửi từng bước một thì mỗi lần kéo là
     * nhiều lượt gọi, và nửa chừng đứt mạng để lại một trật tự không ai định.
     */
    @Transactional
    public void reorder(List<UUID> publicIdsInOrder) {
        int order = 0;
        for (UUID publicId : publicIdsInOrder) {
            get(publicId).setSortOrder(order);
            order += 10;
        }
    }

    /** Xoá mềm — chỉ khi không còn mục con, cùng lý do với thư mục media. */
    @Transactional
    public void delete(UUID publicId) {
        MenuItem item = get(publicId);
        if (items.countByParentIdAndDeletedAtIsNull(item.getId()) > 0) {
            throw new BusinessRuleException(ErrorCode.CMS_2011);
        }
        item.markDeleted(Instant.now());
    }

    // -------------------------------------------------------------------------

    /**
     * Đích của một mục menu, ở dạng giao diện gửi lên: {@code publicId} chứ không phải khoá chạy số
     * (§4.2 chống IDOR).
     */
    public record Target(MenuLinkType linkType, UUID categoryPublicId, UUID articlePublicId, String url) {

        public static Target none() {
            return new Target(MenuLinkType.NONE, null, null, null);
        }
    }

    /**
     * Tra đích ra khoá nội bộ và gán vào mục.
     *
     * <p>⚠ Kiểm sự tồn tại ở đây là có chủ đích, dù CSDL đã có khoá ngoại. Khoá ngoại chỉ nói "không
     * có dòng nào mang id đó" — nó không phân biệt được với "danh mục đã bị xoá mềm", mà một menu
     * trỏ vào danh mục đã xoá thì cổng hiện một mục dẫn tới trang trống.
     */
    private void applyTarget(MenuItem item, Target target) {
        Long categoryId = null;
        Long articleId = null;

        switch (target.linkType()) {
            case CATEGORY ->
                categoryId = categories
                        .findByPublicIdAndDeletedAtIsNull(requireId(target.categoryPublicId()))
                        .orElseThrow(() -> new BusinessRuleException(ErrorCode.CMS_2012))
                        .getId();
            case ARTICLE ->
                articleId = articles.findByPublicIdAndDeletedAtIsNull(requireId(target.articlePublicId()))
                        .orElseThrow(() -> new BusinessRuleException(ErrorCode.CMS_2012))
                        .getId();
            case URL, EXTERNAL_DOC -> {
                if (target.url() == null || target.url().isBlank()) {
                    throw new BusinessRuleException(ErrorCode.CMS_2012);
                }
            }
            case NONE -> {
                // Mục chỉ mở menu con — không có đích nào để kiểm
            }
            // Checkstyle đòi nhánh mặc định dù switch trên enum đã phủ hết. Ném lỗi chứ KHÔNG để
            // rỗng: nhánh rỗng biến "thêm một loại liên kết mà quên xử lý" thành một mục menu lặng
            // lẽ không có đích, còn ném lỗi thì nó lộ ra ngay lượt chạy đầu tiên.
            default -> throw new IllegalStateException("Loại liên kết chưa được xử lý: " + target.linkType());
        }
        item.pointTo(target.linkType(), categoryId, articleId, target.url());
    }

    private static UUID requireId(UUID publicId) {
        if (publicId == null) {
            throw new BusinessRuleException(ErrorCode.CMS_2012);
        }
        return publicId;
    }

    /**
     * Phân giải cả cây trong <b>ba</b> lượt truy vấn — một cho danh mục, một cho bài viết, một cho
     * chính cây. Tra từng mục một thì menu 15 mục thành 15 lượt xuống CSDL, ở đúng chỗ cổng công
     * khai gọi nhiều nhất.
     */
    private List<MenuNode> toNodes(List<MenuItem> all) {
        Map<Long, UUID> theoMuc = all.stream().collect(Collectors.toMap(MenuItem::getId, MenuItem::getPublicId));

        Map<Long, Category> danhMuc =
                categories
                        .findAllById(all.stream()
                                .map(MenuItem::getCategoryId)
                                .filter(Objects::nonNull)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Category::getId, c -> c));

        Map<Long, Article> baiViet =
                articles
                        .findAllById(all.stream()
                                .map(MenuItem::getArticleId)
                                .filter(Objects::nonNull)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Article::getId, a -> a));

        return all.stream()
                .map(item -> new MenuNode(
                        item.getPublicId(),
                        item.getLabel(),
                        item.getLinkType(),
                        item.getParentId() == null ? null : theoMuc.get(item.getParentId()),
                        item.getCategoryId() == null
                                ? null
                                : truong(danhMuc, item.getCategoryId(), Category::getPublicId),
                        item.getCategoryId() == null ? null : truong(danhMuc, item.getCategoryId(), Category::getSlug),
                        item.getArticleId() == null ? null : truong(baiViet, item.getArticleId(), Article::getPublicId),
                        item.getArticleId() == null ? null : truong(baiViet, item.getArticleId(), Article::getSlug),
                        item.getUrl(),
                        item.isOpenNewTab(),
                        item.getDepth(),
                        item.getSortOrder(),
                        item.isActive(),
                        // ⚠⚠ ĐÂY là đường mà cổng công khai đi (`tree()` → `/public/menus/…`),
                        //    còn `toNode()` bên dưới chỉ phục vụ lượt tạo/sửa một mục. Quên vế
                        //    này thì màn hình quản trị hiện logo, CSDL có logo, và trang chủ
                        //    vẫn là thẻ chữ — hỏng câm, đúng nửa cặp đọc–ghi (quy tắc 27).
                        //    Lượt này chỉ lộ ra vì `record` bắt sai số tham số; một `setter`
                        //    thì đã đi lọt.
                        item.getLogoAttachmentPublicId()))
                .toList();
    }

    private static <T, R> R truong(Map<Long, T> nguon, Long id, java.util.function.Function<T, R> lay) {
        T found = nguon.get(id);
        return found == null ? null : lay.apply(found);
    }

    /** Bản một-mục, dùng sau khi tạo hoặc sửa — cha đã biết nên không phải dựng lại cả cây. */
    private MenuNode toNode(MenuItem item, UUID parentPublicId) {
        Category danhMuc = item.getCategoryId() == null
                ? null
                : categories.findById(item.getCategoryId()).orElse(null);
        Article baiViet = item.getArticleId() == null
                ? null
                : articles.findById(item.getArticleId()).orElse(null);
        return new MenuNode(
                item.getPublicId(),
                item.getLabel(),
                item.getLinkType(),
                parentPublicId,
                danhMuc == null ? null : danhMuc.getPublicId(),
                danhMuc == null ? null : danhMuc.getSlug(),
                baiViet == null ? null : baiViet.getPublicId(),
                baiViet == null ? null : baiViet.getSlug(),
                item.getUrl(),
                item.isOpenNewTab(),
                item.getDepth(),
                item.getSortOrder(),
                item.isActive(),
                item.getLogoAttachmentPublicId());
    }

    /**
     * Tải logo cho một mục của dải "Liên kết website".
     *
     * <h2>⛔ Chỉ vị trí {@code LIEN_KET}, và đó là một ràng buộc chứ không phải một bộ lọc giao diện</h2>
     *
     * Menu Header và Footer là menu <b>chữ</b>: cổng không dựng ô ảnh nào cho chúng. Cho tải logo
     * vào đấy nghĩa là tạo ra một cột có người ghi mà không ai đọc — đúng thứ {@code CLAUDE.md} quy
     * tắc 15 gọi tên, và nó im lặng: màn hình quản trị báo *lưu thành công*, cổng không đổi gì
     * (quy tắc 27).
     *
     * <p>Chặn ở ĐÂY chứ không ở màn hình quản trị: giao diện chỉ là một trong các đường vào, còn
     * đây là chỗ dữ liệu đi qua (quy tắc 12).
     *
     * <h2>⚠ Tệp cũ không bị xoá</h2>
     *
     * Cùng lý do với {@code SiteConfigService.uploadBrandImage}: trang đã dựng sẵn (ISR) còn trỏ
     * vào nó. Vài tệp bỏ lại trong kho rẻ hơn ảnh vỡ trên những trang ấy.
     */
    @Transactional
    public MenuNode uploadLogo(UUID publicId, String originalName, byte[] content) {
        MenuItem item = get(publicId);
        if (item.getPosition() != MenuPosition.LIEN_KET) {
            throw new BusinessRuleException(ErrorCode.CMS_2015);
        }
        AttachmentRef ref = attachments.upload(new AttachmentUploadCommand(
                MenuItem.OWNER_TYPE, item.getId(), "MENU_LOGO", originalName, content, DINH_DANG_LOGO));
        item.setLogoAttachmentPublicId(ref.publicId());
        items.flush();
        return toNode(item, parentPublicIdOf(item));
    }

    /** Gỡ logo — mục quay về thẻ chữ, không để lại ô ảnh rỗng. */
    @Transactional
    public MenuNode removeLogo(UUID publicId) {
        MenuItem item = get(publicId);
        item.setLogoAttachmentPublicId(null);
        items.flush();
        return toNode(item, parentPublicIdOf(item));
    }

    private UUID parentPublicIdOf(MenuItem item) {
        return item.getParentId() == null
                ? null
                : items.findById(item.getParentId()).map(MenuItem::getPublicId).orElse(null);
    }
}
