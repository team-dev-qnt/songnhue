package com.songnhue.core.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AppException;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.filter.AuthRequestAttributes;
import com.songnhue.core.domain.security.SecurityEventType;

/**
 * Tầng 2 của phân quyền — kiểm chốt chặn ở mức thao tác (T5.9, §4.2).
 *
 * <p>Bốn câu hỏi bài này trả lời: đúng quyền có qua không · thiếu quyền có <b>403 AUTH-3001</b>
 * không · chưa đăng nhập có <b>401</b> không · và <b>endpoint quên khai báo quyền thì mặc định là
 * cấm hay là cho qua</b>. Câu cuối là câu quan trọng nhất.
 */
class PermissionInterceptorTest {

    private SecurityEventService securityEvents;
    private PermissionInterceptor interceptor;
    private MockHttpServletRequest request;
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        securityEvents = mock(SecurityEventService.class);
        interceptor = new PermissionInterceptor(securityEvents);
        request = new MockHttpServletRequest("POST", "/api/v1/ops/maintenance");
        request.setRemoteAddr("10.0.0.9");
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Đã đăng nhập")
    class Authenticated {

        @Test
        @DisplayName("Đúng quyền → cho qua")
        void grantsWhenPermissionPresent() throws Exception {
            login(Set.of("ops:maintenance:create"));

            assertThat(interceptor.preHandle(request, response, handler("requiresCreate")))
                    .isTrue();
            verify(securityEvents, never()).record(any(), any(), any(), any(), anyString());
        }

        @Test
        @DisplayName("Thiếu quyền → 403 AUTH-3001 + ghi sự kiện bảo mật")
        void deniesWhenPermissionMissing() {
            login(Set.of("ops:maintenance:view"));

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("requiresCreate")))
                    .isInstanceOf(PermissionDeniedException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_3001);

            // Bị từ chối quyền là tín hiệu đáng theo dõi, không chỉ là một dòng log rồi thôi
            verify(securityEvents)
                    .record(eq(SecurityEventType.ACCESS_DENIED_PERMISSION), eq("nva"), eq(7L), any(), anyString());
        }

        @Test
        @DisplayName("mode = ANY: có một trong các quyền là đủ")
        void anyModeNeedsOnlyOne() throws Exception {
            login(Set.of("ops:maintenance:approve"));

            assertThat(interceptor.preHandle(request, response, handler("requiresAny")))
                    .isTrue();
        }

        @Test
        @DisplayName("mode = ALL: thiếu một quyền là bị chặn")
        void allModeNeedsEveryOne() {
            login(Set.of("ops:maintenance:create"));

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("requiresAll")))
                    .isInstanceOf(PermissionDeniedException.class);
        }

        @Test
        @DisplayName("@AuthenticatedEndpoint: chỉ cần đăng nhập, không cần quyền nào")
        void authenticatedEndpointNeedsNoPermission() throws Exception {
            login(Set.of());

            assertThat(interceptor.preHandle(request, response, handler("ownProfile")))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Chưa đăng nhập")
    class Anonymous {

        @Test
        @DisplayName("Không có token → 401 AUTH-0002")
        void rejectsAnonymous() {
            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("requiresCreate")))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_0002);
        }

        @Test
        @DisplayName("Token bị từ chối (hết hạn / phiên đã thu hồi) → cũng 401")
        void rejectsInvalidToken() {
            request.setAttribute(AuthRequestAttributes.TOKEN_REJECTED, Boolean.TRUE);

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("requiresCreate")))
                    .isInstanceOf(AuthenticationException.class);
        }

        @Test
        @DisplayName("@PublicEndpoint → qua được dù chưa đăng nhập")
        void allowsPublicEndpoint() throws Exception {
            assertThat(interceptor.preHandle(request, response, handler("publicPing")))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Deny by default")
    class DenyByDefault {

        @Test
        @DisplayName("⚠ Endpoint QUÊN khai báo quyền → bị CẤM, không phải được cho qua")
        void unannotatedEndpointIsDenied() {
            login(Set.of("ops:maintenance:create", "adm:user:manage"));

            // Đây là câu hỏi gốc của "deny by default": lỡ quên thì mặc định là gì.
            // Nếu mặc định cho qua thì mỗi lần quên là một lỗ hổng không triệu chứng.
            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("forgotToDeclare")))
                    .isInstanceOf(PermissionDeniedException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_3001);
        }
    }

    @Nested
    @DisplayName("Bắt buộc đổi mật khẩu")
    class MustChangePassword {

        @Test
        @DisplayName("Chặn mọi endpoint khác bằng AUTH-0007")
        void blocksEverythingElse() {
            AuthContext.set(user(Set.of("ops:maintenance:create"), true));

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("requiresCreate")))
                    .isInstanceOf(PermissionDeniedException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_0007);
        }

        @Test
        @DisplayName("Vẫn cho gọi chính endpoint đổi mật khẩu — nếu không thì người dùng bị kẹt")
        void allowsTheChangePasswordEndpoint() throws Exception {
            AuthContext.set(user(Set.of(), true));
            request = new MockHttpServletRequest("POST", "/api/v1/auth/change-password");

            assertThat(interceptor.preHandle(request, response, handler("ownProfile")))
                    .isTrue();
        }
    }

    // -------------------------------------------------------------------------

    private void login(Set<String> permissions) {
        AuthContext.set(user(permissions, false));
    }

    private static AuthenticatedUser user(Set<String> permissions, boolean mustChangePassword) {
        return new AuthenticatedUser(
                7L,
                UUID.randomUUID(),
                "nva",
                "Nguyễn Văn A",
                4L,
                "/1/4/",
                Set.of("TECHNICIAN"),
                permissions,
                mustChangePassword,
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private static HandlerMethod handler(String methodName) {
        SampleController controller = new SampleController();
        for (Method method : SampleController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return new HandlerMethod(controller, method);
            }
        }
        throw new IllegalArgumentException("Không có phương thức " + methodName);
    }

    /** Controller giả lập, chỉ tồn tại trong bài kiểm này. */
    @SuppressWarnings("unused")
    static class SampleController {

        @RequirePermission("ops:maintenance:create")
        public void requiresCreate() {}

        @RequirePermission({"ops:maintenance:create", "ops:maintenance:approve"})
        public void requiresAny() {}

        @RequirePermission(
                value = {"ops:maintenance:create", "ops:maintenance:approve"},
                mode = RequirePermission.Mode.ALL)
        public void requiresAll() {}

        @AuthenticatedEndpoint(reason = "thao tác với chính mình")
        public void ownProfile() {}

        @PublicEndpoint(reason = "trang công khai")
        public void publicPing() {}

        /** Cố ý không có annotation nào — đây chính là đối tượng của bài kiểm deny-by-default. */
        public void forgotToDeclare() {}
    }
}
