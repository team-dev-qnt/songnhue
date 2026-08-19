package com.songnhue.content.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;
import com.songnhue.core.common.persistence.WorkflowAware;

/**
 * Bài viết / tin tức trên cổng thông tin — CN-01.1.
 *
 * <h2>Vì sao KHÔNG kế thừa {@code ScopedEntity}</h2>
 *
 * Điểm nghiệp vụ 9: nội dung cổng là của toàn Công ty. Gắn phạm vi đơn vị vào đây thì Xí nghiệp A
 * không đọc được tin của Xí nghiệp B <b>trên chính cổng công khai</b> — một cái cổng mà nhân viên
 * nội bộ nhìn thấy ít hơn khách vãng lai.
 *
 * <h2>⭐ Hai khái niệm dễ nhầm nhất của lớp này</h2>
 *
 * <pre>
 *   các trường nội dung ở đây  →  BẢN ĐANG BIÊN TẬP
 *   publishedVersionId         →  BẢN CỔNG CÔNG KHAI ĐANG PHỤC VỤ
 * </pre>
 *
 * Sửa một bài đang xuất bản thì trạng thái chạy về {@code CHO_DUYET} còn {@code publishedVersionId}
 * <b>giữ nguyên</b> — cổng vẫn phục vụ nội dung cũ cho tới khi bản mới được duyệt. Đó là
 * copy-on-write (điểm nghiệp vụ 1, {@code architecture-review.md} §10.2).
 *
 * <p>Nói cách khác: <b>{@code publishedVersionId} quyết định hiển thị NỘI DUNG NÀO, {@code status}
 * quyết định CÓ hiển thị hay không.</b> Gộp hai việc vào một cột thì hoặc là bài biến mất khỏi cổng
 * ngay lúc biên tập viên bấm Lưu, hoặc là bản chưa ai duyệt lên thẳng trang chủ.
 */
@Entity
@Table(name = "articles")
@Audited(module = "cms", entityType = "Bài viết")
public class Article extends BaseEntity implements WorkflowAware {

    /** Khớp {@code workflow_definitions.entity_type} — sai chuỗi này là không tìm ra quy trình nào. */
    public static final String ENTITY_TYPE = "ARTICLE";

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "slug", nullable = false, length = 255)
    private String slug;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "cover_attachment_public_id")
    private UUID coverAttachmentPublicId;

    /** Mặc định là người đang đăng nhập, nhưng cho đổi (CN-01.1) — nên KHÔNG dùng {@code createdBy} thay. */
    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(name = "source", length = 255)
    private String source;

    @Column(name = "status", nullable = false, length = 50)
    private String status = ArticleState.NHAP;

    @Column(name = "published_version_id")
    private Long publishedVersionId;

    /**
     * Thời điểm hiệu lực. Ở tương lai = "Đã lên lịch" (điểm nghiệp vụ 5).
     *
     * <p>Truy vấn công khai lọc {@code published_at <= now()}, nên <b>không cần trạng thái thứ
     * bảy</b> cho việc hẹn giờ. Job 5 phút chỉ để bắn revalidate ISR đúng lúc tới hạn — bài vẫn tự
     * hiện đúng giờ ngay cả khi job chết.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "meta_title", length = 70)
    private String metaTitle;

    @Column(name = "meta_description", length = 160)
    private String metaDescription;

    @Column(name = "meta_keywords", length = 500)
    private String metaKeywords;

    /** ⚠ Số xấp xỉ — cộng dồn trong bộ nhớ rồi ghi theo lô. Không dùng để kiểm toán. */
    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "article_categories",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "article_tags",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    protected Article() {}

    public Article(String title, String slug, String content, Long authorUserId) {
        this.title = title;
        this.slug = slug;
        this.content = content;
        this.authorUserId = authorUserId;
    }

    // ---- WorkflowAware -------------------------------------------------------

    @Override
    public String workflowEntityType() {
        return ENTITY_TYPE;
    }

    @Override
    public String currentState() {
        return status;
    }

    @Override
    public void applyState(String newState) {
        this.status = newState;
    }

    @Override
    public Long entityId() {
        return getId();
    }

    /**
     * Tác giả — người nhận của chiều phản hồi (duyệt xong, trả về sửa).
     *
     * <p>Cố ý trả {@code authorUserId} chứ không phải {@code createdBy}: biểu mẫu cho đổi tác giả,
     * và khi đó thư "bài của bạn bị trả về" phải tới tác giả hiện tại chứ không tới người đã bấm
     * nút Tạo mới từ tháng trước.
     */
    @Override
    public Long ownerUserId() {
        return authorUserId;
    }

    // ---- Truy vấn nghiệp vụ --------------------------------------------------

    /**
     * Bài này có đang hiện trên cổng công khai không.
     *
     * <p>Ba điều kiện, thiếu một là không hiện: đã có bản được duyệt · trạng thái cho phép hiển thị ·
     * đã tới giờ đăng. Gói vào một chỗ để truy vấn của cổng và màn hình quản trị không trả lời khác
     * nhau về cùng một bài.
     */
    public boolean isPubliclyVisible(Instant now) {
        return publishedVersionId != null
                && publishedAt != null
                && !publishedAt.isAfter(now)
                && !ArticleState.GO_BAI.equals(status)
                && !ArticleState.NHAP.equals(status);
    }

    // ---- Getter / setter -----------------------------------------------------

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UUID getCoverAttachmentPublicId() {
        return coverAttachmentPublicId;
    }

    public void setCoverAttachmentPublicId(UUID coverAttachmentPublicId) {
        this.coverAttachmentPublicId = coverAttachmentPublicId;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public void setAuthorUserId(Long authorUserId) {
        this.authorUserId = authorUserId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public Long getPublishedVersionId() {
        return publishedVersionId;
    }

    /** Chỉ gọi khi một bản đã được duyệt — đây là thao tác thay nội dung mà cổng công khai phục vụ. */
    public void servePublicly(Long versionId) {
        this.publishedVersionId = versionId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public String getMetaTitle() {
        return metaTitle;
    }

    public void setMetaTitle(String metaTitle) {
        this.metaTitle = metaTitle;
    }

    public String getMetaDescription() {
        return metaDescription;
    }

    public void setMetaDescription(String metaDescription) {
        this.metaDescription = metaDescription;
    }

    public String getMetaKeywords() {
        return metaKeywords;
    }

    public void setMetaKeywords(String metaKeywords) {
        this.metaKeywords = metaKeywords;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public Set<Tag> getTags() {
        return tags;
    }
}
