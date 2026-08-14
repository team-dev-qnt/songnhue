package com.songnhue.core.common.filter;

/**
 * Thứ tự cố định của filter chain (conventions.md §2.4).
 *
 * <pre>
 * [1] CorrelationFilter   sinh/nhận traceId, đưa vào MDC
 * [2] RateLimitFilter     chặn theo IP + user; login có bucket riêng
 * [3] AuthFilter          verify access token, đối chiếu token_denylist   ← WS-5
 * [4] ScopeContextFilter  nạp role, permission, org_unit vào SecurityContext ← WS-5
 * [5] AuditContextFilter  gắn user + traceId cho audit interceptor
 * </pre>
 *
 * <p><b>Thứ tự này không phải tùy chọn.</b> Vài hệ quả nếu đảo:
 *
 * <ul>
 *   <li>Rate limit đứng <i>sau</i> auth → kẻ tấn công dò mật khẩu vẫn tiêu tốn tài nguyên xác thực
 *       của hệ thống trước khi bị chặn.
 *   <li>Correlation không đứng đầu → chính những lỗi sớm nhất lại không có traceId, đúng lúc cần
 *       tra nhất thì không tra được.
 *   <li>Audit đứng <i>trước</i> scope → bản ghi nhật ký thiếu đơn vị của người thao tác.
 * </ul>
 *
 * <p>Hằng số ở đây là hợp đồng giữa WS-4 và WS-5: filter của WS-5 chỉ việc khai báo đúng số thứ tự
 * đã chừa sẵn, không phải sửa lại filter đã có.
 */
public final class FilterOrder {

    public static final int CORRELATION = 10;
    /**
     * Ghi log truy cập. Nằm TRONG correlation (đã có traceId) nhưng NGOÀI rate limit, để cả
     * request bị chặn 429 cũng được ghi lại — nếu không thì đúng lúc bị tấn công lại không có log.
     */
    public static final int REQUEST_LOG = 15;

    public static final int RATE_LIMIT = 20;
    /** Chừa sẵn cho {@code AuthFilter} — WS-5 / T5.1. */
    public static final int AUTH = 30;
    /** Chừa sẵn cho {@code ScopeContextFilter} — WS-5 / T5.11. */
    public static final int SCOPE_CONTEXT = 40;

    public static final int AUDIT_CONTEXT = 50;

    private FilterOrder() {}
}
