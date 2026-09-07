package com.songnhue.core.domain.backup;

/** Khớp CHECK {@code ck_system_backups_trigger}. */
public enum BackupTrigger {
    /** Job 02:00 hằng đêm (T7.1). */
    SCHEDULED,
    /** Người dùng bấm nút trên màn hình quản trị (M5.10). */
    MANUAL,
    /**
     * Bản chụp bắt buộc ngay trước khi khôi phục ghi đè (M5.11).
     *
     * <p>Không có nó thì khôi phục nhầm bản là mất luôn trạng thái hiện tại, và không còn đường lùi
     * nào — bản dump đêm trước đã là thứ vừa bị ghi đè lên.
     */
    PRE_RESTORE
}
