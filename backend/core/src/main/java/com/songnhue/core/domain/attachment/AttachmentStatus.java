package com.songnhue.core.domain.attachment;

/**
 * Vòng đời một tệp đính kèm. Khớp ràng buộc {@code ck_attachments_status}.
 *
 * <p>Tệp chỉ chuyển {@link #READY} <b>sau khi</b> quét virus xong (conventions.md §4.4). Trước đó
 * mọi nơi hiển thị phải coi như tệp chưa tồn tại — cho tải một tệp chưa quét là biến hệ thống thành
 * nơi phát tán.
 */
public enum AttachmentStatus {
    UPLOADING,
    READY,
    /** Phát hiện mã độc — giữ lại bản ghi để điều tra, nhưng không ai tải được. */
    QUARANTINED,
    FAILED
}
