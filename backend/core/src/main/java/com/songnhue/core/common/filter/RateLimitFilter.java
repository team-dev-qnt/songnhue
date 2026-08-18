package com.songnhue.core.common.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.songnhue.core.common.exception.RateLimitException;
import com.songnhue.core.common.ratelimit.RateLimitPolicy;
import com.songnhue.core.common.ratelimit.RateLimitStore;

/**
 * Filter [2] — chặn tần suất theo IP, <b>trước khi</b> tới bước xác thực.
 *
 * <p>Đặt trước AuthFilter là có chủ đích: một đợt dò mật khẩu phải bị chặn ngay ở cửa, không được
 * tiêu tốn tài nguyên băm BCrypt (cost ≥ 12 — cố ý chậm) của máy chủ.
 *
 * <p>Đây là lớp thứ hai; nginx đã chặn thô theo IP ở lớp ngoài (§4.5). Lớp trong này biết đường dẫn
 * nên phân biệt được đăng nhập / API thường / kết xuất.
 *
 * <p>Exception được đẩy qua {@link HandlerExceptionResolver} chứ không tự ghi response: nhờ vậy lỗi
 * 429 vẫn đi qua {@code GlobalExceptionHandler} và có đúng envelope + traceId như mọi lỗi khác
 * (DoD #9).
 */
@Component
@Order(FilterOrder.RATE_LIMIT)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String EXPORT_MARKER = "/export";
    private static final String API_PREFIX = "/api/v1";

    private final RateLimitStore store;
    private final HandlerExceptionResolver exceptionResolver;

    public RateLimitFilter(
            RateLimitStore store,
            @org.springframework.beans.factory.annotation.Qualifier("handlerExceptionResolver")
                    HandlerExceptionResolver exceptionResolver) {
        this.store = store;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Actuator do mạng nội bộ gọi (Prometheus trên VM-3), không tính hạn mức
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        RateLimitPolicy policy = policyFor(request);
        String identity = clientIp(request);
        RateLimitStore.Decision decision = store.hit(policy.key(identity), policy.limit(), policy.window());

        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            long retryAfterSeconds = Math.max(1, decision.retryAfter().toSeconds());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            // Ghi WARN: dồn dập bất thường là tín hiệu cần nhìn, không phải chuyện thường ngày
            log.warn(
                    "Chặn theo hạn mức {} — IP {} · {} {}",
                    policy,
                    identity,
                    request.getMethod(),
                    request.getRequestURI());
            exceptionResolver.resolveException(request, response, null, new RateLimitException());
            return;
        }

        chain.doFilter(request, response);
    }

    private static RateLimitPolicy policyFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith(LOGIN_PATH)) {
            return RateLimitPolicy.LOGIN;
        }
        if (path.contains(EXPORT_MARKER)) {
            return RateLimitPolicy.EXPORT;
        }
        return RateLimitPolicy.API;
    }

    /**
     * IP thật của client.
     *
     * <p>Chỉ lấy phần tử ĐẦU của {@code X-Forwarded-For} và chỉ nên tin khi đứng sau nginx của mình
     * (production luôn vậy — §4.5). Client tự đặt header này được, nên nginx phải ghi đè chứ không
     * nối thêm; nếu không thì kẻ tấn công đổi header là thoát rate limit.
     */
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
