package com.songnhue.core.application.backup;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.domain.backup.SystemBackup;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;

/**
 * Chạy khôi phục CSDL trong hàng đợi (T7.5, M5.11).
 *
 * <p><b>{@code maxAttempts = 1} — tuyệt đối không thử lại.</b> Đây là điểm khác biệt quan trọng nhất
 * so với mọi handler khác. Khôi phục hỏng giữa chừng để lại CSDL ở trạng thái dở dang; chạy lại lần
 * hai là ghi đè tiếp lên đúng chỗ đang dở, và bản {@code PRE_RESTORE} chụp ở lượt thứ hai sẽ chụp
 * lại chính trạng thái hỏng đó — <b>đè mất đường lùi</b>. Một lượt hỏng cần người nhìn vào, không
 * cần máy thử lại.
 *
 * <p>Handler này <b>có</b> ném ngoại lệ khi thất bại (khác {@link BackupJobHandler}): khôi phục hỏng
 * phải hiện đỏ ở màn hình theo dõi việc, vì người dùng đang ngồi nhìn tiến độ của đúng job này.
 */
@Component
public class RestoreJobHandler implements JobHandler {

    private final RestoreService restoreService;
    private final BackupService backupService;
    private final ObjectMapper objectMapper;

    public RestoreJobHandler(RestoreService restoreService, BackupService backupService, ObjectMapper objectMapper) {
        this.restoreService = restoreService;
        this.backupService = backupService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return JobTypes.DB_RESTORE;
    }

    @Override
    public short maxAttempts() {
        return 1;
    }

    @Override
    public void handle(JobContext context) throws Exception {
        JsonNode payload = objectMapper.readTree(context.payload());
        UUID backupId = UUID.fromString(payload.get("backupId").asText());
        String reason = payload.path("reason").asText("");
        String username = payload.path("actorUsername").asText("system");

        SystemBackup backup = backupService.get(backupId);

        // Kiểm lại checksum ngay trước khi ghi đè, dù controller đã kiểm lúc nhận yêu cầu. Giữa hai
        // thời điểm đó tệp có thể đã bị job dọn retention xoá hoặc bị ghi đè — và lần kiểm có ý
        // nghĩa là lần cuối cùng trước khi mất dữ liệu, không phải lần đầu.
        restoreService.verifyUsable(backup);

        restoreService.performRestore(
                backup, reason, new RestoreService.RestoreActor(context.requestedBy(), username), context::progress);
    }
}
