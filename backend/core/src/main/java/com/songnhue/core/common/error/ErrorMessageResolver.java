package com.songnhue.core.common.error;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

/**
 * Đổi {@link ErrorCode} thành câu tiếng Việt cho người dùng đọc.
 *
 * <p>Luôn tra bằng locale {@code vi} chứ không theo {@code Accept-Language} của request: hệ thống
 * chốt chỉ tiếng Việt (BOQ đợt 1), và nếu để locale trôi theo trình duyệt thì máy đặt tiếng Anh sẽ
 * nhận về khoá thô kiểu {@code OPS-2001} — vô nghĩa với người dùng.
 *
 * <p>Thiếu message thì trả về chính mã lỗi kèm log cảnh báo, KHÔNG ném exception: lỗi trong lúc xử
 * lý lỗi mà lại ném tiếp thì che mất lỗi gốc. Việc chặn thiếu message là của {@code
 * ErrorCatalogTest} ở CI, không phải của runtime.
 */
@Component
public class ErrorMessageResolver {

    /** BOQ đợt 1 chốt: giao diện chỉ tiếng Việt. */
    private static final Locale VI = Locale.of("vi");

    private final MessageSource messageSource;

    public ErrorMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * @param code mã lỗi cần dịch
     * @param args tham số điền vào chỗ {@code {0}}, {@code {1}}… của message
     */
    public String resolve(ErrorCode code, Object... args) {
        try {
            return messageSource.getMessage(code.messageKey(), args, VI);
        } catch (NoSuchMessageException ignored) {
            return code.code();
        }
    }

    /** Có message cho mã này không — dùng cho test danh mục. */
    public boolean hasMessage(ErrorCode code) {
        try {
            messageSource.getMessage(code.messageKey(), new Object[0], VI);
            return true;
        } catch (NoSuchMessageException ignored) {
            return false;
        }
    }
}
