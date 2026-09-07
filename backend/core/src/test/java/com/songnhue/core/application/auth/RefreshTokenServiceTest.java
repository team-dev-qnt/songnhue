package com.songnhue.core.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.common.config.JwtProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AuthenticationException;
import com.songnhue.core.common.util.HashUtils;
import com.songnhue.core.domain.identity.SessionRevokeReason;
import com.songnhue.core.domain.identity.UserSession;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserSessionRepository;
import com.songnhue.core.testsupport.DirectTransactionManager;

/**
 * Kiểm cơ chế xoay vòng refresh token và <b>phát hiện token bị đánh cắp</b> (T5.2, T5.3).
 *
 * <p>Đây là phần bảo vệ duy nhất chống lại tình huống refresh token lọt ra ngoài. Nó cũng là loại cơ
 * chế dễ hỏng âm thầm nhất: hỏng thì mọi thứ vẫn chạy đúng, chỉ là kẻ trộm dùng token vô thời hạn mà
 * không ai biết. Vì vậy test ở đây bám vào <i>hành vi phải xảy ra</i> chứ không chỉ vào mã lỗi trả về.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private UserSessionRepository sessions;

    @Mock
    private SecurityEventService securityEvents;

    private RefreshTokenService service;

    private final Instant now = Instant.parse("2026-08-14T03:00:00Z");
    private final ClientInfo client = new ClientInfo("10.0.0.5", "JUnit", "Test");

    /** Bản ghi "đã lưu" gần nhất, để lấy lại token vừa phát. */
    private final Map<String, UserSession> saved = new HashMap<>();

    @BeforeEach
    void setUp() {
        service =
                new RefreshTokenService(sessions, securityEvents, new JwtProperties(), new DirectTransactionManager());
    }

    @Test
    @DisplayName("Mở phiên: token gốc KHÔNG được lưu vào CSDL, chỉ lưu SHA-256")
    void storesOnlyHash() {
        when(sessions.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken issued = service.openSession(7L, client, now);

        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(sessions).save(captor.capture());
        UserSession stored = captor.getValue();

        assertThat(stored.getRefreshTokenHash())
                .isEqualTo(HashUtils.sha256Hex(issued.rawToken()))
                .hasSize(HashUtils.SHA256_HEX_LENGTH)
                // Lộ cả bảng sessions cũng không dựng lại được token nào
                .isNotEqualTo(issued.rawToken());
        assertThat(stored.getUserId()).isEqualTo(7L);
        assertThat(issued.userId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Xoay vòng: bản ghi cũ bị đánh dấu ROTATED, bản mới cùng family")
    void rotationMarksOldAndKeepsFamily() {
        UUID familyId = UUID.randomUUID();
        UserSession current = activeSession(7L, familyId, "token-cu");
        when(sessions.findByRefreshTokenHash(HashUtils.sha256Hex("token-cu"))).thenReturn(Optional.of(current));
        when(sessions.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken rotated = service.rotate("token-cu", client, now);

        assertThat(current.getRotatedAt()).isEqualTo(now);
        assertThat(current.getRevokedReason()).isEqualTo(SessionRevokeReason.ROTATED.name());
        assertThat(rotated.familyId()).isEqualTo(familyId);
        assertThat(rotated.rawToken()).isNotEqualTo("token-cu");
    }

    @Test
    @DisplayName("⚠ Dùng lại token ĐÃ XOAY → thu hồi CẢ family + ghi sự kiện CRITICAL + AUTH-0008")
    void reuseOfRotatedTokenRevokesWholeFamily() {
        UUID familyId = UUID.randomUUID();
        UserSession rotatedAlready = activeSession(7L, familyId, "token-bi-trom");
        rotatedAlready.markRotated(now.minusSeconds(60));

        when(sessions.findByRefreshTokenHash(HashUtils.sha256Hex("token-bi-trom")))
                .thenReturn(Optional.of(rotatedAlready));
        when(sessions.revokeFamily(eq(familyId), anyString(), any())).thenReturn(3);

        assertThatThrownBy(() -> service.rotate("token-bi-trom", client, now))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_0008);

        // Hệ thống không biết ai là kẻ trộm trong hai bên → cắt hết, buộc đăng nhập lại bằng mật khẩu
        verify(sessions).revokeFamily(familyId, SessionRevokeReason.REUSE_DETECTED.name(), now);
        verify(securityEvents)
                .record(eq(SecurityEventType.REFRESH_REUSE_DETECTED), eq(null), eq(7L), eq(client), anyString());
        // Và tuyệt đối KHÔNG phát token mới cho lần gọi này
        verify(sessions, never()).save(any(UserSession.class));
    }

    @Test
    @DisplayName("Token không tồn tại → AUTH-0002, KHÔNG thu hồi family nào")
    void unknownTokenDoesNotTriggerRevocation() {
        when(sessions.findByRefreshTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("token-la", client, now))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_0002);

        // Quan trọng: token rác không được phép làm người dùng thật bị đăng xuất hàng loạt
        verify(sessions, never()).revokeFamily(any(), anyString(), any());
    }

    @Test
    @DisplayName("Token đã thu hồi vì lý do khác (đăng xuất) → AUTH-0002, không coi là tấn công")
    void revokedForOtherReasonIsNotAnAttack() {
        UUID familyId = UUID.randomUUID();
        UserSession loggedOut = activeSession(7L, familyId, "token-da-dang-xuat");
        loggedOut.revoke(SessionRevokeReason.LOGOUT, now.minusSeconds(10));

        when(sessions.findByRefreshTokenHash(HashUtils.sha256Hex("token-da-dang-xuat")))
                .thenReturn(Optional.of(loggedOut));

        assertThatThrownBy(() -> service.rotate("token-da-dang-xuat", client, now))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_0002);

        verify(securityEvents, never())
                .record(eq(SecurityEventType.REFRESH_REUSE_DETECTED), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("Token hết hạn → AUTH-0002")
    void expiredTokenRejected() {
        UserSession expired = activeSession(7L, UUID.randomUUID(), "token-het-han");
        expired.setExpiresAt(now.minusSeconds(1));
        when(sessions.findByRefreshTokenHash(HashUtils.sha256Hex("token-het-han")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("token-het-han", client, now))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @DisplayName("Đổi mật khẩu / khoá tài khoản → thu hồi mọi phiên của tài khoản")
    void revokeAllSessions() {
        when(sessions.revokeAllOfUser(eq(7L), anyString(), any())).thenReturn(4);

        assertThat(service.revokeAllSessions(7L, SessionRevokeReason.PASSWORD_CHANGED, now))
                .isEqualTo(4);
        verify(sessions, times(1)).revokeAllOfUser(7L, SessionRevokeReason.PASSWORD_CHANGED.name(), now);
    }

    // -------------------------------------------------------------------------

    private UserSession activeSession(Long userId, UUID familyId, String rawToken) {
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setFamilyId(familyId);
        session.setRefreshTokenHash(HashUtils.sha256Hex(rawToken));
        session.setExpiresAt(now.plusSeconds(3600));
        saved.put(rawToken, session);
        return session;
    }
}
