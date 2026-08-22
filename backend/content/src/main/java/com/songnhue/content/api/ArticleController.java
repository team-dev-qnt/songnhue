package com.songnhue.content.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.content.application.ArticleDraft;
import com.songnhue.content.application.ArticleService;
import com.songnhue.content.domain.Article;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.core.spi.UserDirectoryPort;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Bài viết — {@code /api/v1/cms/articles/**} (CN-01.1).
 *
 * <p>⭐ <b>Không có endpoint nào đặt trạng thái.</b> Muốn đổi trạng thái thì gọi
 * {@code POST /{id}/transitions} với tên hành động, và workflow engine quyết định hành động đó có
 * hợp lệ không, người gọi có quyền không. Một endpoint {@code PUT /{id}/status} sẽ vô hiệu hoá toàn
 * bộ chuyện đó chỉ bằng một dòng JSON.
 */
@RestController
@RequestMapping("/api/v1/cms/articles")
@Tag(name = "01-cms · Bài viết", description = "Soạn, duyệt và xuất bản nội dung cổng")
public class ArticleController {

    /** Cột được phép sắp xếp — người dùng truyền tên cột tự do là mở đường dò dữ liệu. */
    private static final List<String> SORTABLE = List.of("createdAt", "updatedAt", "publishedAt", "title", "viewCount");

    private final ArticleService articles;
    private final UserDirectoryPort userDirectory;

    public ArticleController(ArticleService articles, UserDirectoryPort userDirectory) {
        this.articles = articles;
        this.userDirectory = userDirectory;
    }

    /**
     * Bộ lọc của màn hình danh sách, gói làm một đối tượng.
     *
     * <p>Spring gắn từng tham số truy vấn vào đúng thành phần cùng tên. Gói lại vừa để Checkstyle
     * khỏi kêu chín tham số, vừa để thêm một tiêu chí lọc về sau không phải sửa chữ ký hàm.
     */
    public record SearchFilter(String q, String status, UUID authorId, UUID categoryId, Instant from, Instant to) {}

    @GetMapping
    @Operation(summary = "Tìm và lọc bài viết")
    @RequirePermission("cms:article:view")
    public Page<ArticleDtos.ArticleSummary> search(
            @ParameterObject SearchFilter filter,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "createdAt,desc") String sort) {

        Long author = filter.authorId() == null ? null : requireUser(filter.authorId());
        Pageable pageable = PageUtils.toPageable(page, size, sort, SORTABLE);
        return articles.search(
                        filter.q(), filter.status(), author, filter.categoryId(), filter.from(), filter.to(), pageable)
                .map(ArticleDtos.ArticleSummary::of);
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Chi tiết bài viết kèm danh sách nút được phép bấm")
    @RequirePermission("cms:article:view")
    public ArticleDtos.ArticleDetail detail(@PathVariable UUID publicId) {
        Article article = articles.get(publicId);
        return ArticleDtos.ArticleDetail.of(article, articles.allowedActions(publicId), Instant.now());
    }

    @GetMapping("/{publicId}/versions")
    @Operation(summary = "Lịch sử phiên bản — nguồn cho so sánh và phục hồi")
    @RequirePermission("cms:article:view")
    public List<ArticleDtos.VersionSummary> versions(@PathVariable UUID publicId) {
        Long publishedVersionId = articles.get(publicId).getPublishedVersionId();
        return articles.versionsOf(publicId).stream()
                .map(v -> ArticleDtos.VersionSummary.of(v, publishedVersionId))
                .toList();
    }

    /**
     * Nội dung đầy đủ của một phiên bản — nguồn cho màn hình so sánh.
     *
     * <p>Tách khỏi danh sách phiên bản có chủ đích: danh sách có thể dài hàng chục dòng, mà nội dung
     * bài là RichText nặng. Trả kèm là mỗi lần mở tab "Lịch sử" lại kéo về cả chục bản HTML đầy đủ.
     * So sánh thì chỉ cần đúng hai bản.
     */
    @GetMapping("/{publicId}/versions/{versionId}")
    @Operation(summary = "Nội dung một phiên bản — để so sánh hoặc xem lại")
    @RequirePermission("cms:article:view")
    public ArticleDtos.VersionContent versionContent(@PathVariable UUID publicId, @PathVariable UUID versionId) {
        return ArticleDtos.VersionContent.of(articles.version(publicId, versionId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo bài viết mới — luôn bắt đầu ở trạng thái Nháp")
    @RequirePermission("cms:article:create")
    public ArticleDtos.ArticleDetail create(@Valid @RequestBody ArticleDtos.SaveRequest request) {
        Article saved = articles.create(toDraft(request));
        return ArticleDtos.ArticleDetail.of(saved, articles.allowedActions(saved.getPublicId()), Instant.now());
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa nội dung — bài đang xuất bản vẫn giữ nguyên bản công khai")
    @RequirePermission("cms:article:update")
    public ArticleDtos.ArticleDetail update(
            @PathVariable UUID publicId, @Valid @RequestBody ArticleDtos.SaveRequest request) {
        Article saved = articles.update(publicId, toDraft(request));
        return ArticleDtos.ArticleDetail.of(saved, articles.allowedActions(publicId), Instant.now());
    }

    /**
     * Chuyển trạng thái.
     *
     * <p>⛔ Khai {@code cms:article:view} chứ không phải quyền của từng bước — quyền thật nằm ở
     * {@code workflow_transitions.required_permission} và engine kiểm trong cùng transaction. Khai
     * cứng ở đây một quyền cụ thể là dựng bộ luật thứ hai, rồi hai bộ sẽ lệch nhau.
     */
    @PostMapping("/{publicId}/transitions")
    @Operation(summary = "Gửi duyệt / duyệt / trả về / gỡ bài / lưu trữ")
    @RequirePermission("cms:article:view")
    public ArticleDtos.ArticleDetail transition(
            @PathVariable UUID publicId, @Valid @RequestBody ArticleDtos.TransitionRequest request) {

        if ("REQUEST_CHANGES".equals(request.action())
                && (request.reason() == null || request.reason().isBlank())) {
            // Trả bài mà không nói vì sao thì người viết chỉ biết là "bị từ chối" — vòng lặp sửa/gửi
            // lại sẽ chạy vài lượt trước khi hai bên hiểu nhau.
            throw new ValidationException(ErrorCode.SYS_0003, "reason", "Phải nêu lý do khi yêu cầu chỉnh sửa");
        }
        Article saved = articles.execute(publicId, request.action(), request.reason());
        return ArticleDtos.ArticleDetail.of(saved, articles.allowedActions(publicId), Instant.now());
    }

    @PostMapping("/{publicId}/versions/{versionId}/restore")
    @Operation(summary = "Phục hồi nội dung từ một phiên bản cũ")
    @RequirePermission("cms:article:update")
    public ArticleDtos.ArticleDetail restore(@PathVariable UUID publicId, @PathVariable UUID versionId) {
        Article saved = articles.restoreVersion(publicId, versionId);
        return ArticleDtos.ArticleDetail.of(saved, articles.allowedActions(publicId), Instant.now());
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm bài viết")
    @RequirePermission("cms:article:delete")
    public void delete(@PathVariable UUID publicId) {
        articles.delete(publicId);
    }

    // -------------------------------------------------------------------------

    private ArticleDraft toDraft(ArticleDtos.SaveRequest r) {
        return new ArticleDraft(
                r.title(),
                r.slug(),
                r.summary(),
                r.content(),
                r.coverAttachmentPublicId(),
                r.authorPublicId() == null ? null : requireUser(r.authorPublicId()),
                r.source(),
                r.publishedAt(),
                r.metaTitle(),
                r.metaDescription(),
                r.metaKeywords(),
                r.categoryPublicIds());
    }

    private Long requireUser(UUID publicId) {
        return userDirectory
                .internalIdOf(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }
}
