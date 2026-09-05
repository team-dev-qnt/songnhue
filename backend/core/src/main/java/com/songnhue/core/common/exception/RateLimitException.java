package com.songnhue.core.common.exception;

import java.io.Serial;

import com.songnhue.core.common.error.ErrorCode;

/**
 * Vượt giới hạn tần suất — HTTP 429.
 *
 * <p>Do RateLimitFilter ném (conventions.md §4.5).
 */
public class RateLimitException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RateLimitException(Object... messageArgs) {
        super(ErrorCode.SYS_0002, messageArgs);
    }

    public RateLimitException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }

    public RateLimitException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode, cause, messageArgs);
    }
}
