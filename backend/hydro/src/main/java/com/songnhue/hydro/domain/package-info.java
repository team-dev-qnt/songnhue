/**
 * Tầng Domain — Entity, Value Object, domain service, rule nghiệp vụ, validation.
 *
 * <p>Rule nghiệp vụ đặt ở đây chứ không chỉ ở FE/controller, và mỗi rule phải có unit test
 * (architecture-review.md §2.2). Số đo và tiền dùng {@code BigDecimal} — cấm {@code float/double}.
 */
package com.songnhue.hydro.domain;
