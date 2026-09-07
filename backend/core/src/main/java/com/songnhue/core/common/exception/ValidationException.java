package com.songnhue.core.common.exception;

import java.io.Serial;

import com.songnhue.core.common.error.ErrorCode;

/**
 * Input sai định dạng hoặc thiếu trường bắt buộc — HTTP 400.
 *
 * <p>Dùng khi dữ liệu gửi lên không đúng dạng. Rule nghiệp vụ (VD vượt ngưỡng, sai trạng thái) thì dùng {@link BusinessRuleException} chứ không phải lớp này.
 */
public class ValidationException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationException(Object... messageArgs) {
        super(ErrorCode.SYS_0003, messageArgs);
    }

    public ValidationException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }

    public ValidationException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode, cause, messageArgs);
    }
}
