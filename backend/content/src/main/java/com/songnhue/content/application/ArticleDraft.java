package com.songnhue.content.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Nội dung một bài viết do người dùng nhập — đầu vào của {@link ArticleService}.
 *
 * <p>Gói thành record thay vì truyền hơn chục tham số rời, và cố ý <b>chỉ chứa những trường người
 * dùng được đặt</b>. Trạng thái, phiên bản đang công khai, lượt xem đều không có mặt ở đây: chúng là
 * hệ quả của quy trình duyệt và của máy đếm, nhận từ biểu mẫu là mở đường cho một request tự phong
 * cho mình trạng thái {@code XUAT_BAN}.
 *
 * @param publishedAt để {@code null} khi tạo mới = xuất bản ngay lúc được duyệt; đặt thời điểm tương
 *     lai = hẹn giờ đăng (điểm nghiệp vụ 5)
 * @param categoryPublicIds bắt buộc có ít nhất một — {@code CMS-2006}
 */
public record ArticleDraft(
        String title,
        String slug,
        String summary,
        String content,
        UUID coverAttachmentPublicId,
        Long authorUserId,
        String source,
        Instant publishedAt,
        String metaTitle,
        String metaDescription,
        String metaKeywords,
        String docNumber,
        LocalDate docIssuedDate,
        Set<UUID> categoryPublicIds) {}
