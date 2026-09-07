package com.songnhue.core.common.web;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * Giữ {@code traceId} của request hiện tại trong {@link MDC} của SLF4J.
 *
 * <p>Dùng MDC chứ không phải một {@code ThreadLocal} riêng: mọi dòng log trong cùng request tự động
 * mang traceId mà không phải truyền tay qua từng lớp, và bộ ghi log JSON (T4.9) lấy thẳng từ đây.
 *
 * <p>{@link CorrelationFilter} đặt giá trị vào đầu request và <b>bắt buộc</b> xoá ở {@code finally}
 * — thread trong pool được tái sử dụng, quên xoá là traceId của người này dính sang log của người
 * khác.
 */
public final class RequestContext {

    /** Tên khoá trong MDC; cấu hình logback tham chiếu đúng chuỗi này. */
    public static final String TRACE_ID = "traceId";

    /** Header nhận/trả traceId. Nginx và FE dùng chung tên này để nối chuỗi log. */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private RequestContext() {}

    /** traceId của request hiện tại, hoặc {@code null} nếu đang ở ngoài phạm vi request. */
    public static String traceId() {
        return MDC.get(TRACE_ID);
    }

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    public static void clear() {
        MDC.remove(TRACE_ID);
    }

    /**
     * Sinh traceId mới: 16 ký tự hex đầu của UUID.
     *
     * <p>Đủ ngắn để người dùng đọc qua điện thoại khi báo lỗi, đủ dài để không đụng nhau trong
     * khoảng thời gian log còn được giữ.
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
