package com.songnhue.content.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.content.application.ContactService;
import com.songnhue.content.domain.Contact;
import com.songnhue.content.domain.ContactStatus;
import com.songnhue.core.common.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Hộp thư liên hệ ở phía quản trị — CN-01.4.
 *
 * <h2>Vì sao controller này ra đời CÙNG lượt với biểu mẫu công khai</h2>
 *
 * Không có nó thì mọi thứ người dân gửi rơi vào một bảng không ai mở được — tức biểu mẫu vẫn là
 * cái mà chú thích cũ ở trang Liên hệ đã cảnh báo: một form gửi đi mà không ai nhận. Vòng đọc–ghi
 * phải đủ cả hai nửa trong cùng một lượt (luật 27).
 *
 * <h2>⛔ Nội dung liên hệ là văn bản do người lạ nhập</h2>
 *
 * Trả về nguyên văn; nơi hiển thị bắt buộc dựng thành text, không dựng thành HTML. Xem
 * {@link ContactService}.
 */
@RestController
@RequestMapping("/api/v1/cms/contacts")
@Tag(name = "01-cms · Liên hệ", description = "Hộp thư tiếp nhận phản ánh từ cổng công khai")
public class ContactController {

    private final ContactService contacts;

    public ContactController(ContactService contacts) {
        this.contacts = contacts;
    }

    public record ContactView(
            UUID publicId,
            String fullName,
            String email,
            String phone,
            String subject,
            String content,
            ContactStatus status,
            Instant createdAt,
            Instant readAt) {

        static ContactView of(Contact c) {
            return new ContactView(
                    c.getPublicId(),
                    c.getFullName(),
                    c.getEmail(),
                    c.getPhone(),
                    c.getSubject(),
                    c.getContent(),
                    c.getStatus(),
                    c.getCreatedAt(),
                    c.getReadAt());
        }
    }

    @GetMapping
    @Operation(summary = "Danh sách liên hệ, mới nhất trước")
    @RequirePermission("cms:contact:manage")
    public Page<ContactView> list(
            @RequestParam(required = false) ContactStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return contacts.danhSach(status, page, size).map(ContactView::of);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số liên hệ chưa đọc — cho huy hiệu trên thanh điều hướng quản trị")
    @RequirePermission("cms:contact:manage")
    public long unreadCount() {
        return contacts.demChuaDoc();
    }

    @PatchMapping("/{publicId}/read")
    @Operation(summary = "Đánh dấu đã đọc — chỉ có tác dụng ở lần đầu")
    @RequirePermission("cms:contact:manage")
    public ContactView markRead(@PathVariable UUID publicId) {
        return ContactView.of(contacts.danhDauDaDoc(publicId));
    }
}
