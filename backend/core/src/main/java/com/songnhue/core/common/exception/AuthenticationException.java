package com.songnhue.core.common.exception;

import java.io.Serial;

import com.songnhue.core.common.error.ErrorCode;

/**
 * Chưa xác thực hoặc phiên hết hạn — HTTP 401.
 *
 * <p>Message cố ý mơ hồ, không tiết lộ tài khoản có tồn tại hay không (conventions.md §4.1).
 */
public class AuthenticationException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthenticationException(Object... messageArgs) {
        super(ErrorCode.AUTH_0001, messageArgs);
    }

    public AuthenticationException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }

    public AuthenticationException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode, cause, messageArgs);
    }
}
