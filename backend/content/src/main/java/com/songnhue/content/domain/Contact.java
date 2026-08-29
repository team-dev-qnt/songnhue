package com.songnhue.content.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một liên hệ / phản ánh gửi từ cổng công khai — CN-01.4.
 *
 * <h2>⛔ Không lưu địa chỉ IP người gửi</h2>
 *
 * IP là dữ liệu cá nhân theo NĐ 13/2023, và ở đây nó không phục vụ mục đích nào đã công bố:
 * chống lạm dụng đã do {@code RateLimitFilter} lo, ngay trong bộ nhớ, không lưu xuống. Thu thập
 * "để đó phòng khi cần" đúng là thứ nghị định ấy cấm.
 *
 * <h2>Phải có ít nhất một đường liên lạc ngược</h2>
 *
 * Ràng buộc nằm ở {@code ck_contacts_lien_lac} trong CSDL <b>và</b> ở hàm dựng này — không phải
 * chép đôi cho chắc, mà vì hai tầng chặn hai loại lỗi khác nhau: hàm dựng trả lời được người
 * dùng bằng thông báo cụ thể, còn ràng buộc CSDL bịt đường ghi thẳng. Một lời nhắn không có
 * cách trả lời là một bản ghi không dùng được.
 */
@Entity
@Table(name = "contacts")
@Audited(module = "cms", entityType = "Contact")
public class Contact extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContactStatus status = ContactStatus.MOI;

    @Column(name = "read_by")
    private Long readBy;

    @Column(name = "read_at")
    private Instant readAt;

    protected Contact() {}

    public Contact(String fullName, String email, String phone, String subject, String content) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.subject = subject;
        this.content = content;
    }

    /** Đánh dấu đã đọc. Lần đầu tiên ghi dấu; những lần sau giữ nguyên người và mốc đầu. */
    public void danhDauDaDoc(Long nguoiDoc, Instant luc) {
        if (this.status == ContactStatus.MOI) {
            this.status = ContactStatus.DA_DOC;
            this.readBy = nguoiDoc;
            this.readAt = luc;
        }
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public ContactStatus getStatus() {
        return status;
    }

    public Long getReadBy() {
        return readBy;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
