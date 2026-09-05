package com.songnhue.core.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.songnhue.core.common.config.CryptoProperties;

/** Mã hoá cột nhạy cảm — trường 🔒 của HRM và credential bên thứ 3 (§4.7). */
class CryptoServiceTest {

    private static final String KEY_V1 =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String KEY_V2 =
            Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    private CryptoProperties properties;
    private CryptoService crypto;

    @BeforeEach
    void setUp() {
        properties = newProperties("v1", Map.of("v1", KEY_V1));
        crypto = new CryptoService(properties);
    }

    private static CryptoProperties newProperties(String activeKeyId, Map<String, String> keys) {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId(activeKeyId);
        props.setKeys(new LinkedHashMap<>(keys));
        ReflectionTestUtils.invokeMethod(props, "validateAndDecode");
        return props;
    }

    @Test
    @DisplayName("Mã hoá rồi giải mã ra đúng bản gốc")
    void roundTrip() {
        String plaintext = "001234567890";
        String encrypted = crypto.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext).startsWith("v1:");
        assertThat(crypto.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Mã hoá hai lần cùng một giá trị ra hai bản mã KHÁC nhau")
    void producesDifferentCiphertextEachTime() {
        String a = crypto.encrypt("Nguyễn Văn A");
        String b = crypto.encrypt("Nguyễn Văn A");

        // IV ngẫu nhiên mỗi lần. Nếu giống nhau thì kẻ tấn công nhìn cột mã hoá là
        // đoán được ai trùng giá trị với ai — rò rỉ dù không giải mã được.
        assertThat(a).isNotEqualTo(b);
        assertThat(crypto.decrypt(a)).isEqualTo(crypto.decrypt(b));
    }

    @Test
    @DisplayName("Giữ nguyên tiếng Việt có dấu")
    void handlesVietnameseText() {
        String plaintext = "Nguyễn Thị Hồng Nhuệ — Đội trưởng";
        assertThat(crypto.decrypt(crypto.encrypt(plaintext))).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Sửa một ký tự trong bản mã là giải mã hỏng ngay — đặc tính AEAD của GCM")
    void detectsTampering() {
        String encrypted = crypto.encrypt("so-tai-khoan-123456");

        // Đổi ký tự cuối phần base64
        char last = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Xoay khoá: bản ghi mã bằng khoá cũ vẫn đọc được sau khi đổi sang khoá mới")
    void supportsKeyRotation() {
        String encryptedWithV1 = crypto.encrypt("du-lieu-cu");

        // Thêm v2 và chuyển sang dùng v2, GIỮ LẠI v1
        CryptoProperties rotated = newProperties("v2", Map.of("v1", KEY_V1, "v2", KEY_V2));
        CryptoService rotatedCrypto = new CryptoService(rotated);

        assertThat(rotatedCrypto.decrypt(encryptedWithV1)).isEqualTo("du-lieu-cu");
        assertThat(rotatedCrypto.encrypt("du-lieu-moi")).startsWith("v2:");
        assertThat(rotatedCrypto.keyIdOf(encryptedWithV1)).isEqualTo("v1");
    }

    @Test
    @DisplayName("Bỏ khoá cũ khỏi cấu hình → lỗi nói rõ nguyên nhân, không im lặng")
    void missingOldKeyFailsLoudly() {
        String encryptedWithV1 = crypto.encrypt("du-lieu-cu");

        CryptoService onlyV2 = new CryptoService(newProperties("v2", Map.of("v2", KEY_V2)));

        assertThatThrownBy(() -> onlyV2.decrypt(encryptedWithV1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("v1");
    }

    @Test
    @DisplayName("Khoá sai độ dài → chặn ngay lúc khởi động, không chờ tới lúc dùng")
    void rejectsWrongKeyLength() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("v1");
        props.setKeys(Map.of("v1", Base64.getEncoder().encodeToString("qua-ngan".getBytes())));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(props, "validateAndDecode"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }

    @Test
    @DisplayName("active-key-id trỏ vào khoá không tồn tại → chặn lúc khởi động")
    void rejectsDanglingActiveKeyId() {
        CryptoProperties props = new CryptoProperties();
        props.setActiveKeyId("v9");
        props.setKeys(Map.of("v1", KEY_V1));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(props, "validateAndDecode"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("v9");
    }

    @Test
    void handlesNull() {
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
    }
}
