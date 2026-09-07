package com.songnhue.core.domain.attachment;

/** Kết quả quét virus. Khớp ràng buộc {@code ck_attachments_scan_status}. */
public enum ScanStatus {
    PENDING,
    CLEAN,
    INFECTED,
    /** Chưa cấu hình ClamAV — ghi rõ là "bỏ qua" chứ không giả vờ là "sạch". */
    SKIPPED,
    ERROR
}
