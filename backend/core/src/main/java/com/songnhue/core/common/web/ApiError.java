package com.songnhue.core.common.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Phần lỗi của envelope (conventions.md §2.1).
 *
 * <p><b>Chỉ chứa thông tin người dùng đọc được.</b> Stacktrace, câu SQL, tên bảng, tên class, thông
 * điệp của thư viện — tất cả chỉ ghi vào log kèm traceId, không bao giờ ra tới đây (§2.2).
 *
 * @param code mã trong danh mục, VD {@code OPS-2001}
 * @param message câu tiếng Việt lấy từ {@code error-messages.properties}
 * @param details lỗi theo từng trường, để FE tô đỏ đúng ô nhập; rỗng thì không xuất hiện trong JSON
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<ErrorDetail> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }

    /**
     * @param field tên trường theo DTO
     * @param rule mã quy tắc bị vi phạm, VD {@code AFTER_OR_EQUAL_START_DATE} — FE map sang câu chữ
     *     riêng nếu muốn, không phụ thuộc vào câu tiếng Việt của BE
     * @param rejectedValue giá trị bị từ chối
     */
    public record ErrorDetail(String field, String rule, Object rejectedValue) {}
}
