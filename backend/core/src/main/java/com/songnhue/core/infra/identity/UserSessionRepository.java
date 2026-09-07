package com.songnhue.core.infra.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.identity.UserSession;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /**
     * Tra phiên theo hash của refresh token.
     *
     * <p>Cố ý KHÔNG lọc {@code revoked_at IS NULL}: phải tìm thấy cả bản ghi đã bị xoay thì mới phát
     * hiện được việc dùng lại token cũ. Lọc ở đây là vô hiệu hoá toàn bộ cơ chế reuse detection —
     * request sẽ chỉ nhận "token không hợp lệ" và kẻ trộm cứ thế thử tiếp.
     */
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    /** Các phiên đang sống của một tài khoản — màn hình quản lý phiên M5.14. */
    @Query(
            """
            SELECT s FROM UserSession s
             WHERE s.userId = :userId
               AND s.revokedAt IS NULL
               AND s.expiresAt > :now
             ORDER BY s.issuedAt DESC
            """)
    List<UserSession> findActiveByUser(@Param("userId") Long userId, @Param("now") Instant now);

    Optional<UserSession> findByPublicId(UUID publicId);

    /**
     * Thu hồi cả token family trong MỘT câu lệnh.
     *
     * <p>Làm bằng một câu UPDATE chứ không phải nạp từng bản ghi rồi sửa: khi phát hiện token bị đánh
     * cắp thì tốc độ đóng cửa mới là thứ đáng kể, và một câu lệnh thì không có khe hở nào ở giữa cho
     * kẻ tấn công kịp xoay thêm một vòng token nữa.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE UserSession s
               SET s.revokedAt = :now, s.revokedReason = :reason
             WHERE s.familyId = :familyId
               AND s.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("reason") String reason, @Param("now") Instant now);

    /** Thu hồi mọi phiên của một tài khoản — dùng khi đổi mật khẩu hoặc khoá tài khoản (§4.1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE UserSession s
               SET s.revokedAt = :now, s.revokedReason = :reason
             WHERE s.userId = :userId
               AND s.revokedAt IS NULL
            """)
    int revokeAllOfUser(@Param("userId") Long userId, @Param("reason") String reason, @Param("now") Instant now);

    /**
     * Dọn phiên đã hết hạn từ lâu.
     *
     * <p>Giữ thêm một khoảng sau khi hết hạn để còn tra được lịch sử đăng nhập gần đây khi điều tra
     * sự cố; quá mốc đó thì bản ghi chỉ làm bảng phình lên.
     */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
