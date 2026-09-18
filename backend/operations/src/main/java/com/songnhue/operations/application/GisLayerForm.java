package com.songnhue.operations.application;

/**
 * Dữ liệu nhập của một lớp bản đồ — CN-02.4 / M2.9.
 *
 * <p>⚠ Bốn trường trình bày ({@code color}, {@code opacity}, {@code sortOrder}, {@code active})
 * nhận {@code null} = <b>giữ nguyên</b>, ⛔ không phải *"đặt về mặc định"*. Biểu mẫu sửa ⛔ không
 * phải lúc nào cũng gửi đủ, và một trường thiếu bị đọc thành *"đặt về mặc định"* là mỗi lượt lưu
 * một lượt xoá dữ liệu — đúng khuyết tật §11.19 đã trả giá ở điểm đo.
 */
public record GisLayerForm(
        String name, String description, String color, Short opacity, Integer sortOrder, Boolean active) {}
