package com.songnhue.core.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.songnhue.core.common.config.JwtProperties;
import com.songnhue.core.common.security.JwtKeyStore;

/**
 * Sinh cặp khoá RSA và ghi ra file PEM cho test.
 *
 * <p>Sinh mới mỗi lần thay vì nhúng sẵn một cặp khoá vào repo: khoá nằm trong mã nguồn có thói quen
 * bị copy nhầm sang môi trường thật, và {@code .gitignore} đã chặn {@code *.pem} chính vì lý do đó.
 */
public final class RsaKeyPairFixture {

    private RsaKeyPairFixture() {}

    public static JwtKeyStore keyStoreIn(Path directory, String keyId) {
        return new JwtKeyStore(propertiesFor(directory, keyId));
    }

    public static JwtProperties propertiesFor(Path directory, String keyId) {
        KeyPair pair = generate();
        Path privatePath = directory.resolve(keyId + "-private.pem");
        Path publicPath = directory.resolve(keyId + "-public.pem");

        write(privatePath, "PRIVATE KEY", pair.getPrivate().getEncoded());
        write(publicPath, "PUBLIC KEY", pair.getPublic().getEncoded());

        JwtProperties properties = new JwtProperties();
        properties.setKeyId(keyId);
        properties.setPrivateKeyPath(privatePath.toString());
        properties.setPublicKeyPath(publicPath.toString());
        return properties;
    }

    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không hỗ trợ RSA", e);
        }
    }

    public static void write(Path path, String label, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        String pem = "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
        try {
            Files.writeString(path, pem, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Không ghi được file PEM " + path, e);
        }
    }
}
