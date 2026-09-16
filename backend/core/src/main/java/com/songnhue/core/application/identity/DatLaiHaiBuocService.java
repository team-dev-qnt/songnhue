package com.songnhue.core.application.identity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.RefreshTokenService;
import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.application.auth.TotpService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.identity.SessionRevokeReason;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Quản trị viên đặt lại 2FA của một tài khoản — người dùng mất cả ứng dụng xác thực lẫn mã khôi phục. T61.30.
 *
 * <h2>Vì sao có lớp này</h2>
 *
 * Trước T61.30 người dùng tự "đăng ký lại" bằng vé challenge sau bước mật khẩu — cũng chính là đường vượt 2FA
 * của kẻ có mật khẩu (ASVS 4.3.1 / 2.5.6). Chặn đường ấy ({@code AUTH-0009}) thì phải có đường THẬT cho người
 * mất điện thoại: một người KHÁC, có quyền, làm hộ, để lại dấu vết.
 *
 * <p>Xoá đăng ký + mã khôi phục · thu hồi MỌI phiên (phiên đang sống có thể là của kẻ đã chiếm) · ghi sự kiện
 * {@code DANGER}. Lần đăng nhập sau người dùng đi luồng đăng ký lần đầu.
 *
 * <p>⛔ Tự đặt lại của chính mình ({@code ADM-2021}): đó đúng là thao tác kẻ chiếm phiên muốn làm.
 *
 * <p>Tách khỏi {@code UserAdminService} (đã 8 phụ thuộc — trần Checkstyle) vì nó chạm ba hệ khác: TOTP, phiên,
 * sự kiện bảo mật.
 */
@Service
public class DatLaiHaiBuocService {

    private final UserRepository users;
    private final TotpService totp;
    private final RefreshTokenService refreshTokens;
    private final SecurityEventService securityEvents;

    public DatLaiHaiBuocService(
            UserRepository users,
            TotpService totp,
            RefreshTokenService refreshTokens,
            SecurityEventService securityEvents) {
        this.users = users;
        this.totp = totp;
        this.refreshTokens = refreshTokens;
        this.securityEvents = securityEvents;
    }

    @Transactional
    public void datLai(UUID publicId, ClientInfo client) {
        User user = users.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        Long nguoiLam = AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
        if (user.getId().equals(nguoiLam)) {
            throw new PermissionDeniedException(ErrorCode.ADM_2021);
        }
        boolean coDangKy = totp.xoaDangKy(user.getId());
        int soPhien = refreshTokens.revokeAllSessions(user.getId(), SessionRevokeReason.ADMIN_REVOKED, Instant.now());
        securityEvents.record(
                SecurityEventType.TWO_FACTOR_RESET_BY_ADMIN,
                user.getUsername(),
                user.getId(),
                client,
                "{\"nguoiLam\":%s,\"coDangKy\":%b,\"soPhienThuHoi\":%d}"
                        .formatted(nguoiLam == null ? "null" : nguoiLam, coDangKy, soPhien));
    }
}
