package com.songnhue.core.common.exception;

import java.io.Serial;

import com.songnhue.core.common.error.ErrorCode;

/**
 * Không tìm thấy bản ghi — HTTP 404.
 *
 * <p>Lưu ý phân biệt với {@link PermissionDeniedException}: bản ghi tồn tại nhưng ngoài phạm vi đơn vị thì trả AUTH-3002, không trả 404.
 */
public class ResourceNotFoundException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(Object... messageArgs) {
        super(ErrorCode.SYS_0004, messageArgs);
    }

    public ResourceNotFoundException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }

    public ResourceNotFoundException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode, cause, messageArgs);
    }
}
