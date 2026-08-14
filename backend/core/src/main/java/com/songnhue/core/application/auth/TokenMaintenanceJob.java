package com.songnhue.core.application.auth;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.infra.identity.TokenDenylistRepository;
import com.songnhue.core.infra.identity.UserSessionRepository;

/**
 * Dọn phiên và denylist đã hết hạn.
 *
 * <p><b>Vì sao cần.</b> {@code token_denylist} bị đọc ở <i>mỗi</i> request; để nó phình vô hạn thì
 * mọi request đều chậm dần theo tuổi hệ thống — một loại suy giảm âm thầm, khó truy vì không có sự
 * kiện nào tương ứng. Bản ghi quá hạn cũng vô dụng: token của nó tự hết hiệu lực rồi.
 *
 * <p>Phiên hết hạn thì giữ thêm 30 ngày, không xoá ngay: đó là dấu vết đăng nhập gần đây, cần khi
 * điều tra sự cố bảo mật.
 *
 * <p>⚠ <b>Tạm thời</b> chạy bằng {@code @Scheduled}. WS-6 / T6.8 chuyển sang hàng đợi job trong DB
 * (có trạng thái, có retry, có màn hình theo dõi) và bọc ShedLock để lên ≥2 node không chạy trùng.
 * Ở v1 một node thì cách này là đủ, và bỏ hẳn việc dọn dẹp mới là lựa chọn sai.
 */
@Component
@ConditionalOnProperty(name = "app.worker-enabled", havingValue = "true", matchIfMissing = true)
public class TokenMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(TokenMaintenanceJob.class);

    /** Giữ phiên đã hết hạn thêm khoảng này để còn tra lịch sử đăng nhập. */
    private static final Duration SESSION_RETENTION_AFTER_EXPIRY = Duration.ofDays(30);

    private final UserSessionRepository sessions;
    private final TokenDenylistRepository denylist;

    public TokenMaintenanceJob(UserSessionRepository sessions, TokenDenylistRepository denylist) {
        this.sessions = sessions;
        this.denylist = denylist;
    }

    /** 03:15 hằng ngày — ngoài giờ hành chính, và lệch giờ với job sao lưu 02:00 (WS-7). */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpired() {
        Instant now = Instant.now();
        int removedTokens = denylist.deleteExpired(now);
        int removedSessions = sessions.deleteExpiredBefore(now.minus(SESSION_RETENTION_AFTER_EXPIRY));

        if (removedTokens > 0 || removedSessions > 0) {
            log.info("Dọn dẹp: {} token hết hạn, {} phiên quá hạn lưu trữ", removedTokens, removedSessions);
        }
    }
}
