package com.songnhue.core.common.filter;

/**
 * Thứ tự cố định của filter chain (conventions.md §2.4).
 *
 * <pre>
 * [1] CorrelationFilter   sinh/nhận traceId, đưa vào MDC
 * [2] RateLimitFilter     chặn theo IP + user; login có bucket riêng
 * [3] CsrfFilter          double-submit token cho request thay đổi dữ liệu     ← WS-5
 * [4] AuthFilter          verify access token, đối chiếu sessions + denylist   ← WS-5
 * [5] ScopeContextFilter  nạp role, permission, org_unit vào AuthContext       ← WS-5
 * [6] AuditContextFilter  gắn user + traceId cho audit interceptor
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

    /**
     * Kiểm CSRF trước cả xác thực (WS-5 / T5.5).
     *
     * <p>Phép kiểm chỉ là so hai chuỗi — rẻ hơn nhiều so với đọc DB và kiểm chữ ký RSA. Request giả
     * mạo bị loại ở đây thì không tiêu tốn gì thêm của máy chủ.
     */
    public static final int CSRF = 25;

    /** Xác thực access token — WS-5 / T5.1. */
    public static final int AUTH = 30;

    /** Nạp quyền và phạm vi đơn vị — WS-5 / T5.9, T5.11. */
    public static final int SCOPE_CONTEXT = 40;

    /**
     * Chặn ghi khi đang bảo trì — WS-7 / T7.6.
     *
     * <p>Phải đứng <b>sau</b> {@link #SCOPE_CONTEXT}: quyết định "cho qua hay không" phụ thuộc người
     * gọi có phải Super Admin hay không, mà vai trò chỉ có trong {@code AuthContext} sau khi filter
     * kia chạy xong. Đặt trước nó thì mọi request đều bị chặn, kể cả của chính người đang khôi phục
     * dữ liệu — tức là bật bảo trì xong thì không ai tắt được nữa.
     */
    public static final int MAINTENANCE = 45;

    public static final int AUDIT_CONTEXT = 50;

    private FilterOrder() {}
}
