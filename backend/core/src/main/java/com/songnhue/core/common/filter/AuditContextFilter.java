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

/**
 * Filter [5] — nạp {@link AuditContext} cho request hiện tại (conventions.md §2.4).
 *
 * <p>Đứng SAU AuthFilter và ScopeContextFilter để lúc này đã biết người thao tác là ai.
 *
 * <p>⬜ Hiện mới lấy được IP. Phần {@code userId}/{@code username} sẽ do <b>WS-5</b> điền vào khi có
 * SecurityContext — chỗ cắm đã chừa sẵn ở {@link #currentUserId()} và {@link #currentUsername()},
 * không phải sửa lại filter này.
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

    /** ⬜ WS-5 / T5.9: đọc từ SecurityContext. */
    private Long currentUserId() {
        return null;
    }

    /** ⬜ WS-5 / T5.9: đọc từ SecurityContext. */
    private String currentUsername() {
        return null;
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
