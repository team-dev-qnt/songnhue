package com.songnhue.content.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

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

    /**
     * Số ký hiệu và ngày ban hành của văn bản — xem javadoc hai trường cùng tên ở {@code Article}.
     *
     * <p>⭐ Chúng nằm ở đây vì chúng là <b>nội dung</b>, và cổng công khai đọc mọi trường nội dung từ
     * bản đã duyệt. Để chúng chỉ ở {@code articles} thì sửa số ký hiệu của một bài đang xuất bản là
     * đổi ngay trên cổng, <b>không qua duyệt</b> — đúng thứ cơ chế bản chụp sinh ra để chặn.
     */
    @Column(name = "doc_number", length = 100)
    private String docNumber;

    @Column(name = "doc_issued_date")
    private LocalDate docIssuedDate;

    /**
     * Tài liệu đính kèm — <b>BẢN CHỤP</b>, và đây là thứ cổng công khai thật sự đọc (WS-40).
     *
     * <h2>⭐ Vì sao tài liệu phải được chụp, dù danh mục thì không</h2>
     *
     * Tiền lệ gần nhất <b>không</b> phải {@code article_categories} (phân loại, không chụp) mà là
     * {@code coverAttachmentPublicId} — tham chiếu <i>tệp</i> duy nhất đang có, và nó <b>có</b>
     * trong bản chụp. Tài liệu giống ảnh bìa hơn giống danh mục.
     *
     * <p>Khoá danh sách theo {@code article_id} một mình mang đúng hai hệ quả, cả hai đều im lặng:
     *
     * <ol>
     *   <li>phục hồi bản #3 vẫn giữ danh sách tài liệu <i>hiện tại</i> ⇒ cổng phục vụ
     *       <i>"nội dung bản 3 + tài liệu bản 7"</i>;
     *   <li>⛔ đổi tài liệu của một bài <b>đang xuất bản</b> là đổi ngay trên cổng, <b>không qua ai
     *       duyệt</b> — trái CN-01.1.
     * </ol>
     *
     * <p>⛔⛔ {@code ArticleVersionSnapshotTest} <b>không thấy được trường này</b>: nó đếm bằng phản
     * chiếu và một {@code List} khởi tạo rỗng vẫn khác {@code null}, nên nó sẽ xanh kể cả khi
     * {@link #snapshotOf} quên chép. Đó đúng là "một khẳng định không phân biệt được hai trạng
     * thái" (quy tắc 9). Bài giữ chỗ này là {@code ArticleAttachmentTest} — nó đi qua HTTP và đọc
     * lại cổng.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "article_version_attachments", joinColumns = @JoinColumn(name = "article_version_id"))
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    private List<ArticleDocument> documents = new ArrayList<>();

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
        // ⚠ Quy tắc 14: hai cột này phải được chép Ở ĐÂY và phục hồi ở `restoreInto` bên dưới —
        //   quên một trong hai chỗ thì bài kiểm không đỏ, chỉ có một ô trên cổng lặng lẽ rỗng.
        //   `ArticleVersionSnapshotTest` đếm số trường được chép nên nó bắt được thiếu sót.
        version.docNumber = article.getDocNumber();
        version.docIssuedDate = article.getDocIssuedDate();
        // ⭐ Chép DANH SÁCH TÀI LIỆU ở đây — cùng một lượt, cùng một chỗ với `content`.
        //   Đặt ở `ArticleService` thay vì ở đây thì việc chụp thành HAI lời gọi rời nhau, và lời
        //   gọi thứ hai là thứ sẽ bị quên ở đường sinh version thứ ba (quy tắc 12).
        //   ⚠ Chép PHẦN TỬ chứ không gán tham chiếu: hai `@ElementCollection` dùng chung một
        //     `PersistentList` là Hibernate ghi cùng một tập vào cả hai bảng.
        version.documents.addAll(article.getDocuments());
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
        article.setDocNumber(docNumber);
        article.setDocIssuedDate(docIssuedDate);
        // ⭐ Chép NGƯỢC danh sách tài liệu. Thiếu dòng này thì phục hồi bản #3 cho ra
        //   "nội dung bản 3 + tài liệu bản 7" — đúng một trong hai lý do bảng chụp tồn tại, và
        //   triệu chứng của nó là một danh sách tệp trông hoàn toàn bình thường.
        article.getDocuments().clear();
        article.getDocuments().addAll(documents);
        // ⚠ Cố ý KHÔNG phục hồi slug: slug là địa chỉ công khai, đổi nó lúc phục hồi nội dung là âm
        //   thầm làm chết mọi liên kết đang trỏ tới bài. Muốn đổi slug thì đổi tường minh.
    }

    /** Tài liệu của bản chụp này — <b>thứ cổng công khai đọc</b>. */
    public List<ArticleDocument> getDocuments() {
        return documents;
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

    public String getDocNumber() {
        return docNumber;
    }

    public LocalDate getDocIssuedDate() {
        return docIssuedDate;
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
