package com.songnhue.core.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Chốt chặn dò mật khẩu — 5 lần/15 phút (conventions.md §4.5). */
class CaffeineRateLimitStoreTest {

    private final CaffeineRateLimitStore store = new CaffeineRateLimitStore();

    @Test
    @DisplayName("Cho qua đúng `limit` lượt rồi chặn")
    void blocksAfterLimit() {
        String key = "login:203.0.113.7";
        int limit = 5;

        for (int i = 1; i <= limit; i++) {
            RateLimitStore.Decision decision = store.hit(key, limit, Duration.ofMinutes(15));
            assertThat(decision.allowed())
                    .as("Lượt thứ %d phải được cho qua", i)
                    .isTrue();
            assertThat(decision.remaining()).isEqualTo(limit - i);
        }

        RateLimitStore.Decision blocked = store.hit(key, limit, Duration.ofMinutes(15));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.remaining()).isZero();
        assertThat(blocked.retryAfter()).isPositive();
    }

    @Test
    @DisplayName("Các khoá khác nhau đếm độc lập — một IP bị chặn không ảnh hưởng IP khác")
    void countsPerKey() {
        store.hit("login:1.1.1.1", 1, Duration.ofMinutes(15));
        assertThat(store.hit("login:1.1.1.1", 1, Duration.ofMinutes(15)).allowed())
                .isFalse();
        assertThat(store.hit("login:2.2.2.2", 1, Duration.ofMinutes(15)).allowed())
                .isTrue();
    }

    @Test
    @DisplayName("Ba nhóm hạn mức không đè lên nhau")
    void policiesDoNotShareBuckets() {
        String ip = "203.0.113.7";
        store.hit(RateLimitPolicy.LOGIN.key(ip), 1, Duration.ofMinutes(15));

        // Đã dùng hết bucket login, nhưng bucket api vẫn còn nguyên
        assertThat(store.hit(RateLimitPolicy.LOGIN.key(ip), 1, Duration.ofMinutes(15))
                        .allowed())
                .isFalse();
        assertThat(store.hit(RateLimitPolicy.API.key(ip), 1, Duration.ofMinutes(1))
                        .allowed())
                .isTrue();
    }

    @Test
    @DisplayName("Hết cửa sổ thì đếm lại từ đầu")
    void resetsAfterWindow() throws InterruptedException {
        String key = "api:203.0.113.9";
        store.hit(key, 1, Duration.ofMillis(50));
        assertThat(store.hit(key, 1, Duration.ofMillis(50)).allowed()).isFalse();

        Thread.sleep(80);
        assertThat(store.hit(key, 1, Duration.ofMillis(50)).allowed()).isTrue();
    }

    @Test
    @DisplayName("reset() xoá bộ đếm — dùng sau khi đăng nhập thành công")
    void resetClearsCounter() {
        String key = "login:203.0.113.7";
        store.hit(key, 1, Duration.ofMinutes(15));
        assertThat(store.hit(key, 1, Duration.ofMinutes(15)).allowed()).isFalse();

        store.reset(key);
        assertThat(store.hit(key, 1, Duration.ofMinutes(15)).allowed()).isTrue();
    }

    @Test
    @DisplayName("Hạn mức chốt theo conventions.md §4.5 — không được đổi tuỳ tiện")
    void policyValuesMatchConventions() {
        assertThat(RateLimitPolicy.LOGIN.limit()).isEqualTo(5);
        assertThat(RateLimitPolicy.LOGIN.window()).isEqualTo(Duration.ofMinutes(15));
        assertThat(RateLimitPolicy.API.limit()).isEqualTo(100);
        assertThat(RateLimitPolicy.API.window()).isEqualTo(Duration.ofMinutes(1));
        assertThat(RateLimitPolicy.EXPORT.limit()).isEqualTo(10);
        assertThat(RateLimitPolicy.EXPORT.window()).isEqualTo(Duration.ofHours(1));
    }
}
