package com.songnhue.core.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật bộ lập lịch — <b>trừ khi đang chạy ở vai trò migrator</b>.
 *
 * <h3>Vì sao phải tách khỏi {@link CorePlatformConfig}</h3>
 *
 * Service {@code migrator} chạy Flyway rồi phải <b>thoát</b>, vì {@code docker compose} dựa vào
 * {@code depends_on: service_completed_successfully} để quyết định có cho {@code app} khởi động hay
 * không (architecture-review.md §9.2). Đó chính là cơ chế ngăn "app lên nửa vời trên schema hỏng".
 *
 * <p>{@code @EnableScheduling} dựng một {@code ThreadPoolTaskScheduler} với luồng <b>không phải
 * daemon</b>. Luồng không-daemon giữ JVM sống mãi mãi. Hệ quả đo được: migration chạy xong, log in
 * "✓ Migration hoàn tất", tiến trình <b>không bao giờ kết thúc</b> — container đứng ở trạng thái
 * {@code Up} vô hạn và {@code app} kẹt ở {@code Created}, không một dòng lỗi nào.
 *
 * <p>Đặt {@code spring.main.web-application-type: none} <b>không</b> cứu được: nó chỉ tắt cổng HTTP,
 * không đụng gì tới bộ lập lịch. Khởi tạo lười cũng không, vì Spring Boot cố ý loại bean mang
 * {@code @Scheduled} ra khỏi cơ chế đó (nếu không thì tác vụ định kỳ chẳng bao giờ chạy).
 *
 * <p>Bộ lập lịch phục vụ vài việc dọn dẹp hạ tầng (dọn token hết hạn — WS-5; làm mới chỉ số quan
 * sát — WS-7). Không việc nào trong số đó có nghĩa với một tiến trình chỉ chạy DDL rồi tắt.
 */
@Configuration
@Profile("!migrate")
@EnableScheduling
public class SchedulingConfig {}
