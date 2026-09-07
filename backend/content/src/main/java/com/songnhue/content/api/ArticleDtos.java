package com.songnhue.content.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.songnhue.content.application.TaiLieuDinhKem;
import com.songnhue.content.domain.Article;
import com.songnhue.content.domain.ArticleVersion;
import com.songnhue.content.domain.Category;
import com.songnhue.core.spi.AllowedAction;

/**
 * DTO của nhóm bài viết.
 *
 * <p>⛔ Không trả entity ra API và <b>không có trường {@code id}</b> — mọi định danh đi ra ngoài là
 * {@code publicId} (§4.2 chống IDOR). Cũng không có trường {@code status} trong DTO nhận vào: trạng
 * thái là kết quả của quy trình duyệt, nhận từ biểu mẫu là để một request tự phong cho mình
 * {@code XUAT_BAN}.
 */
public final class ArticleDtos {

    private ArticleDtos() {}

    /**
     * Một tài liệu đính kèm gửi lên từ màn hình soạn bài — WS-40.
     *
     * @param label tên gợi nhớ hiện trên cổng; rỗng ⇒ cổng hiện tên gốc của tệp
     */
    public record DocumentLink(@jakarta.validation.constraints.NotNull UUID publicId, @Size(max = 255) String label) {}

    /** Dữ liệu tạo/sửa bài viết. */
    public record SaveRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 255) String slug,
            @Size(max = 500) String summary,
            @NotBlank String content,
            UUID coverAttachmentPublicId,
            UUID authorPublicId,
            @Size(max = 255) String source,
            Instant publishedAt,
            @Size(max = 70) String metaTitle,
            @Size(max = 160) String metaDescription,
            @Size(max = 500) String metaKeywords,
            @Size(max = 100) String docNumber,
            LocalDate docIssuedDate,
            @NotEmpty Set<UUID> categoryPublicIds,
            // ⚠ List chứ không Set: thứ tự tài liệu là dữ liệu người dùng sắp, khác categoryPublicIds.
            //   ⛔ Không @NotEmpty — bài không có tệp nào là chuyện bình thường.
            @jakarta.validation.Valid List<DocumentLink> documents) {}

    /** Yêu cầu chuyển trạng thái. {@code reason} bắt buộc khi trả bài về — kiểm ở controller. */
    public record TransitionRequest(@NotBlank String action, @Size(max = 2000) String reason) {}

    public record ArticleSummary(
            UUID publicId,
            String title,
            String slug,
            String status,
            Instant publishedAt,
            Long viewCount,
            List<String> categoryNames) {

        public static ArticleSummary of(Article a) {
            return new ArticleSummary(
                    a.getPublicId(),
                    a.getTitle(),
                    a.getSlug(),
                    a.getStatus(),
                    a.getPublishedAt(),
                    a.getViewCount(),
                    a.getCategories().stream().map(Category::getName).toList());
        }
    }

    /**
     * Chi tiết bài viết cho màn hình quản trị.
     *
     * @param publiclyVisible bài này có đang hiện trên cổng không — <b>tính ở BE</b>. Ba điều kiện
     *     (đã duyệt · trạng thái cho phép · đã tới giờ) mà để FE tự ghép thì sớm muộn màn hình quản
     *     trị và cổng công khai sẽ trả lời khác nhau về cùng một bài
     * @param allowedActions nút được phép bấm, đã lọc theo quyền — FE render chứ không tự suy
     */
    public record ArticleDetail(
            UUID publicId,
            String title,
            String slug,
            String summary,
            String content,
            UUID coverAttachmentPublicId,
            String source,
            String status,
            Instant publishedAt,
            String reviewNote,
            String metaTitle,
            String metaDescription,
            String metaKeywords,
            String docNumber,
            LocalDate docIssuedDate,
            Long viewCount,
            boolean publiclyVisible,
            Set<UUID> categoryPublicIds,
            // ⛔ Đây là BẢN ĐANG BIÊN TẬP. Cổng công khai đọc bản chụp — hai danh sách này khác nhau
            //    chừng nào bản sửa chưa được duyệt, và đó chính là điểm của cơ chế (WS-40).
            List<TaiLieuDinhKem> documents,
            List<AllowedAction> allowedActions) {

        public static ArticleDetail of(
                Article a, List<TaiLieuDinhKem> documents, List<AllowedAction> actions, Instant now) {
            return new ArticleDetail(
                    a.getPublicId(),
                    a.getTitle(),
                    a.getSlug(),
                    a.getSummary(),
                    a.getContent(),
                    a.getCoverAttachmentPublicId(),
                    a.getSource(),
                    a.getStatus(),
                    a.getPublishedAt(),
                    a.getReviewNote(),
                    a.getMetaTitle(),
                    a.getMetaDescription(),
                    a.getMetaKeywords(),
                    a.getDocNumber(),
                    a.getDocIssuedDate(),
                    a.getViewCount(),
                    a.isPubliclyVisible(now),
                    a.getCategories().stream().map(Category::getPublicId).collect(java.util.stream.Collectors.toSet()),
                    documents,
                    actions);
        }
    }

    /** Nội dung đầy đủ của một phiên bản — hai bản ghép lại thành màn hình so sánh. */
    public record VersionContent(
            UUID publicId,
            Integer versionNo,
            String title,
            String summary,
            String content,
            String metaTitle,
            String metaDescription,
            Instant createdAt) {

        public static VersionContent of(ArticleVersion v) {
            return new VersionContent(
                    v.getPublicId(),
                    v.getVersionNo(),
                    v.getTitle(),
                    v.getSummary(),
                    v.getContent(),
                    v.getMetaTitle(),
                    v.getMetaDescription(),
                    v.getCreatedAt());
        }
    }

    public record VersionSummary(
            UUID publicId, Integer versionNo, String title, String note, Instant createdAt, boolean servingPublic) {

        public static VersionSummary of(ArticleVersion v, Long publishedVersionId) {
            return new VersionSummary(
                    v.getPublicId(),
                    v.getVersionNo(),
                    v.getTitle(),
                    v.getNote(),
                    v.getCreatedAt(),
                    v.getId().equals(publishedVersionId));
        }
    }
}
