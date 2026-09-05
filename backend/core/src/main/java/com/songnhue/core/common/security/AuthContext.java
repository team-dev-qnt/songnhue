package com.songnhue.core.common.security;

import java.util.Optional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AuthenticationException;

/**
 * Người dùng của request hiện tại, giữ trong {@link ThreadLocal}.
 *
 * <p>Không dùng {@code SecurityContextHolder} của Spring Security vì dự án cố ý không kéo cả framework
 * đó vào (xem {@code core/pom.xml}). Cơ chế thì giống hệt: đặt ở đầu request, xoá ở {@code finally}.
 *
 * <p><b>Bắt buộc xoá ở {@code finally}.</b> Thread nằm trong pool và sẽ phục vụ người tiếp theo —
 * quên xoá không làm hỏng gì trông thấy, nó chỉ khiến người sau thao tác dưới danh nghĩa người
 * trước. Đây là loại lỗi rò rỉ dữ liệu tệ nhất: hệ thống chạy hoàn toàn bình thường.
 *
 * <p>Tác vụ nền (WS-6) chạy ngoài request nên đọc ra rỗng — đúng: job hệ thống không có người thao
 * tác, và không được mượn quyền của ai.
 */
public final class AuthContext {

    private static final ThreadLocal<AuthenticatedUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(AuthenticatedUser user) {
        HOLDER.set(user);
    }

    public static Optional<AuthenticatedUser> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * @throws AuthenticationException khi chưa đăng nhập — dùng ở nơi mà việc không có người dùng là
     *     lỗi lập trình (đã qua interceptor phân quyền rồi mới gọi tới)
     */
    public static AuthenticatedUser require() {
        AuthenticatedUser user = HOLDER.get();
        if (user == null) {
            throw new AuthenticationException(ErrorCode.AUTH_0002);
        }
        return user;
    }

    public static boolean isAuthenticated() {
        return HOLDER.get() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
