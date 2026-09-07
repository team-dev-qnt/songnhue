package com.songnhue.core.domain.identity;

/**
 * Lý do một phiên bị thu hồi — khớp CHECK {@code ck_sessions_revoked_reason}.
 *
 * <p>Giữ lý do chứ không chỉ giữ cờ đã/chưa thu hồi: khi điều tra sự cố, câu hỏi luôn là
 * "phiên này chết vì đâu" — người dùng tự đăng xuất, token bị xoay, hay hệ thống phát hiện bất
 * thường.
 */
public enum SessionRevokeReason {

    /** Người dùng bấm đăng xuất. */
    LOGOUT,

    /** Đã làm mới token, bản ghi này được thay bằng bản ghi con cùng family. */
    ROTATED,

    /** ⚠ Refresh token đã xoay lại được dùng lần nữa — thu hồi cả family (§4.1). */
    REUSE_DETECTED,

    PASSWORD_CHANGED,

    ACCOUNT_LOCKED,

    /** Admin thu hồi phiên của người khác. */
    ADMIN_REVOKED,

    EXPIRED,

    /** Người dùng tự đăng xuất một thiết bị khác từ màn hình quản lý phiên (M5.14). */
    REMOTE_LOGOUT
}
