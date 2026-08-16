package com.songnhue.app.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.songnhue.core.common.config.JwtProperties;
import com.songnhue.core.testsupport.RsaKeyPairFixture;

/**
 * Nền cho test tích hợp: cả ứng dụng thật, chạy trên PostgreSQL thật — T10.1.
 *
 * <p>Lớp con chỉ việc kế thừa và tiêm bean. Mọi biến môi trường bắt buộc được điền ở đây vì
 * {@code UnresolvedPlaceholderGuard} (T4.8) từ chối khởi động khi còn placeholder chưa giải được —
 * đó là hành vi <i>đúng</i> và test không được phép né nó, nên phải cấp đủ như môi trường thật.
 *
 * <p><b>Ba thứ cố tình khác môi trường thật, và vì sao:</b>
 *
 * <ul>
 *   <li><b>{@code WORKER_ENABLED=false}</b> — worker hàng đợi chạy nền sẽ nhặt việc trong lúc bài
 *       kiểm đang khẳng định về đúng bảng đó. Test nào cần worker thì tự bật.
 *   <li><b>MinIO là địa chỉ giả</b> — {@code MinioClient} không mở kết nối lúc khởi tạo bean, nên
 *       không cần thêm một container chỉ để context lên được. Test nào thật sự đụng tệp thì phải tự
 *       dựng MinIO.
 *   <li><b>Khoá RSA và AES sinh mới mỗi lần chạy</b> — khoá cố định nhúng trong repo có thói quen bị
 *       chép nhầm sang môi trường thật, và {@code .gitignore} chặn {@code *.pem} chính vì lẽ đó.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        var postgres = SongnhuePostgres.instance();

        // Kết nối runtime dùng songnhue_app (không có DELETE trên audit_logs); Flyway dùng
        // songnhue_owner. Giữ đúng cách tách vai trò của môi trường thật, vì chính sự tách đó là
        // thứ WS-2 dựng lên để bảo vệ nhật ký kiểm toán.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "songnhue_app");
        registry.add("spring.datasource.password", SongnhuePostgres::password);
        // ⚠ Thêm `db/migration/test` cho MỌI test tích hợp, không riêng bài kiểm tầng 3.
        // `@EntityScan(basePackages = "com.songnhue")` của SongnhueApplication quét cả lớp test, nên
        // entity ScopedRecord có mặt trong mọi context; thiếu bảng của nó thì Hibernate
        // `ddl-auto: validate` chặn context ngay lúc khởi động — kể cả bài kiểm chẳng liên quan gì.
        // Đặt ở đây cũng gom mọi test tích hợp về CÙNG MỘT context Spring, chỉ dựng một lần.
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration/core,classpath:db/migration/cms,classpath:db/migration/ops,"
                        + "classpath:db/migration/hyd,classpath:db/migration/hr,classpath:db/migration/test");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "songnhue_owner");
        registry.add("spring.flyway.password", SongnhuePostgres::password);

        // Kết nối kết xuất nhật ký dùng vai trò riêng songnhue_archiver — vai trò DUY NHẤT có
        // DELETE trên audit_logs. Cấp đủ ở đây để đường đó cũng được chạy thật, thay vì chỉ tồn tại
        // trên giấy.
        registry.add("app.audit.archiver.username", () -> "songnhue_archiver");
        registry.add("app.audit.archiver.password", SongnhuePostgres::password);

        registry.add("app.worker-enabled", () -> "false");
        registry.add("app.shedlock-enabled", () -> "false");

        JwtProperties jwt = RsaKeyPairFixture.propertiesFor(keyDirectory(), "test-key");
        registry.add("app.jwt.key-id", jwt::getKeyId);
        registry.add("app.jwt.private-key-path", jwt::getPrivateKeyPath);
        registry.add("app.jwt.public-key-path", jwt::getPublicKeyPath);

        registry.add("app.crypto.active-key-id", () -> "v1");
        registry.add("app.crypto.keys.v1", IntegrationTestBase::randomAesKey);

        registry.add("app.storage.endpoint", () -> "http://minio.invalid:9000");
        registry.add("app.storage.access-key", () -> "test");
        registry.add("app.storage.secret-key", () -> "test");
        registry.add("app.storage.bucket-media", () -> "test-media");
        registry.add("app.storage.bucket-report", () -> "test-report");
        registry.add("app.storage.bucket-audit", () -> "test-audit");

        registry.add("app.notification.from", () -> "no-reply@songnhue.test");
        // Bỏ trống máy chủ thư: bean EmailSender không được tạo và thông báo email đánh dấu
        // SKIPPED. Đúng hành vi đã chốt ở WS-6 khi chưa cấu hình SMTP.
        registry.add("spring.mail.host", () -> "");

        // Sao lưu (WS-7). Thư mục tạm của lượt chạy test — cùng phép fail-fast như production:
        // thiếu `app.backup.directory` là ứng dụng KHÔNG khởi động, nên phải cấp ở đây.
        registry.add("app.backup.directory", () -> backupDirectory().toString());
        registry.add("app.backup.host", postgres::getHost);
        registry.add("app.backup.port", () -> postgres.getMappedPort(5432));
        registry.add("app.backup.database", postgres::getDatabaseName);
        registry.add("app.backup.dump-username", () -> "songnhue_readonly");
        registry.add("app.backup.dump-password", SongnhuePostgres::password);
        // ⛔ Cố ý ĐỂ TRỐNG mật khẩu khôi phục: test tích hợp tuyệt đối không được có đường chạy
        // pg_restore --clean lên chính CSDL nó đang dùng.
        registry.add("app.backup.restore-password", () -> "");
    }

    /** Thư mục sao lưu dùng chung cho mọi test tích hợp — dọn khi JVM thoát. */
    private static Path backupDirectory() {
        try {
            Path dir = Files.createTempDirectory("songnhue-backup-test");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được thư mục sao lưu tạm cho test", e);
        }
    }

    private static String randomAesKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static Path keyDirectory() {
        try {
            Path directory = Files.createTempDirectory("songnhue-test-keys");
            directory.toFile().deleteOnExit();
            return directory;
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được thư mục tạm cho khoá test", e);
        }
    }
}
