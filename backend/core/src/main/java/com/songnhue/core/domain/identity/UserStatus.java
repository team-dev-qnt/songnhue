package com.songnhue.core.domain.identity;

/**
 * Trạng thái tài khoản — khớp CHECK constraint {@code ck_users_status} của bảng {@code users}.
 *
 * <p>Lưu vào DB dạng {@code VARCHAR} chứ không phải enum của Postgres (conventions.md §1.2): thêm
 * trạng thái mới không phải {@code ALTER TYPE} trên bảng đang chạy.
 */
public enum UserStatus {

    /** Đã tạo nhưng chưa đặt mật khẩu — không đăng nhập được. */
    PENDING_ACTIVATION,

    ACTIVE,

    /** Admin khoá thủ công, hoặc bị khoá do vi phạm. Khác với khoá tạm 15' (cột `locked_until`). */
    LOCKED,

    /** Nghỉ việc / ngừng sử dụng. CN-05.1: không xoá tài khoản đã có lịch sử thao tác. */
    DISABLED
}
