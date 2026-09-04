package com.songnhue.content.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.content.application.ContactService;
import com.songnhue.content.application.MenuService;
import com.songnhue.content.application.PublicArticleDetail;
import com.songnhue.content.application.PublicArticleRow;
import com.songnhue.content.application.PublicPortalService;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.PublicEndpoint;
import com.songnhue.core.common.util.HttpHeaderText;
import com.songnhue.core.spi.AttachmentContent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Nội dung cho cổng thông tin điện tử — {@code /api/v1/public/**} (T16.1).
 *
 * <h2>⛔ Toàn bộ nhóm này không cần đăng nhập</h2>
 *
 * Đó là mục đích của nó: {@code public-web} dựng trang tĩnh phía máy chủ và không có tài khoản nào
 * để đăng nhập. Hai hệ quả phải nhớ mỗi lần thêm endpoint vào đây:
 *
 * <ol>
 *   <li><b>Không có tầng phân quyền nào phía sau.</b> Mọi phép lọc "được xem cái gì" phải nằm trong
 *       truy vấn của {@code PublicPortalService}, và phải có bài kiểm cố tình hỏi thứ không được
 *       phép xem.
 *   <li><b>Hạn mức tần suất tính bằng bucket riêng</b> ({@code RateLimitPolicy.PUBLIC}) — gộp với
 *       API quản trị thì một con bọ tìm kiếm quét cổng sẽ khoá người đang soạn bài, vì cả Công ty
 *       ra Internet qua một IP NAT.
 * </ol>
 *
 * <p>Đường dẫn tách hẳn tiền tố {@code /api/v1/public} chứ không nằm lẫn trong {@code /api/v1/cms}:
 * nginx và bộ lọc tần suất phân biệt được bằng tiền tố, và người rà soát an ninh đọc một danh sách
 * thay vì đi tìm annotation trong từng lớp.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "06-public · Cổng thông tin", description = "Nội dung công khai — không cần đăng nhập")
public class PublicPortalController {

    /**
     * Bao lâu trình duyệt và proxy được giữ lại một tệp.
     *
     * <p>Dài được vì tên tệp trong kho là chuỗi ngẫu nhiên: đổi ảnh là sinh một {@code publicId}
     * mới, nên không có chuyện phục vụ nhầm phiên bản cũ dưới cùng một địa chỉ.
     */
    private static final long CACHE_TEP_GIAY = 86_400L;

    private final PublicPortalService portal;

    private final ContactService contacts;

    public PublicPortalController(PublicPortalService portal, ContactService contacts) {
        this.contacts = contacts;
        this.portal = portal;
    }

    public record MenuLink(
            String label,
            String linkType,
            String categorySlug,
            String articleSlug,
            String url,
            boolean openNewTab,
            Short depth,
            String parentLabel,
            /**
             * {@code publicId} của logo, hoặc {@code null} khi mục chưa có.
             *
             * <p>⚠ Chỉ mục ở vị trí {@code LIEN_KET} mới đặt được (CR-21). Nơi hiển thị phải xử lý
             * được {@code null}: dải liên kết là bốn cơ quan cấp trên, Công ty tải logo từng cái
             * một, nên trạng thái "một nửa có logo" là bình thường chứ không phải lỗi.
             */
            UUID logoId) {}

    /**
     * Một ảnh của thư viện trang chủ.
     *
     * <p>⚠ KHÔNG có trường "nơi chụp". Ảnh Công ty gửi không kèm dữ liệu ấy, và bịa ra một địa
     * điểm cho mỗi ảnh là đúng thứ {@code CLAUDE.md} luật 16 cấm. Thiếu thì để thiếu.
     */
    public record PhotoView(java.util.UUID publicId, String title) {}

    public record BannerView(
            String title, String description, UUID imageId, String linkUrl, boolean openNewTab, Integer sortOrder) {}

    /**
     * Một danh mục trên cổng.
     *
     * @param parentSlug slug của danh mục cha, {@code null} với danh mục gốc.
     *     <p>⚠⚠ Trường này <b>bắt buộc phải có</b>, và nó thay cho một phép suy đã hỏng thật: giao
     *     diện từng suy quan hệ cha–con từ <i>vị trí trong danh sách phẳng</i> (mọi mục {@code depth
     *     = n+1} đứng sau một mục {@code depth = n} là con của mục ấy). Phép suy đó đúng chừng nào
     *     danh sách còn nguyên vẹn — và nó sai ngay lượt đầu tiên có một mục bị lọc bỏ khỏi giữa
     *     danh sách. Đo được trên máy: trang "Tiến độ sản xuất" liệt kê hai danh mục của mục
     *     "Thông báo" (đã ẩn) làm các <b>Năm</b> của nó.
     *     <p>Không trả {@code parentId} chạy số — cùng lý do với mọi DTO công khai khác (§4.2).
     */
    public record CategoryNode(
            String slug, String name, String description, String parentSlug, Short depth, Integer sortOrder) {}

    // ---- Khung cổng ----------------------------------------------------------

    @GetMapping("/site-config")
    @Operation(summary = "Tên cổng, logo, màu, chân trang, khối trang chủ")
    @PublicEndpoint(reason = "Cổng thông tin điện tử hiển thị cho mọi người dân — CN-01.5")
    public Map<String, String> siteConfig() {
        return portal.siteConfig();
    }

    @GetMapping("/menus/{position}")
    @Operation(summary = "Menu điều hướng — chỉ mục đang bật")
    @PublicEndpoint(reason = "Điều hướng của cổng công khai — CN-01.5")
    public List<MenuLink> menu(@PathVariable MenuPosition position) {
        List<MenuService.MenuNode> nodes = portal.menu(position);
        return nodes.stream()
                .map(node -> new MenuLink(
                        node.label(),
                        node.linkType().name(),
                        node.categorySlug(),
                        node.articleSlug(),
                        node.url(),
                        node.openNewTab(),
                        node.depth(),
                        nodes.stream()
                                .filter(x -> x.publicId().equals(node.parentPublicId()))
                                .map(MenuService.MenuNode::label)
                                .findFirst()
                                .orElse(null),
                        node.logoAttachmentId()))
                .toList();
    }

    @GetMapping("/banners")
    @Operation(summary = "Banner đang trong khung lịch hiển thị")
    @PublicEndpoint(reason = "Ảnh carousel trang chủ — CN-01.5")
    public List<BannerView> banners() {
        return portal.banners().stream()
                .map(b -> new BannerView(
                        b.getTitle(),
                        b.getDescription(),
                        b.getImageAttachmentPublicId(),
                        b.getLinkUrl(),
                        b.isOpenNewTab(),
                        b.getSortOrder()))
                .toList();
    }

    @GetMapping("/photos")
    @Operation(summary = "Ảnh thư viện hoạt động trên trang chủ")
    @PublicEndpoint(reason = "Thư viện ảnh hoạt động của cổng — CN-01.5")
    public List<PhotoView> photos() {
        return portal.photos().stream()
                .map(a -> new PhotoView(a.publicId(), a.title()))
                .toList();
    }

    @GetMapping("/categories")
    @Operation(summary = "Danh mục đang hiện")
    @PublicEndpoint(reason = "Điều hướng theo chuyên mục của cổng — CN-01.2")
    public List<CategoryNode> categories() {
        List<com.songnhue.content.domain.Category> hien = portal.categories();
        // Tra slug của cha trong CHÍNH danh sách đang hiện: cha đã bị ẩn thì con cũng không còn ở
        // đây (PublicPortalService loại cả nhánh), nên map này luôn giải được — và nếu một ngày nó
        // không giải được thì `null` là câu trả lời đúng, không phải một slug đoán ra.
        Map<Long, String> slugTheoId = hien.stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.songnhue.content.domain.Category::getId, com.songnhue.content.domain.Category::getSlug));
        return hien.stream()
                .map(c -> new CategoryNode(
                        c.getSlug(),
                        c.getName(),
                        c.getDescription(),
                        c.getParentId() == null ? null : slugTheoId.get(c.getParentId()),
                        c.getDepth(),
                        c.getSortOrder()))
                .toList();
    }

    // ---- Bài viết ------------------------------------------------------------

    @GetMapping("/articles")
    @Operation(summary = "Danh sách bài đã xuất bản, mới nhất trước")
    @PublicEndpoint(reason = "Danh sách tin bài của cổng — CN-01.1")
    public Page<PublicArticleRow> articles(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return portal.articles(category, q, page, size);
    }

    /**
     * Chi tiết một bài.
     *
     * <p>Bài Nháp, Chờ duyệt, Gỡ bài và bài hẹn giờ chưa tới hạn đều trả <b>404</b> — cùng một câu
     * trả lời với slug không tồn tại. Trả 403 hay một thông báo riêng là xác nhận rằng cổng đang có
     * một bài chưa công bố mang tên đó.
     */
    @GetMapping("/articles/{slug}")
    @Operation(summary = "Chi tiết bài viết theo slug")
    @PublicEndpoint(reason = "Trang chi tiết tin bài của cổng — CN-01.1")
    public PublicArticleDetail article(@PathVariable String slug) {
        return portal.article(slug).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /**
     * Ghi nhận một lượt xem — T13.10.
     *
     * <p>{@code 204} và không thân phản hồi: người xem không cần biết kết quả, và trả về con số hiện
     * tại là mời gọi việc gọi liên tục để theo dõi.
     */
    @PostMapping("/articles/{slug}/views")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(summary = "Ghi nhận một lượt xem — gom lô, không ghi ngay xuống CSDL")
    @PublicEndpoint(reason = "Đếm lượt xem bài trên cổng — CN-01.1")
    public void recordView(@PathVariable String slug) {
        portal.recordView(slug);
    }

    // ---- Liên hệ / phản ánh ---------------------------------------------------

    /** Thân yêu cầu của biểu mẫu liên hệ. Kiểm tra nằm ở {@link ContactService}, không ở đây. */
    public record ContactRequest(String fullName, String email, String phone, String subject, String content) {}

    /**
     * Tiếp nhận một liên hệ / phản ánh — CN-01.4.
     *
     * <h2>⛔ 204 và không thân phản hồi</h2>
     *
     * Không trả lại {@code publicId} hay bất kỳ mảnh nào của bản ghi vừa tạo. Đây là đường
     * <b>ẩn danh</b>: trả về một mã định danh là trao cho người gửi (và cho người quét) một tay
     * cầm vào dữ liệu bên trong, mà chẳng để làm gì — biểu mẫu chỉ cần biết đã gửi được.
     *
     * <h2>Chống lạm dụng</h2>
     *
     * Hạn mức tần suất do {@code RateLimitFilter} lo trên tiền tố {@code /api/v1/public}
     * ({@code RateLimitPolicy.PUBLIC}). ⚠ reCAPTCHA v3 mà CN-01.4 yêu cầu <b>chưa dựng</b> —
     * chặn bởi <b>G13</b> (Công ty chưa cấp khoá). Ghi ra để đây không bị đọc thành "đã đủ biện
     * pháp chống lạm dụng".
     */
    @PostMapping("/contacts")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(summary = "Gửi liên hệ / phản ánh từ cổng công khai")
    @PublicEndpoint(reason = "Biểu mẫu liên hệ của người dân — CN-01.4")
    public void submitContact(@RequestBody ContactRequest yeuCau) {
        contacts.tiepNhan(yeuCau.fullName(), yeuCau.email(), yeuCau.phone(), yeuCau.subject(), yeuCau.content());
    }

    // ---- Tệp -----------------------------------------------------------------

    /**
     * Phục vụ một tệp công khai — T16.6.
     *
     * <p>⛔ Chỉ tệp thuộc <b>ba loại chủ sở hữu công khai</b>; danh sách nằm ở
     * {@code PublicPortalService} và được kiểm ở tầng đính kèm của Core, không ở đây. Tệp thuộc loại
     * khác trả 404 y hệt tệp không tồn tại.
     *
     * <p>Trả thẳng {@code ResponseEntity} nên <b>không</b> bị bọc trong envelope — đúng ý: đây là
     * byte của một tấm ảnh, không phải một tài nguyên JSON.
     */
    @GetMapping("/files/{publicId}")
    @Operation(summary = "Ảnh và tệp công khai — phục vụ trực tiếp, không qua presigned URL")
    @PublicEndpoint(reason = "Ảnh trong bài viết, banner và logo của cổng — CN-01.3, CN-01.5")
    public ResponseEntity<byte[]> file(@PathVariable UUID publicId) {
        AttachmentContent tep =
                portal.file(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tep.contentType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(CACHE_TEP_GIAY))
                        .cachePublic()
                        .immutable())
                // `inline` để ảnh hiện trong trang thay vì bật hộp thoại tải về. Tên gốc chỉ để
                // người dùng thấy tên có nghĩa khi họ chủ động lưu tệp.
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + HttpHeaderText.tenTepAnToan(tep.originalName()) + "\"")
                .body(tep.content());
    }

    /**
     * Tài liệu đính kèm của một bài <b>đã xuất bản</b> — WS-40, CN-01.1.
     *
     * <h2>⛔⛔ Vì sao KHÔNG dùng {@code /public/files/&#123;id&#125;}</h2>
     *
     * Tệp tài liệu mang {@code owner_type = 'TAI_LIEU'}, <b>cố ý</b> không nằm trong
     * {@code LOAI_TEP_CONG_KHAI}. Nới danh sách ấy là làm mọi tệp trong Kho tài liệu công khai
     * <i>ngay khi tải lên</i> — kể cả bản dự thảo chưa ai duyệt, kể cả tệp của một bài đã gỡ.
     * QuanTran chốt 04/09 là <b>siết</b>: chỉ tài liệu thuộc bài ĐÃ xuất bản mới tải được. Cùng
     * khuôn với {@code /public/constructions/documents/&#123;id&#125;} của WS-27.
     *
     * <p>⛔ <b>404 trần cho mọi lý do từ chối</b> — bài còn Nháp, bài đã gỡ, tệp chưa quét xong, tệp
     * sai kho, tệp không tồn tại: một câu trả lời duy nhất. Nói <i>"bài chưa xuất bản"</i> là xác
     * nhận tệp có tồn tại. Ngoại lệ duy nhất là <b>413</b> (CMS-2017): lúc nó bắn ra thì tệp đã
     * công khai, nên giấu lý do chỉ làm người biên tập không biết vì sao độc giả tải không được.
     *
     * <p>⚠ {@code attachment} chứ không {@code inline}: đây là văn bản để phát hành, không phải ảnh
     * để hiện trong trang. Và thuộc tính {@code download} của HTML <b>bị bỏ qua khi khác gốc</b> —
     * mà {@code API_BASE_URL} của cổng có thể khác gốc — nên header này là thứ duy nhất quyết định
     * được tên tệp lúc lưu.
     */
    @GetMapping("/article-documents/{publicId}")
    @Operation(summary = "Tài liệu đính kèm của một bài đã xuất bản — tải về")
    @PublicEndpoint(reason = "Tệp đính kèm trong bài viết của cổng — CN-01.1, WS-40")
    public ResponseEntity<byte[]> articleDocument(@PathVariable UUID publicId) {
        AttachmentContent tep =
                portal.articleDocument(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(tep.contentType()))
                // `immutable` an toàn: mỗi lượt tải lên sinh một `public_id` mới, nên thay tệp
                // không bao giờ tái dùng UUID cũ.
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(CACHE_TEP_GIAY))
                        .cachePublic()
                        .immutable())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + HttpHeaderText.tenTepAnToan(tep.originalName()) + "\"")
                .body(tep.content());
    }

    /** Thời điểm máy chủ trả lời — cổng dùng để hiện "cập nhật lúc" mà không phụ thuộc giờ máy khách. */
    @GetMapping("/now")
    @Operation(summary = "Giờ máy chủ (UTC)")
    @PublicEndpoint(reason = "Cổng hiển thị mốc thời gian cập nhật theo giờ máy chủ")
    public Instant now() {
        return Instant.now();
    }
}
