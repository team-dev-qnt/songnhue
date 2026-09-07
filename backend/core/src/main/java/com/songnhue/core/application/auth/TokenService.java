package com.songnhue.core.application.auth;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.songnhue.core.common.config.JwtProperties;
import com.songnhue.core.common.security.AccessTokenClaims;
import com.songnhue.core.common.security.JwtKeyStore;

/**
 * Ký và kiểm access token / vé 2FA — RS256, {@code kid} trong header (conventions.md §4.1, T5.1).
 *
 * <p><b>Chỉ chấp nhận đúng một thuật toán.</b> Lỗ hổng kinh điển của JWT là để bên kiểm tin vào
 * trường {@code alg} do chính token khai báo: kẻ tấn công đổi thành {@code none} (bỏ chữ ký), hoặc
 * đổi sang {@code HS256} rồi ký bằng chính khoá công khai — thứ ai cũng lấy được. Ở đây thuật toán
 * là hằng số phía máy chủ, token khai báo gì cũng không đổi được cách kiểm.
 *
 * <p>Refresh token <b>không</b> phải JWT mà là chuỗi ngẫu nhiên, xử lý ở {@link RefreshTokenService}
 * — nó cần thu hồi được ngay, mà JWT thì bản chất là không thu hồi được.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    /** Cố định phía máy chủ — KHÔNG bao giờ đọc từ header của token. */
    private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.RS256;

    private static final String CLAIM_USERNAME = "usr";
    private static final String CLAIM_SESSION_FAMILY = "fid";
    private static final String CLAIM_TOKEN_TYPE = "typ";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_TWO_FACTOR = "2fa";

    private final JwtKeyStore keyStore;
    private final JwtProperties properties;

    public TokenService(JwtKeyStore keyStore, JwtProperties properties) {
        this.keyStore = keyStore;
        this.properties = properties;
    }

    // -------------------------------------------------------------------------
    // Phát hành
    // -------------------------------------------------------------------------

    /**
     * @param jti định danh token, đưa vào {@code token_denylist} khi cần thu hồi trước hạn
     */
    public String issueAccessToken(UUID userPublicId, String username, UUID sessionFamilyId, UUID jti, Instant now) {
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());
        JWTClaimsSet claims = baseClaims(userPublicId, jti, now, expiresAt)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_SESSION_FAMILY, sessionFamilyId.toString())
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .build();
        return sign(claims);
    }

    /**
     * Vé đi tiếp sang bước nhập mã 2FA — đã qua mật khẩu nhưng <b>chưa</b> phải token đăng nhập.
     *
     * <p>Đánh dấu {@code typ=2fa} và kiểm lại lúc dùng: thiếu bước này thì cái vé tạm đó dùng thay
     * access token được, tức là bỏ qua luôn bước xác thực hai lớp mà nó sinh ra để phục vụ.
     */
    public String issueTwoFactorChallenge(UUID userPublicId, Instant now) {
        Instant expiresAt = now.plus(properties.getTwoFactorChallengeTtl());
        JWTClaimsSet claims = baseClaims(userPublicId, UUID.randomUUID(), now, expiresAt)
                .claim(CLAIM_TOKEN_TYPE, TYPE_TWO_FACTOR)
                .build();
        return sign(claims);
    }

    // -------------------------------------------------------------------------
    // Kiểm
    // -------------------------------------------------------------------------

    /**
     * @return rỗng khi token sai chữ ký, hết hạn, sai kiểu, hoặc sai {@code iss} — <b>không ném
     *     exception</b>: quyết định trả 401 hay coi là khách vãng lai là việc của tầng gọi
     *     ({@code AuthFilter} + interceptor), không phải của lớp này
     */
    public Optional<AccessTokenClaims> verifyAccessToken(String token, Instant now) {
        return verify(token, TYPE_ACCESS, now).map(claims -> {
            try {
                return new AccessTokenClaims(
                        UUID.fromString(claims.getSubject()),
                        claims.getStringClaim(CLAIM_USERNAME),
                        UUID.fromString(claims.getJWTID()),
                        UUID.fromString(claims.getStringClaim(CLAIM_SESSION_FAMILY)),
                        claims.getExpirationTime().toInstant());
            } catch (java.text.ParseException | IllegalArgumentException | NullPointerException e) {
                log.debug("Access token thiếu claim bắt buộc hoặc sai định dạng");
                return null;
            }
        });
    }

    /** @return {@code public_id} của người dùng nếu vé 2FA hợp lệ */
    public Optional<UUID> verifyTwoFactorChallenge(String token, Instant now) {
        return verify(token, TYPE_TWO_FACTOR, now).map(claims -> {
            try {
                return UUID.fromString(claims.getSubject());
            } catch (IllegalArgumentException | NullPointerException e) {
                return null;
            }
        });
    }

    // -------------------------------------------------------------------------

    private JWTClaimsSet.Builder baseClaims(UUID subject, UUID jti, Instant now, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .audience(properties.getIssuer())
                .subject(subject.toString())
                .jwtID(jti.toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(expiresAt));
    }

    private String sign(JWTClaimsSet claims) {
        JWSHeader header =
                new JWSHeader.Builder(ALGORITHM).keyID(keyStore.keyId()).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(keyStore.privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Không ký được token bằng khoá " + keyStore.keyId(), e);
        }
        return jwt.serialize();
    }

    private Optional<JWTClaimsSet> verify(String token, String expectedType, Instant now) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            // ⚠ Thứ tự quan trọng: KHÔNG đọc claim nào trước khi chữ ký được xác nhận.
            if (!ALGORITHM.equals(jwt.getHeader().getAlgorithm())) {
                log.debug(
                        "Từ chối token khai báo thuật toán {}", jwt.getHeader().getAlgorithm());
                return Optional.empty();
            }
            if (!jwt.verify(new RSASSAVerifier(keyStore.publicKey()))) {
                log.debug("Token sai chữ ký");
                return Optional.empty();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!properties.getIssuer().equals(claims.getIssuer())) {
                return Optional.empty();
            }
            if (!expectedType.equals(claims.getStringClaim(CLAIM_TOKEN_TYPE))) {
                log.debug("Token sai loại: cần {}", expectedType);
                return Optional.empty();
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(now)) {
                return Optional.empty();
            }
            Date notBefore = claims.getNotBeforeTime();
            if (notBefore != null && notBefore.toInstant().isAfter(now)) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (java.text.ParseException | JOSEException e) {
            // Token rác là chuyện thường ngày (bot quét, token cũ của tab bỏ quên) → không log ồn ào
            log.debug("Token không đọc được");
            return Optional.empty();
        }
    }
}
