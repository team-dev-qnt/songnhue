package com.songnhue.app.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Container;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.SongnhuePostgres;

/**
 * Vai trò {@code songnhue_readonly} <b>dump được toàn bộ schema</b> — bằng {@code pg_dump} thật.
 *
 * <h3>Vì sao bài kiểm này tồn tại</h3>
 *
 * {@code BackupServiceTest} mock {@code PostgresToolRunner}: nó chứng minh phần điều phối (checksum
 * đọc lại từ đĩa, ghi FAILED khi hỏng, mật khẩu đi qua {@code PGPASSWORD}) nhưng <b>không hề gọi
 * pg_dump</b>. Vì vậy nó xanh trong khi cơ chế sao lưu không sinh ra nổi một tệp nào.
 *
 * <p>Đó chính là chuyện đã xảy ra: rà soát ngày 17/8 chạy {@code make backup} trên hệ thật và nhận
 *
 * <pre>pg_dump: error: permission denied for sequence system_backups_id_seq</pre>
 *
 * V202608131006 khai quyền mặc định cho bảng tạo sau nhưng thiếu dòng tương ứng cho SEQUENCES, nên
 * bảng đầu tiên tạo sau nó — {@code system_backups}, đúng bảng sổ đăng ký sao lưu — sinh ra một
 * sequence mà vai trò dump không đọc được. Sửa ở V202608171011.
 *
 * <p><b>Điều làm lỗi này đắt hơn vẻ ngoài của nó</b>: sao lưu là lưới an toàn <i>duy nhất</i> của hệ
 * này (không PITR, không replica — {@code architecture-review.md} §6.5), và mỗi bảng mới của Phase
 * 1+ sẽ làm hỏng lại đúng như vậy, im lặng cho tới lần sao lưu kế tiếp. Nên bài kiểm chạy pg_dump
 * trên <b>toàn bộ</b> schema hiện có, không kiểm riêng một sequence nào.
 *
 * <p>pg_dump chạy <b>bên trong container</b> chứ không phải trên máy chạy JVM: phiên bản luôn khớp
 * máy chủ, và bài kiểm không đòi CI phải cài sẵn postgresql-client.
 */
class BackupRoleTest extends IntegrationTestBase {

    /** Y hệt tham số production — `BackupService.dumpCommand()` và `deploy/backup/backup.sh`. */
    private static final String DUMP_FLAGS = "--format=custom --compress=6 --no-password --no-owner --no-privileges";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("⚠ pg_dump THẬT bằng vai trò readonly chạy trọn schema, sinh tệp khác rỗng")
    void readonlyDumpsWholeSchema() throws Exception {
        var postgres = SongnhuePostgres.instance();
        String target = "/tmp/songnhue-guard.dump";

        Container.ExecResult dump = postgres.execInContainer(
                "sh",
                "-c",
                "PGPASSWORD='%s' pg_dump --host=127.0.0.1 --port=5432 --username=songnhue_readonly --dbname=%s %s --file=%s"
                        .formatted(SongnhuePostgres.password(), postgres.getDatabaseName(), DUMP_FLAGS, target));

        assertThat(dump.getExitCode())
                .as("pg_dump thất bại — sao lưu là lưới an toàn duy nhất của hệ này.%nstderr:%n%s", dump.getStderr())
                .isZero();

        Container.ExecResult size = postgres.execInContainer("sh", "-c", "wc -c < " + target);
        assertThat(Integer.parseInt(size.getStdout().trim()))
                .as("pg_dump thoát 0 nhưng tệp rỗng thì vẫn là mất bản sao lưu")
                .isGreaterThan(1024);
    }

    @Test
    @DisplayName("Mọi sequence trong schema đều đọc được bằng vai trò dump")
    void everySequenceIsReadableByDumpRole() {
        // MATERIALIZED là bắt buộc, không phải để chạy nhanh: gộp hai điều kiện vào một mệnh đề
        // WHERE thì bộ tối ưu được phép gọi has_sequence_privilege() TRƯỚC bộ lọc relkind, và hàm
        // đó ném lỗi trên kiểu composite của PostGIS ("geometry_dump" is not a sequence).
        List<String> unreadable = jdbc.queryForList(
                """
                WITH seqs AS MATERIALIZED (
                    SELECT c.oid, c.relname
                      FROM pg_class c
                      JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE n.nspname = 'public'
                       AND c.relkind = 'S'
                )
                SELECT relname
                  FROM seqs
                 WHERE NOT has_sequence_privilege('songnhue_readonly', oid, 'SELECT')
                 ORDER BY 1
                """,
                String.class);

        assertThat(unreadable)
                .as("sequence không cấp quyền cho vai trò dump → pg_dump hỏng toàn bộ, không chỉ bảng đó")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Quyền MẶC ĐỊNH cho sequence tạo sau đã khai — bảng mới của Phase 1+ không làm hỏng lại")
    void defaultPrivilegeCoversFutureSequences() {
        // Không kiểm bằng danh sách sequence hôm nay: bảng của Phase 1 chưa tồn tại. Thứ phải giữ
        // là chính cái quyền mặc định — mất nó thì bài kiểm trên vẫn xanh cho tới bảng kế tiếp.
        Integer granted = jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM pg_default_acl d
                 WHERE d.defaclobjtype = 'S'
                   AND array_to_string(d.defaclacl, ',') LIKE '%songnhue_readonly=r%'
                """,
                Integer.class);

        assertThat(granted)
                .as("thiếu ALTER DEFAULT PRIVILEGES … ON SEQUENCES TO songnhue_readonly (V202608171011)")
                .isPositive();
    }
}
