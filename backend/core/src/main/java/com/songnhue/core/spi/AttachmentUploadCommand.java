package com.songnhue.core.spi;

import java.util.List;

/**
 * Yêu cầu tải một tệp lên kho.
 *
 * <p>Gói thành record thay vì sáu tham số rời: nơi gọi nằm rải khắp các module, và thêm một trường
 * về sau không được phép làm vỡ mọi chỗ gọi.
 *
 * @param ownerType loại chủ sở hữu — {@code 'ARTICLE'}, {@code 'CONSTRUCTION'},
 *     {@code 'MAINTENANCE_LOG'}, {@code 'MEDIA_FOLDER'}…
 * @param purpose phân loại trong cùng một chủ sở hữu; cũng là khoá đánh số phiên bản
 * @param allowedMimeTypes danh sách MIME chấp nhận. ⚠ Kiểm bằng <b>magic bytes</b>, không tin đuôi
 *     tệp và không tin {@code Content-Type} do trình duyệt gửi
 */
public record AttachmentUploadCommand(
        String ownerType,
        Long ownerId,
        String purpose,
        String originalName,
        byte[] content,
        List<String> allowedMimeTypes) {}
