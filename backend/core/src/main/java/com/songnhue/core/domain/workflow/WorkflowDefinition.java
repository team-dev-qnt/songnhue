package com.songnhue.core.domain.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Định nghĩa một quy trình duyệt — pattern P1 (implement.md §2).
 *
 * <p>Quy trình nằm trong <b>dữ liệu</b>, không nằm trong mã: thêm một bước duyệt hay đổi vai trò
 * được phép duyệt là thêm dòng vào {@code workflow_transitions}, không phải deploy lại. Đây là điều
 * kiện để Phase 1+ "chỉ khai báo cấu hình".
 */
@Entity
@Table(name = "workflow_definitions")
public class WorkflowDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    /** Khớp với {@code WorkflowAware.workflowEntityType()} của entity dùng quy trình này. */
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "initial_state", nullable = false, length = 50)
    private String initialState;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected WorkflowDefinition() {}

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getName() {
        return name;
    }

    public String getInitialState() {
        return initialState;
    }

    public boolean isActive() {
        return active;
    }
}
