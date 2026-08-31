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
 * @param source ô "Nguồn tin" biên tập viên nhập (CN-01.1), <b>nguyên văn</b> — có thể là URL bài
 *     gốc, có thể là tên báo, và có thể {@code null}. ⛔ Không suy diễn, không điền mặc định ở đây:
 *     tới 31/08/2026 chân mỗi bài trên cổng in cứng <i>"Nguồn: Cổng TTĐT Thủy lợi Sông Nhuệ"</i> cho
 *     MỌI bài, kể cả năm bài mang URL báo ngoài trong CSDL — một câu sai sự thật, không phải một
 *     nhãn mặc định (nợ T26.63). Rỗng thì nơi hiển thị <b>bỏ hẳn dòng</b>
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
        String source,
        boolean archived,
        List<CategoryRef> categories) {

    public record CategoryRef(String slug, String name) {}
}
