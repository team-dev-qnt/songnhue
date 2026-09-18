package com.songnhue.core.application.auth;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Xác thực lại bằng mã 2FA NGAY LÚC NÀY trước một thao tác nhạy cảm — T61.42 (ASVS 4.3.x, NFR-05).
 *
 * <p>Dùng cho: khôi phục CSDL · đặt/xoá bí mật tích hợp · sửa tham số nhóm nhạy cảm. Phiên mở từ sáng trên một máy
 * ⛔ khoá màn hình vẫn là phiên hợp lệ; bắt nhập mã chứng minh người đang ngồi đó giữ thiết bị thứ hai.
 *
 * <h2>⛔⛔ Hai khuyết tật của bản gọi thẳng {@code TotpService.verifyLoginCode} (đường khôi phục CSDL, WS-7)</h2>
 *
 * <ol>
 *   <li><b>Mã sai ⛔ bị đếm.</b> 6 chữ số, drift ±1 ⇒ 3 mã đúng/10⁶; ở hạn mức API 100 lượt/phút một phiên bị chiếm dò
 *       ra mã trong vài ngày mà ⛔ tài khoản nào bị khoá. Nay mỗi lượt sai đi qua {@link LoginAttemptService} —
 *       cùng bộ đếm, cùng ngưỡng với đăng nhập (T61.33).
 *   <li><b>Mã sai trả 401.</b> Giao diện đọc 401 là mất phiên ⇒ làm mới token, gửi lại cùng mã sai, rồi đá người dùng
 *       ra ngoài. ⇒ {@code ADM-2024} (403).
 * </ol>
 */
@Service
public class XacThucLaiService {

    private final UserRepository users;
    private final TotpService totp;
    private final LoginAttemptService loginAttempts;

    public XacThucLaiService(UserRepository users, TotpService totp, LoginAttemptService loginAttempts) {
        this.users = users;
        this.totp = totp;
        this.loginAttempts = loginAttempts;
    }

    /**
     * @param maXacThuc mã 6 số từ ứng dụng xác thực. ⛔ Không nhận mã khôi phục: mã khôi phục là lối thoát khi MẤT
     *     thiết bị, còn thao tác nhạy cảm là lúc phải chứng minh ĐANG giữ nó.
     * @return tài khoản vừa xác thực lại
     * @throws PermissionDeniedException {@code ADM-2023} thiếu mã / chưa đăng ký 2FA · {@code ADM-2024} mã sai
     * @throws AuthenticationException {@code AUTH-0003} tài khoản đang khoá tạm hoặc vừa bị khoá bởi lượt sai này
     */
    @Transactional
    public User xacThuc(String maXacThuc, ClientInfo client) {
        AuthenticatedUser hienTai =
                AuthContext.current().orElseThrow(() -> new PermissionDeniedException(ErrorCode.AUTH_3001));
        User user = users.findByPublicIdAndDeletedAtIsNull(hienTai.publicId())
                .orElseThrow(() -> new PermissionDeniedException(ErrorCode.AUTH_3001));

        Instant now = Instant.now();
        if (user.isTemporarilyLocked(now)) {
            throw new AuthenticationException(ErrorCode.AUTH_0003);
        }
        if (maXacThuc == null || maXacThuc.isBlank() || !totp.isEnrolled(user.getId())) {
            throw new PermissionDeniedException(ErrorCode.ADM_2023);
        }
        try {
            totp.verifyLoginCode(user, maXacThuc.trim(), client, now);
        } catch (AuthenticationException e) {
            if (e.errorCode() != ErrorCode.AUTH_0004) {
                throw e;
            }
            if (loginAttempts.recordFailure(user.getId(), user.getUsername(), client, now)) {
                throw new AuthenticationException(ErrorCode.AUTH_0003);
            }
            throw new PermissionDeniedException(ErrorCode.ADM_2024);
        }
        loginAttempts.recordSuccess(user.getId(), client.ipAddress(), now);
        return user;
    }
}
