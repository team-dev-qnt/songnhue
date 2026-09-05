package com.songnhue.content.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * @param documents tài liệu đính kèm theo <b>thứ tự người dùng sắp</b> (WS-40). ⚠ {@link List} chứ
 *     không {@code Set} như {@code categoryPublicIds}: danh mục không có thứ tự, còn thứ tự tài
 *     liệu là dữ liệu người dùng nhập. {@code null} hoặc rỗng = bài không có tệp nào — ⛔ đó là một
 *     câu trả lời hợp lệ, nơi hiển thị bỏ hẳn khối chứ không vẽ "Đang cập nhật" (quy tắc 16)
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
        Set<UUID> categoryPublicIds,
        List<TaiLieu> documents) {

    /**
     * Một tài liệu người dùng chọn: mã tệp + <b>tên gợi nhớ</b>.
     *
     * @param label chữ hiện trên cổng — <i>"Xem quyết định ở đây"</i> thay cho
     *     {@code quyet-dinh-thanh-lap.pdf}. Rỗng ⇒ rơi về tên gốc; ⛔ không sinh nhãn mặc định
     */
    public record TaiLieu(UUID publicId, String label) {}

    /** {@code null} đọc như danh sách rỗng — nơi gọi khỏi phải nhớ kiểm hai trạng thái. */
    public List<TaiLieu> documentsOrEmpty() {
        return documents == null ? List.of() : documents;
    }
}
