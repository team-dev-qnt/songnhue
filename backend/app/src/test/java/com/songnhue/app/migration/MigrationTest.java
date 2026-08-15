package com.songnhue.app.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;

/**
 * Migration chạy sạch từ một DB rỗng — <b>Definition of Done mục 4</b>, và ba thứ đi kèm mà chỉ DB
 * thật mới kiểm chứng được.
 *
 * <p>Cả bài kiểm này đứng được là nhờ context Spring đã lên: nghĩa là Flyway đã chạy hết migration
 * bằng vai trò {@code songnhue_owner}, Hibernate đã {@code validate} toàn bộ entity với schema vừa
 * tạo, và ứng dụng kết nối tiếp bằng vai trò {@code songnhue_app}. Lệch một cột giữa entity và
 * migration là context không lên nổi.
 */
class MigrationTest extends IntegrationTestBase {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Migration chạy hết, không version nào lỗi")
    void everyMigrationApplied() {
        Integer applied =
                jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        Integer failed =
                jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);

        assertThat(applied).as("số migration đã áp dụng").isGreaterThanOrEqualTo(9);
        assertThat(failed).as("migration lỗi còn sót trong lịch sử").isZero();
    }

    @Test
    @DisplayName("Bảng nền và phân mảnh audit_logs đều có mặt")
    void coreTablesExist() {
        List<String> required = List.of(
                "users",
                "roles",
                "permissions",
                "role_permissions",
                "user_roles",
                "org_units",
                "sessions",
                "token_denylist",
                "user_totp",
                "attachments",
                "settings",
                "jobs",
                "notifications",
                "workflow_definitions",
                "audit_logs",
                "audit_archive_anchors",
                "security_events",
                "shedlock",
                "code_sequences");

        for (String table : required) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class,
                    table);
            assertThat(count).as("thiếu bảng `%s`", table).isEqualTo(1);
        }

        Integer partitions = jdbc.queryForObject(
                "SELECT count(*) FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent "
                        + "WHERE p.relname = 'audit_logs'",
                Integer.class);
        assertThat(partitions)
                .as("audit_logs phải là bảng phân mảnh theo tháng")
                .isPositive();
    }

    /**
     * ⚠ <b>Nhận nợ WS-2/T2.2 — sổ nợ liên WS mục 8.</b>
     *
     * <p>WS-2 đặt {@code clean-disabled: true} vào {@code application.yml} rồi tick task. Nhưng một
     * dòng cấu hình chỉ là một lời khai: không có gì chứng minh nó thật sự chặn, và
     * {@code flyway clean} là lệnh <b>xoá sạch schema</b> — thứ mà nếu chạy nhầm trên production thì
     * chỉ còn bản dump đêm trước để quay về (không có PITR — {@code architecture-review.md} §6.5).
     *
     * <p>Bài kiểm này gọi thẳng {@code clean()} trên đúng bean Flyway mà ứng dụng đang dùng, nên nó
     * kiểm chứng <i>cấu hình thật</i> chứ không phải một bản Flyway dựng riêng cho test.
     */
    @Test
    @DisplayName("⚠ flyway clean bị từ chối — xoá sạch schema chỉ còn bản dump đêm trước để quay về")
    void cleanIsRefused() {
        assertThat(flyway.getConfiguration().isCleanDisabled())
                .as("spring.flyway.clean-disabled phải bật")
                .isTrue();

        assertThatThrownBy(flyway::clean).isInstanceOf(FlywayException.class).hasMessageContaining("clean");

        // Và schema vẫn còn nguyên sau lời gọi đó — bằng chứng cuối cùng.
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'", Integer.class);
        assertThat(tables).as("schema phải còn nguyên sau khi clean bị từ chối").isGreaterThan(20);
    }

    /**
     * ⚠ Collation ICU {@code vi-VN} — tham số <b>không sửa được sau khi đã có dữ liệu</b>.
     *
     * <p>Đổi collation của một database đang chạy nghĩa là dump toàn bộ rồi restore lại. Vì vậy sai
     * sót phải bị bắt ở đây, chứ không phải lúc có người báo danh sách nhân viên xếp sai thứ tự.
     * Với collation mặc định (so sánh theo byte) thì "Đăng" xếp <i>sau</i> "Em", vì Đ nằm ngoài bảng
     * ASCII.
     */
    @Test
    @DisplayName("⚠ ORDER BY tiếng Việt đúng — Anh < Dung < Đăng < Em (collation ICU vi-VN)")
    void vietnameseCollationIsCorrect() {
        List<String> sorted = jdbc.queryForList(
                "SELECT name FROM (VALUES ('Em'), ('Đăng'), ('Anh'), ('Dung')) AS t(name) ORDER BY name", String.class);

        assertThat(sorted)
                .as("DB đang không dùng collation ICU vi-VN — kiểm tra POSTGRES_INITDB_ARGS")
                .containsExactly("Anh", "Dung", "Đăng", "Em");
    }

    @Test
    @DisplayName("Ba extension bắt buộc đã cài (postgis, unaccent, pg_trgm)")
    void requiredExtensionsInstalled() {
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);

        assertThat(extensions).contains("postgis", "unaccent", "pg_trgm");
    }
}
