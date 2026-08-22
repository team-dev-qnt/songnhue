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
     * Trạng thái-giả đứng ở vế {@code from_state} để khai một <b>đường vào đời</b> của bản ghi.
     *
     * <p>Một dòng {@code workflow_transitions} có {@code from_state = '__NEW__'} không phải bước
     * chuyển, mà là "được phép tạo mới thẳng ở trạng thái này". CSDL có ràng buộc cấm nó xuất hiện ở
     * vế {@code to_state} — vào được trạng thái này nghĩa là có bản ghi thật mang nó, và bản ghi ấy
     * sẽ nhận được cả những bước vốn chỉ dành cho lúc tạo mới.
     */
    String CREATION_STATE = "__NEW__";

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

    /** Trạng thái khởi tạo <b>mặc định</b> — dùng khi nơi gọi không cho người dùng chọn. */
    String initialState(String entityType);

    /**
     * Mọi đường vào đời hợp lệ, đã lọc theo quyền của người đang đăng nhập.
     *
     * <p>Giao diện dựng ô chọn "Trạng thái ban đầu" từ danh sách này, <b>không tự liệt kê</b> — cùng
     * lý do với {@link #allowedActions}. Quy trình chỉ có một đường vào thì trả đúng một phần tử.
     *
     * @return {@code toState} của mỗi phần tử là trạng thái bản ghi sẽ mang khi được tạo
     */
    List<AllowedAction> initialActions(String entityType);

    /**
     * Kiểm một trạng thái khởi tạo do người dùng chọn, trả về chính nó nếu hợp lệ.
     *
     * <p>Đây là chốt chặn thật — {@link #initialActions} chỉ phục vụ hiển thị, mà giao diện thì
     * không phải nguồn tin cậy. Nơi gọi truyền thẳng kết quả vào hàm dựng entity.
     *
     * @param requestedState {@code null} = dùng {@link #initialState(String)}
     * @throws com.songnhue.core.common.exception.BusinessRuleException {@code SYS-0008} khi trạng
     *     thái không phải một đường vào hợp lệ của quy trình
     * @throws com.songnhue.core.common.exception.PermissionDeniedException {@code AUTH-3001} khi
     *     thiếu quyền tạo mới thẳng ở trạng thái đó
     */
    String resolveInitialState(String entityType, String requestedState);
}
