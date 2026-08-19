package com.songnhue.core.common.persistence;

/**
 * Entity có trạng thái do quy trình duyệt điều khiển — pattern P1.
 *
 * <p><b>Cố ý chỉ có {@code applyState} chứ không có {@code setState} công khai.</b> Tên hàm nói rõ
 * "áp dụng kết quả mà engine vừa quyết định", và mã nghiệp vụ đọc thấy ngay là không được gọi thẳng.
 * Quy tắc 4 của dự án: <b>đổi trạng thái chỉ qua Workflow engine</b> — gọi tay là bỏ qua kiểm tra
 * quyền, bỏ qua bắn thông báo, và bỏ qua ghi nhật ký.
 *
 * <p>Cách chặn triệt để hơn (đặt {@code applyState} ở phạm vi package của engine) không làm được vì
 * entity nằm ở module nghiệp vụ khác. Chốt chặn thật là luật ArchUnit
 * {@code SilentFailureRuleTest#chi_workflow_engine_duoc_goi_applyState} (T10.2).
 *
 * <p><b>Vì sao interface này nằm ở {@code core.common} (chuyển từ {@code core.domain.workflow} ngày
 * 19/8/2026 — WS-12/T12.3).</b> Người <i>hiện thực</i> nó là entity, và từ Phase 1 thì entity nằm ở
 * <b>module khác</b>: {@code content.domain.Article}, {@code operations.domain.MaintenanceLog}. Mà
 * luật ranh giới module chỉ cho phép import {@code <module>.spi.*} và {@code core.common.*} — nên để
 * ở {@code core.domain.workflow} thì entity nghiệp vụ đầu tiên implement nó đã làm CI đỏ.
 *
 * <p>Đặt ở {@code core.common.persistence} cạnh {@link BaseEntity} và {@link ScopedEntity} là đúng
 * loại: cả ba đều là <b>hợp đồng hạ tầng mà entity phải tuân theo</b>, không phải mô hình nghiệp vụ.
 * Chỗ này cũng tránh được cái bẫy của phương án "để ở {@code core.application} cạnh engine": khi đó
 * entity phải import ngược lên tầng trên và luật "domain không phụ thuộc application" sẽ đỏ.
 */
public interface WorkflowAware {

    /** Khớp {@code workflow_definitions.entity_type}. Sai chuỗi này là không tìm ra quy trình nào. */
    String workflowEntityType();

    String currentState();

    /** Chỉ {@code WorkflowEngine} gọi — xem chú thích ở đầu interface. */
    void applyState(String newState);

    /** Id dùng cho nhật ký và thông báo. */
    Long entityId();

    /** Đơn vị của bản ghi — nguồn tìm người nhận thông báo theo G11. Có thể null. */
    default Long orgUnitId() {
        return null;
    }
}
