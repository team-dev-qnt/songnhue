/**
 * Tầng Application — Service use-case, orchestration, ranh giới transaction.
 *
 * <p>{@code @Transactional} chỉ đặt ở đây, không đặt ở controller hay repository.
 * Đây cũng là nơi duy nhất Entity được phép xuất hiện trước khi map sang DTO (conventions.md §1.1).
 */
package com.songnhue.hydro.application;
