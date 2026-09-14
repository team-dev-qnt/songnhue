/**
 * Tầng SPI — Service interface công khai cho module khác gọi.
 *
 * <p><b>Đây là package DUY NHẤT được phép import chéo giữa các module.</b> Module khác cấm import
 * {@code domain/}, {@code infra/}, {@code application/} — ArchUnit test chặn trong CI
 * (conventions.md §1.1, rule 6 CLAUDE.md).
 *
 * <p>⚠ <b>RỖNG — có chủ đích, đo 14/09/2026 (T54.10 · T61.15).</b> Package này ⛔ có hợp đồng nào và
 * ⛔ module nào import nó. Hợp đồng của MOD-04 với {@code core} (danh bạ, liên kết tài khoản) nằm ở
 * {@code core.spi.EmployeeDirectoryPort}, vì {@code core} ⛔ phụ thuộc Maven vào {@code hr} được (T54.0).
 * Giữ package để khung module của {@code conventions.md §1.1} đồng dạng với {@code content.spi} và
 * {@code operations.spi} — hai package cũng đang rỗng. Hợp đồng đầu tiên theo chiều {@code content → hr}
 * hoặc {@code operations → hr} thì đặt ở đây và xoá đoạn này.
 */
package com.songnhue.hr.spi;
