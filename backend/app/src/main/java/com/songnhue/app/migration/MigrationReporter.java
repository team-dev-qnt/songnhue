package com.songnhue.app.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Tóm tắt kết quả migration cho service {@code migrator} (profile {@code migrate}).
 *
 * <p>Flyway đã chạy xong trước khi bean này được gọi — Spring Boot migrate lúc khởi tạo DataSource.
 * Lớp này chỉ ghi lại kết quả ở dạng đọc được trong log deploy: phiên bản schema hiện tại và số
 * migration đã áp dụng. Script deploy đọc dòng này để đối chiếu, thay vì phải mở psql.
 *
 * <p>Không tự gọi {@code System.exit}: tiến trình tự kết thúc vì profile {@code migrate} tắt web
 * server nên không còn luồng non-daemon nào. Migration lỗi thì context không lên được và Spring Boot
 * trả mã thoát khác 0 — đúng thứ {@code depends_on: service_completed_successfully} cần.
 */
@Component
@Profile("migrate")
public class MigrationReporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationReporter.class);

    private final Flyway flyway;

    public MigrationReporter(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public void run(ApplicationArguments args) {
        MigrationInfo current = flyway.info().current();

        if (current == null) {
            throw new IllegalStateException("Không có migration nào được áp dụng — kiểm tra spring.flyway.locations "
                    + "và tài khoản DB_MIGRATION_USER.");
        }

        MigrationInfo[] applied = flyway.info().applied();
        log.info(
                "✓ Migration hoàn tất — phiên bản schema: {} ({}), tổng số migration đã áp dụng: {}",
                current.getVersion(),
                current.getDescription(),
                applied.length);
    }
}
