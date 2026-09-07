package com.songnhue.core.application.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.config.BackupProperties;
import com.songnhue.core.domain.backup.BackupStatus;
import com.songnhue.core.domain.backup.BackupTrigger;
import com.songnhue.core.domain.backup.SystemBackup;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.backup.PostgresToolRunner;
import com.songnhue.core.infra.backup.SystemBackupRepository;
import com.songnhue.core.testsupport.DirectTransactionManager;

/**
 * Sao lưu CSDL (T7.1, T7.4).
 *
 * <p>Không gọi {@code pg_dump} thật — {@link PostgresToolRunner} bị thay thế. Cái được kiểm ở đây là
 * <b>hành vi quanh nó</b>, và đó mới là chỗ hay sai: lượt hỏng có được ghi lại không, tệp dở dang có
 * bị xoá không, checksum tính trên cái gì. Việc gọi thật {@code pg_dump} là bài kiểm tích hợp.
 */
class BackupServiceTest {

    @TempDir
    Path tempDir;

    private BackupProperties properties;
    private SystemBackupRepository repository;
    private PostgresToolRunner toolRunner;
    private SettingService settings;
    private SecurityEventService securityEvents;
    private BackupService service;

    @BeforeEach
    void setUp() {
        properties = new BackupProperties();
        properties.setDirectory(tempDir.toString());
        properties.setHost("localhost");
        properties.setPort(5432);
        properties.setDatabase("songnhue");
        properties.setDumpPassword("mat-khau-doc");
        properties.setTimeout(Duration.ofMinutes(5));

        repository = mock(SystemBackupRepository.class);
        toolRunner = mock(PostgresToolRunner.class);
        settings = mock(SettingService.class);
        securityEvents = mock(SecurityEventService.class);

        when(settings.getInt(anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
        when(settings.getBoolean(anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(1));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(repository.findExpired(any())).thenReturn(List.of());

        service = new BackupService(
                properties, repository, toolRunner, settings, securityEvents, new DirectTransactionManager());
    }

    @Test
    @DisplayName("pg_dump thành công → SUCCEEDED, có kích thước và checksum của tệp THẬT trên đĩa")
    void recordsSuccessWithChecksum() throws Exception {
        // Giả lập pg_dump: ghi ra đúng tệp mà --file trỏ tới
        when(toolRunner.run(any(), anyString(), any(), any())).thenAnswer(call -> {
            List<String> command = call.getArgument(0);
            writeDumpFile(command, "noi-dung-ban-dump");
            return new PostgresToolRunner.ToolResult(0, "");
        });

        SystemBackup result = service.runBackup(BackupTrigger.SCHEDULED, 7L);

        assertThat(result.getStatus()).isEqualTo(BackupStatus.SUCCEEDED);
        assertThat(result.getSizeBytes()).isEqualTo("noi-dung-ban-dump".getBytes().length);
        assertThat(result.getChecksumSha256()).hasSize(64);
        assertThat(result.getRequestedBy()).isEqualTo(7L);
        verify(securityEvents)
                .record(org.mockito.ArgumentMatchers.eq(SecurityEventType.BACKUP_CREATED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Checksum là của nội dung thật — đổi nội dung thì đổi checksum")
    void checksumFollowsContent() throws Exception {
        // ⚠ Phải dùng doAnswer(...).when(mock) chứ không phải when(mock.run(...)).thenAnswer(...)
        // khi ĐẶT LẠI stub: dạng thứ hai thật sự GỌI mock để ghi nhận lời gọi, và lời gọi đó chạy
        // qua chính answer đang được thay thế — với toàn tham số null.
        stubDumpWriting("noi-dung-A");
        String first = service.runBackup(BackupTrigger.MANUAL, null).getChecksumSha256();

        stubDumpWriting("noi-dung-B");
        String second = service.runBackup(BackupTrigger.MANUAL, null).getChecksumSha256();

        assertThat(first).isNotEqualTo(second);
    }

    private void stubDumpWriting(String content) throws Exception {
        org.mockito.Mockito.doAnswer(call -> {
                    writeDumpFile(call.getArgument(0), content);
                    return new PostgresToolRunner.ToolResult(0, "");
                })
                .when(toolRunner)
                .run(any(), any(), any(), any());
    }

    @Test
    @DisplayName("⚠ pg_dump hỏng → ghi FAILED và XOÁ tệp dở dang")
    void deletesPartialFileOnFailure() throws Exception {
        when(toolRunner.run(any(), anyString(), any(), any())).thenAnswer(call -> {
            // pg_dump hỏng giữa chừng vẫn để lại một tệp dở dang trên đĩa
            writeDumpFile(call.getArgument(0), "mot-nua-du-lieu");
            return new PostgresToolRunner.ToolResult(1, "pg_dump: error: No space left on device");
        });

        SystemBackup result = service.runBackup(BackupTrigger.SCHEDULED, null);

        assertThat(result.getStatus()).isEqualTo(BackupStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("No space left on device");
        assertThat(dumpFilesInDirectory())
                .as("tệp dở dang trông như bản sao lưu hợp lệ — phải bị xoá")
                .isEmpty();
        verify(securityEvents)
                .record(org.mockito.ArgumentMatchers.eq(SecurityEventType.BACKUP_FAILED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("⚠ pg_dump báo mã 0 nhưng tệp rỗng → vẫn tính là THẤT BẠI")
    void treatsEmptyFileAsFailure() throws Exception {
        when(toolRunner.run(any(), anyString(), any(), any())).thenAnswer(call -> {
            writeDumpFile(call.getArgument(0), "");
            return new PostgresToolRunner.ToolResult(0, "");
        });

        SystemBackup result = service.runBackup(BackupTrigger.SCHEDULED, null);

        assertThat(result.getStatus()).isEqualTo(BackupStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("rỗng");
    }

    @Test
    @DisplayName("⛔ Mật khẩu đi qua PGPASSWORD, KHÔNG nằm trong tham số dòng lệnh")
    void neverPutsPasswordOnCommandLine() throws Exception {
        when(toolRunner.run(any(), anyString(), any(), any())).thenAnswer(call -> {
            writeDumpFile(call.getArgument(0), "x");
            return new PostgresToolRunner.ToolResult(0, "");
        });

        service.runBackup(BackupTrigger.SCHEDULED, null);

        List<String> dumpCommand = capturedDumpCommand();
        assertThat(dumpCommand)
                .as("tham số dòng lệnh đọc được bằng `ps` từ mọi tiến trình khác trên máy")
                .noneMatch(arg -> arg.contains("mat-khau-doc"));
        assertThat(dumpCommand).contains("--format=custom", "--no-owner", "--no-password");

        verify(toolRunner, org.mockito.Mockito.atLeastOnce())
                .run(any(), org.mockito.ArgumentMatchers.eq("mat-khau-doc"), any(), any());
    }

    @Test
    @DisplayName("Chưa cấu hình mật khẩu vai trò đọc → từ chối ngay bằng ADM-2008")
    void refusesWhenNotConfigured() {
        properties.setDumpPassword("");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> service.runBackup(BackupTrigger.SCHEDULED, null)))
                .isInstanceOf(com.songnhue.core.common.exception.BusinessRuleException.class);
    }

    // -------------------------------------------------------------------------

    /**
     * Lấy đường dẫn từ tham số {@code --file=} rồi ghi nội dung vào đó, đúng như pg_dump làm.
     *
     * <p>Bỏ qua lệnh không có {@code --file=}: {@code BackupService} còn gọi {@code pg_dump --version}
     * để ghi lại phiên bản máy chủ, và lệnh đó không sinh tệp nào.
     */
    private static void writeDumpFile(List<String> command, String content) throws Exception {
        Optional<String> target = command.stream()
                .filter(arg -> arg.startsWith("--file="))
                .map(arg -> arg.substring("--file=".length()))
                .findFirst();
        if (target.isPresent()) {
            Files.writeString(Path.of(target.get()), content);
        }
    }

    /** Lệnh dump thật (có {@code --file=}), bỏ qua lệnh {@code --version}. */
    private List<String> capturedDumpCommand() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(toolRunner, org.mockito.Mockito.atLeastOnce()).run(captor.capture(), any(), any(), any());
        return captor.getAllValues().stream()
                .filter(command -> command.stream().anyMatch(arg -> arg.startsWith("--file=")))
                .findFirst()
                .orElseThrow();
    }

    private List<Path> dumpFilesInDirectory() throws Exception {
        try (var stream = Files.list(tempDir)) {
            return stream.filter(p -> p.toString().endsWith(".dump")).toList();
        }
    }
}
