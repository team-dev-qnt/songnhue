package com.songnhue.content.application;

import java.time.Instant;
import java.time.LocalDate;
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
 * @param docNumber số ký hiệu văn bản, {@code null} với tin bài thường
 * @param docIssuedDate ngày ký ban hành — <b>khác</b> {@code publishedAt} (thời gian đăng lên cổng),
 *     và không cột nào được suy ra từ cột kia. ⛔ Rỗng thì nơi hiển thị để TRỐNG, không dựng dấu
 *     gạch giả làm một giá trị (quy tắc 16)
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
        String docNumber,
        LocalDate docIssuedDate,
        List<CategoryRef> categories,
        List<TaiLieuRef> documents) {

    public record CategoryRef(String slug, String name) {}

    /**
     * Một tài liệu đính kèm <b>đã đủ điều kiện phục vụ</b> — WS-40.
     *
     * <p>⛔ Danh sách này chỉ chứa tệp <b>tải về được thật</b>: đúng kho ({@code TAI_LIEU}), chưa
     * xoá, đã quét virus xong. Một dòng có tên mà bấm vào là 404 tệ hơn hẳn một dòng không có —
     * đúng hình dạng §10.52, nơi bài kiểm chỉ đi nhánh 404 nên chưa ai thấy ảnh cổng chưa từng ra
     * được một byte.
     *
     * <p>⛔ Rỗng thì nơi hiển thị <b>bỏ hẳn khối</b>, không vẽ "Đang cập nhật" (quy tắc 16).
     *
     * @param title chữ hiện trên cổng — {@code label} do người biên tập đặt, hoặc tên gốc của tệp
     *     khi chưa ai đặt. ⭐ Việc rơi-về-tên-gốc quyết định ở ĐÂY, một chỗ duy nhất: để phía giao
     *     diện tự chọn thì khối cuối bài, liên kết giữa nội dung và tên tệp lúc tải về sẽ nói ba
     *     kiểu khác nhau (quy tắc 12)
     * @param contentType để giao diện in nhãn loại (pdf/docx/xlsx) — ⛔ không suy từ đuôi tên tệp,
     *     tên do người dùng đặt còn cái này do magic-bytes xác định
     */
    public record TaiLieuRef(UUID publicId, String title, String contentType, long sizeBytes) {}
}
