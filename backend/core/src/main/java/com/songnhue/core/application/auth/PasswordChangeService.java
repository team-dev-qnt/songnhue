package com.songnhue.core.application.auth;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.identity.SessionRevokeReason;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Đổi mật khẩu — tách khỏi {@link AuthService} vì là một tình huống sử dụng riêng, có bộ phụ thuộc
 * riêng và những ràng buộc riêng.
 *
 * <p><b>Thu hồi toàn bộ phiên, kể cả phiên đang thao tác</b> (§4.1). Có hơi phiền — người dùng phải
 * đăng nhập lại ngay sau khi đổi. Nhưng người ta đổi mật khẩu chủ yếu vì <i>nghi bị lộ</i>, và lúc
 * đó việc quan trọng nhất là đá kẻ kia ra khỏi mọi thiết bị. Giữ lại phiên hiện tại thì cũng phải
 * trả lời được câu "làm sao biết phiên hiện tại là của chủ nhân chứ không phải của kẻ trộm".
 */
@Service
public class PasswordChangeService {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

    private final UserRepository users;
    private final PasswordPolicyService passwords;
    private final RefreshTokenService refreshTokens;
    private final AuthorityLoader authorityLoader;
    private final SecurityEventService securityEvents;

    public PasswordChangeService(
            UserRepository users,
            PasswordPolicyService passwords,
            RefreshTokenService refreshTokens,
            AuthorityLoader authorityLoader,
            SecurityEventService securityEvents) {
        this.users = users;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.authorityLoader = authorityLoader;
        this.securityEvents = securityEvents;
    }

    /**
     * @throws AuthenticationException {@code AUTH-0001} khi mật khẩu hiện tại sai
     * @throws BusinessRuleException {@code AUTH-0006} khi mật khẩu mới không đạt chính sách hoặc
     *     trùng mật khẩu cũ
     */
    @Transactional
    public void changePassword(
            AuthenticatedUser current, String currentPassword, String newPassword, ClientInfo client, Instant now) {

        User user =
                users.findById(current.userId()).orElseThrow(() -> new AuthenticationException(ErrorCode.AUTH_0002));

        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            // Bắt buộc nhập lại mật khẩu cũ: nếu không thì ai mượn được máy đang mở phiên là đổi
            // được mật khẩu và chiếm hẳn tài khoản
            securityEvents.record(
                    SecurityEventType.LOGIN_FAILED,
                    user.getUsername(),
                    user.getId(),
                    client,
                    "{\"context\":\"change-password\"}");
            throw new AuthenticationException(ErrorCode.AUTH_0001);
        }
        if (passwords.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessRuleException(ErrorCode.AUTH_0006)
                    .withDetail("newPassword", "MUST_DIFFER_FROM_CURRENT", null);
        }
        passwords.validate(newPassword, user.getUsername());

        user.setPasswordHash(passwords.hash(newPassword));
        user.setPasswordChangedAt(now);
        user.setMustChangePassword(false);
        users.save(user);

        int revoked = refreshTokens.revokeAllSessions(user.getId(), SessionRevokeReason.PASSWORD_CHANGED, now);
        // Cờ must_change_password vừa đổi → xoá cache để không còn ai bị chặn oan bởi giá trị cũ
        authorityLoader.invalidate(user.getPublicId());

        log.info("Tài khoản {} đổi mật khẩu, thu hồi {} phiên", user.getUsername(), revoked);
        securityEvents.record(
                SecurityEventType.PASSWORD_CHANGED,
                user.getUsername(),
                user.getId(),
                client,
                "{\"revokedSessions\":" + revoked + "}");
    }
}
