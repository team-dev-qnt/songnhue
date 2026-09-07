package com.songnhue.core.application.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.identity.SessionRevokeReason;
import com.songnhue.core.domain.identity.UserSession;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserSessionRepository;

/**
 * Quản lý phiên đăng nhập của chính người dùng — M5.14 (T5.13).
 *
 * <p>Chức năng này tồn tại vì một lý do rất cụ thể: khi ai đó nghi tài khoản bị dùng trộm, thứ họ
 * cần ngay là <b>nhìn thấy</b> có mấy thiết bị đang đăng nhập và <b>đá</b> thiết bị lạ ra — không
 * phải đợi gọi điện cho quản trị viên.
 *
 * <p>Mỗi lần làm mới token sinh một bản ghi mới trong cùng family, nên danh sách này gộp theo
 * family: người dùng thấy "3 thiết bị", không phải "47 lần làm mới token".
 */
@Service
public class SessionService {

    private final UserSessionRepository sessions;
    private final SecurityEventService securityEvents;

    public SessionService(UserSessionRepository sessions, SecurityEventService securityEvents) {
        this.sessions = sessions;
        this.securityEvents = securityEvents;
    }

    /** Các phiên đang sống, gộp theo family, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<AuthService.ActiveSession> listOwnSessions(AuthenticatedUser current, Instant now) {
        return sessions.findActiveByUser(current.userId(), now).stream()
                // Trong một family chỉ có đúng một mắt xích chưa bị xoay — chính là phiên hiện tại
                .map(session -> new AuthService.ActiveSession(
                        session.getPublicId(),
                        session.getDeviceLabel(),
                        session.getIpAddress(),
                        session.getIssuedAt(),
                        session.getLastUsedAt(),
                        session.getExpiresAt(),
                        session.getFamilyId().equals(current.sessionFamilyId())))
                .toList();
    }

    /**
     * Đăng xuất từ xa một phiên.
     *
     * <p>Hai điểm quan trọng:
     *
     * <ul>
     *   <li>Nhận {@code public_id} và <b>đối chiếu chủ sở hữu</b> — thiếu bước này thì ai cũng đăng
     *       xuất được phiên của người khác chỉ bằng cách đoán một UUID (§4.2 chống IDOR).
     *   <li>Thu hồi cả family chứ không riêng một bản ghi: bỏ sót mắt xích nào là thiết bị đó vẫn
     *       làm mới token được và quay lại như chưa có gì xảy ra.
     * </ul>
     *
     * @throws ResourceNotFoundException khi phiên không tồn tại <i>hoặc</i> không thuộc về người gọi
     *     — cùng một câu trả lời cho cả hai, để không dò được UUID nào có thật
     */
    @Transactional
    public void revokeOwnSession(AuthenticatedUser current, UUID sessionPublicId, ClientInfo client, Instant now) {
        UserSession session = sessions.findByPublicId(sessionPublicId)
                .filter(s -> s.getUserId().equals(current.userId()))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));

        sessions.revokeFamily(session.getFamilyId(), SessionRevokeReason.REMOTE_LOGOUT.name(), now);
        securityEvents.record(
                SecurityEventType.SESSION_REVOKED,
                current.username(),
                current.userId(),
                client,
                "{\"sessionId\":\"" + sessionPublicId + "\",\"mode\":\"remote-logout\"}");
    }
}
