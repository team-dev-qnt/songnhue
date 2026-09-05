package com.songnhue.core.domain.audit;

/**
 * Loại thao tác ghi vào nhật ký. Khớp <b>đúng</b> ràng buộc {@code ck_audit_logs_action} trong DB —
 * thêm giá trị ở đây mà quên migration thì INSERT bị từ chối lúc chạy.
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    RESTORE,
    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    PERMISSION_CHANGE,
    EXPORT,
    IMPORT,
    APPROVE,
    REJECT,
    PUBLISH,
    BACKUP,
    DB_RESTORE,
    ARCHIVE
}
