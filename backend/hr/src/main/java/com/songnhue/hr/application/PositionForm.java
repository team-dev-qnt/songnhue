package com.songnhue.hr.application;

/**
 * Dữ liệu tạo/sửa một chức vụ — CN-04.2.
 *
 * <p>Record ở tầng {@code application}: ⛔ không mang annotation validation, ⛔ không biết gì về
 * HTTP. Ranh giới ấy giữ cho service gọi được từ đường nhập tệp hàng loạt về sau mà ⛔ không phải
 * đi vòng qua một DTO của web.
 */
public record PositionForm(
        String code, String name, String positionGroup, String description, Integer sortOrder, Boolean active) {}
