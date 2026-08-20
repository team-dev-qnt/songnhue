package com.songnhue.core.application.backup;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.domain.backup.BackupTrigger;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;

/**
 * Chạy một lượt sao lưu trong hàng đợi (T7.1, T7.4).
 *
 * <p><b>{@code maxAttempts = 1} — cố ý không thử lại.</b> {@code pg_dump} hỏng gần như luôn vì một
 * trong ba nguyên nhân: hết đĩa, CSDL không nối được, sai mật khẩu. Không nguyên nhân nào tự khỏi
 * sau vài giây, nên thử lại chỉ tạo thêm hai lượt hỏng nữa và ba dòng cảnh báo cho cùng một sự cố.
 * Lượt tiếp theo là đêm mai, và sự cố thì đã có cảnh báo riêng của nó.
 *
 * <p><b>Vì sao handler này KHÔNG ném ngoại lệ khi sao lưu hỏng.</b> {@link BackupService} đã ghi
 * dòng {@code FAILED} và bắn sự kiện bảo mật {@code BACKUP_FAILED} — sự cố đã hiện ra ở đúng chỗ
 * người vận hành nhìn. Ném thêm ngoại lệ chỉ để job hiện màu đỏ là báo hai lần cho một chuyện; ngược
 * lại, ném thì mất thông tin vì bảng {@code jobs} bị dọn theo retention còn {@code system_backups}
 * thì không.
 */
@Component
public class BackupJobHandler implements JobHandler {

    private final BackupService backupService;
    private final ObjectMapper objectMapper;

    public BackupJobHandler(BackupService backupService, ObjectMapper objectMapper) {
        this.backupService = backupService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return JobTypes.DB_BACKUP;
    }

    @Override
    public short maxAttempts() {
        return 1;
    }

    @Override
    public void handle(JobContext context) throws Exception {
        BackupTrigger trigger = readTrigger(context.payload());
        context.progress(5);

        // Kết quả (thành hay bại) đã nằm trong system_backups + security_events.
        // Xem javadoc lớp về việc cố ý không ném lại khi FAILED.
        backupService.runBackup(trigger, context.requestedBy());
        context.progress(100);
    }

    private BackupTrigger readTrigger(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode trigger = node.get("trigger");
            return trigger == null ? BackupTrigger.SCHEDULED : BackupTrigger.valueOf(trigger.asText());
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            // Payload hỏng không được làm mất cả lượt sao lưu — mặc định về loại thường gặp nhất
            return BackupTrigger.SCHEDULED;
        }
    }
}
