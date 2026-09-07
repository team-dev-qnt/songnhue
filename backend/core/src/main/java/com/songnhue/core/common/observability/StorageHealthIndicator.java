package com.songnhue.core.common.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.config.StorageProperties;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;

/**
 * Kho tệp MinIO có nối được không (T7.8 · M5.12).
 *
 * <p>Kiểm bằng {@code bucketExists} trên bucket media chứ không chỉ "mở được TCP": MinIO sống nhưng
 * bucket bị xoá hoặc khoá truy cập là hỏng thật, mà một phép kiểm ở mức mạng thì vẫn báo xanh. Đây
 * cũng là lệnh rẻ nhất chạm được tới cả xác thực lẫn phân quyền.
 */
@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {

    private final MinioClient client;
    private final StorageProperties properties;

    public StorageHealthIndicator(MinioClient client, StorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucketMedia())
                    .build());
            if (!exists) {
                return Health.down()
                        .withDetail("reason", "Không thấy bucket " + properties.getBucketMedia())
                        .build();
            }
            return Health.up().withDetail("endpoint", properties.getEndpoint()).build();
        } catch (Exception e) {
            // ⛔ Chỉ lấy loại ngoại lệ và câu thông báo. Chi tiết của MinIO có thể chứa cả access
            // key trong thông tin request — mà /actuator/health là endpoint đọc được từ mạng nội bộ.
            return Health.down()
                    .withDetail("reason", e.getClass().getSimpleName())
                    .build();
        }
    }
}
