package com.songnhue.content.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.content.application.ContactService;
import com.songnhue.content.domain.Contact;
import com.songnhue.content.domain.ContactNote;
import com.songnhue.content.domain.ContactStatus;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.AllowedAction;

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

    /**
     * ⚠ {@code categoryName} / {@code assignedUnitName} là <b>tên đã tra</b>, ⛔ không phải id.
     *
     * <p>Giao diện ⛔ không được tự nối hai lượt gọi API để dựng một cái tên: đó là N+1 ở phía trình
     * duyệt, và nó hỏng lặng lẽ khi danh mục bị tắt. Rỗng ⇒ {@code null}, và màn hình nói "Chưa
     * phân loại" — ⛔ không phải một chuỗi rỗng trông như đã điền (quy tắc 16 của CLAUDE.md).
     */
    public record ContactView(
            UUID publicId,
            String fullName,
            String email,
            String phone,
            String subject,
            String content,
            ContactStatus status,
            Instant createdAt,
            Instant readAt,
            UUID categoryPublicId,
            String categoryName,
            UUID assignedUnitPublicId,
            String assignedUnitName,
            String resolutionNote) {}

    /** Một ghi chú nội bộ. ⛔ ⛔ Record này ⛔ KHÔNG BAO GIỜ được dùng ở một endpoint công khai. */
    public record NoteView(UUID publicId, String content, Instant createdAt, Long createdBy) {
        static NoteView of(ContactNote n) {
            return new NoteView(n.getPublicId(), n.getContent(), n.getCreatedAt(), n.getCreatedBy());
        }
    }

    public record TransitionForm(String action, String reason) {}

    public record CategoryForm(UUID categoryPublicId) {}

    public record AssignForm(UUID orgUnitPublicId) {}

    public record NoteForm(String content) {}

    /**
     * Dựng {@link ContactView} kèm hai cái tên đã tra.
     *
     * <p>⚠ ⛔ Không phải phương thức tĩnh {@code of(Contact)} như các controller khác: nó cần tra
     * danh mục và sơ đồ tổ chức, và hai thứ ấy sống ở service. Đặt nó tĩnh là mời người sau nối
     * repository vào controller.
     */
    private ContactView view(Contact c) {
        var pl = contacts.phanLoaiCua(c);
        var dv = contacts.donViCua(c);
        return new ContactView(
                c.getPublicId(),
                c.getFullName(),
                c.getEmail(),
                c.getPhone(),
                c.getSubject(),
                c.getContent(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getReadAt(),
                pl.map(x -> x.getPublicId()).orElse(null),
                pl.map(x -> x.getName()).orElse(null),
                dv.map(x -> x.publicId()).orElse(null),
                dv.map(x -> x.name()).orElse(null),
                c.getResolutionNote());
    }

    @GetMapping
    @Operation(summary = "Danh sách liên hệ, mới nhất trước")
    @RequirePermission("cms:contact:manage")
    public Page<ContactView> list(
            @RequestParam(required = false) ContactStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return contacts.danhSach(status, page, size).map(this::view);
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
        return view(contacts.danhDauDaDoc(publicId));
    }

    // === Quy trình xử lý (T36.1) =============================================

    /**
     * Các nút được phép hiện — <b>giao diện ⛔ không tự suy</b> (conventions.md §3).
     *
     * <p>Danh sách này đã lọc theo quyền và theo trạng thái hiện tại, và nó mang cả cờ
     * {@code requiresReason} để màn hình biết có phải mở ô nhập lý do hay không. Tự liệt kê nút ở FE
     * là dựng bản sao thứ hai của một luật đang nằm trong CSDL — và bản sao ấy sẽ lệch.
     */
    @GetMapping("/{publicId}/actions")
    @Operation(summary = "Bước chuyển hợp lệ ở trạng thái hiện tại, đã lọc theo quyền")
    @RequirePermission("cms:contact:manage")
    public List<AllowedAction> actions(@PathVariable UUID publicId) {
        return contacts.hanhDongChoPhep(publicId);
    }

    @PostMapping("/{publicId}/transitions")
    @Operation(summary = "Thực hiện một bước chuyển; bước nào đòi lý do thì thiếu lý do là SYS-0003")
    @RequirePermission("cms:contact:manage")
    public ContactView transition(@PathVariable UUID publicId, @RequestBody TransitionForm form) {
        return view(contacts.chuyenTrangThai(publicId, form.action(), form.reason()));
    }

    // === Phân loại · chuyển đơn vị · ghi chú nội bộ (T36.2) ===================

    @PatchMapping("/{publicId}/category")
    @Operation(summary = "Gán hoặc gỡ phân loại — thân rỗng/`null` là gỡ")
    @RequirePermission("cms:contact:manage")
    public ContactView setCategory(@PathVariable UUID publicId, @RequestBody CategoryForm form) {
        return view(contacts.phanLoai(publicId, form.categoryPublicId()));
    }

    @PatchMapping("/{publicId}/assignment")
    @Operation(summary = "Chuyển phòng ban/Xí nghiệp xử lý — `null` là thu hồi")
    @RequirePermission("cms:contact:manage")
    public ContactView assign(@PathVariable UUID publicId, @RequestBody AssignForm form) {
        return view(contacts.chuyenDonVi(publicId, form.orgUnitPublicId()));
    }

    @GetMapping("/{publicId}/notes")
    @Operation(summary = "Ghi chú nội bộ, mới nhất trước")
    @RequirePermission("cms:contact:manage")
    public List<NoteView> notes(@PathVariable UUID publicId) {
        return contacts.danhSachGhiChu(publicId).stream().map(NoteView::of).toList();
    }

    @PostMapping("/{publicId}/notes")
    @Operation(summary = "Thêm một ghi chú nội bộ")
    @RequirePermission("cms:contact:manage")
    public NoteView addNote(@PathVariable UUID publicId, @RequestBody NoteForm form) {
        return NoteView.of(contacts.themGhiChu(publicId, form.content()));
    }

    @DeleteMapping("/notes/{notePublicId}")
    @Operation(summary = "Xoá mềm một ghi chú nội bộ")
    @RequirePermission("cms:contact:manage")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID notePublicId) {
        contacts.xoaGhiChu(notePublicId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Xoá mềm một liên hệ. ⛔ <b>CMS-2018</b> khi đang {@code DANG_XU_LY} — CN-01.4.
     *
     * <p>⚠ Gác bằng cùng một quyền {@code cms:contact:manage} với mọi thao tác khác của hộp thư, ⛔
     * không dựng một quyền {@code :delete} riêng. Lý do đo được: danh mục quyền hôm nay có <b>đúng
     * một</b> mã cho liên hệ, và CN-05.2 (sửa ma trận quyền trên giao diện) <b>chưa tồn tại</b> —
     * nên một quyền mới chỉ gán được bằng migration. Thêm một mã mà ⛔ không ai gán được là dựng
     * đúng thứ luật 15 gọi là công tắc chưa ai đọc.
     */
    @DeleteMapping("/{publicId}")
    @Operation(summary = "Xoá mềm — ⛔ cấm khi đang xử lý (CMS-2018)")
    @RequirePermission("cms:contact:manage")
    public ResponseEntity<Void> delete(@PathVariable UUID publicId) {
        contacts.xoa(publicId);
        return ResponseEntity.noContent().build();
    }
}
