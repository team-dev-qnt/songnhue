package com.songnhue.core.spi;

/**
 * Một dòng cấu hình, ở dạng module nghiệp vụ dùng được.
 *
 * <p>Không phải bọc mỏng quanh entity {@code Setting}: trả entity ra khỏi {@code core.application} là
 * buộc nơi gọi phải import {@code core.domain}, tức là SPI chỉ dời chỗ vi phạm chứ không xoá nó
 * ({@code architecture-review.md} §9.14).
 *
 * @param effectiveValue giá trị đang có hiệu lực — {@code value} nếu đã đặt, ngược lại
 *     {@code defaultValue}. Giao diện cần cả hai để phân biệt "đang để mặc định" với "đã đặt trùng
 *     giá trị mặc định"; gộp làm một thì nút *Khôi phục mặc định* không biết mình có việc gì để làm
 * @param validation luật kiểm tra dạng {@code min=8;max=64} — trả ra để giao diện báo sớm, còn chốt
 *     chặn thật vẫn ở máy chủ (§4.2: tầng 1 chỉ phục vụ trải nghiệm)
 */
public record SettingItem(
        String key,
        String value,
        String effectiveValue,
        String valueType,
        String defaultValue,
        String groupCode,
        String label,
        String description,
        String validation,
        boolean editable) {}
