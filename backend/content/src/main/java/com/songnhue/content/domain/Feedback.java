package com.songnhue.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;
import com.songnhue.core.common.persistence.WorkflowReasonAware;

/**
 * Một phản hồi / đánh giá gửi từ cổng công khai — CN-01.6, chốt <b>D1</b>.
 *
 * <h2>⛔⛔ Đây ⛔ KHÔNG phải một bình luận</h2>
 *
 * <p>Chốt D1 (12/8/2026) <b>tắt bình luận công khai tự do</b>. Khác biệt nằm ở lược đồ, ⛔ không ở
 * cái tên: lớp này ⛔ không có {@code parentId} (⛔ không có cây trả lời), ⛔ không có
 * {@code articleId} (⛔ không gắn vào một bài viết), và mặc định của {@code status} là
 * {@code CHO_DUYET} chứ ⛔ không phải "hiện ngay". Thêm bất kỳ thứ nào trong ba thứ ấy là dựng lại
 * đúng cái D1 đã loại.
 *
 * <h2>⭐ {@code rating} cho phép {@code null} — và hệ quả phải được nói ra</h2>
 *
 * <p>CN-01.6 nói "đánh giá mức độ hài lòng <b>hoặc</b> góp ý". Ép chấm sao là ép người chỉ muốn
 * viết một câu; ép nội dung rỗng là để màn hình kiểm duyệt nhận về những bản ghi ⛔ không có gì để
 * duyệt. ⇒ {@code content} bắt buộc, {@code rating} tuỳ chọn.
 *
 * <p>⚠ Vì thế điểm trung bình <b>luôn là con số của một tập con</b>, và
 * {@code FeedbackModerationService.tongHop()} bắt buộc trả kèm số phiếu có chấm điểm — một số
 * trung bình đứng một mình ⛔ không phân biệt được "4,2 trên 5 phiếu" với "4,2 trên 500" (luật 9).
 *
 * <h2>⛔ Nội dung là VĂN BẢN THUẦN</h2>
 *
 * <p>Chuỗi do người lạ trên Internet nhập, và ở entity này nó đi <b>xa hơn</b> {@code contacts}:
 * sau khi duyệt nó hiện trên <b>cổng công khai</b>. Một {@code dangerouslySetInnerHTML} đặt lên
 * {@code content} là XSS lưu trữ nhắm vào <i>mọi người đọc cổng</i>, ⛔ không chỉ vào người quản
 * trị. Nơi hiển thị bắt buộc để React escape.
 *
 * <h2>{@code ownerUserId()} trả {@code null} — câu trả lời ĐÚNG</h2>
 *
 * <p>Người gửi là người dân ngoài hệ thống. Mọi bước chuyển vì thế khai
 * {@code notify_owner = FALSE}; bật lên là bắn thông báo vào hư không (cùng lý lẽ với
 * {@link Contact}).
 */
@Entity
@Table(name = "feedbacks")
@Audited(module = "cms", entityType = "Feedback")
public class Feedback extends BaseEntity implements WorkflowReasonAware {

    /** Khớp {@code workflow_definitions.entity_type}. Sai chuỗi này là ⛔ không tìm ra quy trình nào. */
    public static final String ENTITY_TYPE = "FEEDBACK";

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "email", length = 255)
    private String email;

    /** 1..5, hoặc {@code null} khi người gửi chỉ viết góp ý. Xem javadoc lớp. */
    @Column(name = "rating")
    private Short rating;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.CHO_DUYET;

    /**
     * Lý do duyệt / từ chối / ẩn của bước gần nhất — ghi qua {@link #applyWorkflowReason}.
     *
     * <p>⛔ ⛔ Cột này ⛔ <b>KHÔNG BAO GIỜ</b> ra cổng công khai: đây là chỗ cán bộ viết <i>về</i>
     * người gửi. {@code PublicFeedbackView} ⛔ không có trường tương ứng, và có một bài kiểm phản
     * chiếu đếm số trường của record ấy để giữ điều đó đúng.
     */
    @Column(name = "moderation_note", columnDefinition = "text")
    private String moderationNote;

    protected Feedback() {}

    public Feedback(String fullName, String email, Short rating, String content) {
        this.fullName = fullName;
        this.email = email;
        this.rating = rating;
        this.content = content;
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
     * ⛔ Chỉ {@code WorkflowEngine} gọi — và đây là <b>đường ghi trạng thái DUY NHẤT</b>.
     *
     * <p>Lớp này cố ý ⛔ không có một {@code duyet()} / {@code tuChoi()} nào: bài học T36.1 là
     * {@code Contact.danhDauDaDoc()} từng gán thẳng {@code this.status} và luật ArchUnit
     * {@code chi_workflow_engine_duoc_goi_applyState} ⛔ <b>không</b> thấy — luật soi <i>lời gọi</i>
     * {@code applyState()}, còn một phép gán field bên trong entity thì nằm ngoài tầm nhìn của nó.
     * {@code FeedbackWorkflowHttpTest#chiConMotDuongGhiTrangThai} đếm số chỗ gán trong tệp này.
     */
    @Override
    public void applyState(String newState) {
        this.status = FeedbackStatus.valueOf(newState);
    }

    @Override
    public Long entityId() {
        return getId();
    }

    /**
     * ⭐ Ghi đè bằng {@code null} ở bước ⛔ không đòi lý do là <b>cố ý</b> — cùng lý lẽ với
     * {@link Contact#applyWorkflowReason}: cột mang lý do của <b>bước gần nhất</b>, và bước gần
     * nhất ⛔ không có lý do thì cột phải rỗng, ⛔ không phải giữ câu giải thích của tháng trước
     * đứng cạnh một trạng thái của hôm nay.
     */
    @Override
    public void applyWorkflowReason(String action, String reason) {
        this.moderationNote = reason;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Short getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public String getModerationNote() {
        return moderationNote;
    }
}
