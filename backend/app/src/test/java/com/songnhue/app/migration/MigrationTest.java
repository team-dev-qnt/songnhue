package com.songnhue.app.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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

    /**
     * <b>Số migration ĐÃ ÁP phải khớp ĐÚNG số tệp Flyway nhìn thấy — T11.72.</b>
     *
     * <h2>Vì sao ngưỡng ghi cứng là một lời hứa rỗng</h2>
     *
     * Bản trước khẳng định {@code applied >= 9}. Con số 9 đúng ở ngày viết ra và không ai sửa nữa;
     * đo 3/9/2026 thì kho có <b>52</b> tệp {@code V*.sql}. Nghĩa là Flyway áp được 9 tệp rồi dừng —
     * hoặc 40 tệp rồi dừng — bài này vẫn <b>xanh trọn vẹn</b>. Một ngưỡng đứng yên trong khi thứ nó
     * canh lớn lên năm lần thì thôi phân biệt được hai trạng thái, tức là thôi khẳng định gì (luật 9).
     *
     * <h2>Bất biến thay thế: hai chiều, không có số nào để lỗi thời</h2>
     *
     * Đếm tệp trên <b>chính classpath</b>, qua <b>chính danh sách location</b> mà bean Flyway đang
     * chạy khai — {@code flyway.getConfiguration().getLocations()}. Không ghi cứng đường dẫn, không
     * ghi cứng số lượng: thêm một migration thì cả hai vế cùng tăng, còn Flyway áp thiếu một tệp thì
     * hai vế lệch nhau ngay.
     *
     * <p>⚠ Đây là chỗ đọc bằng cùng một cơ chế Flyway dùng để phân giải location, nên nó không thể
     * "đúng ở test mà sai ở chạy thật" vì lệch đường dẫn — đó là cả lý do dùng
     * {@code getConfiguration()} thay vì {@code find backend -name 'V*.sql'}.
     *
     * <h2>⛔ Lớp lỗi bài này sinh ra để bắt</h2>
     *
     * Boot 4 dời auto-config của Flyway sang starter riêng. Nếu {@code app/pom.xml} khai
     * {@code flyway-core} mà thiếu starter, {@code migrator} ở máy chủ <b>im lặng không áp gì</b>,
     * trong khi bộ test vẫn xanh vì {@code IntegrationTestBase} tự đặt {@code spring.flyway.*} bằng
     * {@code @DynamicPropertySource}. Ngưỡng 9 sẽ không thấy; phép so hai chiều thì thấy.
     */
    @Test
    @DisplayName("⭐⭐ Số migration đã áp KHỚP ĐÚNG số tệp trên classpath — không ngưỡng ghi cứng")
    void soMigrationDaApKhopSoTepTrenClasspath() throws IOException {
        Map<String, Integer> theoLocation = demTepTheoLocation();
        int tongTep = theoLocation.values().stream().mapToInt(Integer::intValue).sum();

        // ⛔ Chặn tập rỗng (luật 7): quét hỏng cho ra 0 tệp, và `0 == 0` là một bài xanh vô nghĩa.
        assertThat(tongTep)
                .as(
                        "quét classpath ra %d tệp V*.sql qua %s — phép quét đang hỏng, và khi nó hỏng "
                                + "thì phép so bên dưới là 0 với 0",
                        tongTep, theoLocation.keySet())
                .isGreaterThanOrEqualTo(NGUONG_TAP_RONG);

        Integer applied =
                jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        Integer failed =
                jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);

        assertThat(failed).as("migration lỗi còn sót trong lịch sử").isZero();
        assertThat(applied)
                .as(
                        "Flyway ghi %d hàng thành công, nhưng classpath có %d tệp V*.sql (%s). "
                                + "Lệch nghĩa là có migration KHÔNG được áp — trên máy chủ đó là một cột "
                                + "thiếu, và `ddl-auto: validate` sẽ chặn ứng dụng lúc khởi động.",
                        applied, tongTep, theoLocation)
                .isEqualTo(tongTep);
    }

    /**
     * ⭐ Tự kiểm: chứng minh phép đếm bám vào location THẬT, không khớp bừa.
     *
     * <p>Chỗ bài trên dễ sai nhất là mẫu glob quá lỏng — một mẫu quét trúng mọi thứ vẫn cho ra hai
     * vế bằng nhau và xanh. Nên hỏi nó một location <b>không tồn tại</b>: phải ra <b>0</b>. Và hỏi
     * một location có thật: phải ra <b>số dương</b>. Hai câu ấy cùng đúng thì phép đếm mới đang đo
     * cái nó nói là đang đo.
     *
     * <p>⚠ Khẳng định ở đây là <b>về số lượng</b>, không khớp chuỗi — nó không chia sẻ giả định nào
     * với mẫu glob mà nó đang kiểm (luật 29).
     */
    @Test
    @DisplayName("⭐ Tự kiểm: location không tồn tại phải ra 0, location thật phải ra số dương")
    void tuKiemPhepDemBamVaoLocationThat() throws IOException {
        assertThat(demTep("db/migration/khong-ton-tai-" + getClass().getSimpleName()))
                .as("một location KHÔNG TỒN TẠI mà ra > 0 ⇒ mẫu glob đang khớp bừa, và phép so "
                        + "hai chiều ở bài trên xanh vì lý do sai")
                .isZero();

        Map<String, Integer> theoLocation = demTepTheoLocation();
        assertThat(theoLocation)
                .as("Flyway phải khai đủ các location của 5 module + thư mục test")
                .hasSizeGreaterThanOrEqualTo(5);
        assertThat(theoLocation.values().stream().filter(n -> n > 0).count())
                .as("phải có ít nhất 3 location thật sự chứa migration — %s", theoLocation)
                .isGreaterThanOrEqualTo(3);
    }

    /** Số tệp {@code V*.sql} tối thiểu phải quét ra; 52 ở thời điểm viết, để chừa biên rộng. */
    private static final int NGUONG_TAP_RONG = 30;

    /**
     * Đếm tệp {@code V*.sql} theo TỪNG location mà bean Flyway đang chạy khai.
     *
     * <p>Trả về map giữ thứ tự để thông báo lỗi chỉ thẳng được location nào rỗng bất thường.
     */
    private Map<String, Integer> demTepTheoLocation() throws IOException {
        Map<String, Integer> ket = new LinkedHashMap<>();
        for (Location loc : flyway.getConfiguration().getLocations()) {
            ket.put(loc.getPath(), demTep(loc.getPath()));
        }
        return ket;
    }

    /** Số tệp {@code V*.sql} thấy được trên classpath dưới một đường dẫn location. */
    private int demTep(String duongDan) throws IOException {
        return new PathMatchingResourcePatternResolver().getResources("classpath*:" + duongDan + "/V*.sql").length;
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
     *
     * <p>Sai thì sai theo kiểu nào là tuỳ locale mặc định — đo 26/8 trên chính image
     * {@code postgis/postgis:16-3.4}: mặc định glibc {@code en_US.utf8} cho ra
     * Anh &lt; Đăng &lt; Dung &lt; Em ("Đăng" chen lên trước "Dung"), còn locale {@code C} cho ra
     * Anh &lt; Dung &lt; Em &lt; Đăng. Nên bài kiểm so với thứ tự ĐÚNG, không so với một kiểu sai
     * cụ thể: cái sai thứ hai sẽ đi lọt.
     *
     * <p>Bài này chỉ nói về cluster do Testcontainers dựng. Cluster của staging/production được đo
     * riêng bằng {@code deploy/postgres/kiem-collation.sh} ở mỗi lượt triển khai.
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

    @Test
    @DisplayName("⚠ CSDL test KHÔNG có extension nào ngoài danh sách của production")
    void noExtraExtensionsBeyondProduction() {
        // Kiểm chiều ngược của bài trên, và nó khó thấy hơn hẳn: thừa extension thì mọi bài kiểm
        // vẫn xanh, chỉ production mới hỏng. Đã xảy ra thật (17/8) — image `postgis/postgis` mang
        // sẵn script tạo `postgis_topology` + `postgis_tiger_geocoder`, ở production bị bind-mount
        // của compose che mất, còn trong test thì vẫn chạy. Hệ quả đo được: `pg_dump` bằng vai trò
        // readonly đỏ vì schema `tiger` — một schema không tồn tại ở production.
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension ORDER BY 1", String.class);

        assertThat(extensions)
                .as("khớp đúng `deploy/postgres/init/10-bootstrap.sh` — thừa ở đây là test và thật đã lệch nhau")
                .containsExactlyInAnyOrder("plpgsql", "postgis", "unaccent", "pg_trgm");
    }
}
