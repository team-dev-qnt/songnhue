package com.songnhue.hr.application;

import java.time.LocalDate;

import com.songnhue.hr.domain.EmployeeEventType;

/**
 * Dữ liệu tạo/sửa một sự kiện trên timeline công tác — CN-04.4.
 *
 * @param effectiveOn ngày quyết định <b>có hiệu lực</b> — trục timeline. ⛔ Khác
 *     {@code decisionDate} (ngày ký) và khác {@code createdAt} (ngày nhập). Một quyết định ký tháng
 *     3 có hiệu lực từ tháng 1 phải nằm ở tháng 1
 */
public record SuKienForm(
        EmployeeEventType eventType,
        LocalDate effectiveOn,
        String decisionNo,
        LocalDate decisionDate,
        String title,
        String detail) {}
