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
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ghi một dòng log cho mỗi request: method, path, status, thời gian xử lý (conventions.md §2.4).
 *
 * <p><b>CỐ Ý KHÔNG ghi request/response body.</b> Body chứa mật khẩu lúc đăng nhập, mã số hệ thống
 * văn bản điều hành, CCCD và lương ở hồ sơ nhân sự (§4.7, quy tắc 10 CLAUDE.md). Ghi body ra log là
 * đưa dữ liệu nhạy cảm ra khỏi vòng mã hoá mà không ai để ý — file log không được mã hoá và nằm
 * ngoài phạm vi kiểm soát của tầng ứng dụng.
 *
 * <p>Query string cũng bị cắt vì tham số lọc có thể mang dữ liệu cá nhân (tìm theo CCCD, theo số
 * điện thoại). Cần xem chi tiết một request thì tra theo {@code traceId}.
 *
 * <p>Được cài là filter chứ không phải {@code HandlerInterceptor}: interceptor không thấy các
 * request bị chặn từ tầng filter (VD 429 của rate limit) và không đo được trọn thời gian.
 */
@Component
@Order(FilterOrder.REQUEST_LOG)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /** Request chậm hơn mức này thì nâng lên WARN — NFR-03 yêu cầu trang tải < 3 giây. */
    private static final long SLOW_MILLIS = 3_000;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Prometheus quét mỗi 15 giây; ghi log những lượt này chỉ làm loãng log thật
        return path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();

            if (status >= 500 || millis >= SLOW_MILLIS) {
                log.warn("{} {} → {} ({} ms)", request.getMethod(), request.getRequestURI(), status, millis);
            } else {
                log.info("{} {} → {} ({} ms)", request.getMethod(), request.getRequestURI(), status, millis);
            }
        }
    }
}
