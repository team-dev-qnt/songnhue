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
    PRE_RESTORE,
    /**
     * Bản chụp trước mỗi lượt triển khai — {@code deploy/backup/pre-deploy-dump.sh} (T11.34).
     *
     * <p>Đây là <b>điểm quay lui duy nhất</b> của một lượt deploy hỏng. Trước 08/09/2026 nó ghi
     * {@code MANUAL} vì ràng buộc chưa có giá trị nào đúng nghĩa, và được phân biệt bằng tiền tố
     * tên tệp {@code predeploy-*} — một quy ước sống trong trí nhớ con người, nên mọi truy vấn lọc
     * theo {@code trigger_type} đều trộn nó vào cùng rổ với bản người dùng bấm tay.
     */
    PRE_DEPLOY
}
