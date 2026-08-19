package com.songnhue.core.spi;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin một tệp đính kèm trả ra cho module nghiệp vụ — thay cho entity {@code Attachment}.
 *
 * <p>Không có {@code id} chạy số: mọi lời gọi tiếp theo dùng {@code publicId} (§4.2 chống IDOR).
 *
 * @param downloadable {@code false} khi tệp còn đang chờ quét virus hoặc đã bị cách ly. Module
 *     nghiệp vụ phải hiện đúng trạng thái đó thay vì đưa ra một đường tải sẽ bị từ chối
 * @param validUntil hạn hiệu lực tài liệu (giấy phép, chứng chỉ) — {@code null} nghĩa là không hạn
 */
public record AttachmentRef(
        UUID publicId,
        String originalName,
        String contentType,
        long sizeBytes,
        int fileVersion,
        String purpose,
        boolean downloadable,
        Instant createdAt,
        java.time.LocalDate validUntil) {}
