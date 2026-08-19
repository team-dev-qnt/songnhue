package com.songnhue.content.application;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Article;
import com.songnhue.content.domain.ArticleState;
import com.songnhue.content.domain.ArticleVersion;
import com.songnhue.content.domain.Category;
import com.songnhue.content.infra.ArticleRepository;
import com.songnhue.content.infra.ArticleVersionRepository;
import com.songnhue.content.infra.CategoryRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.util.VietnameseUtils;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.WorkflowPort;

/**
 * Bài viết — CN-01.1.
 *
 * <h2>⭐ Copy-on-write, và vì sao nó không thể nằm ở tầng khác</h2>
 *
 * Sửa một bài <b>đang xuất bản</b> không được làm bài biến mất khỏi cổng. Cách làm:
 *
 * <ol>
 *   <li>Nội dung mới ghi thẳng lên {@link Article} — đó là bản đang biên tập.
 *   <li>{@code publishedVersionId} <b>không đụng tới</b> — cổng vẫn phục vụ bản chụp cũ.
 *   <li>Bài chuyển sang {@code CHO_DUYET} qua workflow engine.
 *   <li>Duyệt xong mới chụp bản mới và trỏ {@code publishedVersionId} sang đó.
 * </ol>
 *
 * <p>Người có quyền {@code cms:article:publish} thì bước 3–4 gộp làm một: sửa xong là bản mới lên
 * cổng luôn (điểm nghiệp vụ 1). Không phải đặc quyền tuỳ tiện — đội nội dung của Công ty có 1–2
 * người, bắt họ tự gửi cho chính mình duyệt là thêm hai lần bấm chuột không đổi lại điều gì.
 *
 * <p>⛔ <b>Không có chỗ nào trong lớp này gọi {@code applyState}.</b> Mọi chuyển trạng thái đi qua
 * {@link WorkflowPort#execute} — đi tắt là bỏ qua cùng lúc kiểm quyền, bắn thông báo và ghi nhật ký,
 * cả ba đều im lặng (quy tắc 4).
 */
@Service
public class ArticleService {

    private final ArticleRepository articles;
    private final ArticleVersionRepository versions;
    private final CategoryRepository categories;
    private final WorkflowPort workflow;

    public ArticleService(
            ArticleRepository articles,
            ArticleVersionRepository versions,
            CategoryRepository categories,
            WorkflowPort workflow) {
        this.articles = articles;
        this.versions = versions;
        this.categories = categories;
        this.workflow = workflow;
    }

    // ---- Đọc -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public Article get(UUID publicId) {
        return articles.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    @Transactional(readOnly = true)
    public Page<Article> search(
            String tuKhoa, String trangThai, Long tacGia, UUID danhMuc, Instant tuNgay, Instant denNgay, Pageable p) {

        Long danhMucId = danhMuc == null
                ? null
                : categories
                        .findByPublicIdAndDeletedAtIsNull(danhMuc)
                        .map(Category::getId)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        // Bọc `%…%` ở đây chứ không ở repository: nơi gọi truyền chuỗi người dùng gõ, còn cú pháp
        // LIKE là chuyện của tầng truy vấn. Chuẩn hoá luôn để "Đê Điều" khớp "de dieu".
        String mau = tuKhoa == null || tuKhoa.isBlank() ? null : "%" + VietnameseUtils.normalizeForSearch(tuKhoa) + "%";

        return articles.search(mau, trangThai, tacGia, danhMucId, tuNgay, denNgay, p);
    }

    @Transactional(readOnly = true)
    public List<ArticleVersion> versionsOf(UUID publicId) {
        return versions.findByArticleIdOrderByVersionNoDesc(get(publicId).getId());
    }

    /**
     * Một phiên bản cụ thể của một bài cụ thể.
     *
     * <p>Lọc theo {@code articleId} chứ không tra {@code versionPublicId} trần: nếu không thì biết
     * một mã phiên bản là đọc được nội dung của bài bất kỳ, kể cả bài chưa xuất bản của người khác.
     */
    @Transactional(readOnly = true)
    public ArticleVersion version(UUID publicId, UUID versionPublicId) {
        Long articleId = get(publicId).getId();
        return versions.findByPublicId(versionPublicId)
                .filter(v -> v.getArticleId().equals(articleId))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /** Nút giao diện được phép hiện — FE render từ đây, không tự suy từ trạng thái. */
    @Transactional(readOnly = true)
    public List<AllowedAction> allowedActions(UUID publicId) {
        return workflow.allowedActions(get(publicId));
    }

    // ---- Ghi -----------------------------------------------------------------

    @Transactional
    public Article create(ArticleDraft draft) {
        requireCategories(draft.categoryPublicIds());

        Long author = draft.authorUserId() != null ? draft.authorUserId() : currentUserId();
        Article article = new Article(
                draft.title(), requireUniqueSlug(draft.slug(), draft.title(), null), draft.content(), author);
        applyEditableFields(article, draft);

        Article saved = articles.saveAndFlush(article);
        saved.getCategories().addAll(resolveCategories(draft.categoryPublicIds()));
        snapshot(saved, "Tạo mới");
        return saved;
    }

    /**
     * Sửa nội dung bài viết.
     *
     * <p>⚠ Chặn sửa khi đang {@code CHO_DUYET} (CN-01.1 "khóa chỉnh sửa"): người duyệt đang đọc một
     * bản, mà tác giả sửa dưới chân thì người duyệt bấm Duyệt cho một nội dung họ chưa từng thấy.
     *
     * <p>Bài đang xuất bản sửa được bình thường — bản công khai không đổi cho tới khi có người duyệt.
     * Đó chính là copy-on-write, xem javadoc của lớp.
     */
    @Transactional
    public Article update(UUID publicId, ArticleDraft draft) {
        Article article = get(publicId);
        if (ArticleState.CHO_DUYET.equals(article.getStatus())) {
            throw new BusinessRuleException(ErrorCode.CMS_2007);
        }
        requireCategories(draft.categoryPublicIds());

        article.setTitle(draft.title());
        article.setSlug(requireUniqueSlug(draft.slug(), draft.title(), article.getId()));
        article.setContent(draft.content());
        applyEditableFields(article, draft);
        if (draft.authorUserId() != null) {
            article.setAuthorUserId(draft.authorUserId());
        }
        article.getCategories().clear();
        article.getCategories().addAll(resolveCategories(draft.categoryPublicIds()));

        snapshot(article, "Lưu bản sửa");

        // Người có quyền xuất bản sửa một bài đang chạy → bản mới lên cổng ngay, không vòng qua duyệt.
        if (ArticleState.XUAT_BAN.equals(article.getStatus()) && hasPermission("cms:article:publish")) {
            serveLatestVersion(article);
        }
        return article;
    }

    /**
     * Thực hiện một hành động của quy trình duyệt.
     *
     * <p>Hai việc <b>phải nằm cùng một transaction</b> và đúng thứ tự này: engine kiểm quyền + đổi
     * trạng thái trước, rồi mới tới hệ quả về nội dung công khai. Ngược lại thì một lượt duyệt bị từ
     * chối vì thiếu quyền vẫn kịp đẩy bản mới lên cổng.
     */
    @Transactional
    public Article execute(UUID publicId, String action, String reason) {
        Article article = get(publicId);
        workflow.execute(article, action, null);

        switch (action) {
            case "APPROVE" -> {
                article.setReviewNote(null);
                if (article.getPublishedAt() == null) {
                    article.setPublishedAt(Instant.now());
                }
                serveLatestVersion(article);
            }
            case "REQUEST_CHANGES" -> article.setReviewNote(reason);
            // GO_BAI / LUU_TRU / REPUBLISH chỉ đổi việc CÓ hiển thị hay không, nên không đụng vào
            // publishedVersionId — nội dung công khai giữ nguyên, đúng như spec: tái xuất bản từ
            // Gỡ bài không cần duyệt lại.
            default -> {}
        }
        return article;
    }

    /**
     * Phục hồi nội dung từ một phiên bản cũ (CN-01.1).
     *
     * <p>Phục hồi là một <b>lần sửa</b>, không phải một bước lùi thời gian: nó ghi thêm một phiên bản
     * mới mang nội dung cũ. Ghi đè ngược lại lịch sử thì bản đã bị thay không còn dấu vết, mà đó lại
     * đúng là bản người ta cần tra khi đi tìm "ai đã bỏ đoạn này đi".
     */
    @Transactional
    public Article restoreVersion(UUID publicId, UUID versionPublicId) {
        Article article = get(publicId);
        if (ArticleState.CHO_DUYET.equals(article.getStatus())) {
            throw new BusinessRuleException(ErrorCode.CMS_2007);
        }
        ArticleVersion version = versions.findByPublicId(versionPublicId)
                .filter(v -> v.getArticleId().equals(article.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        version.restoreInto(article);
        snapshot(article, "Phục hồi từ bản #" + version.getVersionNo());
        return article;
    }

    @Transactional
    public void delete(UUID publicId) {
        get(publicId).markDeleted(Instant.now());
    }

    // -------------------------------------------------------------------------

    /** Chụp bản hiện tại và trỏ cổng công khai sang đó. */
    private void serveLatestVersion(Article article) {
        ArticleVersion published = snapshot(article, "Xuất bản");
        article.servePublicly(published.getId());
    }

    private ArticleVersion snapshot(Article article, String note) {
        int next = versions.maxVersionNo(article.getId()) + 1;
        ArticleVersion version = ArticleVersion.snapshotOf(article, next, note);
        version.setCreatedBy(currentUserId());
        return versions.saveAndFlush(version);
    }

    private void applyEditableFields(Article article, ArticleDraft draft) {
        article.setSummary(draft.summary());
        article.setSource(draft.source());
        article.setCoverAttachmentPublicId(draft.coverAttachmentPublicId());
        article.setMetaTitle(draft.metaTitle());
        article.setMetaDescription(draft.metaDescription());
        article.setMetaKeywords(draft.metaKeywords());
        if (draft.publishedAt() != null) {
            article.setPublishedAt(draft.publishedAt());
        }
    }

    private static void requireCategories(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.CMS_2006);
        }
    }

    private Set<Category> resolveCategories(Set<UUID> ids) {
        return ids.stream()
                .map(id -> categories
                        .findByPublicIdAndDeletedAtIsNull(id)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String requireUniqueSlug(String slug, String title, Long selfId) {
        String candidate =
                slug == null || slug.isBlank() ? VietnameseUtils.toSlug(title) : VietnameseUtils.toSlug(slug);
        boolean taken = selfId == null
                ? articles.existsBySlugAndDeletedAtIsNull(candidate)
                : articles.existsBySlugAndDeletedAtIsNullAndIdNot(candidate, selfId);
        if (taken) {
            throw new BusinessRuleException(ErrorCode.CMS_2001, candidate);
        }
        return candidate;
    }

    private static boolean hasPermission(String code) {
        return AuthContext.current()
                .map(AuthenticatedUser::permissions)
                .map(p -> p.contains(code))
                .orElse(false);
    }

    private static Long currentUserId() {
        return AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
    }
}
