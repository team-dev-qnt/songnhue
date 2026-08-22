package com.songnhue.core.spi;

import java.time.Instant;
import java.util.UUID;

/**
 * Một việc chạy nền — pattern P5.
 *
 * @param status {@code PENDING} · {@code PROCESSING} · {@code COMPLETED} · {@code FAILED} ·
 *     {@code CANCELLED}
 * @param resultRef con trỏ tới kết quả khi đã xong (VD khoá tệp báo cáo trong kho), {@code null}
 *     khi chưa có
 */
public record JobRef(
        UUID publicId, String jobType, String status, short attempts, Instant createdAt, String resultRef) {}
