package com.songnhue.core.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;

/**
 * Khởi tạo client MinIO <b>một lần</b> qua Spring bean — nợ WS-4/T4.6, trả ở T6.3.
 *
 * <p>conventions.md §1.6: mã nghiệp vụ chỉ được inject client, <b>cấm tự tạo kết nối</b>. Mỗi chỗ
 * tự dựng client là một chỗ giữ access key trong mã, một bộ tham số timeout khác nhau, và một nhóm
 * kết nối riêng — ba thứ đều chỉ lộ ra khi tải cao.
 *
 * <p>Client này an toàn cho nhiều luồng và được thiết kế để dùng lại; tạo mới cho mỗi lần tải tệp
 * là mở lại toàn bộ vòng bắt tay HTTP.
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public MinioClient minioClient(StorageProperties properties) {
        log.info("Kết nối kho đối tượng tại {}", properties.getEndpoint());
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
