package com.songnhue.content.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ảnh chụp nội dung bài viết tại một thời điểm — CN-01.1 ("so sánh phiên bản, rollback về bản cũ").
 *
 * <p>Bảng này giữ <b>hai</b> vai, và vai thứ hai mới là vai quan trọng:
 *
 * <ol>
 *   <li>Nguồn cho màn hình so sánh và phục hồi bản cũ.
 *   <li><b>Thứ cổng công khai thật sự đọc.</b> {@code Article.publishedVersionId} trỏ vào đây, nên
 *       cổng phục vụ bản đã duyệt chứ không phải bản biên tập viên đang gõ dở.
 * </ol>
 *
 * <p><b>Cố ý không có {@code deletedAt} và không có {@code version}.</b> Đây là lịch sử, mà lịch sử
 * sửa được hoặc xoá mềm được thì không còn là lịch sử — nó thành "phiên bản tôi muốn bạn tin". Cùng
 * lý do với {@code audit_logs} chỉ thêm chứ không sửa.
 *
 * <p>Không gắn {@code @Audited}: ghi nhật ký cho một bảng vốn đã là nhật ký là tự nhân đôi dữ liệu.
 */
@Entity
@Table(name = "article_versions")
public class ArticleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "article_id", nullable = false, updatable = false)
    private Long articleId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private Integer versionNo;

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

    @Column(name = "meta_title", length = 70)
    private String metaTitle;

    @Column(name = "meta_description", length = 160)
    private String metaDescription;

    @Column(name = "meta_keywords", length = 500)
    private String metaKeywords;

    /** Vì sao có bản này: "Lưu nháp", "Duyệt xuất bản", "Phục hồi từ bản #3"… */
    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    protected ArticleVersion() {}

    /**
     * Chụp lại nội dung hiện tại của bài viết.
     *
     * <p>Chép trường thay vì trỏ tham chiếu là <b>có chủ đích</b>: bản chụp phải bất biến kể cả khi
     * bài viết đổi tiếp. Trỏ tham chiếu thì "so sánh hai phiên bản" sẽ luôn cho ra hai bản giống hệt
     * nhau — và không ai phát hiện ra, vì màn hình vẫn chạy.
     */
    public static ArticleVersion snapshotOf(Article article, int versionNo, String note) {
        ArticleVersion version = new ArticleVersion();
        version.articleId = article.getId();
        version.versionNo = versionNo;
        version.title = article.getTitle();
        version.slug = article.getSlug();
        version.summary = article.getSummary();
        version.content = article.getContent();
        version.coverAttachmentPublicId = article.getCoverAttachmentPublicId();
        version.metaTitle = article.getMetaTitle();
        version.metaDescription = article.getMetaDescription();
        version.metaKeywords = article.getMetaKeywords();
        version.note = note;
        return version;
    }

    /** Đưa nội dung của bản chụp này trở lại bài viết — phục hồi bản cũ (CN-01.1). */
    public void restoreInto(Article article) {
        article.setTitle(title);
        article.setSummary(summary);
        article.setContent(content);
        article.setCoverAttachmentPublicId(coverAttachmentPublicId);
        article.setMetaTitle(metaTitle);
        article.setMetaDescription(metaDescription);
        article.setMetaKeywords(metaKeywords);
        // ⚠ Cố ý KHÔNG phục hồi slug: slug là địa chỉ công khai, đổi nó lúc phục hồi nội dung là âm
        //   thầm làm chết mọi liên kết đang trỏ tới bài. Muốn đổi slug thì đổi tường minh.
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }

    public String getSummary() {
        return summary;
    }

    public String getContent() {
        return content;
    }

    public UUID getCoverAttachmentPublicId() {
        return coverAttachmentPublicId;
    }

    public String getMetaTitle() {
        return metaTitle;
    }

    public String getMetaDescription() {
        return metaDescription;
    }

    public String getMetaKeywords() {
        return metaKeywords;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
