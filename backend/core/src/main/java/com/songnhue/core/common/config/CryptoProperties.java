package com.songnhue.core.common.config;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Khoá AES-256-GCM đọc từ biến môi trường (conventions.md §1.6, §4.7).
 *
 * <pre>
 * AES_KEY_ID=v1                    → app.crypto.active-key-id
 * AES_KEY_V1=&lt;base64 32 byte&gt;      → app.crypto.keys.v1
 * </pre>
 *
 * <p>Giữ NHIỀU khoá cùng lúc để xoay khoá được: thêm {@code v2}, đổi {@code active-key-id} sang
 * {@code v2}; bản ghi cũ mang {@code key_id=v1} vẫn giải mã bình thường, không cần dừng hệ thống để
 * chuyển đổi toàn bộ dữ liệu.
 *
 * <p><b>Fail-fast:</b> thiếu khoá, khoá sai độ dài, hoặc {@code active-key-id} trỏ vào khoá không
 * tồn tại → app KHÔNG khởi động. Chạy được với khoá sai còn tệ hơn nhiều: dữ liệu ghi ra sẽ không
 * giải mã lại được, và chỉ phát hiện ra khi cần đọc — lúc đó đã muộn.
 */
@Validated
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    private static final int AES_256_KEY_BYTES = 32;

    @NotBlank(message = "Thiếu AES_KEY_ID")
    private String activeKeyId;

    /** key_id → khoá base64. Ít nhất phải có khoá đang hoạt động. */
    private Map<String, String> keys = new LinkedHashMap<>();

    private final Map<String, byte[]> decoded = new LinkedHashMap<>();

    @PostConstruct
    void validateAndDecode() {
        if (keys.isEmpty()) {
            throw new IllegalStateException("Chưa cấu hình khoá mã hoá nào. Đặt AES_KEY_V1 trong file env "
                    + "(sinh khoá: openssl rand -base64 32).");
        }
        keys.forEach((keyId, base64) -> {
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(base64.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Khoá AES '" + keyId + "' không phải base64 hợp lệ. "
                                + "Sinh khoá mới: openssl rand -base64 32  → dán vào AES_KEY_"
                                + keyId.toUpperCase(java.util.Locale.ROOT)
                                + " trong deploy/env/local.env (xem docs/setup-guideline.md mục 3).",
                        e);
            }
            if (raw.length != AES_256_KEY_BYTES) {
                throw new IllegalStateException(
                        "Khoá AES '" + keyId + "' phải dài đúng 32 byte, đang có " + raw.length);
            }
            decoded.put(keyId, raw);
        });

        if (!decoded.containsKey(activeKeyId)) {
            throw new IllegalStateException(
                    "AES_KEY_ID='" + activeKeyId + "' nhưng không có khoá tương ứng. Đã nạp: " + decoded.keySet());
        }
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    /** @throws IllegalStateException khi bản ghi mang key_id không còn trong cấu hình */
    public byte[] keyBytes(String keyId) {
        byte[] key = decoded.get(keyId);
        if (key == null) {
            throw new IllegalStateException(
                    "Không có khoá '" + keyId + "'. Dữ liệu cũ mã hoá bằng khoá này sẽ không đọc được — "
                            + "giữ lại khoá cũ trong cấu hình cho tới khi chuyển đổi xong.");
        }
        return key.clone();
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys;
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }
}
