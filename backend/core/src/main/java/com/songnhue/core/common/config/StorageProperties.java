package com.songnhue.core.common.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kết nối MinIO (conventions.md §1.6 — mọi connection đọc từ env).
 *
 * <p>Ba bucket tách biệt có chủ đích: {@code audit} bật versioning và chỉ job kết xuất được ghi
 * (G7), nên không thể trộn chung với ảnh bài viết.
 *
 * <p>Client MinIO sẽ được khởi tạo một lần qua Spring bean ở WS-6/T6.3 — mã nghiệp vụ chỉ inject,
 * cấm tự tạo kết nối (§1.6).
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    @NotBlank(message = "Thiếu MINIO_ENDPOINT")
    private String endpoint;

    @NotBlank(message = "Thiếu MINIO_ACCESS_KEY")
    private String accessKey;

    @NotBlank(message = "Thiếu MINIO_SECRET_KEY")
    private String secretKey;

    @NotBlank(message = "Thiếu MINIO_BUCKET_MEDIA")
    private String bucketMedia;

    @NotBlank(message = "Thiếu MINIO_BUCKET_REPORT")
    private String bucketReport;

    @NotBlank(message = "Thiếu MINIO_BUCKET_AUDIT")
    private String bucketAudit;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketMedia() {
        return bucketMedia;
    }

    public void setBucketMedia(String bucketMedia) {
        this.bucketMedia = bucketMedia;
    }

    public String getBucketReport() {
        return bucketReport;
    }

    public void setBucketReport(String bucketReport) {
        this.bucketReport = bucketReport;
    }

    public String getBucketAudit() {
        return bucketAudit;
    }

    public void setBucketAudit(String bucketAudit) {
        this.bucketAudit = bucketAudit;
    }
}
