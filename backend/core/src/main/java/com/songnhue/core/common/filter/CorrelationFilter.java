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

import com.songnhue.core.common.web.RequestContext;

/**
 * Filter [1] — mọi request đều có {@code traceId}, ngay từ dòng log đầu tiên.
 *
 * <p>Nhận lại traceId từ header nếu phía gọi đã sinh (nginx, hoặc FE khi retry), để một thao tác của
 * người dùng nối được thành một chuỗi log liền mạch. Không có thì tự sinh.
 *
 * <p>traceId cũng được trả về trong header response, nhờ đó FE hiển thị được ở màn hình lỗi —
 * người dùng đọc mã đó cho người hỗ trợ là tra ra ngay (§2.1).
 */
@Component
@Order(FilterOrder.CORRELATION)
public class CorrelationFilter extends OncePerRequestFilter {

    /** Chặn header rác: traceId dài hoặc chứa ký tự lạ sẽ làm bẩn log và có thể dùng để chèn dòng log giả. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String incoming = request.getHeader(RequestContext.TRACE_ID_HEADER);
        String traceId = isAcceptable(incoming) ? incoming : RequestContext.newTraceId();

        RequestContext.setTraceId(traceId);
        response.setHeader(RequestContext.TRACE_ID_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            // BẮT BUỘC: thread nằm trong pool và sẽ phục vụ người khác. Quên dòng này là
            // traceId của request trước dính sang log của request sau.
            RequestContext.clear();
        }
    }

    private static boolean isAcceptable(String value) {
        if (!StringUtils.hasText(value) || value.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
