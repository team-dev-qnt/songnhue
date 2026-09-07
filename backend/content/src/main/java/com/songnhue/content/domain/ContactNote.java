package com.songnhue.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một ghi chú nội bộ trên hồ sơ liên hệ — CN-01.4.
 *
 * <h2>⛔⛔ ⛔ KHÔNG BAO GIỜ ra cổng công khai</h2>
 *
 * <p>Đây là chỗ cán bộ viết những câu như <i>"người này đã gọi ba lần, chuyển anh Nam"</i>. Không
 * DTO công khai nào được mang trường này, và bài kiểm rò rỉ ở {@code ContactHttpTest} phản chiếu
 * component của mọi record công khai để giữ điều đó — ⛔ không dựa vào việc người viết DTO sau nhớ.
 *
 * <h2>Vì sao lưu {@code contactId} chứ không phải một quan hệ JPA</h2>
 *
 * <p>Cùng lý do với mọi bảng con khác của dự án: một {@code @ManyToOne} kéo theo lazy-loading và một
 * lượt truy vấn ngoài tầm kiểm soát ở đúng chỗ ⛔ không ai ngờ. Khoá ngoại vẫn ở CSDL.
 */
@Entity
@Table(name = "contact_notes")
@Audited(module = "cms", entityType = "ContactNote")
public class ContactNote extends BaseEntity {

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    protected ContactNote() {}

    public ContactNote(Long contactId, String content) {
        this.contactId = contactId;
        this.content = content;
    }

    public Long getContactId() {
        return contactId;
    }

    public String getContent() {
        return content;
    }
}
