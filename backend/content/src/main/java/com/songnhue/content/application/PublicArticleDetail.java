package com.songnhue.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nội dung đầy đủ của một bài trên cổng công khai.
 *
 * @param categories tên danh mục để hiện đường dẫn phân cấp; dùng {@code slug} cho liên kết
 * @param archived {@code true} khi bài ở trạng thái Lưu trữ — vẫn vào được bằng địa chỉ trực tiếp
 *     nhưng không nằm trong danh sách nào. Trả ra để giao diện gắn thẻ {@code noindex} thay vì để
 *     công cụ tìm kiếm giữ mãi một bài đã rút khỏi luồng tin
 */
public record PublicArticleDetail(
        String slug,
        String title,
        String summary,
        String content,
        UUID coverAttachmentPublicId,
        String metaTitle,
        String metaDescription,
        String metaKeywords,
        Instant publishedAt,
        long viewCount,
        boolean archived,
        List<CategoryRef> categories) {

    public record CategoryRef(String slug, String name) {}
}
