package com.songnhue.core.application.auth;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.core.common.config.JwtProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.util.HashUtils;
import com.songnhue.core.domain.identity.SessionRevokeReason;
import com.songnhue.core.domain.identity.UserSession;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserSessionRepository;

/**
 * Vòng đời refresh token: phát hành, <b>xoay vòng</b>, và phát hiện dùng lại (T5.2, T5.3).
 *
 * <p><b>Vì sao phải xoay vòng.</b> Refresh token sống nhiều ngày. Nếu mỗi lần làm mới vẫn dùng lại
 * đúng chuỗi cũ thì kẻ trộm được nó cứ thế duy trì quyền truy cập vô thời hạn mà không để lại dấu
 * vết nào. Xoay vòng biến mỗi lần làm mới thành một token mới và giết token cũ.
 *
 * <p><b>Vì sao xoay vòng thôi chưa đủ.</b> Kẻ trộm vẫn có thể dùng token trước chủ nhân. Lúc đó chủ
 * nhân sẽ trình ra một token <i>đã bị xoay</i> — và đó chính là tín hiệu. Hệ thống không có cách nào
 * biết ai là kẻ trộm trong hai bên, nên xử lý duy nhất đúng là <b>thu hồi cả family</b>: cả hai phải
 * đăng nhập lại, và chỉ người biết mật khẩu mới vào được.
 *
 * <p>Chuỗi trả về cho trình duyệt là ngẫu nhiên 256 bit, DB chỉ giữ SHA-256 của nó.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 32 byte = 256 bit entropy. Dò cạn là bất khả thi, nên không cần thêm rate limit riêng. */
    private static final int TOKEN_BYTES = 32;

    private final UserSessionRepository sessions;
    private final SecurityEventService securityEvents;
    private final JwtProperties properties;

    /**
     * Dùng để chạy việc thu hồi trong transaction riêng.
     *
     * <p>Cố ý KHÔNG dùng {@code @Transactional(REQUIRES_NEW)} trên một phương thức của chính lớp
     * này: Spring hiện thực {@code @Transactional} bằng proxy, mà lời gọi từ một phương thức sang
     * phương thức khác <i>trong cùng đối tượng</i> thì không đi qua proxy — annotation sẽ bị bỏ qua
     * hoàn toàn, lặng lẽ, và cơ chế phát hiện token bị đánh cắp mất tác dụng mà test thường không
     * bắt được. {@link TransactionTemplate} không có cái bẫy đó.
     */
    private final TransactionTemplate requiresNew;

    public RefreshTokenService(
            UserSessionRepository sessions,
            SecurityEventService securityEvents,
            JwtProperties properties,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.securityEvents = securityEvents;
        this.properties = properties;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Mở một family mới — tương ứng một lần đăng nhập.
     *
     * @return chuỗi token gốc để đặt vào cookie; sau lời gọi này không nơi nào đọc lại được nữa
     */
    @Transactional
    public IssuedRefreshToken openSession(Long userId, ClientInfo client, Instant now) {
        UUID familyId = UUID.randomUUID();
        return append(userId, familyId, null, client, now);
    }

    /**
     * Đổi refresh token cũ lấy token mới.
     *
     * @throws AuthenticationException {@code AUTH-0008} khi phát hiện dùng lại token đã xoay (đã thu
     *     hồi cả family trước khi ném), {@code AUTH-0002} khi token không tồn tại hoặc đã hết hạn
     */
    @Transactional
    public IssuedRefreshToken rotate(String presentedToken, ClientInfo client, Instant now) {
        String hash = HashUtils.sha256Hex(presentedToken);
        UserSession current = sessions.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new AuthenticationException(ErrorCode.AUTH_0002));

        if (current.isReuseOfRotatedToken()) {
            handleReuse(current, client, now);
        }
        if (!current.isActive(now)) {
            // Đã thu hồi vì lý do khác (đăng xuất, đổi mật khẩu) hoặc quá hạn — không phải tấn công
            throw new AuthenticationException(ErrorCode.AUTH_0002);
        }

        current.markRotated(now);
        current.setLastUsedAt(now);
        sessions.save(current);

        return append(current.getUserId(), current.getFamilyId(), current.getId(), client, now);
    }

    /**
     * Xử lý dấu hiệu token bị đánh cắp.
     *
     * <p>Việc thu hồi chạy trong transaction <b>riêng</b>: ngay sau đây sẽ ném exception, mà
     * transaction bên ngoài lúc đó bị rollback — nếu việc thu hồi nằm chung transaction ấy thì nó
     * cũng bị cuốn theo, và cơ chế phát hiện trở thành vô dụng đúng vào lúc cần nhất.
     */
    private void handleReuse(UserSession current, ClientInfo client, Instant now) {
        Integer revoked = requiresNew.execute(
                status -> sessions.revokeFamily(current.getFamilyId(), SessionRevokeReason.REUSE_DETECTED.name(), now));

        log.error(
                "Phát hiện dùng lại refresh token đã xoay — thu hồi {} phiên của user {}",
                revoked,
                current.getUserId());
        securityEvents.record(
                SecurityEventType.REFRESH_REUSE_DETECTED,
                null,
                current.getUserId(),
                client,
                "{\"familyId\":\"" + current.getFamilyId() + "\",\"revokedSessions\":" + revoked + "}");

        throw new AuthenticationException(ErrorCode.AUTH_0008);
    }

    @Transactional
    public void revokeFamily(UUID familyId, SessionRevokeReason reason, Instant now) {
        sessions.revokeFamily(familyId, reason.name(), now);
    }

    /** Đổi mật khẩu / khoá tài khoản → mọi thiết bị phải đăng nhập lại (§4.1). */
    @Transactional
    public int revokeAllSessions(Long userId, SessionRevokeReason reason, Instant now) {
        return sessions.revokeAllOfUser(userId, reason.name(), now);
    }

    // -------------------------------------------------------------------------

    private IssuedRefreshToken append(
            Long userId, UUID familyId, Long parentSessionId, ClientInfo client, Instant now) {
        String rawToken = HashUtils.randomToken(TOKEN_BYTES);

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setFamilyId(familyId);
        session.setRefreshTokenHash(HashUtils.sha256Hex(rawToken));
        session.setParentSessionId(parentSessionId);
        session.setExpiresAt(now.plus(properties.getRefreshTokenTtl()));
        session.setIpAddress(client.ipAddress());
        session.setUserAgent(client.userAgent());
        session.setDeviceLabel(client.deviceLabel());
        sessions.save(session);

        return new IssuedRefreshToken(rawToken, userId, familyId, session.getPublicId(), session.getExpiresAt());
    }

    /**
     * @param rawToken chuỗi gốc — chỉ đi vào cookie httpOnly, ⛔ cấm ghi log, cấm trả trong body
     * @param userId chủ nhân của phiên, lấy từ bản ghi trong DB chứ không từ dữ liệu client gửi lên
     */
    public record IssuedRefreshToken(
            String rawToken, Long userId, UUID familyId, UUID sessionPublicId, Instant expiresAt) {}
}
