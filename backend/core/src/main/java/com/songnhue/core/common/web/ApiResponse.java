package com.songnhue.core.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope thống nhất cho 100% endpoint (conventions.md §2.1).
 *
 * <pre>
 * { "success": true,  "data": {...}, "meta": {...}, "traceId": "a1b2c3" }
 * { "success": false, "error": { "code": "OPS-2001", "message": "...", "details": [...] },
 *   "traceId": "a1b2c3" }
 * </pre>
 *
 * <p>Controller <b>chỉ return DTO</b>; {@link ResponseEnvelopeAdvice} tự bọc lại. Cấm module tự chế
 * envelope riêng — FE có đúng một chỗ để bóc dữ liệu.
 *
 * <p>{@code traceId} luôn có mặt, kể cả khi thành công: người dùng báo lỗi chỉ cần đọc traceId là
 * tra được toàn bộ log của request đó.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, PageMeta meta, ApiError error, String traceId) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, null, null, traceId);
    }

    public static <T> ApiResponse<T> ok(T data, PageMeta meta, String traceId) {
        return new ApiResponse<>(true, data, meta, null, traceId);
    }

    /** Bọc một trang dữ liệu: phần tử đi vào {@code data}, thông tin phân trang vào {@code meta}. */
    public static <T> ApiResponse<List<T>> ofPage(Page<T> page, String traceId) {
        return new ApiResponse<>(true, page.getContent(), PageMeta.of(page), null, traceId);
    }

    public static <T> ApiResponse<T> fail(ApiError error, String traceId) {
        return new ApiResponse<>(false, null, null, error, traceId);
    }

    /** Chỉ xuất hiện ở response phân trang (§2.1). */
    public record PageMeta(int page, int size, long totalElements, int totalPages) {

        public static PageMeta of(Page<?> page) {
            // page.getNumber() đếm từ 0, API đếm từ 1 — quy ước ở §1.3
            return new PageMeta(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
