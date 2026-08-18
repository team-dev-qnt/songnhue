package com.songnhue.core.common.util;

/**
 * Che dữ liệu nhạy cảm trước khi đưa ra log hoặc màn hình (conventions.md §4.5).
 *
 * <p>Áp dụng cho trường 🔒 của hồ sơ nhân sự (CCCD, số tài khoản — NĐ 13/2023) và credential bên thứ
 * 3 (§4.7).
 *
 * <p>⚠ Che KHÔNG phải là bảo vệ. Đây chỉ là lớp giảm thiệt hại khi dữ liệu lỡ đi vào log hoặc lên
 * màn hình chia sẻ. Dữ liệu thật vẫn phải mã hoá AES-256-GCM và cấm trả ra API.
 */
public final class MaskUtils {

    private static final char MASK = '*';

    private MaskUtils() {}

    /** CCCD 12 số: giữ 4 đầu và 3 cuối. VD {@code 001234567890} → {@code 0012*****890}. */
    public static String maskIdNumber(String value) {
        return maskMiddle(value, 4, 3);
    }

    /** Số điện thoại: giữ 3 đầu và 2 cuối. VD {@code 0912345678} → {@code 091*****78}. */
    public static String maskPhone(String value) {
        return maskMiddle(value, 3, 2);
    }

    /** Số tài khoản ngân hàng: chỉ giữ 4 số cuối. */
    public static String maskAccountNumber(String value) {
        return maskMiddle(value, 0, 4);
    }

    /** Email: {@code nguyenvana@congty.vn} → {@code ngu*******@congty.vn}. */
    public static String maskEmail(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return maskMiddle(value, 1, 0);
        }
        String local = value.substring(0, at);
        String domain = value.substring(at);
        return maskMiddle(local, Math.min(3, local.length()), 0) + domain;
    }

    /**
     * Credential bên thứ 3 (khóa API thủy văn, mã số hệ thống văn bản điều hành).
     *
     * <p>Chỉ giữ 2 ký tự cuối — vừa đủ để người dùng nhận ra mình đã nhập cái nào, không đủ để suy
     * ra giá trị. Giá trị thật không bao giờ được trả ra API, kể cả cho Admin (§4.7).
     */
    public static String maskCredential(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return maskMiddle(value, 0, Math.min(2, value.length() - 1));
    }

    /**
     * Giữ {@code keepStart} ký tự đầu và {@code keepEnd} ký tự cuối, còn lại thay bằng {@code *}.
     *
     * <p>Chuỗi quá ngắn để giữ được hai đầu thì che TOÀN BỘ — thà mất tiện lợi còn hơn lộ gần hết.
     */
    public static String maskMiddle(String value, int keepStart, int keepEnd) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int length = value.length();
        if (keepStart + keepEnd >= length) {
            return String.valueOf(MASK).repeat(length);
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append(value, 0, keepStart);
        sb.append(String.valueOf(MASK).repeat(length - keepStart - keepEnd));
        sb.append(value, length - keepEnd, length);
        return sb.toString();
    }
}
