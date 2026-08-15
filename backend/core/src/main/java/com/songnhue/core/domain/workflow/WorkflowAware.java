package com.songnhue.core.domain.workflow;

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
 * {@code WorkflowRuleTest#chi_workflow_engine_duoc_goi_applyState} (T10.2).
 *
 * <p><b>Vì sao interface này nằm ở {@code domain} chứ không ở {@code application} cạnh engine.</b>
 * Người <i>hiện thực</i> nó là entity — mà entity thì ở {@code domain}. Để interface ở
 * {@code application} nghĩa là mọi entity dùng quy trình duyệt đều phải import ngược lên tầng trên,
 * và luật ArchUnit "domain không phụ thuộc application/api/infra" sẽ đỏ ngay ở entity đầu tiên của
 * Phase 1. Engine gọi xuống domain là đúng chiều; domain gọi lên application thì không.
 */
public interface WorkflowAware {

    /** Khớp {@code workflow_definitions.entity_type}. Sai chuỗi này là không tìm ra quy trình nào. */
    String workflowEntityType();

    String currentState();

    /** Chỉ {@link WorkflowEngine} gọi. */
    void applyState(String newState);

    /** Id dùng cho nhật ký và thông báo. */
    Long entityId();

    /** Đơn vị của bản ghi — nguồn tìm người nhận thông báo theo G11. Có thể null. */
    default Long orgUnitId() {
        return null;
    }
}
