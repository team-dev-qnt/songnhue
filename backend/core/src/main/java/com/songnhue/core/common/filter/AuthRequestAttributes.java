package com.songnhue.core.common.filter;

/**
 * Khoá thuộc tính request mà {@code AuthFilter} đặt và {@code ScopeContextFilter} /
 * {@code PermissionInterceptor} đọc.
 *
 * <p>Dùng thuộc tính của request thay vì thêm một {@code ThreadLocal} nữa: dữ liệu này chỉ sống
 * trong một request và tự biến mất cùng nó — không có nguy cơ sót lại sang người dùng kế tiếp như
 * {@code ThreadLocal} quên xoá.
 */
public final class AuthRequestAttributes {

    /** {@code AccessTokenClaims} khi token hợp lệ. */
    public static final String TOKEN_CLAIMS = "songnhue.tokenClaims";

    /**
     * {@code TRUE} khi có gửi token nhưng token hỏng/hết hạn/phiên đã thu hồi.
     *
     * <p>Phân biệt với "không gửi token" để interceptor chọn đúng câu trả lời: hết hạn thì FE nên
     * gọi làm mới token, còn chưa đăng nhập thì đưa về màn hình đăng nhập.
     */
    public static final String TOKEN_REJECTED = "songnhue.tokenRejected";

    private AuthRequestAttributes() {}
}
