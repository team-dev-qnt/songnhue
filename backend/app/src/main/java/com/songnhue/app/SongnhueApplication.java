package com.songnhue.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi động của Modular Monolith.
 *
 * <p>Quét toàn bộ 5 module nghiệp vụ dưới {@code com.songnhue}. Ranh giới giữa các module KHÔNG do
 * Spring giữ mà do ArchUnit test enforce trong CI (conventions.md §1.1) — module chỉ được import
 * {@code spi/} của module khác.
 */
@SpringBootApplication(scanBasePackages = "com.songnhue")
public class SongnhueApplication {

    public static void main(String[] args) {
        SpringApplication.run(SongnhueApplication.class, args);
    }
}
