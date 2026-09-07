package com.songnhue.core.application.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.application.maintenance.MaintenanceModeService;
import com.songnhue.core.common.config.BackupProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.backup.BackupStatus;
import com.songnhue.core.domain.backup.BackupTrigger;
import com.songnhue.core.domain.backup.SystemBackup;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.backup.PostgresToolRunner;

/**
 * Khôi phục CSDL từ một bản sao lưu (T7.5, M5.11 · architecture-review.md §7.3).
 *
 * <h2>Đây là thao tác nguy hiểm nhất trong toàn hệ thống</h2>
 *
 * <p>Nó ghi đè <b>toàn bộ</b> dữ liệu, không hoàn tác được, và chạy trên chính CSDL mà ứng dụng đang
 * kết nối. SRS M5.11 yêu cầu có nút này trên giao diện, và quyết định §7.3 đảo lại đề xuất "chỉ
 * runbook" trước đây — nhưng kèm điều kiện: mỗi lớp chặn dưới đây là một phần của cam kết đó, không
 * phải chi tiết cài đặt tuỳ chọn.
 *
 * <h2>Sáu lớp chặn, theo thứ tự</h2>
 *
 * <ol>
 *   <li><b>Quyền + 2FA</b> — chặn ở controller: chỉ Super Admin, và phiên phải đã qua TOTP.
 *   <li><b>Chuỗi xác nhận</b> — người dùng gõ đúng tên hệ thống. Chống cái sai thật sự hay xảy ra là
 *       bấm nhầm nút, không phải kẻ tấn công.
 *   <li><b>Lý do bắt buộc</b> — đi vào nhật ký bảo mật. "Ai đó đã khôi phục hồi tháng trước mà không
 *       ai nhớ vì sao" là câu hỏi không trả lời được nếu không ép ghi ngay lúc làm.
 *   <li><b>Kiểm bản sao lưu trước khi đụng vào dữ liệu</b> — tệp còn đó, checksum khớp. Phát hiện
 *       bản dump hỏng <i>sau</i> khi đã xoá dữ liệu hiện tại là mất cả hai.
 *   <li><b>Chụp bản PRE_RESTORE</b> — đường lùi duy nhất khi khôi phục nhầm bản. Không có nó thì
 *       thao tác này là một chiều.
 *   <li><b>Chế độ bảo trì</b> — bật trước, tắt trong khối {@code finally}. Quên tắt là cả hệ thống
 *       kẹt ở trạng thái chỉ đọc.
 * </ol>
 *
 * <h2>⚠ Giới hạn phải biết trước</h2>
 *
 * <p>{@code pg_restore --clean} cần <b>xoá rồi tạo lại</b> từng đối tượng, mà ứng dụng đang giữ kết
 * nối tới chính CSDL đó. Các kết nối khác bị ngắt trước khi khôi phục chạy; pool của ứng dụng sẽ
 * nhận một loạt lỗi rồi tự nối lại. Trong khoảng đó, hệ thống <b>không phục vụ được</b> — đó là lý
 * do §7.3 khuyến nghị khôi phục ra Staging để đối chiếu trước, và vì sao khôi phục thẳng lên
 * Production là việc có phê duyệt chứ không phải nút bấm thường ngày.
 */
@Service
public class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

    /** Chuỗi người dùng phải gõ đúng để xác nhận. Cố ý không phải "OK" hay "XAC NHAN". */
    public static final String CONFIRMATION_PHRASE = "SONGNHUE";

    private static final int REASON_MIN_LENGTH = 10;

    private final BackupProperties properties;
    private final BackupService backupService;
    private final MaintenanceModeService maintenanceMode;
    private final PostgresToolRunner toolRunner;
    private final SecurityEventService securityEvents;

    public RestoreService(
            BackupProperties properties,
            BackupService backupService,
            MaintenanceModeService maintenanceMode,
            PostgresToolRunner toolRunner,
            SecurityEventService securityEvents) {
        this.properties = properties;
        this.backupService = backupService;
        this.maintenanceMode = maintenanceMode;
        this.toolRunner = toolRunner;
        this.securityEvents = securityEvents;
    }

    /**
     * Kiểm mọi điều kiện <b>trước</b> khi đặt việc vào hàng đợi.
     *
     * <p>Kiểm ở đây chứ không kiểm trong job: người dùng phải biết ngay lý do bị từ chối, chứ không
     * phải bấm nút, thấy "đã nhận yêu cầu", rồi vài phút sau mới phát hiện là chưa bật cấu hình.
     */
    public SystemBackup validateRequest(UUID backupPublicId, String confirmation, String reason) {
        if (!properties.isRestoreConfigured()) {
            throw new BusinessRuleException(ErrorCode.ADM_2010);
        }
        if (!CONFIRMATION_PHRASE.equals(
                confirmation == null ? null : confirmation.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessRuleException(ErrorCode.ADM_2011);
        }
        if (!StringUtils.hasText(reason) || reason.trim().length() < REASON_MIN_LENGTH) {
            throw new BusinessRuleException(ErrorCode.ADM_2011);
        }

        SystemBackup backup = backupService.get(backupPublicId);
        verifyUsable(backup);
        return backup;
    }

    /**
     * Bản sao lưu có thật sự dùng được không.
     *
     * <p>Đọc lại cả tệp để băm — với bản dump vài GB thì tốn vài chục giây, và đó là vài chục giây
     * đáng bỏ ra nhất trong toàn bộ quy trình: nó là lần cuối cùng còn có thể dừng lại mà chưa mất
     * gì.
     */
    void verifyUsable(SystemBackup backup) {
        if (backup.getStatus() != BackupStatus.SUCCEEDED || !StringUtils.hasText(backup.getFilePath())) {
            throw new BusinessRuleException(ErrorCode.ADM_2012);
        }
        Path file = Paths.get(backup.getFilePath());
        if (!Files.isReadable(file)) {
            log.error("Bản sao lưu {} không đọc được tại {}", backup.getPublicId(), file);
            throw new BusinessRuleException(ErrorCode.ADM_2012);
        }
        try {
            String actual = BackupService.sha256(file);
            if (!actual.equalsIgnoreCase(backup.getChecksumSha256())) {
                log.error(
                        "Checksum bản sao lưu {} KHÔNG khớp — ghi nhận {}, thực tế {}",
                        backup.getPublicId(),
                        backup.getChecksumSha256(),
                        actual);
                throw new BusinessRuleException(ErrorCode.ADM_2012);
            }
        } catch (IOException e) {
            throw new BusinessRuleException(ErrorCode.ADM_2012);
        }
    }

    /**
     * Thực hiện khôi phục. Gọi từ worker hàng đợi, <b>không bao giờ</b> từ luồng request.
     *
     * <p>⚠ Người thao tác truyền vào tường minh, <b>không</b> đọc từ {@code AuthContext}. Worker chạy
     * ở luồng khác luồng request, nên context ở đó luôn rỗng — đọc từ đó thì mọi sự kiện khôi phục
     * đều mang tên "system" và câu hỏi "ai đã ghi đè CSDL" không còn trả lời được.
     *
     * @param actor người ra lệnh, lấy từ {@code jobs.requested_by}
     * @param progress hàm báo tiến độ 0–100
     */
    public void performRestore(
            SystemBackup backup, String reason, RestoreActor actor, java.util.function.IntConsumer progress) {
        recordEvent(SecurityEventType.DATABASE_RESTORE_STARTED, backup, reason, actor);

        maintenanceMode.enable("Khôi phục dữ liệu từ bản " + backup.getFileName());
        progress.accept(5);

        try {
            // Lớp 5: bản chụp trạng thái hiện tại. Nếu bước này hỏng thì DỪNG — khôi phục mà không
            // có đường lùi là đánh cược toàn bộ dữ liệu đang có vào việc chọn đúng bản.
            SystemBackup safetyNet = backupService.runBackup(BackupTrigger.PRE_RESTORE, actor.userId());
            if (safetyNet.getStatus() != BackupStatus.SUCCEEDED) {
                throw new BusinessRuleException(ErrorCode.ADM_2008);
            }
            progress.accept(30);

            terminateOtherConnections();
            progress.accept(35);

            PostgresToolRunner.ToolResult result = toolRunner.run(
                    restoreCommand(Paths.get(backup.getFilePath())),
                    properties.getRestorePassword(),
                    backupService.backupDirectory(),
                    properties.getTimeout());

            // ⚠ pg_restore trả mã khác 0 cả khi chỉ có cảnh báo (VD "role không tồn tại" với
            // --no-owner). Dùng --exit-on-error để mọi mã khác 0 đều là lỗi thật, không phải đoán.
            if (!result.isSuccess()) {
                throw new RestoreFailedException(result.output());
            }

            progress.accept(95);
            recordEvent(SecurityEventType.DATABASE_RESTORE_FINISHED, backup, reason, actor);
            log.warn("Khôi phục XONG từ {} — lý do: {}", backup.getFileName(), reason);

        } catch (RestoreFailedException e) {
            recordEvent(SecurityEventType.DATABASE_RESTORE_FAILED, backup, reason, actor);
            log.error("Khôi phục THẤT BẠI từ {}:{}{}", backup.getFileName(), System.lineSeparator(), e.getMessage());
            throw new BusinessRuleException(ErrorCode.ADM_2013);
        } catch (IOException e) {
            recordEvent(SecurityEventType.DATABASE_RESTORE_FAILED, backup, reason, actor);
            throw new BusinessRuleException(ErrorCode.ADM_2013);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordEvent(SecurityEventType.DATABASE_RESTORE_FAILED, backup, reason, actor);
            throw new BusinessRuleException(ErrorCode.ADM_2013);
        } finally {
            // BẮT BUỘC nằm trong finally. Khôi phục hỏng giữa chừng mà cờ bảo trì còn bật thì hệ
            // thống kẹt ở chế độ chỉ đọc, và người duy nhất tắt được lại đang bận xử lý sự cố.
            safelyDisableMaintenance();
            progress.accept(100);
        }
    }

    private List<String> restoreCommand(Path source) {
        List<String> command = new ArrayList<>();
        command.add(properties.getPgRestorePath());
        command.add("--host=" + properties.getHost());
        command.add("--port=" + properties.getPort());
        command.add("--username=" + properties.getRestoreUsername());
        command.add("--dbname=" + properties.getDatabase());
        command.add("--no-password");
        // --clean + --if-exists: xoá đối tượng cũ trước khi tạo lại, không báo lỗi với đối tượng
        // chưa tồn tại. Thiếu --if-exists thì mọi DROP của bảng mới thêm đều thành lỗi.
        command.add("--clean");
        command.add("--if-exists");
        command.add("--no-owner");
        command.add("--no-privileges");
        // Dừng ở lỗi ĐẦU TIÊN. Mặc định pg_restore chạy tiếp và kết thúc "có vẻ ổn" với một CSDL
        // thiếu bảng — kiểu hỏng tệ nhất vì nó không giống hỏng.
        command.add("--exit-on-error");
        command.add("--single-transaction");
        command.add(source.toAbsolutePath().toString());
        return command;
    }

    /**
     * Ngắt các kết nối khác tới CSDL.
     *
     * <p>{@code pg_restore --clean} phải {@code DROP} từng đối tượng, mà {@code DROP} chờ vô hạn khi
     * còn phiên khác đang giữ khoá. Không ngắt trước thì khôi phục treo tới hết hạn chờ rồi thất bại
     * — sau khi đã bật bảo trì và chụp bản PRE_RESTORE, tức là tốn công nhất mà không được gì.
     *
     * <p>Chạy qua {@code psql} chứ không qua pool của ứng dụng: câu lệnh này ngắt <i>chính</i> các
     * kết nối trong pool đó.
     */
    private void terminateOtherConnections() throws IOException, InterruptedException {
        List<String> command = List.of(
                properties.getPsqlPath(),
                "--host=" + properties.getHost(),
                "--port=" + properties.getPort(),
                "--username=" + properties.getRestoreUsername(),
                // Nối vào `postgres`, KHÔNG nối vào CSDL đích: phiên của chính lệnh này cũng nằm
                // trong danh sách bị ngắt nếu nối vào đó.
                "--dbname=postgres",
                "--no-password",
                "--command=SELECT pg_terminate_backend(pid) FROM pg_stat_activity " + "WHERE datname = '"
                        + properties.getDatabase() + "' AND pid <> pg_backend_pid()");

        PostgresToolRunner.ToolResult result =
                toolRunner.run(command, properties.getRestorePassword(), null, java.time.Duration.ofMinutes(1));
        if (!result.isSuccess()) {
            log.warn("Không ngắt được kết nối khác trước khi khôi phục: {}", result.output());
        }
    }

    private void safelyDisableMaintenance() {
        try {
            maintenanceMode.disable("Kết thúc khôi phục dữ liệu");
        } catch (RuntimeException e) {
            // Khôi phục vừa ghi đè bảng settings — có thể chưa đọc/ghi được ngay. Ghi ERROR để
            // người vận hành biết phải tắt tay, và chỉ rõ chỗ làm việc đó.
            log.error(
                    "KHÔNG tắt được chế độ bảo trì sau khi khôi phục. Tắt tay: "
                            + "UPDATE settings SET setting_value = 'false' WHERE setting_key = 'system.maintenance-mode'; "
                            + "rồi khởi động lại ứng dụng để xoá cache. Xem docs/runbook/khoi-phuc-du-lieu.md",
                    e);
        }
    }

    private void recordEvent(SecurityEventType type, SystemBackup backup, String reason, RestoreActor actor) {
        String detail = "{\"backup\":\"%s\",\"file\":\"%s\",\"reason\":\"%s\"}"
                .formatted(
                        backup.getPublicId(),
                        backup.getFileName(),
                        reason == null ? "" : reason.replace('"', '\'').replace('\n', ' '));
        securityEvents.record(type, actor.username(), actor.userId(), ClientInfo.unknown(), detail);
    }

    /**
     * Người ra lệnh khôi phục, mang qua ranh giới luồng.
     *
     * @param userId khoá nội bộ, lấy từ {@code jobs.requested_by}
     * @param username giữ dạng text để nhật ký còn đọc được kể cả sau khi CSDL bị ghi đè
     */
    public record RestoreActor(Long userId, String username) {

        public static RestoreActor of(AuthenticatedUser user) {
            return user == null ? system() : new RestoreActor(user.userId(), user.username());
        }

        public static RestoreActor system() {
            return new RestoreActor(null, "system");
        }
    }

    /** Lỗi nội bộ mang theo đầu ra của {@code pg_restore} để ghi nhật ký, không ra tới API. */
    private static final class RestoreFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RestoreFailedException(String output) {
            super(output);
        }
    }
}
