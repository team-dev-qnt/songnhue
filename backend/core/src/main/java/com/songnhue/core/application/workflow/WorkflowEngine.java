package com.songnhue.core.application.workflow;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.notification.NotificationRequest;
import com.songnhue.core.application.notification.NotificationService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.persistence.WorkflowAware;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationSeverity;
import com.songnhue.core.domain.workflow.WorkflowDefinition;
import com.songnhue.core.domain.workflow.WorkflowTransition;
import com.songnhue.core.infra.workflow.WorkflowDefinitionRepository;
import com.songnhue.core.infra.workflow.WorkflowTransitionRepository;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.WorkflowPort;

/**
 * Máy trạng thái dùng chung — pattern P1, <b>nơi duy nhất được đổi trạng thái entity</b>
 * (quy tắc 4 của dự án, conventions.md §4.3).
 *
 * <p><b>Vì sao phải tập trung một chỗ.</b> Khi mỗi service tự đặt trạng thái thì ba thứ sẽ lệch
 * nhau theo thời gian: bước nào được phép đi tiếp, ai được bấm, và ai được báo. Chúng lệch <i>âm
 * thầm</i> — bài viết vẫn đăng được, chỉ là người duyệt không nhận thông báo, hoặc một vai trò
 * không đáng có lại duyệt được. Gom vào đây thì cả ba luôn đi cùng nhau.
 *
 * <p><b>Ba việc trong một transaction:</b> kiểm hợp lệ + đổi trạng thái + bắn thông báo. Nhật ký
 * kiểm toán tự có nhờ {@code AuditEventListener} bắt lệnh UPDATE trên entity — không cần gọi thêm.
 *
 * <p>{@link #allowedActions} trả về danh sách nút cho giao diện. FE <b>không tự suy</b> nút nào hiện
 * (conventions.md §3): luật nằm trong DB, mà FE thì không đọc DB — tự suy là chắc chắn có ngày lệch.
 */
@Service
public class WorkflowEngine implements WorkflowPort {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowTransitionRepository transitions;
    private final NotificationService notifications;

    public WorkflowEngine(
            WorkflowDefinitionRepository definitions,
            WorkflowTransitionRepository transitions,
            NotificationService notifications) {
        this.definitions = definitions;
        this.transitions = transitions;
        this.notifications = notifications;
    }

    /**
     * Thực hiện một hành động trên entity.
     *
     * <p>Thứ tự kiểm tra có chủ đích: <b>hợp lệ về quy trình trước, quyền sau</b>. Ngược lại thì
     * người dùng bấm một nút không tồn tại ở trạng thái hiện tại sẽ nhận "không có quyền" — thông
     * báo sai hướng, và tệ hơn là nó tiết lộ rằng hành động đó có tồn tại ở đâu đó.
     *
     * @throws BusinessRuleException {@code SYS-0008} khi hành động không hợp lệ ở trạng thái hiện tại
     * @throws PermissionDeniedException {@code AUTH-3001} khi thiếu quyền của bước chuyển
     */
    @Override
    @Transactional
    public <T extends WorkflowAware> T execute(T entity, String action, String title) {
        WorkflowDefinition definition = definitionOf(entity);
        WorkflowTransition transition = transitions
                .findByDefinitionIdAndFromStateAndAction(definition.getId(), entity.currentState(), action)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.SYS_0008, action, entity.currentState()));

        requirePermission(transition);

        String previous = entity.currentState();
        entity.applyState(transition.getToState());

        log.info(
                "{} #{}: {} --{}--> {}",
                definition.getEntityType(),
                entity.entityId(),
                previous,
                action,
                transition.getToState());

        if (transition.getNotifyEvent() != null && !transition.getNotifyEvent().isBlank()) {
            notifyAfterTransition(entity, definition, transition, title);
        }
        return entity;
    }

    /**
     * Các hành động người đang đăng nhập được phép làm ở trạng thái hiện tại.
     *
     * <p>Đã lọc theo quyền, nên FE chỉ việc render — không phải biết luật, và cũng không thể render
     * nhầm một nút mà máy chủ sẽ từ chối.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AllowedAction> allowedActions(WorkflowAware entity) {
        WorkflowDefinition definition = definitionOf(entity);
        return transitions
                .findByDefinitionIdAndFromStateOrderBySortOrderAsc(definition.getId(), entity.currentState())
                .stream()
                .filter(WorkflowEngine::hasPermissionFor)
                .map(t -> new AllowedAction(t.getAction(), t.getLabel(), t.getToState()))
                .toList();
    }

    /** Trạng thái khởi tạo của quy trình — dùng lúc tạo mới bản ghi. */
    @Override
    @Transactional(readOnly = true)
    public String initialState(String entityType) {
        return definitions
                .findByEntityTypeAndActiveTrue(entityType)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004))
                .getInitialState();
    }

    // -------------------------------------------------------------------------

    private WorkflowDefinition definitionOf(WorkflowAware entity) {
        return definitions
                .findByEntityTypeAndActiveTrue(entity.workflowEntityType())
                // Entity khai một loại quy trình không tồn tại là lỗi cấu hình, không phải lỗi người
                // dùng — nhưng vẫn phải chặn, vì bỏ qua nghĩa là entity đó không có quy trình nào và
                // trạng thái muốn đổi kiểu gì cũng được.
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private static void requirePermission(WorkflowTransition transition) {
        if (!hasPermissionFor(transition)) {
            throw new PermissionDeniedException(ErrorCode.AUTH_3001);
        }
    }

    private static boolean hasPermissionFor(WorkflowTransition transition) {
        String required = transition.getRequiredPermission();
        if (required == null || required.isBlank()) {
            return true;
        }
        return AuthContext.current()
                .map(AuthenticatedUser::permissions)
                .map(permissions -> permissions.contains(required))
                .orElse(false);
    }

    /**
     * Bắn thông báo sau khi chuyển trạng thái.
     *
     * <p>Nằm trong cùng transaction: {@code NotificationService} chỉ ghi DB rồi trả về, việc gửi
     * email do job nền làm. Nhờ vậy giao dịch nghiệp vụ không phải chờ SMTP, mà thông báo vẫn cùng
     * sống chết với thay đổi trạng thái — duyệt bài rollback thì thông báo "đã duyệt" cũng biến mất.
     */
    private void notifyAfterTransition(
            WorkflowAware entity, WorkflowDefinition definition, WorkflowTransition transition, String title) {

        notifications.notify(new NotificationRequest(
                transition.getNotifyEvent(),
                title != null ? title : definition.getName() + " — " + transition.getLabel(),
                "%s #%d chuyển sang trạng thái %s"
                        .formatted(definition.getName(), entity.entityId(), transition.getToState()),
                NotificationSeverity.INFO,
                null,
                definition.getEntityType(),
                entity.entityId(),
                entity.orgUnitId() == null ? List.of() : List.of(entity.orgUnitId()),
                List.of(),
                List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)));
    }
}
