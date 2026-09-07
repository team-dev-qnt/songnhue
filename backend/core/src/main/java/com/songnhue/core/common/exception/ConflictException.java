package com.songnhue.core.common.exception;

import java.io.Serial;

import com.songnhue.core.common.error.ErrorCode;

/**
 * Xung đột dữ liệu — HTTP 409.
 *
 * <p>Optimistic lock (@Version) hoặc trùng ràng buộc unique. Hai người sửa cùng lúc thì người sau nhận 409 chứ không ghi đè lặng lẽ.
 */
public class ConflictException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConflictException(Object... messageArgs) {
        super(ErrorCode.SYS_0005, messageArgs);
    }

    public ConflictException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }

    public ConflictException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode, cause, messageArgs);
    }
}
