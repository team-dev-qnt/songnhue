package com.songnhue.core.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.config.JwtProperties;

/**
 * Nạp cặp khoá RSA dùng để ký/kiểm access token, đọc từ file PEM ngoài mã nguồn.
 *
 * <p>Khoá nằm ở {@code /opt/songnhue/keys/} trên máy chủ — <b>ngoài bản backup DB</b>
 * (architecture-review.md §6.5). Bản backup lọt cả khoá thì việc mã hoá và ký token mất hết ý nghĩa.
 *
 * <p><b>Fail-fast:</b> thiếu file, sai định dạng, hoặc khoá công khai không khớp khoá riêng → app
 * KHÔNG khởi động. Phép thử khớp cặp là điểm dễ bỏ sót nhất: lỡ tay copy nhầm một trong hai file thì
 * hệ thống vẫn chạy, phát token bình thường, nhưng <i>mọi</i> request tiếp theo đều 401 — triệu chứng
 * trông hệt như lỗi nghiệp vụ và rất mất thời gian truy.
 *
 * <p>{@code kid} đi vào header token để xoay khoá: sau này giữ nhiều khoá công khai cùng lúc, phát
 * token bằng khoá mới trong khi token cũ vẫn kiểm được cho tới lúc hết hạn.
 */
@Component
public class JwtKeyStore {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyStore.class);

    private static final String KEY_PAIR_PROBE = "songnhue-key-pair-probe";

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    public JwtKeyStore(JwtProperties properties) {
        this.keyId = properties.getKeyId();
        this.privateKey = loadPrivateKey(properties.getPrivateKeyPath());
        this.publicKey = loadPublicKey(properties.getPublicKeyPath());
        verifyPair(properties);
        log.info("Đã nạp khoá ký token RS256, kid={}", keyId);
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    private static RSAPrivateKey loadPrivateKey(String path) {
        byte[] der = readPemBody(path, "PRIVATE KEY");
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException(
                    "Khoá riêng ở '" + path + "' không phải RSA PKCS#8. Sinh lại: "
                            + "openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem",
                    e);
        }
    }

    private static RSAPublicKey loadPublicKey(String path) {
        byte[] der = readPemBody(path, "PUBLIC KEY");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException(
                    "Khoá công khai ở '" + path + "' không phải RSA X.509. Sinh lại: "
                            + "openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem",
                    e);
        }
    }

    /**
     * Bóc phần base64 giữa hai dòng {@code -----BEGIN/END …-----}.
     *
     * <p>Cố tình KHÔNG chấp nhận PEM có mật khẩu (định dạng {@code ENCRYPTED PRIVATE KEY}): thêm một
     * mật khẩu nữa để bảo vệ file khoá thì lại phải cất mật khẩu đó ở đâu đó — vòng luẩn quẩn. Bảo
     * vệ file khoá bằng quyền hệ thống tệp (chmod 600) là đúng chỗ hơn.
     */
    private static byte[] readPemBody(String path, String expectedLabel) {
        Path file = Path.of(path);
        String pem;
        try {
            pem = Files.readString(file, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Không đọc được file khoá '" + file.toAbsolutePath() + "'. "
                            + "Sinh cặp khoá cho môi trường local: make gen-keys "
                            + "(xem docs/setup-guideline.md mục 3).",
                    e);
        }

        String begin = "-----BEGIN " + expectedLabel + "-----";
        String end = "-----END " + expectedLabel + "-----";
        int from = pem.indexOf(begin);
        int to = pem.indexOf(end);
        if (from < 0 || to < 0) {
            throw new IllegalStateException("File '" + path + "' không chứa khối '" + begin
                    + "'. Kiểm tra xem có nhầm khoá riêng với khoá công khai không.");
        }
        String body = pem.substring(from + begin.length(), to).replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Nội dung base64 trong '" + path + "' bị hỏng", e);
        }
    }

    /** Ký thử rồi kiểm lại — cách duy nhất chắc chắn hai file đúng là một cặp. */
    private void verifyPair(JwtProperties properties) {
        try {
            java.security.Signature signer = java.security.Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(KEY_PAIR_PROBE.getBytes(StandardCharsets.UTF_8));
            byte[] signature = signer.sign();

            java.security.Signature verifier = java.security.Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(KEY_PAIR_PROBE.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(signature)) {
                throw new IllegalStateException("Khoá công khai '" + properties.getPublicKeyPath()
                        + "' không khớp khoá riêng '" + properties.getPrivateKeyPath()
                        + "'. Sinh lại khoá công khai TỪ khoá riêng: "
                        + "openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không kiểm được cặp khoá RSA", e);
        }
    }
}
