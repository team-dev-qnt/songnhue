package com.songnhue.core.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.songnhue.core.application.maintenance.MaintenanceModeService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AppException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;

/**
 * Chế độ bảo trì chặn ghi (T7.6, M5.11).
 *
 * <p>Ba bài kiểm quan trọng nhất ở đây <b>không</b> phải "chặn được không" — mà là ba lối thoát:
 * đường đăng nhập, Super Admin, và request đọc. Thiếu bất kỳ lối nào thì bật bảo trì xong là tự
 * khoá mình ở ngoài, và người duy nhất tắt được nó lại chính là người không vào được.
 */
class MaintenanceModeFilterTest {

    private MaintenanceModeService maintenanceMode;
    private HandlerExceptionResolver resolver;
    private MaintenanceModeFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        maintenanceMode = mock(MaintenanceModeService.class);
        resolver = mock(HandlerExceptionResolver.class);
        filter = new MaintenanceModeFilter(maintenanceMode, resolver);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Đang bảo trì + request ghi → chặn bằng SYS-0007")
    void blocksWriteDuringMaintenance() throws Exception {
        when(maintenanceMode.isEnabled()).thenReturn(true);

        filter.doFilter(request("POST", "/api/v1/org-units"), response, chain);

        assertThat(chain.getRequest()).as("không được đi tiếp vào controller").isNull();

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(resolver).resolveException(any(), any(), isNull(), captor.capture());
        assertThat(((AppException) captor.getValue()).errorCode()).isEqualTo(ErrorCode.SYS_0007);
    }

    @Test
    @DisplayName("⚠ Lối thoát 1: đường đăng nhập vẫn qua — nếu không thì không ai tắt được bảo trì")
    void alwaysAllowsAuthEndpoints() throws Exception {
        when(maintenanceMode.isEnabled()).thenReturn(true);

        filter.doFilter(request("POST", "/api/v1/auth/login"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("⚠ Lối thoát 2: Super Admin ghi được — đó là người đang khôi phục dữ liệu")
    void allowsSuperAdmin() throws Exception {
        when(maintenanceMode.isEnabled()).thenReturn(true);
        AuthContext.set(userWithRole("SUPER_ADMIN"));

        filter.doFilter(request("POST", "/api/v1/settings/x"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Admin thường KHÔNG được ghi — chỉ Super Admin mới là lối thoát")
    void blocksOrdinaryAdmin() throws Exception {
        when(maintenanceMode.isEnabled()).thenReturn(true);
        AuthContext.set(userWithRole("ADMIN"));

        filter.doFilter(request("PUT", "/api/v1/settings/x"), response, chain);

        assertThat(chain.getRequest()).isNull();
        verify(resolver).resolveException(any(), any(), isNull(), any());
    }

    @Test
    @DisplayName("⚠ Lối thoát 3: request đọc vẫn qua — chặn cả đọc thì màn hình trắng trơn")
    void allowsReads() throws Exception {
        when(maintenanceMode.isEnabled()).thenReturn(true);

        filter.doFilter(request("GET", "/api/v1/org-units"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Actuator không bị chặn — giám sát phải trả lời được trong lúc bảo trì")
    void allowsActuator() throws Exception {
        when(maintenanceMode.isEnabled()).thenReturn(true);

        filter.doFilter(request("POST", "/actuator/refresh"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Không bảo trì → mọi thứ đi qua bình thường")
    void passesThroughWhenDisabled() throws Exception {
        when(maintenanceMode.isEnabled()).thenReturn(false);

        filter.doFilter(request("DELETE", "/api/v1/users/abc"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    /**
     * ⚠ Bài kiểm chứng minh cơ chế canh gác thật sự bắt được vi phạm (conventions.md §1.5).
     *
     * <p>Ba bài "cho qua" ở trên đều xanh cả khi filter không làm gì cả. Bài này khoá lại điều đó:
     * cùng một request POST, chỉ khác giá trị cờ bảo trì, phải cho ra hai kết quả khác nhau. Filter
     * bị vô hiệu hoá — gỡ annotation, sai thứ tự, {@code shouldNotFilter} trả về true — thì hai kết
     * quả giống nhau và bài này đỏ.
     */
    @Test
    @DisplayName("Cơ chế có thật: cùng request, đổi cờ → đổi kết quả")
    void guardActuallyBites() throws Exception {
        when(maintenanceMode.isEnabled()).thenReturn(false);
        MockFilterChain allowed = new MockFilterChain();
        filter.doFilter(request("POST", "/api/v1/org-units"), new MockHttpServletResponse(), allowed);

        when(maintenanceMode.isEnabled()).thenReturn(true);
        MockFilterChain blocked = new MockFilterChain();
        filter.doFilter(request("POST", "/api/v1/org-units"), new MockHttpServletResponse(), blocked);

        assertThat(allowed.getRequest()).as("tắt bảo trì thì phải đi tiếp").isNotNull();
        assertThat(blocked.getRequest()).as("bật bảo trì thì phải dừng").isNull();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private static AuthenticatedUser userWithRole(String role) {
        return new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "nguoi-dung",
                "Người dùng",
                1L,
                "/1/",
                Set.of(role),
                Set.of(),
                false,
                UUID.randomUUID(),
                UUID.randomUUID());
    }
}
