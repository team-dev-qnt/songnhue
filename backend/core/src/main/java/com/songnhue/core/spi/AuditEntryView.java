package com.songnhue.core.spi;

import java.time.Instant;

/**
 * Một dòng nhật ký thay đổi của bản ghi nghiệp vụ.
 *
 * <p>Cố ý <b>không</b> mang {@code hash}/{@code prevHash}/{@code seq}: chuỗi băm là công cụ chứng
 * minh nhật ký chưa bị sửa, và nó được kiểm ở màn hình quản trị chuyên trách ({@code adm:audit:verify}).
 * Đưa vào đây thì mọi module lại phải hiểu về nó, mà không module nào có việc gì để làm với nó.
 *
 * @param oldValue JSON giá trị cũ — {@code null} khi là lượt tạo mới
 * @param newValue JSON giá trị mới — {@code null} khi là lượt xoá
 */
public record AuditEntryView(
        Instant occurredAt,
        String actorUsername,
        Long actorUserId,
        String action,
        String oldValue,
        String newValue,
        String traceId) {}
