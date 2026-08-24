package com.songnhue.core.api.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.auth.AuthService;
import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.PasswordChangeService;
import com.songnhue.core.application.auth.SessionService;
import com.songnhue.core.application.auth.TotpService;
import com.songnhue.core.common.config.JwtProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedEndpoint;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.security.CsrfTokens;
import com.songnhue.core.common.security.PublicEndpoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API xác thực — {@code /api/v1/auth/**}.
 *
 * <p><b>Quy ước chia đôi token</b>, giữ nguyên ở mọi endpoint dưới đây:
 *
 * <ul>
 *   <li><b>Access token</b> trả trong body, FE giữ trong bộ nhớ (không localStorage). Sống 30 phút.
 *   <li><b>Refresh token</b> chỉ đặt vào cookie {@code httpOnly + Secure + SameSite=Strict}. Sống
 *       nhiều ngày, nên tuyệt đối không để JavaScript chạm tới — một lỗ XSS lấy được nó là chiếm
 *       phiên dài hạn.
 *   <li><b>CSRF token</b> ở cookie đọc được + trả trong body, FE gửi lại ở header {@code X-CSRF-Token}.
 * </ul>
 *
 * <p>Mọi endpoint đều khai báo tường minh mức bảo vệ ({@code @PublicEndpoint} /
 * {@code @AuthenticatedEndpoint}) — deny by default, xem {@code PermissionInterceptor}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "00-core · Xác thực", description = "Đăng nhập, 2FA, làm mới token, quản lý phiên")
public class AuthController {

    private final AuthService authService;
    private final PasswordChangeService passwordChangeService;
    private final SessionService sessionService;
    private final JwtProperties jwtProperties;

    /**
     * Cookie có cờ {@code Secure} hay không.
     *
     * <p>Local chạy {@code http://} nên phải tắt — bật lên là trình duyệt bỏ luôn cookie và không ai
     * đăng nhập được, mà lại không có thông báo lỗi nào. Staging/Production chạy HTTPS nên bật.
     */
    private final boolean secureCookie;

    public AuthController(
            AuthService authService,
            PasswordChangeService passwordChangeService,
            SessionService sessionService,
            JwtProperties jwtProperties,
            @org.springframework.beans.factory.annotation.Value("${app.security.secure-cookie:true}")
                    boolean secureCookie) {
        this.authService = authService;
        this.passwordChangeService = passwordChangeService;
        this.sessionService = sessionService;
        this.jwtProperties = jwtProperties;
        this.secureCookie = secureCookie;
    }

    // =========================================================================
    // Đăng nhập
    // =========================================================================

    @PostMapping("/login")
    @PublicEndpoint(reason = "Cửa vào hệ thống — chưa có phiên nào để kiểm")
    @Operation(summary = "Đăng nhập bước 1 (tên đăng nhập + mật khẩu)")
    public AuthDtos.LoginResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Instant now = Instant.now();
        AuthService.LoginOutcome outcome =
                authService.login(request.username(), request.password(), ClientInfo.from(httpRequest), now);

        if (outcome.stage() != AuthService.LoginOutcome.Stage.AUTHENTICATED) {
            return new AuthDtos.LoginResponse(
                    outcome.stage().name(), null, null, null, outcome.challengeToken(), false);
        }
        return issue(outcome.tokens(), httpResponse, now);
    }

    @PostMapping("/2fa/verify")
    @PublicEndpoint(reason = "Bước 2 của đăng nhập — vé challenge thay cho phiên")
    @Operation(summary = "Đăng nhập bước 2 (mã TOTP hoặc mã khôi phục)")
    public AuthDtos.LoginResponse verifyTwoFactor(
            @Valid @RequestBody AuthDtos.TwoFactorRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Instant now = Instant.now();
        AuthService.IssuedTokens tokens = authService.verifyTwoFactor(
                request.challengeToken(), request.code(), request.recoveryCode(), ClientInfo.from(httpRequest), now);
        return issue(tokens, httpResponse, now);
    }

    /**
     * Đăng ký 2FA lần đầu.
     *
     * <p>Chỉ gọi được khi đang giữ vé challenge, tức là vừa nhập đúng mật khẩu. Không có ràng buộc
     * đó thì bất kỳ ai chiếm được phiên cũng tự đăng ký lại 2FA cho mình và khoá chủ nhân ra ngoài.
     */
    @PostMapping("/2fa/enroll")
    @PublicEndpoint(reason = "Đăng ký 2FA bắt buộc ngay sau bước mật khẩu, chưa có access token")
    @Operation(summary = "Sinh secret 2FA + mã khôi phục (hiển thị đúng một lần)")
    public AuthDtos.EnrollResponse enrollTwoFactor(@Valid @RequestBody AuthDtos.EnrollRequest request) {
        TotpService.Enrollment enrollment =
                authService.enrollTwoFactor(request.challengeToken(), jwtProperties.getIssuer(), Instant.now());
        return new AuthDtos.EnrollResponse(enrollment.secret(), enrollment.otpauthUri(), enrollment.recoveryCodes());
    }

    @PostMapping("/2fa/confirm")
    @PublicEndpoint(reason = "Hoàn tất đăng ký 2FA trong cùng luồng đăng nhập")
    @Operation(summary = "Xác nhận đăng ký 2FA bằng mã đầu tiên rồi phát token")
    public AuthDtos.LoginResponse confirmTwoFactorEnrollment(
            @Valid @RequestBody AuthDtos.ConfirmEnrollRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        Instant now = Instant.now();
        AuthService.IssuedTokens tokens = authService.confirmTwoFactorEnrollment(
                request.challengeToken(), request.code(), ClientInfo.from(httpRequest), now);
        return issue(tokens, httpResponse, now);
    }

    // =========================================================================
    // Làm mới, đăng xuất
    // =========================================================================

    /**
     * Làm mới token.
     *
     * <p>Công khai vì access token lúc này thường đã hết hạn — đó chính là lý do FE gọi tới. Danh
     * tính lấy từ cookie refresh, và cookie đó vẫn phải qua kiểm CSRF (endpoint này KHÔNG nằm trong
     * danh sách miễn của {@code CsrfFilter}).
     */
    @PostMapping("/refresh")
    @PublicEndpoint(reason = "Access token đã hết hạn; danh tính lấy từ cookie refresh + CSRF")
    @Operation(summary = "Làm mới access token (xoay vòng refresh token)")
    public AuthDtos.LoginResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = CsrfTokens.readCookie(httpRequest, CsrfTokens.REFRESH_COOKIE);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationException(ErrorCode.AUTH_0002);
        }
        Instant now = Instant.now();
        return issue(authService.refresh(refreshToken, ClientInfo.from(httpRequest), now), httpResponse, now);
    }

    @PostMapping("/logout")
    @AuthenticatedEndpoint(reason = "Ai cũng được đăng xuất chính mình")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đăng xuất — thu hồi cả token family của phiên hiện tại")
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(AuthContext.require(), ClientInfo.from(httpRequest), Instant.now());
        CsrfTokens.clear(httpResponse, secureCookie);
    }

    // =========================================================================
    // Tài khoản của chính mình
    // =========================================================================

    @GetMapping("/me")
    @AuthenticatedEndpoint(reason = "Hồ sơ của chính người đang đăng nhập")
    @Operation(summary = "Thông tin người đang đăng nhập kèm danh sách quyền")
    public AuthDtos.MeResponse me() {
        AuthService.MeView view = authService.me(AuthContext.require());
        AuthenticatedUser current = view.user();
        return new AuthDtos.MeResponse(
                current.publicId(),
                current.username(),
                current.fullName(),
                current.orgUnitId(),
                current.roles(),
                current.permissions(),
                current.mustChangePassword(),
                view.twoFactorEnrolled());
    }

    @PostMapping("/change-password")
    @AuthenticatedEndpoint(reason = "Ai cũng được đổi mật khẩu của chính mình")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đổi mật khẩu — thu hồi toàn bộ phiên, phải đăng nhập lại")
    public void changePassword(
            @Valid @RequestBody AuthDtos.ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        passwordChangeService.changePassword(
                AuthContext.require(),
                request.currentPassword(),
                request.newPassword(),
                ClientInfo.from(httpRequest),
                Instant.now());
        CsrfTokens.clear(httpResponse, secureCookie);
    }

    // =========================================================================
    // Quản lý phiên — M5.14
    // =========================================================================

    @GetMapping("/sessions")
    @AuthenticatedEndpoint(reason = "Chỉ liệt kê phiên của chính người đang đăng nhập")
    @Operation(summary = "Danh sách thiết bị đang đăng nhập")
    public List<AuthDtos.SessionResponse> sessions() {
        return sessionService.listOwnSessions(AuthContext.require(), Instant.now()).stream()
                .map(s -> new AuthDtos.SessionResponse(
                        s.publicId(),
                        s.deviceLabel(),
                        s.ipAddress(),
                        s.issuedAt(),
                        s.lastUsedAt(),
                        s.expiresAt(),
                        s.current()))
                .toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    @AuthenticatedEndpoint(reason = "Chỉ thu hồi được phiên của chính mình — đối chiếu chủ sở hữu ở service")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đăng xuất từ xa một thiết bị")
    public void revokeSession(@PathVariable UUID sessionId, HttpServletRequest httpRequest) {
        sessionService.revokeOwnSession(AuthContext.require(), sessionId, ClientInfo.from(httpRequest), Instant.now());
    }

    // =========================================================================

    /** Đặt cookie refresh + CSRF rồi dựng response chung cho mọi lối đăng nhập. */
    private AuthDtos.LoginResponse issue(AuthService.IssuedTokens tokens, HttpServletResponse response, Instant now) {

        long refreshMaxAge = jwtProperties.getRefreshTokenTtl().toSeconds();
        CsrfTokens.setRefreshCookie(response, tokens.refreshToken().rawToken(), refreshMaxAge, secureCookie);
        String csrfToken = CsrfTokens.issue(response, secureCookie, refreshMaxAge);

        return new AuthDtos.LoginResponse(
                AuthService.LoginOutcome.Stage.AUTHENTICATED.name(),
                tokens.accessToken(),
                now.plus(jwtProperties.getAccessTokenTtl()),
                csrfToken,
                null,
                tokens.mustChangePassword());
    }
}
