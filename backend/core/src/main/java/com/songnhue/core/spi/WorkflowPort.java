package com.songnhue.core.spi;

import java.util.List;

import com.songnhue.core.common.persistence.WorkflowAware;

/**
 * Máy trạng thái dùng chung — pattern P1, <b>nơi duy nhất được đổi trạng thái entity</b> (quy tắc 4
 * của dự án, conventions.md §4.3).
 *
 * <p>Module nghiệp vụ khai trạng thái và bước chuyển bằng <b>dữ liệu</b> ({@code
 * workflow_definitions} + {@code workflow_transitions}, seed bằng migration), rồi gọi
 * {@link #execute} — không viết {@code switch/case} trong service, và tuyệt đối không
 * {@code setStatus} thẳng.
 *
 * <p>Đi đường tắt là bỏ qua <i>cùng lúc</i> ba thứ, cả ba đều im lặng: kiểm quyền của bước chuyển,
 * bắn thông báo cho người liên quan, và ghi nhật ký kiểm toán.
 */
public interface WorkflowPort {

    /**
     * Thực hiện một hành động trên bản ghi.
     *
     * @param title tiêu đề thông báo; {@code null} thì engine tự dựng từ tên quy trình và nhãn bước
     * @throws com.songnhue.core.common.exception.BusinessRuleException {@code SYS-0008} khi hành
     *     động không hợp lệ ở trạng thái hiện tại
     * @throws com.songnhue.core.common.exception.PermissionDeniedException {@code AUTH-3001} khi
     *     thiếu quyền của bước chuyển
     */
    <T extends WorkflowAware> T execute(T entity, String action, String title);

    /** Các nút giao diện được phép hiện — đã lọc theo quyền của người đang đăng nhập. */
    List<AllowedAction> allowedActions(WorkflowAware entity);

    /** Trạng thái khởi tạo của quy trình — dùng lúc tạo mới bản ghi. */
    String initialState(String entityType);
}
