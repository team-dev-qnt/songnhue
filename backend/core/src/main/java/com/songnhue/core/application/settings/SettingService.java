package com.songnhue.core.application.settings;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.songnhue.core.domain.settings.Setting;
import com.songnhue.core.infra.settings.SettingRepository;

/**
 * Đọc tham số nghiệp vụ từ bảng {@code settings} (quy tắc 12 của dự án).
 *
 * <p>WS-5 chỉ cần phần đọc: chính sách mật khẩu, ngưỡng khoá tài khoản, giờ hành chính. <b>WS-6 /
 * T6.11</b> mở rộng lớp này thành dịch vụ đầy đủ (ghi, validate theo cột {@code validation}, API cho
 * UI, xuất/nhập cấu hình loại trừ credential) — chỗ cắm đã chừa ở {@link #invalidate(String)}.
 *
 * <p><b>Vì sao có cache:</b> mỗi lần đăng nhập cần 4–5 tham số; không cache thì mỗi lần đăng nhập là
 * 5 vòng tới DB cho những giá trị gần như không bao giờ đổi. TTL ngắn để Admin sửa trên UI thấy có
 * hiệu lực ngay mà không phải khởi động lại.
 *
 * <p>⚠ Cache nằm trong tiến trình (Caffeine, không có Redis ở v1). Khi lên ≥2 node, sửa tham số ở
 * node này thì node kia còn giữ giá trị cũ tối đa hết TTL — mốc đó được ghi ở
 * {@code architecture-review.md} §6.4 cùng các thay đổi khác phải làm.
 */
@Service
public class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int CACHE_MAX_SIZE = 500;

    private final SettingRepository repository;

    /** Giữ cả giá trị rỗng để khoá không tồn tại cũng không phải hỏi DB lại mỗi lần. */
    private final Cache<String, Optional<String>> cache = Caffeine.newBuilder()
            .maximumSize(CACHE_MAX_SIZE)
            .expireAfterWrite(CACHE_TTL)
            .build();

    public SettingService(SettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<String> getString(String key) {
        return cache.get(
                key,
                k -> repository.findBySettingKey(k).map(Setting::effectiveValue).filter(value -> !value.isBlank()));
    }

    /**
     * @param fallback dùng khi thiếu khoá hoặc giá trị sai định dạng — hệ thống vẫn phải đăng nhập
     *     được kể cả khi ai đó lỡ tay xoá một dòng cấu hình
     */
    public int getInt(String key, int fallback) {
        return getString(key).map(value -> parseInt(key, value, fallback)).orElse(fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        return getString(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    public Duration getMinutes(String key, int fallbackMinutes) {
        return Duration.ofMinutes(getInt(key, fallbackMinutes));
    }

    /** Giá trị kiểu {@code TIME} trong bảng settings, VD {@code 08:00}. */
    public LocalTime getTime(String key, LocalTime fallback) {
        return getString(key).map(value -> parseTime(key, value, fallback)).orElse(fallback);
    }

    /** WS-6 / T6.11 gọi sau khi Admin sửa tham số, để thay đổi có hiệu lực ngay. */
    public void invalidate(String key) {
        cache.invalidate(key);
    }

    // -------------------------------------------------------------------------

    private static int parseInt(String key, String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            // Không ném: một dòng cấu hình hỏng không được phép làm sập đường đăng nhập
            log.warn("Tham số '{}' không phải số nguyên, dùng giá trị dự phòng {}", key, fallback);
            return fallback;
        }
    }

    private static LocalTime parseTime(String key, String value, LocalTime fallback) {
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            log.warn("Tham số '{}' không phải giờ hợp lệ (HH:mm), dùng giá trị dự phòng {}", key, fallback);
            return fallback;
        }
    }
}
