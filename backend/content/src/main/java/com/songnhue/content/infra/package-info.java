/**
 * Tầng Infra — Repository implementation, client gọi API ngoài, adapter.
 *
 * <p>Mọi client (DB, HTTP, S3) khởi tạo qua Spring bean đọc cấu hình từ env — cấm tạo connection
 * trực tiếp trong code nghiệp vụ (conventions.md §1.6).
 */
package com.songnhue.content.infra;
