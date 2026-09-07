package com.songnhue.core.common.exception;

import java.io.Serial;

import com.songnhue.core.common.error.ErrorCode;

/**
 * Vi phạm quy tắc nghiệp vụ — HTTP 422.
 *
 * <p>Dữ liệu đúng định dạng nhưng trái quy tắc: vượt ngưỡng, sai trạng thái workflow, ngày hoàn thành trước ngày bắt đầu… Hầu như luôn được ném kèm mã riêng của module (OPS-2001, HYD-2003…).
 */
public class BusinessRuleException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessRuleException(Object... messageArgs) {
        super(ErrorCode.SYS_0008, messageArgs);
    }

    public BusinessRuleException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }

    public BusinessRuleException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode, cause, messageArgs);
    }
}
