package com.songnhue.app.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL + PostGIS thật cho test tích hợp — T10.1.
 *
 * <p><b>Vì sao là DB thật chứ không phải H2.</b> Gần như mọi thứ khó của tầng dữ liệu trong hệ này
 * đều là thứ H2 không có: {@code SELECT … FOR UPDATE SKIP LOCKED} của hàng đợi job, bảng phân mảnh
 * theo tháng của {@code audit_logs}, trigger tính chuỗi hash, {@code timestamptz}, PostGIS, và
 * collation ICU tiếng Việt. Chạy test trên H2 là kiểm chứng một hệ thống khác với hệ thống sẽ chạy
 * thật — xanh ở đây, đỏ trên production.
 *
 * <p><b>Ba thứ được cố ý giữ giống hệt môi trường thật:</b>
 *
 * <ul>
 *   <li><b>Cùng image</b> {@code postgis/postgis:16-3.4} như {@code compose.infra.yml}.
 *   <li><b>Cùng script khởi tạo</b> — nạp thẳng {@code deploy/postgres/init}, không chép lại. Chép
 *       ra một bản riêng cho test là mở đường cho hai bản trôi xa nhau, mà bản test thì luôn xanh.
 *   <li><b>Cùng {@code POSTGRES_INITDB_ARGS}</b> — collation ICU {@code vi-VN}. Đây là tham số
 *       <i>không sửa được sau khi đã có dữ liệu</i> (đổi = dump + restore toàn bộ DB), nên nó xứng
 *       đáng có một bài kiểm canh chừng.
 * </ul>
 *
 * <p>Container dựng <b>một lần cho cả lần chạy JVM</b> (singleton) thay vì mỗi lớp test một cái:
 * khởi tạo PostGIS mất vài giây, nhân với số lớp test thì CI chậm tới mức người ta bắt đầu bỏ qua
 * nó. Testcontainers tự dọn qua Ryuk khi JVM tắt.
 */
public final class SongnhuePostgres {

    /** Khớp {@code deploy/compose.infra.yml} — đổi ở một nơi thì phải đổi cả hai. */
    private static final DockerImageName IMAGE =
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres");

    /**
     * ⚠ Y hệt {@code compose.infra.yml}. Sai dòng này thì {@code ORDER BY} tiếng Việt xếp
     * "Đăng" sau "Em" — sai rõ trong danh bạ nhân sự và danh mục công trình.
     */
    private static final String INITDB_ARGS =
            "--encoding=UTF8 --locale-provider=icu --icu-locale=vi-VN --locale=C.UTF-8";

    private static final String TEST_PASSWORD = "test_only_not_a_secret";

    @SuppressWarnings("resource") // Ryuk đóng container khi JVM tắt — đóng tay ở đây là hỏng các lớp test sau
    private static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("songnhue")
            .withUsername("postgres")
            .withPassword(TEST_PASSWORD)
            .withEnv("POSTGRES_INITDB_ARGS", INITDB_ARGS)
            // Script khởi tạo cần 4 mật khẩu này, và tự dừng với thông báo rõ ràng nếu thiếu —
            // chính cơ chế đó cũng được kiểm chứng gián tiếp ở đây.
            .withEnv("DB_MIGRATION_PASSWORD", TEST_PASSWORD)
            .withEnv("DB_PASSWORD", TEST_PASSWORD)
            .withEnv("DB_ARCHIVER_PASSWORD", TEST_PASSWORD)
            .withEnv("DB_READONLY_PASSWORD", TEST_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath(initScriptDirectory()), "/docker-entrypoint-initdb.d");

    static {
        INSTANCE.start();
    }

    /**
     * Tìm {@code deploy/postgres/init} bằng cách đi ngược lên từ thư mục làm việc.
     *
     * <p>Không viết đường dẫn tương đối cứng: thư mục làm việc của Surefire là thư mục module, còn của
     * IDE thường là gốc repo — hai chỗ cho ra hai kết quả khác nhau, và triệu chứng là container thoát
     * với mã 2 mà không nói vì sao.
     */
    private static Path initScriptDirectory() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("deploy/postgres/init");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Không tìm thấy deploy/postgres/init khi đi ngược từ "
                + Path.of("").toAbsolutePath());
    }

    private SongnhuePostgres() {}

    public static PostgreSQLContainer<?> instance() {
        return INSTANCE;
    }

    public static String password() {
        return TEST_PASSWORD;
    }
}
