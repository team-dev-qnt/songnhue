package com.songnhue.core.api.auth;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO của nhóm endpoint xác thực.
 *
 * <p>Gom vào một file vì tất cả đều là record ngắn, cùng một ngữ cảnh — tách thành mười file rời chỉ
 * làm khó theo dõi luồng đăng nhập.
 *
 * <p>⛔ Không record nào ở đây được mang refresh token: nó chỉ đi trong cookie httpOnly. Đưa vào body
 * là JavaScript đọc được, và một lỗ XSS duy nhất sẽ lấy được thứ dùng nhiều ngày thay vì thứ dùng 30
 * phút.
 */
public final class AuthDtos {

    private AuthDtos() {}

    // --- Vào -----------------------------------------------------------------

    public record LoginRequest(
            @NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}

    /**
     * @param recoveryCode true = {@code code} là mã khôi phục thay vì mã từ ứng dụng xác thực
     */
    public record TwoFactorRequest(
            @NotBlank String challengeToken, @NotBlank @Size(max = 32) String code, boolean recoveryCode) {}

    public record EnrollRequest(@NotBlank String challengeToken) {}

    public record ConfirmEnrollRequest(@NotBlank String challengeToken, @NotBlank @Size(max = 10) String code) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword, @NotBlank @Size(max = 200) String newPassword) {}

    // --- Ra ------------------------------------------------------------------

    /**
     * @param stage {@code AUTHENTICATED} · {@code TWO_FACTOR_REQUIRED} ·
     *     {@code TWO_FACTOR_ENROLL_REQUIRED} — FE dựa vào đây để biết đi tiếp màn hình nào
     * @param challengeToken chỉ có ở hai nhánh 2FA
     * @param csrfToken cũng đã được đặt vào cookie; trả kèm để FE dùng ngay không phải đọc cookie
     */
    /**
     * Chính sách độ mạnh mật khẩu đang có hiệu lực.
     *
     * <p>⛔ Trả ra để giao diện nói được YÊU CẦU THẬT. Ghi cứng "ít nhất 10 ký tự" vào màn hình
     * là hai con số ở hai nơi, và cái ở màn hình <b>nói dối</b> ngay lần đầu Admin sửa tham số —
     * đúng lớp lỗi §10.69. Ở đây chỉ có ràng buộc, không có bí mật nào: kẻ tấn công biết được
     * "mật khẩu tối thiểu 10 ký tự" thì cũng biết đúng bằng cách thử tạo một mật khẩu 9 ký tự.
     */
    @Schema(description = "Chính sách độ mạnh mật khẩu đang có hiệu lực")
    public record PasswordPolicyResponse(int minLength, boolean requireLetterAndDigit) {}

    @Schema(description = "Kết quả bước 1 của đăng nhập")
    public record LoginResponse(
            String stage,
            String accessToken,
            Instant accessTokenExpiresAt,
            String csrfToken,
            String challengeToken,
            boolean mustChangePassword) {}

    /**
     * @param secret ⛔ chỉ hiện đúng một lần, không lấy lại được
     * @param recoveryCodes ⛔ như trên — người dùng phải in hoặc lưu ngay
     */
    @Schema(description = "Thông tin đăng ký 2FA — hiển thị đúng một lần")
    public record EnrollResponse(String secret, String otpauthUri, List<String> recoveryCodes) {}

    @Schema(description = "Hồ sơ người đang đăng nhập")
    public record MeResponse(
            UUID id,
            String username,
            String fullName,
            Long orgUnitId,
            Set<String> roles,
            Set<String> permissions,
            boolean mustChangePassword,
            boolean twoFactorEnrolled) {}

    /**
     * @param current phiên đang dùng để gọi API này — FE tô khác để người dùng khỏi tự đăng xuất mình
     */
    @Schema(description = "Một phiên đăng nhập đang hoạt động (M5.14)")
    public record SessionResponse(
            UUID id,
            String deviceLabel,
            String ipAddress,
            Instant issuedAt,
            Instant lastUsedAt,
            Instant expiresAt,
            boolean current) {}
}
