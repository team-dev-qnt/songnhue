package com.songnhue.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Một bài trong danh sách của cổng công khai.
 *
 * <p>⭐ <b>Mọi trường nội dung lấy từ bản đã xuất bản</b> ({@code article_versions}), <b>trừ</b>
 * {@code slug} lấy từ {@code articles}. Đó không phải bất nhất mà là hai câu hỏi khác nhau:
 *
 * <ul>
 *   <li><i>Hiển thị nội dung nào?</i> → bản đang phục vụ. Lấy từ {@code articles} thì bản biên tập
 *       viên vừa gõ dở lên thẳng trang chủ.
 *   <li><i>Địa chỉ là gì?</i> → {@code articles.slug}, vì đó là cột có ràng buộc duy nhất. Bản chụp
 *       cũng chứa slug, nhưng hai bài khác nhau có thể có bản chụp trùng slug (A đổi tên rồi B lấy
 *       tên cũ) — dùng nó làm địa chỉ là tự nhận một URL mơ hồ.
 * </ul>
 *
 * <p>Cột {@code slug} của {@code articles} đóng băng sau lần xuất bản đầu ({@code ArticleService})
 * nên hai nguồn này không lệch nhau trong thực tế.
 *
 * <p>⚠ Đặt ở {@code application} chứ không ở {@code infra}, dù chính repository dựng nó bằng biểu
 * thức khởi tạo của JPQL. Lý do là luật ranh giới: {@code api} <b>không được</b> phụ thuộc vào
 * {@code infra}, nên một record của {@code infra} thì controller không chạm tới được và phải có thêm
 * một lớp ánh xạ chỉ để đổi tên gói. Chiều phụ thuộc ngược lại ({@code infra} → {@code application})
 * không vi phạm luật nào và không kéo theo gì.
 */
public record PublicArticleRow(
        String slug,
        String title,
        String summary,
        UUID coverAttachmentPublicId,
        Instant publishedAt,
        long viewCount,
        List<PublicArticleDetail.CategoryRef> categories) {

    /**
     * Hàm dựng mà <b>biểu thức khởi tạo của JPQL</b> gọi — không có {@code categories}.
     *
     * <p>⚠ JPQL không dựng được collection trong {@code SELECT new …}. Nên danh sách chuyên mục
     * <b>không</b> đi ra từ cùng câu truy vấn với các cột khác; nó được ghép vào sau bằng
     * {@link #withCategories} từ một lượt hỏi thứ hai gom cho cả trang.
     *
     * <p>Cố ý <b>không</b> để mặc định là {@code null}: {@link List#of()} nghĩa là "bài này không
     * thuộc chuyên mục nào đang hiện", còn {@code null} sẽ bắt mọi nơi hiển thị phải nhớ kiểm —
     * và nơi thứ ba sẽ quên (quy tắc 12).
     */
    public PublicArticleRow(
            String slug,
            String title,
            String summary,
            UUID coverAttachmentPublicId,
            Instant publishedAt,
            long viewCount) {
        this(slug, title, summary, coverAttachmentPublicId, publishedAt, viewCount, List.of());
    }

    /** Bản sao có nhãn chuyên mục — CR-12 cần tag phân biệt Tin thủy lợi / Tin Công ty. */
    public PublicArticleRow withCategories(List<PublicArticleDetail.CategoryRef> categories) {
        return new PublicArticleRow(slug, title, summary, coverAttachmentPublicId, publishedAt, viewCount, categories);
    }
}
