package com.songnhue.core.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.songnhue.core.common.config.JwtProperties;
import com.songnhue.core.common.security.AccessTokenClaims;
import com.songnhue.core.common.security.JwtKeyStore;
import com.songnhue.core.testsupport.RsaKeyPairFixture;

/**
 * Kiểm token — nơi một lỗi nhỏ là mở toang cửa hệ thống.
 *
 * <p>Trọng tâm không phải "ký rồi đọc lại có ra đúng không" (chuyện đó khó sai), mà là <b>những
 * token phải bị TỪ CHỐI</b>: đổi thuật toán, bỏ chữ ký, sửa nội dung, ký bằng khoá khác, dùng vé 2FA
 * thay access token.
 */
class TokenServiceTest {

    @TempDir
    Path keyDir;

    private JwtProperties properties;
    private JwtKeyStore keyStore;
    private TokenService tokens;

    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private final UUID jti = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-14T03:00:00Z");

    @BeforeEach
    void setUp() {
        properties = RsaKeyPairFixture.propertiesFor(keyDir, "v1");
        keyStore = new JwtKeyStore(properties);
        tokens = new TokenService(keyStore, properties);
    }

    @Nested
    @DisplayName("Luồng bình thường")
    class HappyPath {

        @Test
        @DisplayName("Ký rồi kiểm lại ra đúng nội dung ban đầu")
        void roundTrip() {
            String token = tokens.issueAccessToken(userId, "nva", familyId, jti, now);

            AccessTokenClaims claims =
                    tokens.verifyAccessToken(token, now.plusSeconds(60)).orElseThrow();

            assertThat(claims.subject()).isEqualTo(userId);
            assertThat(claims.username()).isEqualTo("nva");
            assertThat(claims.sessionFamilyId()).isEqualTo(familyId);
            assertThat(claims.tokenId()).isEqualTo(jti);
            assertThat(claims.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        }

        @Test
        @DisplayName("Header mang kid để về sau xoay khoá được")
        void headerCarriesKeyId() throws Exception {
            String token = tokens.issueAccessToken(userId, "nva", familyId, jti, now);

            assertThat(SignedJWT.parse(token).getHeader().getKeyID()).isEqualTo("v1");
        }

        @Test
        @DisplayName("Token KHÔNG chứa danh sách quyền — quyền nạp lại từ CSDL mỗi request")
        void tokenCarriesNoPermissions() {
            String token = tokens.issueAccessToken(userId, "nva", familyId, jti, now);
            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

            // Quyền nhét vào token thì gỡ quyền xong người dùng vẫn làm được tới 30 phút nữa
            assertThat(payload).doesNotContain("permission", "role", ":view", ":create");
        }
    }

    @Nested
    @DisplayName("Token phải bị từ chối")
    class Rejections {

        @Test
        @DisplayName("Hết hạn")
        void expiredToken() {
            String token = tokens.issueAccessToken(userId, "nva", familyId, jti, now);

            assertThat(tokens.verifyAccessToken(token, now.plus(Duration.ofMinutes(31))))
                    .isEmpty();
        }

        @Test
        @DisplayName("Sửa một ký tự trong nội dung → chữ ký không còn khớp")
        void tamperedPayload() {
            String token = tokens.issueAccessToken(userId, "nva", familyId, jti, now);
            String[] parts = token.split("\\.");

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String forged = payload.replace("\"usr\":\"nva\"", "\"usr\":\"adm\"");
            parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(forged.getBytes(StandardCharsets.UTF_8));

            assertThat(tokens.verifyAccessToken(String.join(".", parts), now.plusSeconds(60)))
                    .isEmpty();
        }

        @Test
        @DisplayName("⚠ Tấn công đổi thuật toán: ký HS256 bằng chính khoá công khai")
        void algorithmConfusionAttack() throws Exception {
            // Đây là lỗ hổng JWT kinh điển. Khoá công khai ai cũng lấy được; nếu bên kiểm tin vào
            // trường `alg` do token khai báo thì kẻ tấn công dùng khoá công khai làm bí mật HMAC và
            // tự ký được token hợp lệ cho BẤT KỲ tài khoản nào.
            byte[] publicKeyBytes = keyStore.publicKey().getEncoded();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("songnhue")
                    .subject(UUID.randomUUID().toString())
                    .jwtID(UUID.randomUUID().toString())
                    .claim("usr", "superadmin")
                    .claim("fid", familyId.toString())
                    .claim("typ", "access")
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .build();

            SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            forged.sign(new MACSigner(publicKeyBytes));

            assertThat(tokens.verifyAccessToken(forged.serialize(), now)).isEmpty();
        }

        @Test
        @DisplayName("⚠ Tấn công alg=none: bỏ hẳn chữ ký")
        void noneAlgorithmAttack() {
            String header = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            ("{\"iss\":\"songnhue\",\"sub\":\"" + userId + "\",\"typ\":\"access\",\"exp\":99999999999}")
                                    .getBytes(StandardCharsets.UTF_8));

            assertThat(tokens.verifyAccessToken(header + "." + payload + ".", now))
                    .isEmpty();
        }

        @Test
        @DisplayName("Ký bằng cặp khoá khác — dựng hệ thống giả rồi phát token sang")
        void signedByForeignKey() throws Exception {
            KeyPair foreign = RsaKeyPairFixture.generate();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("songnhue")
                    .subject(userId.toString())
                    .jwtID(jti.toString())
                    .claim("usr", "nva")
                    .claim("fid", familyId.toString())
                    .claim("typ", "access")
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .build();
            SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            forged.sign(new RSASSASigner(foreign.getPrivate()));

            assertThat(tokens.verifyAccessToken(forged.serialize(), now)).isEmpty();
        }

        @Test
        @DisplayName("Vé 2FA KHÔNG dùng thay được access token")
        void twoFactorChallengeIsNotAnAccessToken() {
            String challenge = tokens.issueTwoFactorChallenge(userId, now);

            // Thiếu phép kiểm `typ` thì cái vé tạm này bỏ qua luôn bước xác thực hai lớp
            assertThat(tokens.verifyAccessToken(challenge, now.plusSeconds(10))).isEmpty();
            assertThat(tokens.verifyTwoFactorChallenge(challenge, now.plusSeconds(10)))
                    .hasValue(userId);
        }

        @Test
        @DisplayName("Access token KHÔNG dùng thay được vé 2FA")
        void accessTokenIsNotAChallenge() {
            String token = tokens.issueAccessToken(userId, "nva", familyId, jti, now);

            assertThat(tokens.verifyTwoFactorChallenge(token, now.plusSeconds(10)))
                    .isEmpty();
        }

        @Test
        @DisplayName("Vé 2FA hết hạn sau 5 phút")
        void challengeExpiresQuickly() {
            String challenge = tokens.issueTwoFactorChallenge(userId, now);

            assertThat(tokens.verifyTwoFactorChallenge(challenge, now.plus(Duration.ofMinutes(4))))
                    .isPresent();
            assertThat(tokens.verifyTwoFactorChallenge(challenge, now.plus(Duration.ofMinutes(6))))
                    .isEmpty();
        }

        @Test
        @DisplayName("Chuỗi rác, rỗng, null — trả rỗng chứ không ném lỗi")
        void garbageInput() {
            assertThat(tokens.verifyAccessToken(null, now)).isEmpty();
            assertThat(tokens.verifyAccessToken("", now)).isEmpty();
            assertThat(tokens.verifyAccessToken("khong-phai-jwt", now)).isEmpty();
            assertThat(tokens.verifyAccessToken("a.b.c", now)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Nạp khoá — fail-fast")
    class KeyLoading {

        @Test
        @DisplayName("Khoá công khai không khớp khoá riêng → app không khởi động")
        void mismatchedKeyPairFailsFast() {
            // Lỗi này rất dễ xảy ra khi copy file thủ công giữa các máy chủ, và nếu không chặn ở
            // đây thì triệu chứng là "đăng nhập xong request nào cũng 401" — trông y hệt lỗi nghiệp vụ
            JwtProperties mismatched = RsaKeyPairFixture.propertiesFor(keyDir, "v2");
            RsaKeyPairFixture.write(
                    Path.of(mismatched.getPublicKeyPath()),
                    "PUBLIC KEY",
                    RsaKeyPairFixture.generate().getPublic().getEncoded());

            assertThatThrownBy(() -> new JwtKeyStore(mismatched))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("không khớp khoá riêng");
        }

        @Test
        @DisplayName("Thiếu file khoá → thông báo chỉ rõ cách sinh khoá")
        void missingKeyFileFailsFast() {
            JwtProperties missing = new JwtProperties();
            missing.setKeyId("v1");
            missing.setPrivateKeyPath(keyDir.resolve("khong-ton-tai.pem").toString());
            missing.setPublicKeyPath(keyDir.resolve("khong-ton-tai-pub.pem").toString());

            assertThatThrownBy(() -> new JwtKeyStore(missing))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("make gen-keys");
        }

        @Test
        @DisplayName("Đưa nhầm khoá công khai vào ô khoá riêng → báo đúng chỗ nhầm")
        void swappedKeyFilesFailFast() {
            JwtProperties swapped = new JwtProperties();
            swapped.setKeyId("v1");
            swapped.setPrivateKeyPath(properties.getPublicKeyPath());
            swapped.setPublicKeyPath(properties.getPrivateKeyPath());

            assertThatThrownBy(() -> new JwtKeyStore(swapped))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BEGIN PRIVATE KEY");
        }
    }
}
