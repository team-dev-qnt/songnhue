/**
 * Tầng API — Controller + Request/Response DTO (Java record).
 *
 * <p><b>Không chứa logic nghiệp vụ.</b> Controller chỉ nhận/trả DTO, không bao giờ để Entity lọt ra
 * ngoài. Mọi endpoint bắt buộc khai báo {@code @RequirePermission} — thiếu là CI đỏ
 * (conventions.md §4.2, deny by default).
 */
package com.songnhue.hr.api;
