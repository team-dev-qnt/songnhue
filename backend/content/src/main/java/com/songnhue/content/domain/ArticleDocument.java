package com.songnhue.content.domain;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Một tài liệu đính kèm bài viết — WS-40, CN-01.1 <i>"Tệp đính kèm | File | Nhiều tệp"</i>.
 *
 * <h2>Vì sao {@code @Embeddable} chứ không phải một entity</h2>
 *
 * Hàng này không có vòng đời riêng: nó sinh ra và mất đi cùng bài viết (hoặc cùng bản chụp), không
 * ai tra cứu nó bằng id, và không ai xoá mềm nó. Ánh xạ bằng {@code @ElementCollection} thì
 * Hibernate lo trọn phần thay thế danh sách — y hệt {@code article.getCategories()} — nên
 * {@code ArticleService} không phải giữ một repository thứ tư chỉ để xoá rồi ghi lại vài dòng.
 *
 * <p>⛔ Hệ quả phải nhớ: sửa danh sách là <b>xoá sạch rồi ghi lại</b>, không phải cập nhật từng
 * dòng. Đừng thêm cột nào cần sống qua một lượt lưu.
 *
 * @param attachmentPublicId trỏ vào {@code attachments.public_id} — theo đúng tiền lệ
 *     {@code articles.cover_attachment_public_id} (§4.2 chống IDOR). ⛔ Không khoá ngoại ở tầng
 *     CSDL: bảng {@code attachments} thuộc module Core, và một ràng buộc chéo module ở tầng CSDL là
 *     phụ thuộc không ai thấy khi đọc mã Java
 */
@Embeddable
public class ArticleDocument {

    @Column(name = "attachment_public_id", nullable = false)
    private UUID attachmentPublicId;

    /**
     * Tên gợi nhớ hiện trên cổng — <i>"Xem quyết định ở đây"</i> thay cho
     * {@code quyet-dinh-thanh-lap.pdf}.
     *
     * <p>⭐ Nhãn thuộc về <b>mối nối</b>, không thuộc về tệp: cùng một PDF gắn vào ba bài mang được
     * ba nhãn khác nhau, và đổi nhãn ở bài này không đụng bài kia.
     *
     * <p>⛔ Rỗng ⇒ nơi hiển thị rơi về {@code originalName}. <b>Không</b> sinh nhãn mặc định kiểu
     * <i>"Tài liệu 1"</i> — đó là bịa một cái tên rồi trình bày nó như dữ liệu (quy tắc 16). Và tên
     * gốc vẫn bắt buộc giữ ở {@code attachments.original_name}: tên trong kho là chuỗi ngẫu nhiên,
     * nên tên gốc là thứ <b>duy nhất</b> truy ngược được khi cần đối chiếu.
     */
    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ArticleDocument() {}

    public ArticleDocument(UUID attachmentPublicId, String label, int sortOrder) {
        this.attachmentPublicId = Objects.requireNonNull(attachmentPublicId, "attachmentPublicId");
        this.label = chuanHoa(label);
        this.sortOrder = sortOrder;
    }

    /**
     * Chuỗi toàn khoảng trắng là <b>chưa nhập</b>, không phải một nhãn rỗng.
     *
     * <p>Ép ở hàm dựng chứ không ở nơi hiển thị (quy tắc 16): để {@code ""} lọt xuống CSDL thì nơi
     * hiển thị phải nhớ kiểm cả {@code null} lẫn {@code ""}, và chỗ thứ hai sẽ quên.
     */
    private static String chuanHoa(String label) {
        return label == null || label.isBlank() ? null : label.trim();
    }

    public UUID getAttachmentPublicId() {
        return attachmentPublicId;
    }

    public String getLabel() {
        return label;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /**
     * Bằng nhau khi <b>cùng tệp</b> — {@code @ElementCollection} cần {@code equals}/{@code hashCode}
     * để so hai phiên bản của danh sách.
     *
     * <p>⚠ Cố ý so cả nhãn và thứ tự: đổi nhãn mà Hibernate coi hai hàng là một thì lượt lưu ấy
     * không ghi gì xuống, và màn hình vẫn báo <i>"Đã lưu"</i>. Đúng hình dạng quy tắc 27.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArticleDocument other)) {
            return false;
        }
        return sortOrder == other.sortOrder
                && Objects.equals(attachmentPublicId, other.attachmentPublicId)
                && Objects.equals(label, other.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attachmentPublicId, label, sortOrder);
    }
}
