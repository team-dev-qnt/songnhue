package com.songnhue.core.application.maintenance;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.job.JobContext;
import com.songnhue.core.application.job.JobHandler;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.infra.identity.TokenDenylistRepository;
import com.songnhue.core.infra.identity.UserSessionRepository;

/**
 * Dọn token trong denylist và phiên quá hạn lưu trữ.
 *
 * <p><b>Vì sao cần.</b> {@code token_denylist} bị đọc ở <i>mỗi</i> request; để nó phình vô hạn thì
 * mọi request đều chậm dần theo tuổi hệ thống — một kiểu suy giảm âm thầm, khó truy vì không có sự
 * kiện nào tương ứng. Bản ghi quá hạn cũng vô dụng: token của nó tự hết hiệu lực rồi.
 *
 * <p>Phiên hết hạn thì giữ thêm 30 ngày, không xoá ngay: đó là dấu vết đăng nhập gần đây, cần khi
 * điều tra sự cố bảo mật.
 *
 * <p><b>Đã chuyển từ {@code @Scheduled} sang hàng đợi job</b> (nợ WS-5, trả ở T6.8). Chạy thẳng bằng
 * {@code @Scheduled} thì lên ≥2 node là cả hai cùng chạy, và khi nó lỗi thì không ở đâu nhìn thấy —
 * không trạng thái, không lần thử lại, không dòng nào trên màn hình theo dõi.
 */
@Component
public class TokenCleanupHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupHandler.class);

    /** Giữ phiên đã hết hạn thêm khoảng này để còn tra lịch sử đăng nhập. */
    private static final Duration SESSION_RETENTION_AFTER_EXPIRY = Duration.ofDays(30);

    private final UserSessionRepository sessions;
    private final TokenDenylistRepository denylist;

    public TokenCleanupHandler(UserSessionRepository sessions, TokenDenylistRepository denylist) {
        this.sessions = sessions;
        this.denylist = denylist;
    }

    @Override
    public String jobType() {
        return JobTypes.TOKEN_CLEANUP;
    }

    @Override
    @Transactional
    public void handle(JobContext context) {
        Instant now = Instant.now();
        int removedTokens = denylist.deleteExpired(now);
        int removedSessions = sessions.deleteExpiredBefore(now.minus(SESSION_RETENTION_AFTER_EXPIRY));
        log.info("Dọn dẹp: {} token hết hạn, {} phiên quá hạn lưu trữ", removedTokens, removedSessions);
    }
}
