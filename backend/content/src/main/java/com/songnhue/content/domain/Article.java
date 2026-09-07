package com.songnhue.content.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

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

    /**
     * Số ký hiệu văn bản — ví dụ {@code 43/2015/NĐ-CP}. {@code null} với tin bài thường.
     *
     * <p>Cổng không có thực thể "văn bản": một văn bản <b>là</b> một bài viết thuộc nhánh
     * {@code cong-bo-thong-tin} (CR-07 — cổng KHÔNG dựng mô-đun văn bản nội bộ và KHÔNG đồng bộ từ
     * hệ thống văn bản điều hành của Thành phố, CN-01.7). Hai cột này là <b>ô để biên tập viên
     * nhập</b>, thêm 01/09/2026 để dựng được bảng danh sách năm cột của cổng tham chiếu.
     *
     * <p>⛔ Để trống ⇒ ô tương ứng trên cổng <b>để trống</b>. Không dựng dấu gạch giả làm một giá
     * trị, và không có bộ dữ liệu dự phòng: bản trang chủ 29/08 từng có bốn văn bản viết cứng kèm
     * số hiệu và người ký — tất cả bịa, và chúng đã lên staging (§10.54, quy tắc 16).
     */
    @Column(name = "doc_number", length = 100)
    private String docNumber;

    /**
     * Ngày ký ban hành văn bản. {@code null} với tin bài thường.
     *
     * <p>⭐ {@link LocalDate} chứ không {@link Instant}, và đó không phải một sự tuỳ tiện với quy
     * tắc 1 của dự án: quy tắc ấy nói về <b>timestamp</b> — mốc thời gian do hệ thống ghi. Ngày ban
     * hành là một NGÀY in trên tờ giấy: không có giờ, không có múi giờ, không có "thời điểm" nào để
     * quy về UTC. Ép nó thành {@code timestamptz} là bịa ra một giờ 00:00 rồi để nó lệch đúng một
     * ngày ở biên UTC+7.
     *
     * <p>⚠ KHÁC {@link #publishedAt} — đó mới là mốc thật (bài lên cổng lúc nào), và nó vẫn là
     * {@code timestamptz}. Hai cột hiện ở hai ô khác nhau của bảng danh sách; <b>không cột nào
     * được suy ra từ cột kia</b>.
     */
    @Column(name = "doc_issued_date")
    private LocalDate docIssuedDate;

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

    /**
     * ⚠ {@code @BatchSize} là thứ giữ cho màn hình danh sách không hoá thành N+1.
     *
     * <p>{@code ArticleService} nạp sẵn quan hệ này trước khi entity rời khỏi giao dịch (xem
     * {@code napQuanHe}); thiếu {@code @BatchSize} thì việc nạp sẵn đó là một truy vấn cho mỗi bài
     * trên trang — 20 bài là 21 lượt xuống CSDL. Với {@code @BatchSize} Hibernate gom lại còn hai.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "article_categories",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new LinkedHashSet<>();

    /**
     * Tài liệu đính kèm — <b>BẢN ĐANG BIÊN TẬP</b> (WS-40).
     *
     * <h3>⛔⛔ Cổng công khai KHÔNG đọc danh sách này</h3>
     *
     * Nó đọc {@code ArticleVersion.getDocuments()} của bản đang được xuất bản, đúng như nó đọc
     * {@code content} từ đó. Đọc thẳng từ đây là để một biên tập viên đổi tài liệu của bài <b>đang
     * chạy trên cổng</b> mà <b>không qua ai duyệt</b> — chính điều mà cơ chế bản chụp sinh ra để
     * chặn (xem javadoc lớp và {@code ArticleVersion}).
     *
     * <p>⚠ {@code List} chứ không {@code Set}: thứ tự tài liệu là dữ liệu người dùng nhập, khác hẳn
     * {@link #categories} vốn không có thứ tự.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "article_attachments", joinColumns = @JoinColumn(name = "article_id"))
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    private List<ArticleDocument> documents = new ArrayList<>();

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

    public String getDocNumber() {
        return docNumber;
    }

    /** Chuỗi rỗng quy về {@code null}: một ô để trống và một ô chứa khoảng trắng là cùng một ý. */
    public void setDocNumber(String docNumber) {
        this.docNumber = docNumber == null || docNumber.isBlank() ? null : docNumber.trim();
    }

    public LocalDate getDocIssuedDate() {
        return docIssuedDate;
    }

    public void setDocIssuedDate(LocalDate docIssuedDate) {
        this.docIssuedDate = docIssuedDate;
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

    /**
     * Danh sách tài liệu đang biên tập — sửa tại chỗ ({@code clear()} + {@code addAll()}), y hệt
     * {@link #getCategories()}.
     *
     * <p>⛔ Không có setter: thay cả tham chiếu là Hibernate mất dấu {@code PersistentList} và lượt
     * lưu kế tiếp ném {@code SharedSessionContract} — cùng lý do {@code categories} cũng không có.
     */
    public List<ArticleDocument> getDocuments() {
        return documents;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public Set<Tag> getTags() {
        return tags;
    }
}
