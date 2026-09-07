package com.songnhue.core.common.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.songnhue.core.application.auth.TokenService;
import com.songnhue.core.common.security.AccessTokenClaims;
import com.songnhue.core.infra.identity.UserAuthorityRepository;

/**
 * Filter [4] — kiểm access token: chữ ký, hạn dùng, và <b>phiên còn sống hay không</b> (T5.1, T5.4).
 *
 * <p><b>Vì sao filter này KHÔNG tự trả 401.</b> Nó chưa biết endpoint sắp gọi có cần đăng nhập hay
 * không — thông tin đó nằm ở annotation trên phương thức controller, mà lúc này Spring còn chưa
 * phân giải handler. Chặn ở đây là chặn nhầm cả trang công khai.
 *
 * <p>Còn một lý do cụ thể hơn: FE thường gửi kèm access token <i>đã hết hạn</i> khi gọi
 * {@code /auth/refresh}. Nếu filter thấy token hỏng là trả 401 ngay thì luồng làm mới token không
 * bao giờ chạy được — người dùng bị đá ra đúng lúc hệ thống lẽ ra phải tự gia hạn cho họ.
 *
 * <p>Vì vậy filter chỉ <b>ghi nhận</b> kết quả; việc quyết định 401 hay cho qua là của
 * {@code PermissionInterceptor}, nơi đã biết endpoint yêu cầu gì.
 *
 * <p>Phần đối chiếu DB cố ý <b>không cache</b>: một tài khoản vừa bị khoá, một phiên vừa bị đăng xuất
 * từ xa phải mất hiệu lực <i>ngay</i>, không phải sau vài chục giây. Với 200 người dùng nội bộ, một
 * truy vấn chỉ mục mỗi request là cái giá quá rẻ cho việc đó.
 */
@Component
@Order(FilterOrder.AUTH)
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokens;
    private final UserAuthorityRepository authorities;

    public AuthFilter(TokenService tokens, UserAuthorityRepository authorities) {
        this.tokens = tokens;
        this.authorities = authorities;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = bearerToken(request);
        if (token != null) {
            resolve(token)
                    .ifPresentOrElse(
                            claims -> request.setAttribute(AuthRequestAttributes.TOKEN_CLAIMS, claims),
                            () -> request.setAttribute(AuthRequestAttributes.TOKEN_REJECTED, Boolean.TRUE));
        }
        chain.doFilter(request, response);
    }

    private Optional<AccessTokenClaims> resolve(String token) {
        Optional<AccessTokenClaims> claims = tokens.verifyAccessToken(token, Instant.now());
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        AccessTokenClaims verified = claims.get();

        // Chữ ký đúng chưa đủ: phiên có thể đã bị thu hồi, hoặc token nằm trong denylist
        if (!authorities.isAccessTokenStillValid(verified.sessionFamilyId(), verified.tokenId())) {
            log.debug("Token của {} đúng chữ ký nhưng phiên đã bị thu hồi", verified.username());
            return Optional.empty();
        }
        return claims;
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String value = header.substring(BEARER_PREFIX.length()).trim();
        return value.isEmpty() ? null : value;
    }
}
