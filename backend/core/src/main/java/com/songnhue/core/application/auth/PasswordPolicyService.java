package com.songnhue.core.application.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.songnhue.core.application.settings.SettingKeys;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;

/**
 * Băm mật khẩu và kiểm chính sách độ mạnh (T5.7, M5.15).
 *
 * <p><b>BCrypt cost 12</b> (§4.1): mỗi lần băm tốn khoảng 0,3 giây — chậm có chủ đích. Với người
 * dùng, 0,3 giây khi đăng nhập là không cảm nhận được; với kẻ dò mật khẩu từ bảng hash bị lộ, nó
 * biến hàng tỉ phép thử mỗi giây thành vài phép thử mỗi giây.
 *
 * <p>Tham số chính sách (độ dài tối thiểu, bắt buộc chữ + số) đọc từ bảng {@code settings} để Admin
 * sửa được trên UI — quy tắc 12 của dự án. Riêng <b>cost của BCrypt thì không</b>: đó là tham số an
 * toàn mật mã, hạ xuống bằng UI là mở toang cửa mà không ai nhận ra.
 */
@Service
public class PasswordPolicyService {

    /** ≥ 12 theo conventions.md §4.1. Nâng lên khi phần cứng nhanh hơn, không bao giờ hạ xuống. */
    private static final int BCRYPT_COST = 12;

    /**
     * Băm giả để so sánh khi tài khoản không tồn tại.
     *
     * <p>Không có nó thì thời gian trả lời tự tố cáo: tài khoản có thật mất ~300 ms để băm mật khẩu,
     * tài khoản không có thì trả về tức thì. Đo vài lần là dựng được danh sách tài khoản có thật —
     * đúng thứ mà message chung {@code AUTH-0001} sinh ra để che.
     */
    private static final String DUMMY_HASH = "$2a$12$C6UzMDM.H6dfI/f/IKcEe.iUKMFkjfRWZ2PYlpvHrKgqQGKuqQwmy";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_COST);
    private final SettingService settings;

    public PasswordPolicyService(SettingService settings) {
        this.settings = settings;
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        return encoder.matches(rawPassword, storedHash);
    }

    /** Tiêu tốn đúng lượng thời gian như một lần kiểm mật khẩu thật, rồi trả về sai. */
    public void wasteTimeToHideMissingUser(String rawPassword) {
        encoder.matches(rawPassword == null ? "" : rawPassword, DUMMY_HASH);
    }

    /**
     * Kiểm mật khẩu mới có đạt chính sách không.
     *
     * @throws BusinessRuleException {@code AUTH-0006} kèm chi tiết theo trường để FE chỉ đúng chỗ sai
     */
    public void validate(String rawPassword, String username) {
        int minLength = settings.getInt(SettingKeys.PASSWORD_MIN_LENGTH, SettingKeys.DEFAULT_PASSWORD_MIN_LENGTH);
        boolean requireLetterAndDigit = settings.getBoolean(SettingKeys.PASSWORD_REQUIRE_LETTER_AND_DIGIT, true);

        BusinessRuleException error = new BusinessRuleException(ErrorCode.AUTH_0006);
        boolean failed = false;

        if (rawPassword == null || rawPassword.length() < minLength) {
            // ⚠ rejectedValue để null — giá trị này đi thẳng ra response, không được là mật khẩu
            error.withDetail("newPassword", "MIN_LENGTH_" + minLength, null);
            failed = true;
        }
        if (requireLetterAndDigit && !hasLetterAndDigit(rawPassword)) {
            error.withDetail("newPassword", "REQUIRE_LETTER_AND_DIGIT", null);
            failed = true;
        }
        if (rawPassword != null
                && username != null
                && rawPassword
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains(username.toLowerCase(java.util.Locale.ROOT))) {
            // Không có trong spec nhưng là bước rẻ nhất chặn "admin/admin123" — kiểu mật khẩu bị dò
            // đầu tiên trong mọi đợt tấn công
            error.withDetail("newPassword", "MUST_NOT_CONTAIN_USERNAME", null);
            failed = true;
        }

        if (failed) {
            throw error;
        }
    }

    private static boolean hasLetterAndDigit(String value) {
        if (value == null) {
            return false;
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
            if (letter && digit) {
                return true;
            }
        }
        return false;
    }
}
