package com.songnhue.core.common.security;

import java.util.Arrays;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.filter.AuthRequestAttributes;
import com.songnhue.core.domain.security.SecurityEventType;

/**
 * Tầng 2 — chặn thao tác theo permission (T5.9, conventions.md §4.2).
 *
 * <p>Là {@code HandlerInterceptor} chứ không phải filter vì nó cần biết <b>phương thức controller
 * nào</b> sắp được gọi để đọc annotation trên đó. Filter chạy trước khi Spring phân giải handler,
 * lúc ấy chưa có thông tin này.
 *
 * <p>Thứ tự kiểm cố định:
 *
 * <ol>
 *   <li>Endpoint công khai → cho qua ngay.
 *   <li>Chưa đăng nhập (hoặc token bị từ chối) → 401.
 *   <li>Đang bắt buộc đổi mật khẩu → 403 {@code AUTH-0007}, trừ vài endpoint cho phép.
 *   <li>Thiếu permission → 403 {@code AUTH-3001} + ghi sự kiện bảo mật.
 * </ol>
 *
 * <p>Endpoint không mang annotation nào cũng bị <b>từ chối</b> ở đây, không phải chỉ báo lỗi ở CI:
 * quét ở CI (T5.10) bắt sớm lúc viết mã, còn chốt chặn lúc chạy này lo cho trường hợp mã lọt qua
 * bằng đường khác. Hai lớp cùng nói một điều — mặc định là cấm.
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    /**
     * Cho phép khi tài khoản đang bị buộc đổi mật khẩu — vừa đủ để đổi xong rồi đi tiếp.
     *
     * <p>Thiếu {@code /auth/me} thì FE không dựng nổi màn hình đổi mật khẩu; thiếu {@code /logout}
     * thì người dùng bị kẹt, không thoát ra được.
     */
    private static final Set<String> ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED =
            Set.of("/api/v1/auth/me", "/api/v1/auth/change-password", "/api/v1/auth/logout");

    private final SecurityEventService securityEvents;

    public PermissionInterceptor(SecurityEventService securityEvents) {
        this.securityEvents = securityEvents;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            // Tài nguyên tĩnh, springdoc, actuator — không phải endpoint nghiệp vụ
            return true;
        }
        if (findAnnotation(method, PublicEndpoint.class) != null) {
            return true;
        }

        AuthenticatedUser user = AuthContext.current().orElse(null);
        if (user == null) {
            boolean rejected = Boolean.TRUE.equals(request.getAttribute(AuthRequestAttributes.TOKEN_REJECTED));
            // Hết hạn và chưa đăng nhập dùng chung mã AUTH-0002: FE xử lý giống nhau (gọi làm mới
            // token, hỏng thì về màn hình đăng nhập), còn phân biệt ra ngoài thì lộ thêm thông tin
            log.debug(
                    "Từ chối {} {} — {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    rejected ? "token không còn hiệu lực" : "chưa đăng nhập");
            throw new AuthenticationException(ErrorCode.AUTH_0002);
        }

        if (user.mustChangePassword() && !ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED.contains(request.getRequestURI())) {
            throw new PermissionDeniedException(ErrorCode.AUTH_0007);
        }

        if (findAnnotation(method, AuthenticatedEndpoint.class) != null) {
            return true;
        }

        RequirePermission required = findAnnotation(method, RequirePermission.class);
        if (required == null) {
            // Không khai báo gì = cấm. Đây là ý nghĩa của "deny by default".
            log.error(
                    "Endpoint {}#{} chưa khai báo quyền — bổ sung @RequirePermission / @AuthenticatedEndpoint "
                            + "/ @PublicEndpoint (conventions.md §4.2)",
                    method.getBeanType().getSimpleName(),
                    method.getMethod().getName());
            throw new PermissionDeniedException(ErrorCode.AUTH_3001);
        }

        boolean granted = required.mode() == RequirePermission.Mode.ALL
                ? user.hasAllPermissions(required.value())
                : user.hasAnyPermission(required.value());

        if (!granted) {
            denied(request, user, required);
        }
        return true;
    }

    private void denied(HttpServletRequest request, AuthenticatedUser user, RequirePermission required) {
        log.warn(
                "Từ chối quyền: {} gọi {} {} — cần {}",
                user.username(),
                request.getMethod(),
                request.getRequestURI(),
                Arrays.toString(required.value()));
        securityEvents.record(
                SecurityEventType.ACCESS_DENIED_PERMISSION,
                user.username(),
                user.userId(),
                ClientInfo.from(request),
                "{\"required\":\"" + String.join(",", required.value()) + "\",\"path\":\"" + request.getRequestURI()
                        + "\"}");
        throw new PermissionDeniedException(ErrorCode.AUTH_3001);
    }

    /** Ưu tiên annotation trên phương thức; không có thì lấy của lớp. */
    private static <A extends java.lang.annotation.Annotation> A findAnnotation(HandlerMethod method, Class<A> type) {
        A onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), type);
        return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), type);
    }
}
