package com.songnhue.core.common.exception;

import java.io.Serial;

import com.songnhue.core.common.error.ErrorCode;

/**
 * Hệ thống bên ngoài lỗi — HTTP 502.
 *
 * <p>API thủy văn, SMTP, MinIO, hệ thống văn bản điều hành. Chi tiết kỹ thuật chỉ ghi log, không trả ra ngoài.
 */
public class UpstreamException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UpstreamException(Object... messageArgs) {
        super(ErrorCode.SYS_0006, messageArgs);
    }

    public UpstreamException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }

    public UpstreamException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode, cause, messageArgs);
    }
}
