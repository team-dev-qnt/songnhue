package com.songnhue.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Điểm khởi động của Modular Monolith.
 *
 * <p>Quét toàn bộ 5 module nghiệp vụ dưới {@code com.songnhue}. Ranh giới giữa các module KHÔNG do
 * Spring giữ mà do ArchUnit test enforce trong CI (conventions.md §1.1) — module chỉ được import
 * {@code spi/} của module khác.
 *
 * <p>⚠ <b>{@code @EntityScan} và {@code @EnableJpaRepositories} phải khai báo tường minh.</b>
 * {@code scanBasePackages} chỉ mở rộng phạm vi <i>component scan</i>; phần tự cấu hình JPA của Spring
 * Boot vẫn lấy gói của chính lớp này ({@code com.songnhue.app}) làm gốc. Thiếu hai annotation này thì
 * mọi entity và repository nằm ở module khác đều không được tìm thấy, và ứng dụng chết lúc khởi động
 * với thông báo "required a bean … that could not be found" — trông như lỗi khai báo bean chứ không
 * hề gợi ý tới nguyên nhân thật.
 */
@SpringBootApplication(scanBasePackages = "com.songnhue")
@EntityScan(basePackages = "com.songnhue")
@EnableJpaRepositories(basePackages = "com.songnhue")
public class SongnhueApplication {

    public static void main(String[] args) {
        SpringApplication.run(SongnhueApplication.class, args);
    }
}
