package com.songnhue.core.domain.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một bước chuyển: {@code (from_state, action)} → {@code to_state}.
 *
 * <p>Chỉ mục duy nhất {@code uq_workflow_transitions (definition_id, from_state, action)} bảo đảm
 * mỗi cặp chỉ có một đích. Hai đích cho cùng một hành động thì kết quả phụ thuộc thứ tự dòng trong
 * bảng — không xác định được, và không có lỗi nào báo ra.
 */
@Entity
@Table(name = "workflow_transitions")
public class WorkflowTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "from_state", nullable = false, length = 50)
    private String fromState;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "to_state", nullable = false, length = 50)
    private String toState;

    /** Quyền cần có để thực hiện. Rỗng = ai xem được đối tượng cũng làm được. */
    @Column(name = "required_permission", length = 100)
    private String requiredPermission;

    /** Sự kiện bắn cho Notification service sau khi chuyển thành công. */
    @Column(name = "notify_event", length = 60)
    private String notifyEvent;

    /** Nhãn nút trên giao diện — FE render từ đây, không tự đặt tên hành động. */
    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected WorkflowTransition() {}

    public Long getId() {
        return id;
    }

    public Long getDefinitionId() {
        return definitionId;
    }

    public String getFromState() {
        return fromState;
    }

    public String getAction() {
        return action;
    }

    public String getToState() {
        return toState;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public String getNotifyEvent() {
        return notifyEvent;
    }

    public String getLabel() {
        return label;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
