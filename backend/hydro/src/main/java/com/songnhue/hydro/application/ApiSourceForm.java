package com.songnhue.hydro.application;

import com.songnhue.hydro.domain.ApiSourceStatus;

/**
 * Dữ liệu nhập khi sửa hồ sơ một nguồn dữ liệu — CN-03.1.
 *
 * <h2>⛔ Không có trường mã số ở đây</h2>
 *
 * <p>Mã số đi bằng {@link ApiSourceService#datMaSo} riêng. Lẫn nó vào form sửa tên thì mỗi lượt sửa
 * mô tả cũng sinh một sự kiện bảo mật {@code EXTERNAL_CREDENTIAL_*} — nhật ký đầy sự kiện giả thì
 * sự kiện thật không còn ai để ý (§4.7).
 *
 * <p>Bốn tham số nhịp ({@code frameMinutes}, {@code timeoutSeconds}, {@code maxRetry}, {@code cron})
 * để {@code null} nghĩa là <b>dùng tham số chung ở {@code settings}</b>, không phải "không có giá
 * trị". Việc giải nằm ở một chỗ duy nhất là {@link ApiSourceService#thamSoHieuLuc}.
 */
public record ApiSourceForm(
        String name,
        String baseUrl,
        Integer frameMinutes,
        Integer timeoutSeconds,
        Integer maxRetry,
        String cron,
        ApiSourceStatus status,
        String description) {}
