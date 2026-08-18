package com.songnhue.core.common.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tham số ký/kiểm token (conventions.md §4.1) — đọc từ biến môi trường.
 *
 * <pre>
 * JWT_KEY_ID=v1                → app.jwt.key-id       (đi vào header `kid`)
 * JWT_PRIVATE_KEY_PATH=...pem  → app.jwt.private-key-path
 * JWT_PUBLIC_KEY_PATH=...pem   → app.jwt.public-key-path
 * </pre>
 *
 * <p><b>Vì sao RS256 chứ không HS256:</b> khoá ký (private) chỉ nằm ở tiến trình phát hành token,
 * còn nơi kiểm chỉ cần khoá công khai. Khi tách node hoặc thêm dịch vụ kiểm token về sau, không phải
 * phát tán một bí mật dùng chung — đó chính là thứ khiến HS256 khó xoay khoá an toàn.
 *
 * <p>Thời hạn token nằm ở đây chứ <b>không</b> ở bảng {@code settings}: đây là tham số an toàn hệ
 * thống, không phải tham số nghiệp vụ do Admin chỉnh. Nới hạn access token bằng UI là tự mở rộng cửa
 * sổ mà token bị đánh cắp còn dùng được.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    @NotBlank(message = "Thiếu JWT_KEY_ID")
    private String keyId;

    @NotBlank(message = "Thiếu JWT_PRIVATE_KEY_PATH")
    private String privateKeyPath;

    @NotBlank(message = "Thiếu JWT_PUBLIC_KEY_PATH")
    private String publicKeyPath;

    /** Ngắn có chủ đích: token bị lộ chỉ dùng được trong khoảng này (§4.1 chốt 30'). */
    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(30);

    /** Hết hạn thì phải đăng nhập lại. 7 ngày ≈ nghỉ cuối tuần vẫn không phải đăng nhập lại. */
    @NotNull
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** Vé đi tiếp sang bước nhập mã 2FA. Rất ngắn: nó đã vượt qua bước mật khẩu. */
    @NotNull
    private Duration twoFactorChallengeTtl = Duration.ofMinutes(5);

    /** Giá trị {@code iss}/{@code aud} — chặn token của hệ thống khác lọt vào. */
    @NotBlank
    private String issuer = "songnhue";

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Duration getTwoFactorChallengeTtl() {
        return twoFactorChallengeTtl;
    }

    public void setTwoFactorChallengeTtl(Duration twoFactorChallengeTtl) {
        this.twoFactorChallengeTtl = twoFactorChallengeTtl;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
