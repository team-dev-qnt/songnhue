package com.songnhue.core.common.util;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Sinh và kiểm mã TOTP theo <b>RFC 6238</b> (HOTP của RFC 4226 với bộ đếm là thời gian).
 *
 * <p><b>Vì sao tự cài thay vì kéo thư viện.</b> Toàn bộ thuật toán là: chia thời gian thành bước 30
 * giây → HMAC-SHA1 bí mật với số bước → cắt 4 byte theo offset ghi ở nibble cuối → lấy 6 chữ số
 * cuối. Chưa tới 40 dòng, và RFC có sẵn <b>bộ vector kiểm thử chính thức</b> nên tính đúng đắn chứng
 * minh được bằng test chứ không phải bằng niềm tin — xem {@code TotpGeneratorTest}. Đổi lại, tránh
 * được một phụ thuộc nữa phải theo dõi CVE cho một thứ đã đóng băng từ 2011.
 *
 * <p>Phần mật mã thật (HMAC-SHA1) vẫn do JDK làm, không tự viết.
 *
 * <p>SHA-1 ở đây <b>không phải</b> lựa chọn tuỳ tiện dù SHA-1 đã yếu với chữ ký số: RFC 6238 mặc
 * định HMAC-SHA1 và mọi ứng dụng xác thực phổ biến (Google Authenticator, Microsoft Authenticator,
 * Authy) chỉ chắc chắn hỗ trợ biến thể này. Điểm yếu va chạm của SHA-1 cũng không áp dụng cho HMAC.
 */
public final class TotpGenerator {

    /** 30 giây — mặc định của RFC 6238 và của mọi ứng dụng xác thực phổ thông. */
    public static final int STEP_SECONDS = 30;

    public static final int DIGITS = 6;

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};

    private TotpGenerator() {}

    /** Số thứ tự bước thời gian tại một mốc — cũng là giá trị lưu ở {@code user_totp.last_used_step}. */
    public static long stepAt(long epochSeconds) {
        return epochSeconds / STEP_SECONDS;
    }

    /**
     * @param secret khoá bí mật dạng byte (đã giải Base32)
     * @return mã đã đệm số 0 đằng trước cho đủ {@value #DIGITS} chữ số
     */
    public static String generate(byte[] secret, long step) {
        byte[] counter = ByteBuffer.allocate(Long.BYTES).putLong(step).array();

        byte[] hmac;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            hmac = mac.doFinal(counter);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("JVM không hỗ trợ " + HMAC_ALGORITHM, e);
        }

        // "Dynamic truncation" của RFC 4226 §5.3: 4 bit cuối chỉ ra chỗ bắt đầu cắt
        int offset = hmac[hmac.length - 1] & 0x0F;
        int binary = ((hmac[offset] & 0x7F) << 24)
                | ((hmac[offset + 1] & 0xFF) << 16)
                | ((hmac[offset + 2] & 0xFF) << 8)
                | (hmac[offset + 3] & 0xFF);

        int code = binary % POWERS[DIGITS];
        return String.format("%0" + DIGITS + "d", code);
    }

    /**
     * Kiểm mã, chấp nhận lệch tối đa {@code allowedDrift} bước về hai phía.
     *
     * <p>Phải cho lệch vì đồng hồ điện thoại và máy chủ không bao giờ khớp tuyệt đối, và người dùng
     * hay nhập mã ngay lúc nó sắp đổi. Lệch 1 bước = cửa sổ 90 giây — khuyến nghị của RFC 6238 §5.2.
     * Nới rộng hơn nữa là mỗi mã sống lâu hơn, tức là dễ bị dùng lại hơn.
     *
     * @return số thứ tự bước đã khớp (để ghi vào {@code last_used_step} chống dùng lại), hoặc rỗng
     */
    public static java.util.OptionalLong verify(byte[] secret, String code, long currentStep, int allowedDrift) {
        if (code == null || code.length() != DIGITS) {
            return java.util.OptionalLong.empty();
        }
        for (long step = currentStep - allowedDrift; step <= currentStep + allowedDrift; step++) {
            if (HashUtils.constantTimeEquals(generate(secret, step), code)) {
                return java.util.OptionalLong.of(step);
            }
        }
        return java.util.OptionalLong.empty();
    }
}
