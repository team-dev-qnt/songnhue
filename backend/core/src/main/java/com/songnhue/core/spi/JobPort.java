package com.songnhue.core.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Hàng đợi việc chạy nền — pattern P5, hàng đợi nằm trong CSDL ({@code SKIP LOCKED}).
 *
 * <p>Hình dạng chuẩn cho việc dài: API trả <b>202 + jobId</b> ngay, người dùng tra tiến độ bằng
 * {@link #findJob}, xong thì nhận thông báo kèm đường tải (conventions.md §1.3). Xuất báo cáo, nhập
 * dữ liệu hàng loạt và sinh ảnh phái sinh đều đi đường này.
 */
public interface JobPort {

    JobRef enqueue(JobRequest request);

    Optional<JobRef> findJob(UUID publicId);
}
