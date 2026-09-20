package com.songnhue.core.application.identity;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.auth.RefreshTokenService;
import com.songnhue.core.application.auth.SecurityEventService;
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
 * Quản trị viên đặt lại mật khẩu cho một tài khoản nội bộ — <b>T61.31 · CN-05.2</b>.
 *
 * <h2>Khoảng trống này mở từ ngày có màn hình tài khoản</h2>
 *
 * <p>{@code function-spec.md} CN-05.2 đòi <i>"tạo/sửa/khoá/mở khoá, <b>đặt lại mật khẩu</b> tài khoản
 * nội bộ"</i>, và danh mục quyền có sẵn {@code adm:user:reset-password} <b>từ 13/08/2026</b> — với
 * <b>0 endpoint và 0 lời gọi</b> trong toàn kho (đo 15/09). Người quên mật khẩu ⛔ có đường nào:
 * hệ ⛔ có luồng "quên mật khẩu" qua thư, và mật khẩu tạm chỉ đặt được <b>lúc tạo</b> tài khoản.
 *
 * <h2>Bốn ràng buộc, mỗi cái trả lời một câu hỏi khác nhau</h2>
 *
 * <ul>
 *   <li><b>⛔ tự đặt lại cho chính mình</b> ({@code ADM-2025}) — cửa này ⛔ hỏi mật khẩu cũ, nên nó
 *       là đường vòng quanh {@code AUTH-0001} của lối tự đổi mật khẩu. Cùng lý lẽ {@code ADM-2021}.
 *   <li><b>Phải nhập lại mã 2FA</b> (T61.42, ở tầng controller) — quyền {@code adm:user:reset-password}
 *       cho phép chiếm bất kỳ tài khoản nào, kể cả tài khoản quyền cao hơn.
 *   <li><b>{@code mustChangePassword = true}</b> — người đặt BIẾT mật khẩu tạm, nên nó phải chết ngay
 *       sau lần đăng nhập đầu (đúng luật {@code create()} đang theo).
 *   <li><b>Thu hồi mọi phiên</b> — nếu lý do đặt lại là "tài khoản nghi bị chiếm" thì để phiên cũ
 *       sống là vá một nửa: kẻ chiếm vẫn còn refresh token.
 * </ul>
 *
 * <p>⚠ Chính chủ được <b>báo</b> ({@link CanhBaoTaiKhoanService}) — T61.36. Một lượt đặt lại mật khẩu
 * mà người chủ ⛔ hay biết là đúng hình dạng mà ASVS 2.2.3 gọi tên.
 */
@Service
public class DatLaiMatKhauService {

    private static final Logger log = LoggerFactory.getLogger(DatLaiMatKhauService.class);

    private final UserRepository users;
    private final PasswordPolicyService passwords;
    private final RefreshTokenService refreshTokens;
    private final AuthorityLoader authorityLoader;
    private final SecurityEventService securityEvents;
    private final CanhBaoTaiKhoanService canhBao;

    public DatLaiMatKhauService(
            UserRepository users,
            PasswordPolicyService passwords,
            RefreshTokenService refreshTokens,
            AuthorityLoader authorityLoader,
            SecurityEventService securityEvents,
            CanhBaoTaiKhoanService canhBao) {
        this.users = users;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.authorityLoader = authorityLoader;
        this.securityEvents = securityEvents;
        this.canhBao = canhBao;
    }

    /**
     * @param matKhauTam mật khẩu tạm do quản trị viên đặt — phải qua chính sách độ mạnh đang có hiệu lực
     * @throws PermissionDeniedException {@code ADM-2025} khi tự đặt lại cho chính mình
     */
    @Transactional
    public void datLai(UUID publicId, String matKhauTam, ClientInfo client) {
        User user = users.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        Long nguoiLam = AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
        if (user.getId().equals(nguoiLam)) {
            throw new PermissionDeniedException(ErrorCode.ADM_2025);
        }
        passwords.validate(matKhauTam, user.getUsername(), "matKhauTam");

        Instant now = Instant.now();
        // T73.8 — hash · mốc đổi · cờ buộc đổi · HẠN của mật khẩu tạm, một chỗ cho cả hai đường phát.
        passwords.ganMatKhauTam(user, matKhauTam, now);
        users.save(user);

        int soPhien = refreshTokens.revokeAllSessions(user.getId(), SessionRevokeReason.PASSWORD_CHANGED, now);
        authorityLoader.invalidate(user.getPublicId());

        log.info("Quản trị đặt lại mật khẩu cho {}, thu hồi {} phiên", user.getUsername(), soPhien);
        securityEvents.record(
                SecurityEventType.PASSWORD_RESET_BY_ADMIN,
                user.getUsername(),
                user.getId(),
                client,
                "{\"nguoiLam\":%s,\"soPhienThuHoi\":%d}".formatted(nguoiLam == null ? "null" : nguoiLam, soPhien));
        canhBao.matKhauDaDoi(user, true);
    }
}
