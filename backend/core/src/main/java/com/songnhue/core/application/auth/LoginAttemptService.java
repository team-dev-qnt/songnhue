package com.songnhue.core.application.auth;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.songnhue.core.application.settings.SettingKeys;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.ratelimit.RateLimitPolicy;
import com.songnhue.core.common.ratelimit.RateLimitStore;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Đếm số lần đăng nhập sai và khoá tạm tài khoản (T5.6, §4.1: sai 5 lần/15' → khoá 15').
 *
 * <p><b>Cũng chạy trong transaction riêng.</b> Cùng lý do với {@link SecurityEventService}: luồng
 * đăng nhập sai kết thúc bằng exception, transaction ngoài rollback. Nếu bộ đếm nằm chung transaction
 * đó thì nó bị xoá sạch sau mỗi lần sai — bộ đếm mãi mãi bằng 0 và <b>tính năng khoá tài khoản không
 * bao giờ kích hoạt</b>. Lỗi này im lặng tuyệt đối: hệ thống chạy đúng, chỉ là cửa mở toang.
 *
 * <p>Cửa sổ đếm trượt theo {@code last_failed_login_at}: sai 4 lần rồi nghỉ quá cửa sổ thì lần sai
 * tiếp theo tính lại từ 1. Không có cơ chế này thì một người hay gõ nhầm sẽ bị khoá sau vài tuần dùng
 * bình thường.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository users;
    private final SettingService settings;
    private final SecurityEventService securityEvents;
    private final RateLimitStore hanMuc;
    private final TransactionTemplate requiresNew;

    public LoginAttemptService(
            UserRepository users,
            SettingService settings,
            SecurityEventService securityEvents,
            RateLimitStore hanMuc,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.settings = settings;
        this.securityEvents = securityEvents;
        this.hanMuc = hanMuc;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Ghi nhận một lần đăng nhập sai; khoá tạm khi chạm ngưỡng.
     *
     * @return true nếu lần sai này khiến tài khoản bị khoá
     */
    public boolean recordFailure(Long userId, String username, ClientInfo client, Instant now) {
        int maxAttempts =
                settings.getInt(SettingKeys.LOGIN_MAX_FAILED_ATTEMPTS, SettingKeys.DEFAULT_MAX_FAILED_ATTEMPTS);
        Duration window =
                settings.getMinutes(SettingKeys.LOGIN_FAILED_WINDOW_MINUTES, SettingKeys.DEFAULT_FAILED_WINDOW_MINUTES);
        Duration lockout = settings.getMinutes(SettingKeys.LOGIN_LOCKOUT_MINUTES, SettingKeys.DEFAULT_LOCKOUT_MINUTES);

        Boolean locked = requiresNew.execute(status -> users.findById(userId)
                .map(user -> applyFailure(user, maxAttempts, window, lockout, now))
                .orElse(false));

        securityEvents.record(SecurityEventType.LOGIN_FAILED, username, userId, client);
        if (Boolean.TRUE.equals(locked)) {
            log.warn("Khoá tạm tài khoản {} do đăng nhập sai {} lần", username, maxAttempts);
            securityEvents.record(
                    SecurityEventType.LOGIN_LOCKED,
                    username,
                    userId,
                    client,
                    "{\"lockoutMinutes\":" + lockout.toMinutes() + "}");
        }
        return Boolean.TRUE.equals(locked);
    }

    /** Đăng nhập sai với tên tài khoản không tồn tại — không có gì để đếm, nhưng vẫn phải ghi vết. */
    public void recordFailureForUnknownUser(String username, ClientInfo client) {
        securityEvents.record(SecurityEventType.LOGIN_FAILED, username, null, client, "{\"unknownUser\":true}");
    }

    /**
     * Mật khẩu ĐÚNG ⇒ trả lại lượt mà {@code RateLimitFilter} đã tính vào xô {@code LOGIN} của IP này — T61.17
     * (WS-72).
     *
     * <p>⛔⛔ Trước bản vá, xô ấy (30 lượt / 15′ theo IP) đếm MỌI lượt gọi {@code /auth/login}. Chính
     * {@link RateLimitPolicy#LOGIN} khai <i>"cả Công ty ra Internet qua một IP NAT"</i>, nên người thứ 31 đăng nhập
     * ĐÚNG trong 15 phút đầu giờ nhận 429 tới hết cửa sổ. Tức là lưới chống dò mật khẩu khoá cả cơ quan.
     *
     * <h2>Vì sao TRẢ LẠI chứ ⛔ để bộ lọc chỉ đọc rồi đếm lượt sai ở đây</h2>
     *
     * Bộ lọc tăng bộ đếm NGUYÊN TỬ ({@code compute}) trước khi cho đi, nên số lượt ⛔ đúng mật khẩu lọt qua trong
     * một cửa sổ ⛔ bao giờ vượt trần, kể cả khi hàng trăm lượt tới cùng lúc. Cách "đọc trước, đếm sau khi sai" để
     * mọi lượt đang băm BCrypt cùng qua cửa (vì chưa lượt nào kịp bị đếm), nên trần thật là {@code trần + số lượt
     * đồng thời}.
     *
     * <p>Giá phải trả: một lượt đúng đang xử lý vẫn chiếm một chỗ cho tới khi trả lại. Muốn 30 lượt đúng cùng băm
     * BCrypt trong một khoảnh khắc từ một IP thì ⛔ phải là người dùng thật.
     *
     * <p>Khoá phải trùng từng ký tự với khoá bộ lọc đã dùng: {@link ClientInfo#from} và bộ lọc cùng đọc
     * {@code ClientIp.cua(request)}. Lệch khoá thì lượt trả lại rơi vào một khoá vắng và ⛔ làm gì
     * ({@code DangNhapSauNatHttpTest} đỏ ở lượt 31).
     */
    public void hoanLuotDangNhapDung(ClientInfo client) {
        hanMuc.hoanLai(RateLimitPolicy.LOGIN.key(client.ipAddress()));
    }

    /** Đăng nhập thành công → xoá bộ đếm, mở khoá tạm nếu còn. */
    public void recordSuccess(Long userId, String ipAddress, Instant now) {
        requiresNew.executeWithoutResult(status -> users.findById(userId).ifPresent(user -> {
            user.setFailedLoginCount((short) 0);
            user.setLockedUntil(null);
            user.setLastLoginAt(now);
            user.setLastLoginIp(ipAddress);
            users.save(user);
        }));
    }

    // -------------------------------------------------------------------------

    private boolean applyFailure(User user, int maxAttempts, Duration window, Duration lockout, Instant now) {
        Instant lastFailure = user.getLastFailedLoginAt();
        boolean withinWindow = lastFailure != null && lastFailure.isAfter(now.minus(window));

        short count = withinWindow ? (short) (user.getFailedLoginCount() + 1) : (short) 1;
        user.setFailedLoginCount(count);
        user.setLastFailedLoginAt(now);

        boolean locked = count >= maxAttempts;
        if (locked) {
            user.setLockedUntil(now.plus(lockout));
        }
        users.save(user);
        return locked;
    }
}
