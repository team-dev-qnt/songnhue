package com.songnhue.core.common.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.songnhue.core.common.config.CryptoProperties;

/**
 * Mã hoá cột nhạy cảm bằng AES-256-GCM (conventions.md §4.7, quy tắc 10 và 13 của dự án).
 *
 * <p>Dùng cho: trường 🔒 của hồ sơ nhân sự (bảng {@code employee_sensitive}), khoá API thủy văn,
 * mã số hệ thống văn bản điều hành, secret TOTP.
 *
 * <p><b>Chọn GCM chứ không phải CBC</b>: GCM có xác thực gắn liền (AEAD). Với CBC, kẻ tấn công sửa
 * được bản mã mà giải mã vẫn "thành công" ra rác — dữ liệu hỏng âm thầm. GCM thì giải mã hỏng là
 * ném lỗi ngay.
 *
 * <p><b>Định dạng lưu:</b> {@code <key_id>:<base64(iv || ciphertext || tag)>}. Nhúng {@code key_id}
 * vào chính bản mã để xoay khoá được: sau khi thêm khoá mới, bản ghi cũ vẫn giải mã được bằng khoá
 * cũ mà không cần chuyển đổi toàn bộ dữ liệu trong một lần.
 *
 * <p>⛔ Khoá đọc từ env, <b>không nằm trong DB và không nằm trong bản backup DB</b>
 * (architecture-review.md §6.5) — khoá lọt vào bản backup thì việc mã hoá mất hết ý nghĩa.
 */
@Service
public class CryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // 96 bit — độ dài khuyến nghị cho GCM
    private static final int TAG_LENGTH_BITS = 128;
    private static final char KEY_ID_SEPARATOR = ':';

    private final CryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(CryptoProperties properties) {
        this.properties = properties;
    }

    /**
     * Mã hoá bằng khoá đang hoạt động.
     *
     * @return chuỗi {@code <key_id>:<base64>} để lưu thẳng vào cột TEXT
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        String activeKeyId = properties.activeKeyId();
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(activeKeyId), new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return activeKeyId + KEY_ID_SEPARATOR + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            // KHÔNG đưa plaintext vào message — nó sẽ đi thẳng vào log
            throw new IllegalStateException("Mã hoá thất bại với khoá " + activeKeyId, e);
        }
    }

    /** Giải mã, tự chọn khoá theo {@code key_id} nhúng trong bản mã. */
    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        int separator = encoded.indexOf(KEY_ID_SEPARATOR);
        if (separator <= 0) {
            throw new IllegalArgumentException("Bản mã sai định dạng: thiếu key_id");
        }
        String keyId = encoded.substring(0, separator);
        byte[] combined = Base64.getDecoder().decode(encoded.substring(separator + 1));

        if (combined.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Bản mã sai định dạng: quá ngắn");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(keyId), new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Lỗi ở đây nghĩa là sai khoá HOẶC bản mã đã bị sửa — GCM không phân biệt hai trường
            // hợp, và đó là chủ ý: phân biệt được sẽ thành kênh rò rỉ thông tin
            throw new IllegalStateException("Giải mã thất bại với khoá " + keyId, e);
        }
    }

    /** Bản mã này đang dùng khoá nào — phục vụ job chuyển đổi khi xoay khoá. */
    public String keyIdOf(String encoded) {
        int separator = encoded == null ? -1 : encoded.indexOf(KEY_ID_SEPARATOR);
        return separator <= 0 ? null : encoded.substring(0, separator);
    }

    private SecretKeySpec keySpec(String keyId) {
        byte[] key = properties.keyBytes(keyId);
        if (key.length != 32) {
            throw new IllegalStateException(
                    "Khoá " + keyId + " phải dài đúng 32 byte (AES-256), đang có " + key.length);
        }
        return new SecretKeySpec(key, "AES");
    }
}
