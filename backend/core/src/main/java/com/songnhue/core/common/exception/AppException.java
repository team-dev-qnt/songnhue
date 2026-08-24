package com.songnhue.core.common.exception;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.songnhue.core.common.error.ErrorCode;

/**
 * Gốc của mọi lỗi nghiệp vụ trong hệ thống.
 *
 * <p>Module nghiệp vụ chỉ được ném các lớp con của lớp này, <b>không ném {@code RuntimeException}
 * trần</b> (conventions.md §2.2). Lý do: {@code GlobalExceptionHandler} dựa vào {@link ErrorCode}
 * để chọn HTTP status và câu message tiếng Việt; exception không mang mã sẽ rơi vào nhánh "lỗi
 * không lường trước" và trả về {@code SYS-0001} — người dùng không biết mình sai ở đâu.
 *
 * <p>{@code details} dành cho lỗi theo từng trường, để FE tô đỏ đúng ô nhập.
 */
public abstract class AppException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /** Tham số điền vào chỗ {0}, {1}… của message. */
    private final transient Object[] messageArgs;

    private final transient List<FieldViolation> details = new ArrayList<>();

    protected AppException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode.code());
        this.errorCode = errorCode;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }

    protected AppException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(errorCode.code(), cause);
        this.errorCode = errorCode;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object[] messageArgs() {
        return messageArgs.clone();
    }

    public List<FieldViolation> details() {
        return Collections.unmodifiableList(details);
    }

    /** Gắn thêm lỗi ở mức trường. Trả về chính nó để gọi nối chuỗi. */
    public AppException withDetail(String field, String rule, Object rejectedValue) {
        details.add(new FieldViolation(field, rule, rejectedValue));
        return this;
    }

    /**
     * Lỗi ở mức một trường cụ thể.
     *
     * @param field tên trường theo DTO (FE dùng để tô đỏ đúng ô)
     * @param rule mã quy tắc vi phạm, VD {@code AFTER_OR_EQUAL_START_DATE}
     * @param rejectedValue giá trị bị từ chối — <b>cấm đưa dữ liệu nhạy cảm vào đây</b>, nó đi
     *     thẳng ra response
     */
    public record FieldViolation(String field, String rule, Object rejectedValue) {}
}
