package com.songnhue.core.application.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.config.CryptoProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.util.CryptoService;
import com.songnhue.core.common.util.HashUtils;
import com.songnhue.core.common.util.TotpGenerator;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.identity.UserRecoveryCode;
import com.songnhue.core.domain.identity.UserTotp;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserAuthorityRepository;
import com.songnhue.core.infra.identity.UserRecoveryCodeRepository;
import com.songnhue.core.infra.identity.UserTotpRepository;

/**
 * Xác thực hai bước bằng TOTP — bắt buộc với Admin / Admin HR (T5.8, chốt G12).
 *
 * <p>Ba điểm đáng chú ý:
 *
 * <ul>
 *   <li><b>Secret mã hoá, không băm.</b> Khác mật khẩu, máy chủ phải đọc lại được secret để tự tính
 *       mã đối chiếu. Nên lớp bảo vệ duy nhất là AES-256-GCM với khoá nằm ngoài DB — lộ bản sao lưu
 *       cơ sở dữ liệu vẫn không dựng lại được mã 2FA của ai.
 *   <li><b>Một mã dùng đúng một lần.</b> Mã sống 30–90 giây, thừa thời gian cho kẻ bắt được request
 *       gửi lại lần nữa. Ghi lại bước thời gian đã dùng và từ chối bước ≤ nó là bịt hẳn.
 *   <li><b>Mã QR do FE vẽ.</b> Máy chủ chỉ trả chuỗi {@code otpauth://}. Sinh ảnh QR ở máy chủ nghĩa
 *       là secret đi qua thêm một lớp (bộ nhớ đệm ảnh, proxy, log truy cập) — thêm đúng một thư viện
 *       để tăng thêm chỗ rò rỉ.
 * </ul>
 */
@Service
public class TotpService {

    private static final Logger log = LoggerFactory.getLogger(TotpService.class);

    /** 160 bit — đúng độ dài khoá HMAC-SHA1 khuyến nghị ở RFC 4226 §4. */
    private static final int SECRET_BYTES = 20;

    /** Cho lệch 1 bước mỗi phía (§5.2 RFC 6238) — bù sai lệch đồng hồ điện thoại. */
    private static final int ALLOWED_DRIFT_STEPS = 1;

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_BYTES = 8;

    private final UserTotpRepository totpRepository;
    private final UserRecoveryCodeRepository recoveryCodes;
    private final UserAuthorityRepository authorities;
    private final CryptoService crypto;
    private final CryptoProperties cryptoProperties;
    private final SecurityEventService securityEvents;

    public TotpService(
            UserTotpRepository totpRepository,
            UserRecoveryCodeRepository recoveryCodes,
            UserAuthorityRepository authorities,
            CryptoService crypto,
            CryptoProperties cryptoProperties,
            SecurityEventService securityEvents) {
        this.totpRepository = totpRepository;
        this.recoveryCodes = recoveryCodes;
        this.authorities = authorities;
        this.crypto = crypto;
        this.cryptoProperties = cryptoProperties;
        this.securityEvents = securityEvents;
    }

    public boolean isEnrolled(Long userId) {
        return totpRepository.findByUserId(userId).filter(UserTotp::isConfirmed).isPresent();
    }

    /**
     * Tài khoản này có bắt buộc 2FA không.
     *
     * <p>Hai nguồn, lấy hợp: vai trò nằm trong danh sách chốt ở G12 (Super Admin / Admin / Admin HR),
     * hoặc cờ riêng của tài khoản. Thêm một vai trò quản trị mới về sau chỉ cần bổ sung vào danh sách
     * chứ không phải đi bật cờ cho từng người — mà bỏ sót một người ở bước đó thì đúng tài khoản có
     * quyền cao nhất lại là tài khoản không có lớp bảo vệ thứ hai.
     */
    @Transactional(readOnly = true)
    public boolean isRequiredFor(User user) {
        if (user.isTwoFactorRequired()) {
            return true;
        }
        return authorities.findRoleCodes(user.getId()).stream()
                .anyMatch(AuthenticatedUser.TWO_FACTOR_REQUIRED_ROLES::contains);
    }

    /**
     * Sinh secret mới và bộ mã khôi phục.
     *
     * <p>Đăng ký lại khi đã có (đổi điện thoại) sẽ <b>xoá bản cũ</b> — kể cả bản đã xác nhận. Vì thế
     * lối vào chức năng này phải được canh: hoặc người dùng vừa nhập đúng mật khẩu, hoặc đang trong
     * phiên đã đăng nhập. Nếu không, ai chiếm được phiên sẽ tự đăng ký lại 2FA cho mình.
     *
     * @return secret dạng Base32 + chuỗi otpauth + mã khôi phục — <b>lần duy nhất</b> những giá trị
     *     này rời khỏi máy chủ
     */
    @Transactional
    public Enrollment enroll(User user, String issuer) {
        byte[] secret = HashUtils.randomBytes(SECRET_BYTES);
        String base32Secret = new Base32().encodeToString(secret).replace("=", "");

        totpRepository.findByUserId(user.getId()).ifPresent(totpRepository::delete);
        recoveryCodes.deleteByUserId(user.getId());
        totpRepository.flush();

        UserTotp entry = new UserTotp();
        entry.setUserId(user.getId());
        entry.setSecretEncrypted(crypto.encrypt(base32Secret));
        entry.setKeyId(cryptoProperties.activeKeyId());
        entry.setCreatedBy(user.getId());
        totpRepository.save(entry);

        List<String> plainCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = HashUtils.randomToken(RECOVERY_CODE_BYTES);
            plainCodes.add(code);
            recoveryCodes.save(new UserRecoveryCode(user.getId(), HashUtils.sha256Hex(code)));
        }

        return new Enrollment(base32Secret, otpauthUri(issuer, user.getUsername(), base32Secret), plainCodes);
    }

    /**
     * Xác nhận đăng ký: người dùng nhập mã đầu tiên từ ứng dụng xác thực.
     *
     * <p>Bắt buộc có bước này — nếu bật 2FA ngay lúc sinh secret mà người dùng quét QR hỏng thì họ bị
     * khoá ngoài chính tài khoản của mình.
     */
    @Transactional
    public void confirmEnrollment(User user, String code, ClientInfo client, Instant now) {
        UserTotp entry = totpRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new AuthenticationException(ErrorCode.AUTH_0004));

        long step = verifyOrThrow(entry, code, user, client, now);
        entry.setLastUsedStep(step);
        entry.setConfirmedAt(now);
        entry.setUpdatedAt(now);
        entry.setUpdatedBy(user.getId());
        totpRepository.save(entry);

        securityEvents.record(SecurityEventType.TWO_FACTOR_ENROLLED, user.getUsername(), user.getId(), client);
    }

    /**
     * Kiểm mã ở bước 2 của đăng nhập.
     *
     * @throws AuthenticationException {@code AUTH-0004} khi mã sai, hết hạn, hoặc đã dùng rồi
     */
    @Transactional
    public void verifyLoginCode(User user, String code, ClientInfo client, Instant now) {
        UserTotp entry = totpRepository
                .findByUserId(user.getId())
                .filter(UserTotp::isConfirmed)
                .orElseThrow(() -> new AuthenticationException(ErrorCode.AUTH_0004));

        long step = verifyOrThrow(entry, code, user, client, now);
        entry.setLastUsedStep(step);
        entry.setUpdatedAt(now);
        totpRepository.save(entry);
    }

    /**
     * Dùng mã khôi phục thay mã TOTP (mất điện thoại).
     *
     * <p>Ghi sự kiện mức DANGER: người dùng bình thường hầu như không bao giờ dùng tới mã khôi phục,
     * nên mỗi lần dùng đều đáng để Admin nhìn qua.
     */
    @Transactional
    public void verifyRecoveryCode(User user, String code, ClientInfo client, Instant now) {
        UserRecoveryCode entry = recoveryCodes
                .findByCodeHashAndUsedAtIsNull(HashUtils.sha256Hex(code == null ? "" : code.trim()))
                .filter(rc -> rc.getUserId().equals(user.getId()))
                .orElseThrow(() -> {
                    securityEvents.record(
                            SecurityEventType.TWO_FACTOR_FAILED,
                            user.getUsername(),
                            user.getId(),
                            client,
                            "{\"method\":\"recovery-code\"}");
                    return new AuthenticationException(ErrorCode.AUTH_0004);
                });

        entry.markUsed(now);
        recoveryCodes.save(entry);

        long remaining = recoveryCodes.countByUserIdAndUsedAtIsNull(user.getId());
        log.warn("Tài khoản {} dùng mã khôi phục 2FA, còn lại {} mã", user.getUsername(), remaining);
        securityEvents.record(
                SecurityEventType.TWO_FACTOR_RECOVERY_USED,
                user.getUsername(),
                user.getId(),
                client,
                "{\"remainingCodes\":" + remaining + "}");
    }

    // -------------------------------------------------------------------------

    private long verifyOrThrow(UserTotp entry, String code, User user, ClientInfo client, Instant now) {
        byte[] secret = new Base32().decode(crypto.decrypt(entry.getSecretEncrypted()));
        long currentStep = TotpGenerator.stepAt(now.getEpochSecond());

        OptionalLong matched =
                TotpGenerator.verify(secret, code == null ? null : code.trim(), currentStep, ALLOWED_DRIFT_STEPS);

        if (matched.isEmpty()) {
            securityEvents.record(SecurityEventType.TWO_FACTOR_FAILED, user.getUsername(), user.getId(), client);
            throw new AuthenticationException(ErrorCode.AUTH_0004);
        }

        long step = matched.getAsLong();
        Long lastUsed = entry.getLastUsedStep();
        if (lastUsed != null && step <= lastUsed) {
            // Mã đúng nhưng đã dùng rồi — gần như chắc chắn là request bị phát lại
            log.warn("Từ chối mã TOTP đã dùng của tài khoản {} (bước {})", user.getUsername(), step);
            securityEvents.record(
                    SecurityEventType.TWO_FACTOR_FAILED,
                    user.getUsername(),
                    user.getId(),
                    client,
                    "{\"reason\":\"replayed-code\"}");
            throw new AuthenticationException(ErrorCode.AUTH_0004);
        }
        return step;
    }

    /**
     * Chuỗi {@code otpauth://} theo quy ước Key Uri Format của Google Authenticator.
     *
     * <p>{@code issuer} xuất hiện hai lần (trong nhãn và trong tham số) — đúng theo đặc tả: ứng dụng
     * cũ đọc nhãn, ứng dụng mới đọc tham số.
     */
    private static String otpauthUri(String issuer, String username, String base32Secret) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String label = encodedIssuer + ":" + URLEncoder.encode(username, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1"
                + "&digits=" + TotpGenerator.DIGITS
                + "&period=" + TotpGenerator.STEP_SECONDS;
    }

    /**
     * @param secret Base32 — hiện cho người dùng nhập tay khi không quét được QR
     * @param otpauthUri chuỗi để FE vẽ mã QR
     * @param recoveryCodes ⛔ chỉ trả về đúng một lần, không lưu bản rõ ở đâu cả
     */
    public record Enrollment(String secret, String otpauthUri, List<String> recoveryCodes) {}

    /** Người dùng đã đăng ký nhưng chưa xác nhận — dùng để nhắc hoàn tất. */
    public Optional<UserTotp> pendingEnrollment(Long userId) {
        return totpRepository.findByUserId(userId).filter(entry -> !entry.isConfirmed());
    }
}
