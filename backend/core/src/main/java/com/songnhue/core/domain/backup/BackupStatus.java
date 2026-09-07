package com.songnhue.core.domain.backup;

/** Khớp CHECK {@code ck_system_backups_status}. */
public enum BackupStatus {
    /** Đang chạy. Bản ghi được tạo TRƯỚC khi gọi pg_dump — xem {@code BackupService}. */
    RUNNING,
    SUCCEEDED,
    FAILED
}
