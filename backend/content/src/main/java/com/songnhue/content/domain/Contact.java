package com.songnhue.content.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;
import com.songnhue.core.common.persistence.WorkflowReasonAware;

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
 *
 * <h2>⭐⭐ Trạng thái đổi DUY NHẤT qua Workflow engine — kể cả {@code MOI → DA_DOC}</h2>
 *
 * Cho tới WS-36, {@code danhDauDaDoc()} gán thẳng {@code this.status}. Nó ⛔ <b>không</b> bị luật
 * ArchUnit {@code chi_workflow_engine_duoc_goi_applyState} bắt, vì luật ấy soi lời gọi
 * {@code applyState()} còn đây là một phép gán field <i>bên trong</i> entity. Kết quả là một cột có
 * <b>hai</b> đường ghi — một đường kiểm quyền, bắn thông báo, ghi nhật ký; một đường ⛔ không —
 * và người đọc mã ⛔ không có cách nào thấy sự bất đối xứng đó.
 *
 * <p>⇒ {@code danhDauDaDoc()} đã bị <b>xoá</b>. "Đã đọc" nay là bước chuyển {@code READ}, đi cùng
 * một đường với năm bước còn lại.
 *
 * <h2>{@code ownerUserId()} trả {@code null}, và đó là câu trả lời ĐÚNG</h2>
 *
 * Người gửi là người dân ngoài hệ thống — ⛔ không có {@code user_id}. Mọi bước chuyển vì thế khai
 * {@code notify_owner = FALSE}; bật lên là bắn thông báo vào hư không. Chiều báo cho người gửi đi
 * bằng <b>email</b> (T36.3), một đường khác hẳn.
 */
@Entity
@Table(name = "contacts")
@Audited(module = "cms", entityType = "Contact")
public class Contact extends BaseEntity implements WorkflowReasonAware {

    /** Khớp {@code workflow_definitions.entity_type}. Sai chuỗi này là ⛔ không tìm ra quy trình nào. */
    public static final String ENTITY_TYPE = "CONTACT";

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

    /** Phân loại do Công ty tự khai ({@link ContactCategory}). {@code null} = chưa phân loại. */
    @Column(name = "category_id")
    private Long categoryId;

    /** Phòng ban/Xí nghiệp được chuyển xử lý. {@code null} = chưa chuyển ai. */
    @Column(name = "assigned_org_unit_id")
    private Long assignedOrgUnitId;

    /**
     * Lý do / nội dung phản hồi của bước chuyển gần nhất — ghi qua {@link #applyWorkflowReason}.
     *
     * <p>Đây là chỗ <b>duy nhất</b> giữ nó: {@code audit_logs} có chuỗi băm nên thêm cột là đổi
     * cách tính hash của toàn bộ lịch sử. Xem {@link WorkflowReasonAware}.
     */
    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    protected Contact() {}

    public Contact(String fullName, String email, String phone, String subject, String content) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.subject = subject;
        this.content = content;
    }

    /**
     * Ghi <b>ai</b> đã mở lần đầu và <b>lúc nào</b> — ⛔ không đụng tới {@code status}.
     *
     * <p>Trạng thái là việc của engine. Hai mốc này chỉ là siêu dữ liệu đi kèm, và chúng ghi <b>một
     * lần</b>: người mở thứ hai ⛔ không ghi đè dấu vết của người thứ nhất.
     */
    public void ghiDauVetDoc(Long nguoiDoc, Instant luc) {
        if (this.readAt == null) {
            this.readBy = nguoiDoc;
            this.readAt = luc;
        }
    }

    public void phanLoai(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void chuyenDonVi(Long orgUnitId) {
        this.assignedOrgUnitId = orgUnitId;
    }

    // === Workflow ============================================================

    @Override
    public String workflowEntityType() {
        return ENTITY_TYPE;
    }

    @Override
    public String currentState() {
        return status.name();
    }

    /**
     * ⛔ Chỉ {@code WorkflowEngine} gọi.
     *
     * <p>{@code valueOf} ném khi chuỗi lạ, và đó là hành vi mong muốn: một {@code to_state} gõ sai
     * trong migration phải <b>hỏng ngay ở lượt bấm đầu tiên</b>, ⛔ không lặng lẽ giữ nguyên trạng
     * thái cũ rồi để người dùng bấm lại lần thứ ba.
     */
    @Override
    public void applyState(String newState) {
        this.status = ContactStatus.valueOf(newState);
    }

    @Override
    public Long entityId() {
        return getId();
    }

    /**
     * Đơn vị được chuyển xử lý — nguồn tìm người nhận thông báo.
     *
     * <p>⚠ ⛔ Không phải đơn vị của người gửi: người gửi ở ngoài hệ thống.
     */
    @Override
    public Long orgUnitId() {
        return assignedOrgUnitId;
    }

    /**
     * ⭐ Ghi đè bằng {@code null} ở bước ⛔ không đòi lý do là <b>cố ý</b>.
     *
     * <p>{@code WorkflowReasonAware} nói rõ engine gọi hàm này ở <b>mọi</b> bước và entity tự quyết.
     * Giữ lại lý do cũ sau một bước mới là để một câu giải thích của tháng trước đứng cạnh một
     * trạng thái của hôm nay — người đọc sẽ hiểu nó là lý do của bước vừa rồi. Cột này mang lý do
     * của <b>bước gần nhất</b>, và bước gần nhất ⛔ không có lý do thì cột phải rỗng.
     */
    @Override
    public void applyWorkflowReason(String action, String reason) {
        this.resolutionNote = reason;
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

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getAssignedOrgUnitId() {
        return assignedOrgUnitId;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }
}
