package com.songnhue.core.common.exception;

import java.io.Serial;

import com.songnhue.core.common.error.ErrorCode;

/**
 * Không đủ quyền — HTTP 403.
 *
 * <p>Hai tình huống: thiếu permission (AUTH-3001) và dữ liệu ngoài phạm vi đơn vị (AUTH-3002 — scope filter tầng 3).
 */
public class PermissionDeniedException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PermissionDeniedException(Object... messageArgs) {
        super(ErrorCode.AUTH_3001, messageArgs);
    }

    public PermissionDeniedException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }

    public PermissionDeniedException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode, cause, messageArgs);
    }
}
