package com.songnhue.core.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Đếm tần suất trong bộ nhớ tiến trình bằng cửa sổ cố định (fixed window).
 *
 * <p>Chọn cửa sổ cố định thay vì sliding window hay token bucket vì mục tiêu ở đây là <b>chặn dò
 * mật khẩu và chặn gọi API dồn dập</b>, không phải điều tiết lưu lượng mượt mà. Cửa sổ cố định dễ
 * giải thích cho người vận hành ("5 lần trong 15 phút") và rẻ.
 *
 * <p>Nhược điểm đã biết: ngay ranh giới hai cửa sổ có thể cho qua tối đa 2×limit. Với hạn mức đăng
 * nhập 5/15 phút thì cùng lắm là 10 lần trong khoảnh khắc chuyển cửa sổ — vẫn quá xa mức đủ để dò
 * mật khẩu, nên không đáng đổi lấy độ phức tạp của sliding window.
 *
 * <p>Cache tự dọn theo thời gian sống nên không rò rỉ bộ nhớ dù khoá sinh ra từ IP tuỳ ý.
 */
@Component
public class CaffeineRateLimitStore implements RateLimitStore {

    /** Chặn trên số khoá để một trận flood từ nhiều IP không ăn hết bộ nhớ. */
    private static final int MAX_KEYS = 100_000;

    /** Cửa sổ dài nhất đang dùng là 1 giờ (bucket export); để 2 giờ cho dư. */
    private static final Duration MAX_WINDOW = Duration.ofHours(2);

    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .maximumSize(MAX_KEYS)
            .expireAfterWrite(MAX_WINDOW)
            .build();

    @Override
    public Decision hit(String key, int limit, Duration window) {
        Instant now = Instant.now();

        // compute() chạy nguyên tử theo từng khoá — hai request cùng lúc không đọc trượt nhau
        Window current = windows.asMap().compute(key, (k, existing) -> {
            if (existing == null || !now.isBefore(existing.resetAt())) {
                return new Window(now.plus(window), new AtomicInteger(0));
            }
            return existing;
        });

        int used = current.counter().incrementAndGet();
        Duration retryAfter = Duration.between(now, current.resetAt());

        if (used > limit) {
            return new Decision(false, 0, retryAfter.isNegative() ? Duration.ZERO : retryAfter);
        }
        return new Decision(true, limit - used, retryAfter);
    }

    @Override
    public void reset(String key) {
        windows.invalidate(key);
    }

    private record Window(Instant resetAt, AtomicInteger counter) {}
}
