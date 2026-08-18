/**
 * Tầng SPI — Service interface công khai cho module khác gọi.
 *
 * <p><b>Đây là package DUY NHẤT được phép import chéo giữa các module.</b> Module khác cấm import
 * {@code domain/}, {@code infra/}, {@code application/} — ArchUnit test chặn trong CI
 * (conventions.md §1.1, rule 6 CLAUDE.md).
 */
package com.songnhue.content.spi;
