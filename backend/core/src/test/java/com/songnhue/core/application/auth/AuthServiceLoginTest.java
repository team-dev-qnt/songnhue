package com.songnhue.core.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.config.JwtProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AppException;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.security.JwtKeyStore;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.identity.UserStatus;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.testsupport.RsaKeyPairFixture;

/**
 * Luồng đăng nhập — trọng tâm là <b>những gì hệ thống KHÔNG được tiết lộ</b> (§4.1).
 *
 * <p>Sai tên tài khoản, sai mật khẩu, tài khoản đã vô hiệu hoá — cả ba đều phải trả về đúng một câu
 * {@code AUTH-0001}. Nếu phân biệt được ba trường hợp này thì kẻ tấn công dựng được danh sách tài
 * khoản có thật của Công ty trước khi bắt đầu dò mật khẩu, và bước dò sau đó rẻ hơn hẳn.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @TempDir
    Path keyDir;

    @Mock
    private UserRepository users;

    @Mock
    private LoginAttemptService loginAttempts;

    @Mock
    private RefreshTokenService refreshTokens;

    @Mock
    private TotpService totp;

    @Mock
    private SecurityEventService securityEvents;

    @Mock
    private SettingService settings;

    private AuthService authService;
    private PasswordPolicyService passwords;

    /** 10:00 giờ Việt Nam — trong giờ hành chính, để không lẫn với cảnh báo M5.16. */
    private final Instant now = Instant.parse("2026-08-14T03:00:00Z");

    private final ClientInfo client = new ClientInfo("10.0.0.5", "JUnit", "Test");

    @BeforeEach
    void setUp() {
        passwords = new PasswordPolicyService(settings);
        JwtProperties jwtProperties = RsaKeyPairFixture.propertiesFor(keyDir, "v1");
        TokenService tokens = new TokenService(new JwtKeyStore(jwtProperties), jwtProperties);

        lenient().when(settings.getTime(anyString(), any())).thenAnswer(i -> i.getArgument(1));

        authService = new AuthService(
                users,
                passwords,
                loginAttempts,
                tokens,
                refreshTokens,
                totp,
                securityEvents,
                new AbnormalLoginDetector(settings, securityEvents));
    }

    @Nested
    @DisplayName("Không tiết lộ tài khoản có tồn tại hay không")
    class NoUserEnumeration {

        @Test
        @DisplayName("Tên tài khoản không tồn tại → AUTH-0001 (không phải 'không tìm thấy')")
        void unknownUsername() {
            when(users.findActiveByUsername("khong-co")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("khong-co", "MatKhau123", client, now))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_0001);

            verify(loginAttempts).recordFailureForUnknownUser("khong-co", client);
        }

        @Test
        @DisplayName("Sai mật khẩu → cũng AUTH-0001, giống hệt trường hợp trên")
        void wrongPassword() {
            User user = activeUser("nva", "MatKhauDung123");
            when(users.findActiveByUsername("nva")).thenReturn(Optional.of(user));
            when(loginAttempts.recordFailure(eq(1L), eq("nva"), eq(client), eq(now)))
                    .thenReturn(false);

            assertThatThrownBy(() -> authService.login("nva", "MatKhauSai123", client, now))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_0001);
        }

        @Test
        @DisplayName("Tài khoản DISABLED → cũng AUTH-0001, và vẫn tốn thời gian băm mật khẩu")
        void disabledAccount() {
            User user = activeUser("nva", "MatKhauDung123");
            user.setStatus(UserStatus.DISABLED);
            when(users.findActiveByUsername("nva")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login("nva", "MatKhauDung123", client, now))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_0001);

            // Sự kiện thì ghi rõ lý do — người quản trị cần biết, kẻ tấn công thì không
            verify(securityEvents)
                    .record(eq(SecurityEventType.LOGIN_DISABLED_ACCOUNT), eq("nva"), eq(1L), eq(client), anyString());
        }

        @Test
        @DisplayName("Tài khoản chưa kích hoạt (password_hash = '!') → AUTH-0001")
        void pendingActivationAccount() {
            User user = activeUser("superadmin", "khong-quan-trong");
            user.setPasswordHash(User.NO_PASSWORD);
            user.setStatus(UserStatus.PENDING_ACTIVATION);
            when(users.findActiveByUsername("superadmin")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login("superadmin", "!", client, now))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_0001);
        }
    }

    @Nested
    @DisplayName("Khoá tạm khi đăng nhập sai nhiều lần")
    class Lockout {

        @Test
        @DisplayName("Đang trong thời gian khoá → AUTH-0003 và KHÔNG kiểm mật khẩu nữa")
        void lockedAccountIsRejectedBeforePasswordCheck() {
            User user = activeUser("nva", "MatKhauDung123");
            user.setLockedUntil(now.plusSeconds(600));
            when(users.findActiveByUsername("nva")).thenReturn(Optional.of(user));

            // Ngay cả mật khẩu ĐÚNG cũng bị chặn — nếu vẫn kiểm thì kẻ tấn công dò tiếp được
            // trong lúc bị khoá, và cơ chế khoá chỉ còn là hình thức
            assertThatThrownBy(() -> authService.login("nva", "MatKhauDung123", client, now))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_0003);

            verify(refreshTokens, never()).openSession(any(), any(), any());
        }

        @Test
        @DisplayName("Lần sai chạm ngưỡng → đổi sang AUTH-0003 để người dùng thật biết phải chờ")
        void thresholdReachedSwitchesErrorCode() {
            User user = activeUser("nva", "MatKhauDung123");
            when(users.findActiveByUsername("nva")).thenReturn(Optional.of(user));
            when(loginAttempts.recordFailure(eq(1L), eq("nva"), eq(client), eq(now)))
                    .thenReturn(true);

            assertThatThrownBy(() -> authService.login("nva", "SaiRoi123", client, now))
                    .isInstanceOf(AuthenticationException.class)
                    .extracting(e -> ((AppException) e).errorCode())
                    .isEqualTo(ErrorCode.AUTH_0003);
        }
    }

    @Nested
    @DisplayName("Đăng nhập thành công")
    class Success {

        @Test
        @DisplayName("Tài khoản thường → phát token ngay")
        void issuesTokensForRegularUser() {
            User user = activeUser("nva", "MatKhauDung123");
            when(users.findActiveByUsername("nva")).thenReturn(Optional.of(user));
            when(totp.isRequiredFor(any())).thenReturn(false);
            when(refreshTokens.openSession(eq(1L), eq(client), eq(now)))
                    .thenReturn(new RefreshTokenService.IssuedRefreshToken(
                            "raw-refresh", 1L, UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(3600)));

            AuthService.LoginOutcome outcome = authService.login("nva", "MatKhauDung123", client, now);

            assertThat(outcome.stage()).isEqualTo(AuthService.LoginOutcome.Stage.AUTHENTICATED);
            assertThat(outcome.tokens().accessToken()).isNotBlank();
            verify(loginAttempts).recordSuccess(1L, "10.0.0.5", now);
        }

        @Test
        @DisplayName("Vai trò ADMIN → dừng ở bước 2FA, KHÔNG phát access token")
        void adminMustPassTwoFactor() {
            User user = activeUser("admin", "MatKhauDung123");
            when(users.findActiveByUsername("admin")).thenReturn(Optional.of(user));
            when(totp.isRequiredFor(any())).thenReturn(true);
            when(totp.isEnrolled(1L)).thenReturn(true);

            AuthService.LoginOutcome outcome = authService.login("admin", "MatKhauDung123", client, now);

            assertThat(outcome.stage()).isEqualTo(AuthService.LoginOutcome.Stage.TWO_FACTOR_REQUIRED);
            assertThat(outcome.challengeToken()).isNotBlank();
            assertThat(outcome.tokens()).isNull();
            // Đây là điểm mấu chốt: qua mật khẩu chưa phải là đã đăng nhập
            verify(refreshTokens, never()).openSession(any(), any(), any());
        }

        @Test
        @DisplayName("ADMIN chưa đăng ký 2FA → buộc đăng ký, không cho bỏ qua")
        void adminWithoutEnrollmentMustEnroll() {
            User user = activeUser("admin", "MatKhauDung123");
            when(users.findActiveByUsername("admin")).thenReturn(Optional.of(user));
            when(totp.isRequiredFor(any())).thenReturn(true);
            when(totp.isEnrolled(1L)).thenReturn(false);

            AuthService.LoginOutcome outcome = authService.login("admin", "MatKhauDung123", client, now);

            assertThat(outcome.stage()).isEqualTo(AuthService.LoginOutcome.Stage.TWO_FACTOR_ENROLL_REQUIRED);
            verify(refreshTokens, never()).openSession(any(), any(), any());
        }

        @Test
        @DisplayName("Đăng nhập ngoài giờ hành chính → ghi sự kiện cảnh báo (M5.16)")
        void logsLoginOutsideOfficeHours() {
            User user = activeUser("nva", "MatKhauDung123");
            when(users.findActiveByUsername("nva")).thenReturn(Optional.of(user));
            when(totp.isRequiredFor(any())).thenReturn(false);
            when(refreshTokens.openSession(any(), any(), any()))
                    .thenReturn(new RefreshTokenService.IssuedRefreshToken(
                            "raw", 1L, UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(3600)));

            // 22:30 giờ Việt Nam = 15:30 UTC — đúng loại thời điểm mà M5.16 muốn Admin nhìn thấy
            Instant lateNight = Instant.parse("2026-08-14T15:30:00Z");

            authService.login("nva", "MatKhauDung123", client, lateNight);

            verify(securityEvents)
                    .record(
                            eq(SecurityEventType.LOGIN_OUTSIDE_OFFICE_HOURS),
                            eq("nva"),
                            eq(1L),
                            eq(client),
                            anyString());
        }
    }

    // -------------------------------------------------------------------------

    private User activeUser(String username, String rawPassword) {
        User user = new User() {
            @Override
            public Long getId() {
                return 1L;
            }
        };
        user.setUsername(username);
        user.setFullName("Người dùng thử");
        user.setPasswordHash(passwords.hash(rawPassword));
        user.setOrgUnitId(1L);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
