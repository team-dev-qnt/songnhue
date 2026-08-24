package com.songnhue.core.common.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;

/**
 * Filter [6] — nạp {@link AuditContext} cho request hiện tại (conventions.md §2.4).
 *
 * <p>Đứng SAU {@code AuthFilter} và {@code ScopeContextFilter}, nên lúc này đã biết người thao tác
 * là ai. Đảo thứ tự là {@code audit_logs.actor_user_id} và {@code created_by} để trống toàn bộ —
 * nhật ký vẫn ghi đều đặn, chỉ là không ghi được ai làm.
 *
 * <p>Vẫn chạy cả khi chưa đăng nhập: đăng nhập thất bại và truy cập bị từ chối cũng phải vào được
 * nhật ký, lúc đó {@code userId} rỗng nhưng IP và traceId thì có.
 */
@Component
@Order(FilterOrder.AUDIT_CONTEXT)
public class AuditContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        AuditContext.set(new AuditContext.Data(currentUserId(), currentUsername(), clientIp(request)));
        try {
            chain.doFilter(request, response);
        } finally {
            AuditContext.clear();
        }
    }

    private static Long currentUserId() {
        return AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
    }

    private static String currentUsername() {
        return AuthContext.current().map(AuthenticatedUser::username).orElse(null);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
