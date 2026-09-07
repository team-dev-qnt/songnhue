package com.songnhue.core.common.filter;

/**
 * Thông tin "ai đang thao tác, từ đâu" cho một request — nguồn của các cột trong {@code audit_logs}
 * và {@code security_events}.
 *
 * <p>Tách khỏi SecurityContext của Spring vì hai thứ có vòng đời khác nhau: audit cần ghi cả những
 * thao tác CHƯA đăng nhập (đăng nhập thất bại, truy cập bị từ chối), lúc đó SecurityContext rỗng
 * nhưng IP và traceId vẫn phải vào được nhật ký.
 *
 * <p>Dùng {@link ThreadLocal} — worker chạy nền (WS-6) không có request nên sẽ đọc ra bản rỗng, và
 * đó là đúng: job hệ thống không có "người thao tác".
 */
public final class AuditContext {

    private static final ThreadLocal<Data> HOLDER = new ThreadLocal<>();

    private AuditContext() {}

    public static void set(Data data) {
        HOLDER.set(data);
    }

    /** Không bao giờ trả null — nơi gọi không phải kiểm tra rỗng. */
    public static Data get() {
        Data data = HOLDER.get();
        return data != null ? data : Data.empty();
    }

    /** BẮT BUỘC gọi ở finally: thread nằm trong pool, quên xoá là gán nhầm người thao tác. */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * @param userId id người dùng, null khi chưa đăng nhập
     * @param username giữ dạng text để nhật ký còn đọc được sau khi tài khoản bị xoá mềm
     * @param ipAddress địa chỉ IP nguồn
     */
    public record Data(Long userId, String username, String ipAddress) {

        private static final Data EMPTY = new Data(null, null, null);

        public static Data empty() {
            return EMPTY;
        }
    }
}
