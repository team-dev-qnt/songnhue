package com.songnhue.core.common.config;

import java.time.Duration;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * Khoá phân tán cho job <b>theo lịch</b> — T6.9 (architecture-review.md §6.2).
 *
 * <p><b>Cài sẵn nhưng mặc định TẮT.</b> v1 chạy đúng một node nên không có gì để tranh chấp, mà bật
 * lên thì mỗi lượt chạy phải thêm một vòng ghi DB. Lên ≥2 node chỉ cần đặt
 * {@code SHEDLOCK_ENABLED=true} — không sửa mã, không deploy khác. Đây chính là điều kiện "thêm node
 * = đổi cấu hình" mà §6.4 chốt.
 *
 * <p>⚠ <b>Chỉ dành cho job theo lịch, KHÔNG cho hàng đợi {@code jobs}.</b> Hai bài toán ngược nhau:
 * job theo lịch cần <i>đúng một</i> node chạy (nếu không thì bản sao lưu chạy hai lần), còn hàng đợi
 * cần <i>càng nhiều</i> node cùng lấy việc càng tốt — chỗ đó dùng {@code FOR UPDATE SKIP LOCKED}.
 * Bọc ShedLock quanh worker hàng đợi sẽ biến nó thành một node duy nhất, mất sạch khả năng mở rộng.
 *
 * <p>Bảng {@code shedlock} đã có từ WS-2, đúng schema chuẩn của provider — cố ý không đặt tên cột
 * riêng để nâng cấp thư viện không phải sửa migration.
 */
@Configuration
@ConditionalOnProperty(name = "app.shedlock-enabled", havingValue = "true")
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLockConfig.class);

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        log.info("ShedLock BẬT — job theo lịch chỉ chạy ở một node tại một thời điểm");
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                .withTableName("shedlock")
                // Dùng giờ của DB, không phải giờ máy chủ ứng dụng: hai node lệch đồng hồ vài giây
                // là đủ để khoá hết hạn sớm và cả hai cùng chạy.
                .usingDbTime()
                .build());
    }

    /** Giữ khoá tối đa — node chết thì khoá tự nhả sau khoảng này, không kẹt vĩnh viễn. */
    public static Duration defaultLockAtMostFor() {
        return Duration.ofMinutes(30);
    }
}
