package com.songnhue.core.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.security.CsrfTokens;

/**
 * CSRF double-submit (T5.5, §4.1).
 *
 * <p>Điểm cần chứng minh: một trang độc hại <b>gửi được cookie</b> (trình duyệt tự đính kèm) nhưng
 * <b>không đọc được</b> nó, nên không dựng nổi header khớp. Bài kiểm dưới đây mô phỏng đúng tình
 * huống đó ở trường hợp "có cookie, không có header".
 */
class CsrfFilterTest {

    private HandlerExceptionResolver resolver;
    private CsrfFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        resolver = mock(HandlerExceptionResolver.class);
        filter = new CsrfFilter(resolver);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    @DisplayName("Header khớp cookie → cho qua")
    void allowsMatchingToken() throws Exception {
        MockHttpServletRequest request = mutating();
        request.setCookies(new Cookie(CsrfTokens.COOKIE, "token-abc"));
        request.addHeader(CsrfTokens.HEADER, "token-abc");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    @DisplayName("⚠ Có cookie nhưng thiếu header → chặn (đúng hình dạng của một request giả mạo)")
    void blocksRequestWithCookieButNoHeader() throws Exception {
        MockHttpServletRequest request = mutating();
        request.setCookies(new Cookie(CsrfTokens.COOKIE, "token-abc"));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        verify(resolver).resolveException(any(), any(), isNull(), any(PermissionDeniedException.class));
    }

    @Test
    @DisplayName("Header khác cookie → chặn")
    void blocksMismatchedToken() throws Exception {
        MockHttpServletRequest request = mutating();
        request.setCookies(new Cookie(CsrfTokens.COOKIE, "token-abc"));
        request.addHeader(CsrfTokens.HEADER, "token-khac");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("GET không bị kiểm — phương thức chỉ đọc thì giả mạo cũng không đổi được gì")
    void skipsSafeMethods() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ops/constructions");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Đăng nhập được miễn — lúc đó chưa có cookie nào để đối chiếu")
    void skipsLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("⚠ /auth/refresh KHÔNG được miễn — chính nó là mục tiêu CSRF điển hình nhất")
    void doesNotSkipRefresh() throws Exception {
        // Endpoint này xác thực bằng cookie, tức là trình duyệt tự gửi kèm khi trang lạ gọi tới
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setCookies(new Cookie(CsrfTokens.REFRESH_COOKIE, "refresh-token-cua-nan-nhan"));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        verify(resolver).resolveException(any(), any(), isNull(), any(PermissionDeniedException.class));
    }

    @Test
    @DisplayName("Đường dẫn ngoài /api/v1 không bị kiểm (actuator, tài liệu API)")
    void skipsNonApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/actuator/refresh");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest mutating() {
        return new MockHttpServletRequest("POST", "/api/v1/ops/maintenance");
    }
}
