package com.songnhue.core.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Băm SHA-256 và sinh chuỗi ngẫu nhiên an toàn — util dùng chung thứ 9 (conventions.md §2.5).
 *
 * <p>Gom về một chỗ vì cùng một phép băm được dùng ở nhiều nơi với <b>cùng một định dạng bắt buộc</b>:
 * refresh token và mã khôi phục lưu ở cột {@code CHAR(64)}, checksum tệp tin (WS-6) cũng vậy. Mỗi
 * nơi tự viết một bản là sớm muộn có chỗ ra chữ hoa, chỗ ra chữ thường, và phép so sánh im lặng
 * trượt hết.
 *
 * <p>⚠ <b>Không dùng lớp này cho mật khẩu.</b> SHA-256 nhanh có chủ đích, còn mật khẩu cần thuật
 * toán <i>chậm</i> có muối — đó là việc của BCrypt cost ≥ 12 (§4.1). Băm mật khẩu bằng SHA-256 là
 * mời card đồ hoạ dò cả bảng trong vài giờ.
 */
public final class HashUtils {

    /** Đúng bằng độ dài cột CHAR(64) của `sessions.refresh_token_hash`. */
    public static final int SHA256_HEX_LENGTH = 64;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private HashUtils() {}

    /** SHA-256 dạng hex thường, 64 ký tự. */
    public static String sha256Hex(String value) {
        return HEX.formatHex(digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256Hex(byte[] value) {
        return HEX.formatHex(digest(value));
    }

    /**
     * Chuỗi ngẫu nhiên an toàn dạng base64url — dùng làm refresh token.
     *
     * <p>Base64url (không có {@code +}, {@code /}, {@code =}) để đặt thẳng vào cookie mà không phải
     * mã hoá URL thêm lần nữa.
     *
     * @param byteLength số byte entropy; 32 byte = 256 bit, không thể dò cạn
     */
    public static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * So sánh hai chuỗi <b>không phụ thuộc vào vị trí ký tự khác nhau đầu tiên</b>.
     *
     * <p>{@code String.equals} dừng ngay khi gặp ký tự lệch, nên thời gian trả lời hé lộ "đoán đúng
     * được mấy ký tự đầu". Đo đủ nhiều lần là dựng lại được cả chuỗi bí mật từng ký tự một. Dùng hàm
     * này cho mọi phép so sánh token, mã 2FA và mã khôi phục.
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] digest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 là thuật toán bắt buộc của mọi JVM — tới đây được thì môi trường đã hỏng nặng
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", e);
        }
    }
}
