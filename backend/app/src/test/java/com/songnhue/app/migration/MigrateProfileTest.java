package com.songnhue.app.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.yaml.snakeyaml.Yaml;

import com.songnhue.core.common.config.SchedulingConfig;

/**
 * Canh cho service {@code migrator} <b>thoát được</b> sau khi chạy xong migration.
 *
 * <h3>Vì sao cần bài kiểm này</h3>
 *
 * Cả luồng deploy dựa vào một điều: migrator chạy Flyway rồi kết thúc với mã 0, và
 * {@code depends_on: service_completed_successfully} mới cho {@code app} khởi động
 * (architecture-review.md §9.2, T11.4). Đó là cơ chế duy nhất ngăn app lên trên schema hỏng.
 *
 * <p>Kiểu hỏng đã gặp thật (17/8): migration chạy xong, log in "✓ Migration hoàn tất", rồi tiến
 * trình <b>không bao giờ thoát</b> vì {@code @EnableScheduling} và worker hàng đợi giữ luồng
 * không-daemon. Container đứng {@code Up} vô hạn, {@code app} kẹt ở {@code Created},
 * <b>không một dòng lỗi nào</b>. Không có gì trong bộ kiểm thử lúc đó bắt được, vì mọi bài kiểm
 * đều chạy trong JVM của JUnit — nơi chẳng ai quan tâm tiến trình có thoát hay không.
 *
 * <p>Ba thứ phải cùng đúng thì migrator mới thoát; bài kiểm này canh cả ba, vì tắt hai trong ba
 * vẫn treo y như cũ.
 */
class MigrateProfileTest {

    private static final String PROFILE_FILE = "application-migrate.yml";

    @Test
    @DisplayName("profile migrate tắt worker hàng đợi và ShedLock")
    void tatWorker() {
        Map<String, Object> app = section(loadProfile(), "app");

        assertThat(app)
                .as("worker mở luồng riêng chạy mãi — bật là migrator không thoát")
                .containsEntry("worker-enabled", false)
                .containsEntry("shedlock-enabled", false);
    }

    @Test
    @DisplayName("profile migrate không mở cổng HTTP và bật khởi tạo lười")
    void khongMoWebVaKhoiTaoLuoi() {
        Map<String, Object> main = section(section(loadProfile(), "spring"), "main");

        assertThat(main).containsEntry("web-application-type", "none");
        // Khởi tạo lười là thứ giữ cho migrator KHÔNG phải cầm khoá ký JWT: không có nó,
        // chuỗi AuthController → AuthService → TokenService → JwtKeyStore được dựng và
        // đòi đọc file khoá riêng, dù việc duy nhất của tiến trình này là chạy DDL.
        assertThat(main)
                .as("thiếu cờ này thì migrator đòi khoá ký JWT — xem SchedulingConfig")
                .containsEntry("lazy-initialization", true);
    }

    @Test
    @DisplayName("profile migrate vẫn ép bật Flyway")
    void flywayVanBat() {
        // Migrator thoát 0 mà KHÔNG migrate gì là kiểu hỏng tệ nhất: deploy đi tiếp bình
        // thường trên schema cũ.
        assertThat(section(section(loadProfile(), "spring"), "flyway")).containsEntry("enabled", true);
    }

    @Test
    @DisplayName("chỉ SchedulingConfig được mang @EnableScheduling, và nó phải loại trừ profile migrate")
    void chiMotNoiBatLapLich() {
        Set<Class<?>> classesWithScheduling = timLopMang(EnableScheduling.class);

        assertThat(classesWithScheduling)
                .as("thêm @EnableScheduling ở lớp khác là migrator treo lại — im lặng như lần trước")
                .containsExactly(SchedulingConfig.class);

        Profile profile = SchedulingConfig.class.getAnnotation(Profile.class);
        assertThat(profile).as("SchedulingConfig phải khai @Profile").isNotNull();
        assertThat(profile.value()).containsExactly("!migrate");
    }

    // -------------------------------------------------------------------------

    /** Quét toàn bộ mã sản phẩm tìm lớp mang một annotation cấu hình. */
    private static Set<Class<?>> timLopMang(Class<? extends java.lang.annotation.Annotation> annotation) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));

        return scanner.findCandidateComponents("com.songnhue").stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(java.util.Objects::nonNull)
                .map(MigrateProfileTest::load)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Không nạp được lớp " + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadProfile() {
        try (InputStream in = new ClassPathResource(PROFILE_FILE).getInputStream()) {
            return (Map<String, Object>) new Yaml().load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + PROFILE_FILE, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        assertThat(value).as("thiếu khối '%s' trong %s", key, PROFILE_FILE).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
