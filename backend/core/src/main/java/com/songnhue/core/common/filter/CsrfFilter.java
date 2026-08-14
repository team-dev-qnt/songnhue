package com.songnhue.core.common.filter;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.security.CsrfTokens;

/**
 * Filter [3] — CSRF double-submit cho mọi request thay đổi dữ liệu (T5.5, §4.1).
 *
 * <p>Bỏ qua các phương thức chỉ đọc ({@code GET}, {@code HEAD}, {@code OPTIONS}): chúng không được
 * phép đổi dữ liệu, nên có bị giả mạo cũng không gây hậu quả — và bắt CSRF ở đó thì mọi liên kết
 * chia sẻ đều hỏng.
 *
 * <p>Bỏ qua đường dẫn đăng nhập: lúc đó người dùng chưa có cookie nào cả. Bản thân {@code /login}
 * cũng không phải mục tiêu CSRF — kẻ tấn công không có mật khẩu.
 *
 * <p>⚠ {@code /auth/refresh} thì <b>không</b> được bỏ qua, dù nó cũng thuộc nhóm auth: chính nó là
 * endpoint dùng cookie, tức là mục tiêu CSRF điển hình nhất trong cả hệ thống.
 */
@Component
@Order(FilterOrder.CSRF)
public class CsrfFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CsrfFilter.class);

    private static final String API_PREFIX = "/api/v1";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** Chưa có phiên nên chưa có cookie CSRF nào để đối chiếu. */
    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/2fa/verify", "/api/v1/auth/2fa/enroll", "/api/v1/auth/2fa/confirm");

    private final HandlerExceptionResolver exceptionResolver;

    public CsrfFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX)
                || SAFE_METHODS.contains(request.getMethod())
                || EXEMPT_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(CsrfTokens.HEADER);
        String cookie = CsrfTokens.readCookie(request, CsrfTokens.COOKIE);

        if (!StringUtils.hasText(header) || !StringUtils.hasText(cookie) || !header.equals(cookie)) {
            log.warn(
                    "Từ chối CSRF: {} {} — header {}, cookie {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    StringUtils.hasText(header) ? "có" : "thiếu",
                    StringUtils.hasText(cookie) ? "có" : "thiếu");
            // Đẩy qua HandlerExceptionResolver để lỗi vẫn có envelope + traceId như mọi lỗi khác
            exceptionResolver.resolveException(
                    request, response, null, new PermissionDeniedException(ErrorCode.AUTH_0005));
            return;
        }

        chain.doFilter(request, response);
    }
}
