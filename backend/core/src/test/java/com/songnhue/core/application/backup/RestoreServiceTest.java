package com.songnhue.core.application.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.application.maintenance.MaintenanceModeService;
import com.songnhue.core.common.config.BackupProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.domain.backup.BackupTrigger;
import com.songnhue.core.domain.backup.SystemBackup;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.backup.PostgresToolRunner;

/**
 * Nút khôi phục M5.11 — phần ĐIỀU PHỐI (T68.3). Tới 19/09/2026 lớp này có <b>0</b> bài kiểm, dù nó là thao tác
 * nguy hiểm nhất của hệ.
 *
 * <p>⚠ Phạm vi (luật 28): {@link PostgresToolRunner} bị thay bằng đồ gá, nên bài này ⛔ chứng minh SQL nạp được
 * vào Postgres — vế ấy là {@code KhoiPhucVaoCsdlTrangTest} (công cụ thật, Postgres 16 thật, gọi đúng
 * {@link KeHoachKhoiPhuc}). Thứ bài này canh là những gì bài tích hợp ⛔ thấy vì nó ⛔ đi qua lớp này: thứ tự các
 * bước, thứ được ghép vào tệp nạp, quyền của tệp tạm mang toàn bộ dữ liệu dạng thuần, việc dọn tệp và tắt bảo
 * trì ở MỌI nhánh hỏng, và sự kiện bảo mật của từng nhánh.
 */
class RestoreServiceTest {

    @TempDir
    Path thuMuc;

    private BackupService backupService;
    private MaintenanceModeService baoTri;
    private SecurityEventService suKien;
    private JdbcTemplate jdbc;
    private PostgresToolRunner toolRunner;
    private RestoreService service;
    private SystemBackup banSaoLuu;

    private final List<String> buocDaChay = new ArrayList<>();
    private final AtomicReference<String> mucLucTruyenVao = new AtomicReference<>();
    private final AtomicReference<String> noiDungNap = new AtomicReference<>();
    private final AtomicReference<String> quyenTepNap = new AtomicReference<>();
    private final AtomicReference<List<String>> lenhSinhSql = new AtomicReference<>();
    private final AtomicReference<List<String>> lenhNap = new AtomicReference<>();
    private String demPhanMoRong = "3";
    private int maThoatNap;

    @BeforeEach
    void setUp() throws Exception {
        BackupProperties properties = new BackupProperties();
        properties.setDirectory(thuMuc.toString());
        properties.setHost("postgres");
        properties.setPort(5432);
        properties.setDatabase("songnhue");
        properties.setRestoreUsername("songnhue_owner");
        properties.setRestorePassword("mat-khau-khoi-phuc");
        properties.setPgRestorePath("pg_restore");
        properties.setPsqlPath("psql");
        properties.setTimeout(Duration.ofMinutes(5));

        backupService = mock(BackupService.class);
        when(backupService.backupDirectory()).thenReturn(thuMuc);
        when(backupService.dangCoLuotChay()).thenReturn(false);
        SystemBackup truocKhoiPhuc = new SystemBackup("pre-restore.dump", BackupTrigger.PRE_RESTORE);
        truocKhoiPhuc.markSucceeded(thuMuc.resolve("pre-restore.dump").toString(), 1, "abc", "16.4");
        when(backupService.runBackup(eq(BackupTrigger.PRE_RESTORE), any())).thenReturn(truocKhoiPhuc);

        baoTri = mock(MaintenanceModeService.class);
        suKien = mock(SecurityEventService.class);
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).thenReturn(7L);
        when(jdbc.queryForObject(contains("has_table_privilege"), eq(Boolean.class)))
                .thenReturn(false);

        toolRunner = mock(PostgresToolRunner.class);
        when(toolRunner.run(anyList(), any(), any(), any())).thenAnswer(call -> chayGia(call.getArgument(0)));

        Path dump = Files.writeString(thuMuc.resolve("songnhue-20260919.dump"), "ban-dump");
        banSaoLuu = new SystemBackup("songnhue-20260919.dump", BackupTrigger.SCHEDULED);
        banSaoLuu.markSucceeded(dump.toString(), 8, "x", "16.4");

        service = new RestoreService(properties, backupService, baoTri, toolRunner, suKien, jdbc);
    }

    @Test
    @DisplayName("⭐ Đường đúng: PRE_RESTORE → đếm extension → ngắt kết nối → liệt kê → sinh SQL → nạp MỘT giao dịch")
    void duongDungDiDungTrinhTu() throws Exception {
        service.performRestore(banSaoLuu, "diễn tập khôi phục", RestoreService.RestoreActor.system(), p -> {});

        assertThat(buocDaChay).containsExactly("dem-phan-mo-rong", "ngat-ket-noi", "liet-ke", "sinh-sql", "nap");
        InOrder thuTu = inOrder(baoTri, backupService, toolRunner);
        thuTu.verify(baoTri).enable(anyString());
        thuTu.verify(backupService).runBackup(eq(BackupTrigger.PRE_RESTORE), any());
        // atLeastOnce: `run` được gọi 5 lần, mặc định times(1) của InOrder sẽ đỏ giả.
        thuTu.verify(toolRunner, atLeastOnce()).run(anyList(), any(), any(), any());
        thuTu.verify(baoTri).disable(anyString());

        assertThat(mucLucTruyenVao.get())
                .as("pg_restore phải nhận mục lục ĐÃ LỌC — ⛔ mục lục thô")
                .doesNotContain(" EXTENSION - ")
                .doesNotContain("COMMENT - EXTENSION")
                .doesNotContain("spatial_ref_sys")
                .contains("TABLE public bang_0 songnhue_owner");
        assertThat(lenhSinhSql.get())
                .containsAll(KeHoachKhoiPhuc.CO_SINH_SQL)
                .doesNotContain("--no-" + "privileges")
                .noneMatch(t -> t.startsWith("--dbname"));
        assertThat(lenhNap.get()).containsAll(KeHoachKhoiPhuc.CO_NAP).contains("--dbname=songnhue");
        assertThat(noiDungNap.get())
                .as("tệp nạp = khối trước-khi-nạp (bỏ bảng phân mảnh + gỡ quyền mặc định) rồi mới tới phần thân")
                .startsWith(KeHoachKhoiPhuc.khoiTruocKhiNap())
                .endsWith("THAN-BAN-DUMP;\n");
        assertThat(quyenTepNap.get())
                .as("tệp nạp mang TOÀN BỘ CSDL dạng thuần (password_hash) ⇒ chỉ chủ tiến trình đọc được")
                .isEqualTo("rw-------");

        assertThat(tepConLai()).as("mọi tệp tạm phải bị XOÁ").containsExactly("songnhue-20260919.dump");
        verify(suKien).record(eq(SecurityEventType.DATABASE_RESTORE_FINISHED), any(), any(), any(), any());
        verify(suKien, never()).record(eq(SecurityEventType.DATABASE_RESTORE_FAILED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("⛔ Nạp hỏng (psql thoát 3) ⇒ ADM-2013 + sự kiện FAILED, tệp tạm vẫn bị xoá, bảo trì vẫn được TẮT")
    void napHongVanDonDep() {
        maThoatNap = 3;

        assertThatThrownBy(() -> service.performRestore(
                        banSaoLuu, "diễn tập khôi phục", RestoreService.RestoreActor.system(), p -> {}))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(ErrorCode.ADM_2013));

        verify(suKien).record(eq(SecurityEventType.DATABASE_RESTORE_FAILED), any(), any(), any(), any());
        verify(baoTri).disable(anyString());
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class));
        assertThat(tepConLai()).containsExactly("songnhue-20260919.dump");
    }

    @Test
    @DisplayName(
            "⛔⛔ Sau nạp, vai trò ứng dụng SỬA được audit_logs ⇒ ADM-2013 + FAILED — ⛔ báo XONG trên quyền đã bị hạ")
    void appSuaDuocNhatKyThiKhongBaoXong() {
        when(jdbc.queryForObject(contains("has_table_privilege"), eq(Boolean.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.performRestore(
                        banSaoLuu, "diễn tập khôi phục", RestoreService.RestoreActor.system(), p -> {}))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(ErrorCode.ADM_2013));

        verify(suKien).record(eq(SecurityEventType.DATABASE_RESTORE_FAILED), any(), any(), any(), any());
        verify(suKien, never()).record(eq(SecurityEventType.DATABASE_RESTORE_FINISHED), any(), any(), any(), any());
        verify(baoTri).disable(anyString());
    }

    @Test
    @DisplayName("⛔ Đích thiếu extension ⇒ DỪNG trước khi đụng dữ liệu: ⛔ ngắt kết nối, ⛔ pg_restore, ⛔ psql nạp")
    void thieuPhanMoRongThiDungTruocKhiDungDuLieu() {
        demPhanMoRong = "2";

        assertThatThrownBy(() -> service.performRestore(
                        banSaoLuu, "diễn tập khôi phục", RestoreService.RestoreActor.system(), p -> {}))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(ErrorCode.ADM_2013));

        assertThat(buocDaChay).containsExactly("dem-phan-mo-rong");
        verify(suKien).record(eq(SecurityEventType.DATABASE_RESTORE_FAILED), any(), any(), any(), any());
        verify(baoTri).disable(anyString());
    }

    @Test
    @DisplayName(
            "⛔ T68.4 — đang có lượt sao lưu ⇒ ADM-2009 TRƯỚC khi bật bảo trì, và nhật ký có FAILED (⛔ STARTED lơ lửng)")
    void dangSaoLuuThiTuChoiTruocKhiBatBaoTri() throws Exception {
        when(backupService.dangCoLuotChay()).thenReturn(true);

        assertThatThrownBy(() -> service.performRestore(
                        banSaoLuu, "diễn tập khôi phục", RestoreService.RestoreActor.system(), p -> {}))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(ErrorCode.ADM_2009));

        verify(baoTri, never()).enable(anyString());
        verify(toolRunner, never()).run(anyList(), any(), any(), any());
        verify(suKien).record(eq(SecurityEventType.DATABASE_RESTORE_STARTED), any(), any(), any(), any());
        verify(suKien).record(eq(SecurityEventType.DATABASE_RESTORE_FAILED), any(), any(), any(), any());
    }

    // ---- đồ gá ----------------------------------------------------------------------------------

    /** Đóng vai {@code pg_restore}/{@code psql}: ghi lại bước, đọc/ghi đúng tệp mà lệnh trỏ tới. */
    private PostgresToolRunner.ToolResult chayGia(List<String> lenh) throws IOException {
        String congCu = lenh.get(0);
        if ("psql".equals(congCu) && lenh.stream().anyMatch(t -> t.contains("FROM pg_extension"))) {
            buocDaChay.add("dem-phan-mo-rong");
            return new PostgresToolRunner.ToolResult(0, demPhanMoRong);
        }
        if ("psql".equals(congCu) && lenh.contains("--dbname=postgres")) {
            buocDaChay.add("ngat-ket-noi");
            return new PostgresToolRunner.ToolResult(0, "");
        }
        if ("pg_restore".equals(congCu) && lenh.contains("--list")) {
            buocDaChay.add("liet-ke");
            Files.write(Path.of(giaTri(lenh, "--file=")), mucLucGia(), StandardCharsets.UTF_8);
            return new PostgresToolRunner.ToolResult(0, "");
        }
        if ("pg_restore".equals(congCu)) {
            buocDaChay.add("sinh-sql");
            lenhSinhSql.set(List.copyOf(lenh));
            mucLucTruyenVao.set(Files.readString(Path.of(giaTri(lenh, "--use-list="))));
            Files.writeString(Path.of(giaTri(lenh, "--file=")), "SET client_encoding = 'UTF8';\nTHAN-BAN-DUMP;\n");
            return new PostgresToolRunner.ToolResult(0, "");
        }
        if ("psql".equals(congCu) && lenh.contains("--single-transaction")) {
            buocDaChay.add("nap");
            lenhNap.set(List.copyOf(lenh));
            Path nap = Path.of(giaTri(lenh, "--file="));
            noiDungNap.set(Files.readString(nap));
            quyenTepNap.set(PosixFilePermissions.toString(Files.getPosixFilePermissions(nap)));
            return new PostgresToolRunner.ToolResult(maThoatNap, maThoatNap == 0 ? "" : "ERROR: giả lập nạp hỏng");
        }
        throw new AssertionError("Lệnh ⛔ nằm trong kịch bản: " + lenh);
    }

    private static String giaTri(List<String> lenh, String tienTo) {
        return lenh.stream()
                .filter(t -> t.startsWith(tienTo))
                .map(t -> t.substring(tienTo.length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("thiếu " + tienTo + " trong " + lenh));
    }

    /** Mục lục đúng định dạng {@code pg_restore --list}: 3 EXTENSION + 3 COMMENT + 2 mục của postgres + 120 bảng. */
    private static List<String> mucLucGia() {
        List<String> dong = new ArrayList<>(List.of(
                ";",
                "; Selected TOC Entries:",
                ";",
                "4; 3079 16389 EXTENSION - postgis ",
                "6021; 0 0 COMMENT - EXTENSION postgis ",
                "2; 3079 16420 EXTENSION - pg_trgm ",
                "6022; 0 0 COMMENT - EXTENSION pg_trgm ",
                "3; 3079 16501 EXTENSION - unaccent ",
                "6023; 0 0 COMMENT - EXTENSION unaccent ",
                "220; 1259 17010 TABLE public spatial_ref_sys postgres",
                "5801; 0 17010 TABLE DATA public spatial_ref_sys postgres"));
        IntStream.range(0, 120)
                .forEach(i ->
                        dong.add("%d; 1259 %d TABLE public bang_%d songnhue_owner".formatted(300 + i, 18000 + i, i)));
        return dong;
    }

    private List<String> tepConLai() {
        try (Stream<Path> tep = Files.list(thuMuc)) {
            return tep.map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
