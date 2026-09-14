package com.songnhue.core.common.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.songnhue.core.common.exception.RateLimitException;
import com.songnhue.core.common.ratelimit.RateLimitPolicy;
import com.songnhue.core.common.ratelimit.RateLimitStore;

/**
 * Phần chung của hai tầng hạn mức — T61.17.
 *
 * <p>Hai tầng khác nhau đúng hai thứ: <b>xô nào</b> tầng ấy phụ trách, và <b>khoá</b> của xô tính
 * từ đâu. Phân loại đường dẫn vẫn là MỘT bản duy nhất ở {@link RateLimitPolicy#choDuongDan(String)},
 * nên một đường dẫn rơi vào đúng một xô và đúng một tầng — ⛔ bao giờ bị đếm hai lần.
 *
 * <p>Exception được đẩy qua {@link HandlerExceptionResolver} chứ không tự ghi response: nhờ vậy lỗi
 * 429 vẫn đi qua {@code GlobalExceptionHandler} và có đúng envelope + traceId như mọi lỗi khác.
 */
abstract class HanMucFilterCoSo extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HanMucFilterCoSo.class);

    private static final String API_PREFIX = "/api/v1";

    private final RateLimitStore store;
    private final HandlerExceptionResolver exceptionResolver;

    HanMucFilterCoSo(RateLimitStore store, HandlerExceptionResolver exceptionResolver) {
        this.store = store;
        this.exceptionResolver = exceptionResolver;
    }

    /** Tầng này có đếm xô {@code policy} không. */
    abstract boolean phuTrach(RateLimitPolicy policy);

    /** Khoá của máy khách trong xô — đã qua kiểm, ⛔ bao giờ là giá trị kẻ gọi tự khai. */
    abstract String danhTinh(HttpServletRequest request);

    /** Trần của xô cho lượt gọi này — mặc định hằng số của chính sách (T61.27 ghi đè cho EXPORT). */
    int hanMuc(RateLimitPolicy policy) {
        return policy.limit();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Actuator do mạng nội bộ gọi (Prometheus), không tính hạn mức
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        RateLimitPolicy policy = RateLimitPolicy.choDuongDan(request.getRequestURI());
        if (!phuTrach(policy)) {
            chain.doFilter(request, response);
            return;
        }
        String identity = danhTinh(request);
        int tran = hanMuc(policy);
        RateLimitStore.Decision decision = store.hit(policy.key(identity), tran, policy.window());

        response.setHeader("X-RateLimit-Limit", String.valueOf(tran));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            long retryAfterSeconds = Math.max(1, decision.retryAfter().toSeconds());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            // Ghi WARN: dồn dập bất thường là tín hiệu cần nhìn, không phải chuyện thường ngày
            log.warn(
                    "Chặn theo hạn mức {} — khách {} · {} {}",
                    policy,
                    identity,
                    request.getMethod(),
                    request.getRequestURI());
            exceptionResolver.resolveException(request, response, null, new RateLimitException());
            return;
        }

        chain.doFilter(request, response);
    }
}
