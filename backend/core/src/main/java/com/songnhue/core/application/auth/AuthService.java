package com.songnhue.core.application.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.identity.SessionRevokeReason;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Luồng xác thực: đăng nhập, xác thực hai bước, làm mới token, đăng xuất.
 *
 * <p><b>Nguyên tắc xuyên suốt: không tiết lộ tài khoản có tồn tại hay không</b> (§4.1). Sai tên,
 * sai mật khẩu, tài khoản bị vô hiệu hoá, tài khoản chưa kích hoạt — tất cả đều trả về đúng một câu
 * {@code AUTH-0001}, và đều tiêu tốn xấp xỉ cùng một lượng thời gian. Chỉ khoá tạm mới có mã riêng
 * ({@code AUTH-0003}), vì lúc đó việc giấu là vô nghĩa: người dùng thật cần biết mình phải chờ.
 *
 * <p>Hai việc cố ý <b>không</b> nằm ở đây, dù cùng thuộc nhóm xác thực: đổi mật khẩu
 * ({@link PasswordChangeService}) và phát hiện đăng nhập bất thường ({@link AbnormalLoginDetector}).
 * Cả hai đều là mối quan tâm riêng và sẽ còn lớn thêm; gộp vào thì lớp này thành nơi mọi thay đổi
 * đều phải đi qua — đúng chỗ nhạy cảm nhất hệ thống.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordPolicyService passwords;
    private final LoginAttemptService loginAttempts;
    private final TokenService tokens;
    private final RefreshTokenService refreshTokens;
    private final TotpService totp;
    private final SecurityEventService securityEvents;
    private final AbnormalLoginDetector abnormalLogins;

    public AuthService(
            UserRepository users,
            PasswordPolicyService passwords,
            LoginAttemptService loginAttempts,
            TokenService tokens,
            RefreshTokenService refreshTokens,
            TotpService totp,
            SecurityEventService securityEvents,
            AbnormalLoginDetector abnormalLogins) {
        this.users = users;
        this.passwords = passwords;
        this.loginAttempts = loginAttempts;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.totp = totp;
        this.securityEvents = securityEvents;
        this.abnormalLogins = abnormalLogins;
    }

    // =========================================================================
    // Bước 1 — tên đăng nhập + mật khẩu
    // =========================================================================

    /**
     * @return hoặc bộ token hoàn chỉnh, hoặc vé đi tiếp sang bước 2FA
     * @throws AuthenticationException {@code AUTH-0001} sai thông tin · {@code AUTH-0003} đang khoá tạm
     */
    @Transactional
    public LoginOutcome login(String username, String rawPassword, ClientInfo client, Instant now) {
        User user = users.findActiveByUsername(username).orElse(null);

        if (user == null) {
            // Vẫn băm một lần để thời gian phản hồi không tố cáo tài khoản này không tồn tại
            passwords.wasteTimeToHideMissingUser(rawPassword);
            loginAttempts.recordFailureForUnknownUser(username, client);
            throw new AuthenticationException(ErrorCode.AUTH_0001);
        }

        if (user.isTemporarilyLocked(now)) {
            // Cố ý KHÔNG kiểm mật khẩu ở đây: kiểm rồi thì kẻ tấn công vẫn dò được trong lúc bị khoá
            securityEvents.record(
                    SecurityEventType.LOGIN_FAILED, username, user.getId(), client, "{\"reason\":\"locked\"}");
            throw new AuthenticationException(ErrorCode.AUTH_0003);
        }

        if (!user.canAuthenticate()) {
            passwords.wasteTimeToHideMissingUser(rawPassword);
            securityEvents.record(
                    SecurityEventType.LOGIN_DISABLED_ACCOUNT,
                    username,
                    user.getId(),
                    client,
                    "{\"status\":\"" + user.getStatus() + "\"}");
            throw new AuthenticationException(ErrorCode.AUTH_0001);
        }

        if (!passwords.matches(rawPassword, user.getPasswordHash())) {
            boolean locked = loginAttempts.recordFailure(user.getId(), username, client, now);
            throw new AuthenticationException(locked ? ErrorCode.AUTH_0003 : ErrorCode.AUTH_0001);
        }

        loginAttempts.recordSuccess(user.getId(), client.ipAddress(), now);
        abnormalLogins.inspectSuccessfulLogin(user, client, now);

        if (totp.isRequiredFor(user)) {
            boolean enrolled = totp.isEnrolled(user.getId());
            return LoginOutcome.twoFactor(tokens.issueTwoFactorChallenge(user.getPublicId(), now), enrolled);
        }

        securityEvents.record(SecurityEventType.LOGIN_SUCCESS, username, user.getId(), client);
        return LoginOutcome.authenticated(issueTokens(user, client, now));
    }

    // =========================================================================
    // Bước 2 — mã 2FA
    // =========================================================================

    @Transactional
    public IssuedTokens verifyTwoFactor(
            String challengeToken, String code, boolean isRecoveryCode, ClientInfo client, Instant now) {
        User user = userFromChallenge(challengeToken, now);

        if (isRecoveryCode) {
            totp.verifyRecoveryCode(user, code, client, now);
        } else {
            totp.verifyLoginCode(user, code, client, now);
        }

        securityEvents.record(
                SecurityEventType.LOGIN_SUCCESS, user.getUsername(), user.getId(), client, "{\"twoFactor\":true}");
        return issueTokens(user, client, now);
    }

    /** Đăng ký 2FA lần đầu — chỉ cho phép khi vừa qua bước mật khẩu (giữ vé challenge). */
    @Transactional
    public TotpService.Enrollment enrollTwoFactor(String challengeToken, String issuer, Instant now) {
        return totp.enroll(userFromChallenge(challengeToken, now), issuer);
    }

    /** Xác nhận đăng ký rồi phát token luôn — người dùng không phải nhập lại mật khẩu. */
    @Transactional
    public IssuedTokens confirmTwoFactorEnrollment(String challengeToken, String code, ClientInfo client, Instant now) {
        User user = userFromChallenge(challengeToken, now);
        totp.confirmEnrollment(user, code, client, now);
        securityEvents.record(
                SecurityEventType.LOGIN_SUCCESS,
                user.getUsername(),
                user.getId(),
                client,
                "{\"twoFactor\":true,\"firstEnrollment\":true}");
        return issueTokens(user, client, now);
    }

    // =========================================================================
    // Làm mới, đăng xuất
    // =========================================================================

    @Transactional
    public IssuedTokens refresh(String presentedRefreshToken, ClientInfo client, Instant now) {
        RefreshTokenService.IssuedRefreshToken rotated = refreshTokens.rotate(presentedRefreshToken, client, now);

        User user =
                users.findById(rotated.userId()).orElseThrow(() -> new AuthenticationException(ErrorCode.AUTH_0002));
        if (!user.canAuthenticate()) {
            // Tài khoản bị khoá sau khi đã đăng nhập → cắt luôn tại lần làm mới đầu tiên
            refreshTokens.revokeAllSessions(user.getId(), SessionRevokeReason.ACCOUNT_LOCKED, now);
            throw new AuthenticationException(ErrorCode.AUTH_0002);
        }

        String accessToken = tokens.issueAccessToken(
                user.getPublicId(), user.getUsername(), rotated.familyId(), UUID.randomUUID(), now);
        return new IssuedTokens(accessToken, rotated, user.isMustChangePassword());
    }

    @Transactional
    public void logout(AuthenticatedUser current, ClientInfo client, Instant now) {
        refreshTokens.revokeFamily(current.sessionFamilyId(), SessionRevokeReason.LOGOUT, now);
        securityEvents.record(SecurityEventType.LOGOUT, current.username(), current.userId(), client);
    }

    private User userFromChallenge(String challengeToken, Instant now) {
        UUID publicId = tokens.verifyTwoFactorChallenge(challengeToken, now)
                .orElseThrow(() -> new AuthenticationException(ErrorCode.AUTH_0002));
        User user = users.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new AuthenticationException(ErrorCode.AUTH_0002));
        if (!user.canAuthenticate() || user.isTemporarilyLocked(now)) {
            throw new AuthenticationException(ErrorCode.AUTH_0001);
        }
        return user;
    }

    private IssuedTokens issueTokens(User user, ClientInfo client, Instant now) {
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.openSession(user.getId(), client, now);
        String accessToken = tokens.issueAccessToken(
                user.getPublicId(), user.getUsername(), refresh.familyId(), UUID.randomUUID(), now);
        return new IssuedTokens(accessToken, refresh, user.isMustChangePassword());
    }

    // =========================================================================

    /**
     * @param accessToken JWT gửi trong body, FE giữ trong bộ nhớ
     * @param refreshToken ⛔ CHỈ đặt vào cookie httpOnly, không bao giờ trả trong body
     */
    public record IssuedTokens(
            String accessToken, RefreshTokenService.IssuedRefreshToken refreshToken, boolean mustChangePassword) {}

    /**
     * Kết quả bước 1 — hoặc xong, hoặc phải đi tiếp sang 2FA.
     *
     * <p>Dùng một kiểu trả về có hai hình dạng thay vì ném exception cho nhánh 2FA: cần nhập mã là
     * luồng bình thường, không phải lỗi.
     *
     * @param stage {@code AUTHENTICATED} · {@code TWO_FACTOR_REQUIRED} · {@code TWO_FACTOR_ENROLL_REQUIRED}
     */
    public record LoginOutcome(Stage stage, IssuedTokens tokens, String challengeToken) {

        public enum Stage {
            AUTHENTICATED,
            TWO_FACTOR_REQUIRED,
            /** Vai trò bắt buộc 2FA nhưng chưa đăng ký — phải đăng ký ngay, không bỏ qua được. */
            TWO_FACTOR_ENROLL_REQUIRED
        }

        static LoginOutcome authenticated(IssuedTokens tokens) {
            return new LoginOutcome(Stage.AUTHENTICATED, tokens, null);
        }

        static LoginOutcome twoFactor(String challengeToken, boolean alreadyEnrolled) {
            return new LoginOutcome(
                    alreadyEnrolled ? Stage.TWO_FACTOR_REQUIRED : Stage.TWO_FACTOR_ENROLL_REQUIRED,
                    null,
                    challengeToken);
        }
    }

    /**
     * Hồ sơ người đang đăng nhập cho endpoint {@code /auth/me}.
     *
     * <p>Phần lớn dữ liệu lấy từ {@link AuthenticatedUser} đã nạp sẵn; chỉ trạng thái 2FA phải hỏi
     * thêm — FE cần nó để hiện lời nhắc bật xác thực hai bước.
     */
    @Transactional(readOnly = true)
    public MeView me(AuthenticatedUser current) {
        return new MeView(current, totp.isEnrolled(current.userId()));
    }

    public record MeView(AuthenticatedUser user, boolean twoFactorEnrolled) {}

    /** Phiên đang hoạt động của chính người dùng — màn hình M5.14. */
    public record ActiveSession(
            UUID publicId,
            String deviceLabel,
            String ipAddress,
            Instant issuedAt,
            Instant lastUsedAt,
            Instant expiresAt,
            boolean current) {}
}
