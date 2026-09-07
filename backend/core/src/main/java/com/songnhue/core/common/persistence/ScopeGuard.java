package com.songnhue.core.common.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.security.SecurityEventType;

/**
 * Phân biệt "không có bản ghi đó" với "có, nhưng thuộc đơn vị khác" — mảnh cuối của tầng 3
 * ({@code conventions.md} §4.2, WS-5/T5.11).
 *
 * <p><b>Vì sao cần một lớp riêng.</b> {@link ScopeFilterAspect} bật bộ lọc phạm vi cho mọi truy vấn,
 * nên bản ghi của Xí nghiệp khác đơn giản là <i>không có trong kết quả</i>. Repository trả
 * {@code Optional.empty()}, service ném "không tìm thấy", và người dùng nhận 404. Nhìn thì có vẻ ổn —
 * dữ liệu đúng là không lọt ra. Nhưng có hai thứ mất đi:
 *
 * <ul>
 *   <li><b>Không ai biết chuyện đó vừa xảy ra.</b> Một người dò tuần tự {@code public_id} để tìm hồ sơ
 *       của đơn vị khác trông y hệt một người gõ nhầm đường dẫn. Đây chính là loại hành vi mà
 *       {@code security_events} sinh ra để ghi lại (M5.16), và 404 làm nó vô hình.
 *   <li><b>Mã lỗi {@code AUTH-3002} không bao giờ được dùng.</b> Nó có trong danh mục và trong tiêu
 *       chí nghiệm thu, nhưng không dòng mã nào ném ra — một mã lỗi chết.
 * </ul>
 *
 * <p><b>Vì sao lộ ra 403 mà không sợ rò rỉ thông tin.</b> Phân biệt 403/404 đúng là tiết lộ "id này
 * có tồn tại". Ở đây chấp nhận được vì người gọi <i>đã đăng nhập</i> và {@code public_id} là UUID
 * ngẫu nhiên — đoán trúng một cái là chuyện không xảy ra, nên thứ duy nhất họ biết thêm là điều họ
 * vốn đã biết. Đổi lại, mỗi lần chạm vào dữ liệu ngoài phạm vi đều để lại dấu vết. Với hệ nội bộ
 * 200 người dùng, đánh đổi đó nghiêng hẳn về phía có dấu vết.
 *
 * <p><b>Cách dùng ở tầng application:</b>
 *
 * <pre>
 * Construction c = scopeGuard.require(
 *         repository.findByPublicIdAndDeletedAtIsNull(publicId), Construction.class, publicId);
 * </pre>
 */
@Component
public class ScopeGuard {

    private static final Logger log = LoggerFactory.getLogger(ScopeGuard.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final SecurityEventService securityEvents;

    public ScopeGuard(SecurityEventService securityEvents) {
        this.securityEvents = securityEvents;
    }

    /**
     * @param found kết quả tra cứu đã đi qua bộ lọc phạm vi
     * @param entityType lớp entity, để tra lại khi bộ lọc bị tắt
     * @param publicId định danh công khai mà người gọi gửi lên
     * @return bản ghi khi nó nằm trong phạm vi của người đăng nhập
     * @throws PermissionDeniedException {@code AUTH-3002} — bản ghi có thật nhưng thuộc đơn vị khác
     * @throws ResourceNotFoundException {@code SYS-0004} — không có bản ghi nào mang định danh đó
     */
    public <T> T require(Optional<T> found, Class<T> entityType, UUID publicId) {
        if (found.isPresent()) {
            return found.get();
        }
        if (existsOutsideScope(entityType, publicId)) {
            AuthenticatedUser user = AuthContext.current().orElse(null);
            log.warn(
                    "Chặn truy cập ngoài phạm vi đơn vị: {} hỏi {} {}",
                    user == null ? "?" : user.username(),
                    entityType.getSimpleName(),
                    publicId);
            securityEvents.record(
                    SecurityEventType.ACCESS_DENIED_SCOPE,
                    user == null ? null : user.username(),
                    user == null ? null : user.userId(),
                    null,
                    "{\"entity\":\"" + entityType.getSimpleName() + "\",\"publicId\":\"" + publicId + "\"}");
            throw new PermissionDeniedException(ErrorCode.AUTH_3002);
        }
        throw new ResourceNotFoundException(ErrorCode.SYS_0004);
    }

    /**
     * Tra lại đúng một lần với bộ lọc tắt.
     *
     * <p>Bộ lọc được <b>bật lại nguyên trạng trong khối {@code finally}</b>. Bỏ sót bước đó là mọi
     * truy vấn còn lại của transaction chạy không có lọc phạm vi — nghĩa là lớp sinh ra để bảo vệ
     * phạm vi lại trở thành cái chọc thủng nó, và không có lỗi nào báo ra.
     */
    private boolean existsOutsideScope(Class<?> entityType, UUID publicId) {
        Session session = entityManager.unwrap(Session.class);
        if (session.getEnabledFilter(ScopedEntity.ORG_UNIT_FILTER) == null) {
            // Bộ lọc không bật (job nền, lệnh bootstrap) → đã nhìn thấy toàn bộ dữ liệu ở lần tra
            // đầu, nên "không thấy" đúng nghĩa là không tồn tại.
            return false;
        }
        // Lấy lại tham số từ AuthContext chứ không hỏi bộ lọc: org.hibernate.Filter không cho đọc
        // ngược giá trị đã đặt, và đây đúng là nguồn mà ScopeFilterAspect dùng để bật nó.
        String pathPrefix = AuthContext.current()
                .map(AuthenticatedUser::orgUnitPath)
                .orElseThrow(() -> new IllegalStateException(
                        "Bộ lọc phạm vi đang bật nhưng không có người đăng nhập — không bật lại được"));
        session.disableFilter(ScopedEntity.ORG_UNIT_FILTER);
        try {
            return session.createQuery(
                                    "SELECT count(e) FROM " + entityType.getSimpleName()
                                            + " e WHERE e.publicId = :publicId",
                                    Long.class)
                            .setParameter("publicId", publicId)
                            .getSingleResult()
                    > 0;
        } finally {
            session.enableFilter(ScopedEntity.ORG_UNIT_FILTER)
                    .setParameter(ScopedEntity.ORG_UNIT_PATHS_PARAM, pathPrefix);
        }
    }
}
